<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="编号"><el-input v-model="query.screenNo" clearable placeholder="如 A0001" style="width: 140px" /></el-form-item>
        <el-form-item label="框型">
          <el-select v-model="query.frameType" clearable style="width: 100px" placeholder="框型">
            <el-option v-for="f in FRAME_TYPES" :key="f" :label="`${f}框`" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容"><el-input v-model="query.content" clearable placeholder="网版内容关键字" style="width: 180px" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 110px" placeholder="状态">
            <el-option label="在用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['engineering:screen:add']" @click="openCreate">新增网版</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="screenNo" label="编号" width="110" />
        <el-table-column label="框型" width="80">
          <template #default="{ row }">{{ row.frameType }}框</template>
        </el-table-column>
        <el-table-column prop="content" label="网版内容记录" min-width="320" show-overflow-tooltip />
        <el-table-column prop="mesh" label="目数" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '在用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button v-hasPermi="['engineering:screen:edit']" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-hasPermi="['engineering:screen:edit']" link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
            <el-button v-hasPermi="['engineering:screen:delete']" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
    </el-card>

    <el-dialog v-model="formVisible" :title="isEdit ? '编辑网版' : '新增网版'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="网版编号" prop="screenNo"><el-input v-model="form.screenNo" :disabled="isEdit" placeholder="如 A0001" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="框型" prop="frameType">
            <el-select v-model="form.frameType" style="width: 100%">
              <el-option v-for="f in FRAME_TYPES" :key="f" :label="`${f}框`" :value="f" />
            </el-select>
          </el-form-item></el-col>
        </el-row>
        <el-form-item label="网版内容" prop="content"><el-input v-model="form.content" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="如：JST-464 反印覆银 JTT-056 反印导光油" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="目数"><el-input v-model="form.mesh" placeholder="如 150目（可选）" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="在用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageScreen, getScreen, addScreen, updateScreen, changeScreenStatus, delScreen } from '@/api/engineering/screen'

defineOptions({ name: 'ScreenMaster' })

const FRAME_TYPES = ['A', 'B', 'C', 'F', 'G', 'H']

const loading = ref(false)
const submitting = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const formVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()

const query = reactive<any>({ pageNum: 1, pageSize: 10, screenNo: '', frameType: undefined, content: '', status: undefined })
const form = reactive<any>({})
const rules = {
  screenNo: [{ required: true, message: '请输入网版编号', trigger: 'blur' }],
  frameType: [{ required: true, message: '请选择框型', trigger: 'change' }],
  content: [{ required: true, message: '请输入网版内容', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const res: any = await pageScreen({ ...query })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}
function search() { query.pageNum = 1; load() }
function reset() { Object.assign(query, { pageNum: 1, pageSize: 10, screenNo: '', frameType: undefined, content: '', status: undefined }); search() }

function emptyForm() {
  return { screenNo: '', frameType: 'A', content: '', mesh: '', status: 1, remark: '' }
}
function openCreate() {
  isEdit.value = false
  Object.assign(form, emptyForm())
  formVisible.value = true
}
async function openEdit(row: any) {
  isEdit.value = true
  const res: any = await getScreen(row.screenId)
  Object.assign(form, res.data || {})
  formVisible.value = true
}
async function submitForm() {
  if (!formRef.value || !await formRef.value.validate()) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateScreen(form)
    } else {
      await addScreen(form)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}
async function toggleStatus(row: any) {
  const target = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确认${target === 1 ? '启用' : '停用'}网版【${row.screenNo}】？`, '状态确认', { type: 'warning' })
  try {
    await changeScreenStatus(row.screenId, target)
    ElMessage.success('操作成功')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}
async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除网版【${row.screenNo}】？`, '删除确认', { type: 'warning' })
  try {
    await delScreen(row.screenId)
    ElMessage.success('删除成功')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.toolbar { margin-bottom: 14px; }
</style>

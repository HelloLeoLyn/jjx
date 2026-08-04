<template>
  <div class="app-container">
    <!-- 搜索区 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="产品">
          <el-select
            v-model="queryProductId"
            placeholder="选择产品查看菲林"
            filterable
            clearable
            style="width: 240px"
            @change="onProductChange"
          >
            <el-option
              v-for="p in productOptions"
              :key="p.productId"
              :label="`${p.productCode} - ${p.productName}`"
              :value="p.productId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="菲林名称">
          <el-input v-model="queryName" placeholder="模糊搜索" clearable style="width: 180px" @keyup.enter="loadFilms" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadFilms">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" :disabled="!queryProductId" @click="handleAdd">
          新增菲林
        </el-button>
        <span v-if="!queryProductId" class="toolbar-tip">请先选择产品</span>
      </div>

      <el-table :data="filmList" v-loading="loading" border stripe>
        <el-table-column label="菲林编码" prop="filmCode" width="140" />
        <el-table-column label="菲林名称" prop="filmName" min-width="150" show-overflow-tooltip />
        <el-table-column label="类型" prop="filmTypeName" width="110" />
        <el-table-column label="版本" prop="version" width="90" align="center">
          <template #default="scope">
            <span>{{ scope.row.version }}</span>
            <el-tag v-if="scope.row.isCurrent === 1" size="small" type="success" style="margin-left:4px">当前</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="尺寸" prop="filmSize" width="100" />
        <el-table-column label="厚度" width="90" align="center">
          <template #default="scope">{{ scope.row.filmThickness ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="审批状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="approveTagType(scope.row.approveStatus)" size="small">
              {{ scope.row.approveStatusName || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下发生产" width="90" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.isReleased === 1" type="success" size="small">已下发</el-tag>
            <el-tag v-else type="info" size="small">未下发</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="设计师" prop="designerName" width="90" />
        <el-table-column label="创建时间" width="150">
          <template #default="scope">{{ scope.row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="success" size="small" @click="handleNewVersion(scope.row)">新版本</el-button>
            <template v-if="scope.row.approveStatus === 1 || scope.row.approveStatus === undefined || scope.row.approveStatus === null">
              <el-button link type="warning" size="small" @click="handleSubmit(scope.row)">提交审批</el-button>
            </template>
            <template v-else-if="scope.row.approveStatus === 2">
              <el-button link type="success" size="small" @click="handleApprove(scope.row)">通过</el-button>
              <el-button link type="danger" size="small" @click="handleReject(scope.row)">驳回</el-button>
            </template>
            <el-button v-if="scope.row.approveStatus === 3" link type="primary" size="small" @click="handleSetCurrent(scope.row)">设当前</el-button>
            <el-button v-if="scope.row.approveStatus === 3 && !scope.row.isReleased" link type="success" size="small" @click="handleRelease(scope.row)">下发生产</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !filmList.length" description="暂无菲林数据" />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="form.filmId ? '编辑菲林' : '新增菲林'" v-model="dialogVisible" width="620px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="菲林名称" prop="filmName">
              <el-input v-model="form.filmName" placeholder="如：面板菲林" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="filmType">
              <el-select v-model="form.filmType" placeholder="选择类型" style="width:100%">
                <el-option label="面板菲林" value="OVERLAY" />
                <el-option label="上层线路菲林" value="UPPER_CIRCUIT" />
                <el-option label="间隔菲林" value="SPACER" />
                <el-option label="下层线路菲林" value="LOWER_CIRCUIT" />
                <el-option label="背胶菲林" value="BACK_ADHESIVE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="尺寸">
              <el-input v-model="form.filmSize" placeholder="如：50x30mm" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="厚度">
              <el-input-number v-model="form.filmThickness" :min="0" :precision="2" :controls="false" style="width:100%" placeholder="mm" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="材料">
              <el-input v-model="form.filmMaterial" placeholder="如：PET" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="颜色">
          <el-input v-model="form.color" placeholder="如：透明白" />
        </el-form-item>
        <el-form-item label="技术规格">
          <el-input v-model="form.technicalSpec" type="textarea" :rows="2" placeholder="技术规格说明" />
        </el-form-item>
        <el-form-item label="设计说明">
          <el-input v-model="form.designNotes" type="textarea" :rows="2" placeholder="设计备注" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { TagType } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { filmApi, type EngineeringFilm } from '@/api/product/film'
import { listProductPage } from '@/api/product'

defineOptions({ name: 'EngineeringFilm' })

const loading = ref(false)
const submitting = ref(false)
const filmList = ref<EngineeringFilm[]>([])
const productOptions = ref<any[]>([])
const queryProductId = ref<number | null>(null)
const queryName = ref('')
const dialogVisible = ref(false)

const form = reactive<Partial<EngineeringFilm>>({
  filmId: undefined,
  filmName: '',
  filmType: '',
  filmSize: '',
  filmThickness: undefined,
  filmMaterial: '',
  color: '',
  technicalSpec: '',
  designNotes: '',
  remark: '',
})

const rules = {
  filmName: [{ required: true, message: '菲林名称不能为空', trigger: 'blur' }],
  filmType: [{ required: true, message: '请选择类型', trigger: 'change' }],
}

// 加载产品下拉
async function loadProducts() {
  try {
    const res: any = await listProductPage({ pageNum: 1, pageSize: 100 })
    productOptions.value = res?.data?.records || res?.data?.rows || res?.data || []
  } catch {
    productOptions.value = []
  }
}

// 加载菲林列表
async function loadFilms() {
  if (!queryProductId.value) {
    filmList.value = []
    return
  }
  loading.value = true
  try {
    const res: any = await filmApi.getByProductId(queryProductId.value)
    let list = (res as any)?.data || []
    if (queryName.value) {
      list = list.filter((f: any) => (f.filmName || '').includes(queryName.value) || (f.filmCode || '').includes(queryName.value))
    }
    filmList.value = list
  } catch {
    filmList.value = []
  } finally {
    loading.value = false
  }
}

function onProductChange() {
  queryName.value = ''
  loadFilms()
}

function resetQuery() {
  queryProductId.value = null
  queryName.value = ''
  filmList.value = []
}

function approveTagType(status: number | undefined | null): TagType {
  const map: Record<number, TagType> = { 1: 'info', 2: 'warning', 3: 'success', 4: 'danger' }
  return map[status ?? 1] ?? 'info'
}

function handleAdd() {
  Object.assign(form, { filmId: undefined, filmName: '', filmType: '', filmSize: '', filmThickness: undefined, filmMaterial: '', color: '', technicalSpec: '', designNotes: '', remark: '' })
  dialogVisible.value = true
}

async function handleEdit(row: EngineeringFilm) {
  try {
    const res: any = await filmApi.getById(row.filmId!)
    Object.assign(form, res?.data || row)
    dialogVisible.value = true
  } catch {
    Object.assign(form, row)
    dialogVisible.value = true
  }
}

async function handleSubmitForm() {
  if (!queryProductId.value) return
  submitting.value = true
  try {
    const payload = { ...form, productId: queryProductId.value }
    if (form.filmId) {
      await filmApi.update(form.filmId, payload)
      ElMessage.success('修改成功')
    } else {
      await filmApi.create(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadFilms()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleSubmit(row: EngineeringFilm) {
  await filmApi.submitApprove(row.filmId!)
  ElMessage.success('已提交审批')
  loadFilms()
}

async function handleApprove(row: EngineeringFilm) {
  await ElMessageBox.confirm(`确认通过菲林「${row.filmName}」？`, '审批通过')
  await filmApi.approve(row.filmId!)
  ElMessage.success('已通过')
  loadFilms()
}

async function handleReject(row: EngineeringFilm) {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '审批驳回', {
      inputPlaceholder: '驳回原因（选填）',
    })
    await filmApi.reject(row.filmId!)
    ElMessage.success(value ? '已驳回' : '已驳回')
    loadFilms()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '驳回失败')
  }
}

async function handleNewVersion(row: EngineeringFilm) {
  await ElMessageBox.confirm(`确认为「${row.filmName}」创建新版本？`, '新版本')
  await filmApi.newVersion(row.filmId!)
  ElMessage.success('新版本已创建')
  loadFilms()
}

async function handleSetCurrent(row: EngineeringFilm) {
  await ElMessageBox.confirm(`确认将版本 ${row.version} 设为当前？`, '设为当前')
  await filmApi.setCurrent(row.filmId!)
  ElMessage.success('已设为当前版本')
  loadFilms()
}

async function handleRelease(row: EngineeringFilm) {
  await ElMessageBox.confirm(`确认将「${row.filmName}」下发生产？`, '下发生产', { type: 'warning' })
  await filmApi.release(row.filmId!)
  ElMessage.success('已下发生产')
  loadFilms()
}

async function handleDelete(row: EngineeringFilm) {
  await ElMessageBox.confirm(`确认删除菲林「${row.filmName}」？`, '删除确认', { type: 'warning' })
  await filmApi.remove(row.filmId!)
  ElMessage.success('已删除')
  loadFilms()
}

onMounted(() => {
  loadProducts()
})
</script>

<style scoped>
.search-card, .table-card { margin-bottom: 16px; }
.toolbar { margin-bottom: 12px; display: flex; align-items: center; gap: 12px; }
.toolbar-tip { color: #909399; font-size: 12px; }
</style>

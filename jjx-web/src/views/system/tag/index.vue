<template>
  <div class="tag-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">标签管理</span>
          <span class="card-sub">通用标签体系：按分组维护标签，各业务模块（供应商/物料/产品等）均可挂标签</span>
        </div>
      </template>

      <!-- 搜索 -->
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="标签分组">
          <el-select v-model="query.tagGroup" placeholder="全部分组" clearable style="width: 200px" @change="getList">
            <el-option v-for="g in groupOptions" :key="g.value" :label="g.label" :value="g.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="标签名称/编码" clearable style="width: 200px" @keyup.enter="getList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="getList">
            <el-option label="启用" :value="CommonStatusEnum.NORMAL.value" />
            <el-option label="停用" :value="CommonStatusEnum.DISABLED.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getList">查询</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 操作栏 -->
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" v-hasPermi="['system:tag:add']" @click="handleAdd">新增标签</el-button>
        </el-col>
      </el-row>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="list" border>
        <el-table-column label="标签编码" prop="tagCode" min-width="160" show-overflow-tooltip />
        <el-table-column label="标签名称" prop="tagName" min-width="140" show-overflow-tooltip />
        <el-table-column label="分组" prop="tagGroup" width="150">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ groupLabel(row.tagGroup) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="父标签" width="130">
          <template #default="{ row }">{{ parentName(row.parentId) || '-' }}</template>
        </el-table-column>
        <el-table-column label="排序" prop="sortOrder" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="CommonStatusEnum.getTagProps(row.status).type as any">
              {{ CommonStatusEnum.getLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['system:tag:edit']" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" v-hasPermi="['system:tag:delete']" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="800px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标签分组" prop="tagGroup">
          <el-select v-model="form.tagGroup" placeholder="请选择分组" style="width: 100%" :disabled="isEdit">
            <el-option v-for="g in groupOptions" :key="g.value" :label="g.label" :value="g.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="父标签" prop="parentId">
          <el-select v-model="form.parentId" placeholder="不选=顶级标签" clearable style="width: 100%" :disabled="isEdit">
            <el-option v-for="t in parentOptions" :key="t.tagId" :label="t.tagName" :value="t.tagId!" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="form.tagName" placeholder="如 塑料制品 / 薄膜" maxlength="100" />
        </el-form-item>
        <el-form-item label="标签编码" prop="tagCode">
          <el-input v-model="form.tagCode" placeholder="留空自动生成（二级为 父编码*名称）" maxlength="64" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="CommonStatusEnum.NORMAL.value">启用</el-radio>
            <el-radio :value="CommonStatusEnum.DISABLED.value">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { tagApi } from '@/api/system/tag'
import { dictApi } from '@/api/system/dict'
import type { SysTag } from '@/types/system/tag'
import { CommonStatusEnum } from '@/enums/common/StatusEnum'

interface GroupOption {
  value: string
  label: string
}

const loading = ref(false)
const submitting = ref(false)
const list = ref<SysTag[]>([])
const groupOptions = ref<GroupOption[]>([])

const query = reactive<{ tagGroup?: string; keyword?: string; status?: number }>({
  tagGroup: undefined,
  keyword: '',
  status: undefined,
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const dialogTitle = computed(() => (isEdit.value ? '编辑标签' : '新增标签'))
const formRef = ref<FormInstance>()

const defaultForm = (): SysTag => ({
  tagId: undefined,
  tagCode: '',
  tagName: '',
  tagGroup: 'supplier_goods',
  parentId: null,
  sortOrder: 0,
  status: 1,
  remark: '',
})
const form = reactive<SysTag>(defaultForm())

const rules: FormRules = {
  tagGroup: [{ required: true, message: '请选择标签分组', trigger: 'change' }],
  tagName: [{ required: true, message: '请输入标签名称', trigger: 'blur' }],
}

/** 当前分组下的顶级标签（作为父标签候选） */
const parentOptions = computed(() =>
  list.value.filter((t) => t.tagGroup === form.tagGroup && !t.parentId && t.tagId !== form.tagId),
)

function groupLabel(code?: string) {
  if (!code) return '-'
  return groupOptions.value.find((g) => g.value === code)?.label || code
}

function parentName(parentId?: number | null) {
  if (!parentId) return ''
  return list.value.find((t) => t.tagId === parentId)?.tagName || ''
}

async function loadGroups() {
  try {
    const res: any = await dictApi.getItems('sys_tag_group')
    const items = res?.data || []
    groupOptions.value = items.map((i: any) => ({
      value: i.itemKey ?? i.itemValue,
      label: i.label || i.itemValue,
    }))
  } catch {
    groupOptions.value = []
  }
}

async function getList() {
  loading.value = true
  try {
    const res: any = await tagApi.list({
      tagGroup: query.tagGroup,
      keyword: query.keyword || undefined,
      status: query.status,
    })
    list.value = res?.data || []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.tagGroup = undefined
  query.keyword = ''
  query.status = undefined
  getList()
}

function handleAdd() {
  isEdit.value = false
  Object.assign(form, defaultForm())
  if (query.tagGroup) form.tagGroup = query.tagGroup
  dialogVisible.value = true
}

function handleEdit(row: SysTag) {
  isEdit.value = true
  Object.assign(form, defaultForm(), row, { parentId: row.parentId ?? null })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (isEdit.value && form.tagId) {
      await tagApi.update(form.tagId, form)
    } else {
      await tagApi.add(form)
    }
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    getList()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SysTag) {
  await ElMessageBox.confirm(`确认删除标签「${row.tagName}」？删除后已挂该标签的业务关联也会一并清除。`, '提示', {
    type: 'warning',
  })
  await tagApi.remove([row.tagId!])
  ElMessage.success('删除成功')
  getList()
}

onMounted(async () => {
  await loadGroups()
  getList()
})
</script>

<style scoped>
.tag-page {
  padding: 16px;
}

.card-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.card-sub {
  font-size: 12px;
  color: #909399;
}

.search-form {
  margin-bottom: 8px;
}

.mb8 {
  margin-bottom: 8px;
}
</style>

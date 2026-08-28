<template>
  <div class="dict-page">
    <el-alert
      title="状态类系统字典由后端枚举自动导入，仅供查看，页面显示以代码枚举为准；工序类型、工序类目等运营字典仍可编辑。"
      type="info"
      show-icon
      :closable="false"
      class="readonly-alert"
    />
    <!-- 搜索表单 -->
    <SearchForm
      v-model="queryParams"
      :fields="uiConfig.searchOptions"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 工具栏 -->
    <Toolbar
      :buttons="uiConfig.toolbarOptions"
      :selected-count="selectedRows.length"
      :show-batch-bar="true"
      @click="handleToolbarClick"
      @refresh="handleRefresh"
    >
      <template #batch-actions>
        <el-button
          :disabled="selectedRows.length === 0 || selectedRows.some(isSystemDict)"
          type="danger"
          size="small"
          @click="handleBatchDelete"
          v-hasPermi="['system:dict:delete']"
        >
          批量删除
        </el-button>
      </template>
    </Toolbar>

    <!-- 数据表格 -->
    <DataTable
      :data="dictList"
      :loading="loading"
      :total="total"
      :columns="uiConfig.tableOptions"
      :show-action="true"
      :action-width="280"
      @selection-change="handleSelectionChange"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <!-- 状态列自定义渲染 -->
      <template #isActive="{ row }">
        <el-tag :type="row.isActive === 1 ? 'success' : 'danger'" size="small">
          {{ row.isActive === 1 ? '启用' : '禁用' }}
        </el-tag>
      </template>

      <!-- 创建时间列自定义渲染 -->
      <template #createTime="{ row }">
        <span>{{ parseTime(row.createTime) }}</span>
      </template>

      <!-- 操作列 -->
      <template #action="{ row }">
        <el-tooltip :content="readonlyTip" :disabled="!isSystemDict(row)">
          <span><el-button link type="primary" :disabled="isSystemDict(row)" @click="handleEdit(row)" v-hasPermi="['system:dict:edit']">修改</el-button></span>
        </el-tooltip>
        <el-button
          link
          type="primary"
          @click="handleManageItems(row)"
          v-hasPermi="['system:dict:query']"
        >
          字典项
        </el-button>
        <el-tag v-if="isSystemDict(row)" type="info" size="small">系统字典</el-tag>
        <el-tooltip :content="readonlyTip" :disabled="!isSystemDict(row)">
          <span><el-button link :type="row.isActive === 1 ? 'warning' : 'success'" :disabled="isSystemDict(row)" @click="handleToggleStatus(row)" v-hasPermi="['system:dict:edit']">{{ row.isActive === 1 ? '禁用' : '启用' }}</el-button></span>
        </el-tooltip>
        <el-tooltip :content="readonlyTip" :disabled="!isSystemDict(row)">
          <span><el-button link type="danger" :disabled="isSystemDict(row)" @click="handleDelete(row)" v-hasPermi="['system:dict:delete']">删除</el-button></span>
        </el-tooltip>
      </template>
    </DataTable>

    <!-- 字典类型表单对话框 -->
    <DictFormDialog
      v-model:visible="dialogVisible"
      :title="dialogTitle"
      :form-data="formData"
      :submit-loading="submitLoading"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />

    <!-- 字典项管理对话框 -->
    <DictItemDialog
      v-model:visible="itemDialogVisible"
      :dict-code="currentDictCode"
      :dict-name="currentDictName"
      :readonly="currentDictReadonly"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'Dict',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Toolbar, DataTable, SearchForm } from '@/components/common-ui/index'
import { dictApi } from '@/api/system/dict'
import type { SysDict, SysDictDTO } from '@/types/system/dict'
import * as uiConfig from './index'
import DictFormDialog from './components/DictFormDialog.vue'
import DictItemDialog from './components/DictItemDialog.vue'

// ==================== 响应式数据 ====================
const loading = ref(false)
const dictList = ref<SysDict[]>([])
const total = ref(0)
const selectedRows = ref<SysDict[]>([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  dictCode: '',
  dictName: '',
  isActive: undefined as number | undefined,
})

// 表单对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formData = ref<SysDictDTO>({
  dictId: undefined,
  dictCode: '',
  dictName: '',
  remark: '',
  sortOrder: 0,
  isActive: 1,
})
const submitLoading = ref(false)

// 字典项管理对话框
const itemDialogVisible = ref(false)
const currentDictCode = ref('')
const currentDictName = ref('')
const currentDictReadonly = ref(false)

// ==================== 辅助函数 ====================
const readonlyTip = '由后端枚举自动导入，页面显示以代码枚举为准，此处仅供查看'
const isSystemDict = (dict: SysDict) => dict.remark?.includes('自动导入') === true
const parseTime = (time: string) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

// ==================== API 请求 ====================
const getList = async () => {
  loading.value = true
  try {
    const res = await dictApi.list({
      ...queryParams,
    })
    dictList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

// ==================== 事件处理 ====================
const handleSearch = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.dictCode = ''
  queryParams.dictName = ''
  queryParams.isActive = undefined
  getList()
}

const handleRefresh = () => {
  getList()
}

const handleSelectionChange = (selection: SysDict[]) => {
  selectedRows.value = selection
}

const handlePageChange = (page: number) => {
  queryParams.pageNum = page
  getList()
}

const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  getList()
}

const handleToolbarClick = (key: string) => {
  if (key === 'add') handleAdd()
}

const handleAdd = () => {
  dialogTitle.value = '新增字典类型'
  formData.value = {
    dictId: undefined,
    dictCode: '',
    dictName: '',
    remark: '',
    sortOrder: 0,
    isActive: 1,
  }
  dialogVisible.value = true
}

const handleEdit = async (row: SysDict) => {
  dialogTitle.value = '修改字典类型'
  formData.value = {
    dictId: row.dictId,
    dictCode: row.dictCode,
    dictName: row.dictName,
    remark: row.remark,
    sortOrder: row.sortOrder,
    isActive: row.isActive,
  }
  dialogVisible.value = true
}

const handleDelete = async (row: SysDict) => {
  await ElMessageBox.confirm(`是否确认删除字典"${row.dictName}"？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await dictApi.remove([row.dictId!])
  ElMessage.success('删除成功')
  getList()
}

const handleBatchDelete = async () => {
  if (selectedRows.value.some(isSystemDict)) {
    ElMessage.warning(readonlyTip)
    return
  }
  const ids = selectedRows.value.map((item) => item.dictId!)
  const names = selectedRows.value.map((item) => item.dictName).join(',')
  await ElMessageBox.confirm(`是否确认删除字典"${names}"？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await dictApi.remove(ids)
  ElMessage.success('删除成功')
  getList()
}

const handleToggleStatus = async (row: SysDict) => {
  const newStatus = row.isActive === 1 ? 0 : 1
  const statusText = newStatus === 1 ? '启用' : '禁用'
  await ElMessageBox.confirm(`是否确认${statusText}字典"${row.dictName}"？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await dictApi.changeStatus(row.dictId!, newStatus)
  ElMessage.success(`${statusText}成功`)
  getList()
}

const handleManageItems = (row: SysDict) => {
  currentDictCode.value = row.dictCode
  currentDictName.value = row.dictName
  currentDictReadonly.value = isSystemDict(row)
  itemDialogVisible.value = true
}

const handleSubmit = async () => {
  submitLoading.value = true
  try {
    if (formData.value.dictId) {
      await dictApi.update(formData.value.dictId, formData.value)
      ElMessage.success('修改成功')
    } else {
      await dictApi.add(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleCancel = () => {
  dialogVisible.value = false
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.dict-page {
  padding: 20px;
}
.readonly-alert { margin-bottom: 16px; }
</style>

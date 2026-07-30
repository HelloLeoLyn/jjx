<template>
  <div class="exception-log-page">
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
    </Toolbar>

    <!-- 数据表格 -->
    <DataTable
      :data="logList"
      :loading="loading"
      :total="total"
      :columns="uiConfig.tableOptions"
      :show-action="true"
      :action-min-width="250"
      @selection-change="handleSelectionChange"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <!-- 操作列 -->
      <template #action="{ row }">
        <el-button
          link
          type="primary"
          @click="handleView(row)"
          v-hasPermi="['system:log:exception:view']"
        >
          查看
        </el-button>
      </template>
    </DataTable>

    <!-- 日志详情对话框 -->
    <DialogForm
      v-model:visible="dialogVisible"
      :title="dialogTitle"
      :form-data="formData"
      :fields="formOptions"
      :rules="formRules"
      :submit-loading="submitLoading"
      label-width="100px"
      width="900px"
      :show-footer="false"
      @cancel="handleCancel"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'LogException',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Toolbar, DataTable, DialogForm, SearchForm } from '@/components/common-ui/index'
import { exceptionLogApi } from '@/api/system/exception-log'
import type { SysErrorLog, SysExceptionLogQuery } from '@/types/system'
import * as uiConfig from './index'

// 响应式数据
const loading = ref(false)
const logList = ref<SysErrorLog[]>([])
const total = ref(0)
const selectedRows = ref<SysErrorLog[]>([])
const queryParams = reactive<SysExceptionLogQuery>({
  pageNum: 1,
  pageSize: 10,
  exceptionName: undefined,
  requestUrl: undefined,
  handleStatus: undefined,
})

// 表单对话框
const dialogVisible = ref(false)
const dialogTitle = ref('异常日志详情')
const formData = ref<SysErrorLog>({})
const formOptions = ref(uiConfig.getFormOptions())
const formRules = ref({}) // 查看详情不需要验证规则
const submitLoading = ref(false)

// ==================== API 请求 ====================
const getList = async () => {
  loading.value = true
  try {
    const res = await exceptionLogApi.list(queryParams)
    logList.value = res.data?.records || []
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
  queryParams.exceptionName = undefined
  queryParams.requestUrl = undefined
  queryParams.handleStatus = undefined
  getList()
}

const handleRefresh = () => {
  getList()
}

const handleSelectionChange = (selection: SysErrorLog[]) => {
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
  if (key === 'export') handleExport()
}

const handleView = async (row: SysErrorLog) => {
  try {
    if (row.id) {
      const res = await exceptionLogApi.getInfo(row.id)
      if (res.data) {
        formData.value = res.data
        dialogVisible.value = true
      }
    }
  } catch (error) {
    console.error('获取日志详情失败:', error)
    ElMessage.error('获取日志详情失败')
  }
}

const handleExport = () => {
  ElMessage.info('导出功能待实现')
}

const handleCancel = () => {
  dialogVisible.value = false
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.exception-log-page {
  padding: 20px;
}
</style>

<template>
  <div class="login-log-page">
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
      <!-- 登录状态列自定义渲染 -->
      <template #status="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
          {{ row.status === 1 ? '成功' : '失败' }}
        </el-tag>
      </template>

      <!-- 操作列 -->
      <template #action="{ row }">
        <el-button
          link
          type="primary"
          @click="handleView(row)"
          v-hasPermi="['system:log:login:view']"
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
      width="800px"
      :show-footer="false"
      @cancel="handleCancel"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'LogLogin',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Toolbar, DataTable, DialogForm, SearchForm } from '@/components/common-ui/index'
import { loginLogApi } from '@/api/system/login-log'
import type { SysLoginLog, SysLoginLogQuery } from '@/types/system'
import * as uiConfig from './index'

// 响应式数据
const loading = ref(false)
const logList = ref<SysLoginLog[]>([])
const total = ref(0)
const selectedRows = ref<SysLoginLog[]>([])
const queryParams = reactive<SysLoginLogQuery>({
  pageNum: 1,
  pageSize: 10,
  username: undefined,
  loginType: undefined,
  status: undefined,
})

// 表单对话框
const dialogVisible = ref(false)
const dialogTitle = ref('登录日志详情')
const formData = ref<SysLoginLog>({})
const formOptions = ref(uiConfig.getFormOptions())
const formRules = ref({}) // 查看详情不需要验证规则
const submitLoading = ref(false)

// ==================== API 请求 ====================
const getList = async () => {
  loading.value = true
  try {
    const res = await loginLogApi.list(queryParams)
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
  queryParams.username = undefined
  queryParams.loginType = undefined
  queryParams.status = undefined
  getList()
}

const handleRefresh = () => {
  getList()
}

const handleSelectionChange = (selection: SysLoginLog[]) => {
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

const handleView = async (row: SysLoginLog) => {
  try {
    if (row.id) {
      const res = await loginLogApi.getInfo(row.id)
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
.login-log-page {
  padding: 20px;
}
</style>

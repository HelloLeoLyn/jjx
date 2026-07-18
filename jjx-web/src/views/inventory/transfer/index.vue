<template>
  <div class="transfer-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="调拨单号">
          <el-input
            v-model="queryParams.transferNo"
            placeholder="请输入调拨单号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="调拨类型">
          <el-select
            v-model="queryParams.transferType"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="仓库调拨" value="warehouse" />
            <el-option label="库位调拨" value="location" />
            <el-option label="紧急调拨" value="urgent" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="待调拨" value="draft" />
            <el-option label="调拨中" value="in_progress" />
            <el-option label="已完成" value="completed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <el-card class="operation-card">
      <el-row :gutter="10">
        <el-col :span="1.5">
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>新建调拨单
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleExport">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleRefresh">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card">
      <el-table v-loading="loading" :data="transferList" border style="width: 100%">
        <el-table-column label="调拨单号" prop="transferNo" width="150" />
        <el-table-column label="调拨类型" prop="transferTypeName" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getTransferTypeTag(row.transferType)" size="small">
              {{ row.transferTypeName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="源仓库" prop="fromWarehouseName" width="120" />
        <el-table-column label="目标仓库" prop="toWarehouseName" width="120" />
        <el-table-column label="总数量" prop="totalQuantity" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.totalQuantity) }}
          </template>
        </el-table-column>
        <el-table-column label="总金额" prop="totalAmount" width="120" align="right">
          <template #default="{ row }"> ¥ {{ formatCurrency(row.totalAmount) }} </template>
        </el-table-column>
        <el-table-column label="状态" prop="statusName" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" size="small">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createBy" width="100" />
        <el-table-column label="创建时间" prop="createTime" width="150" align="center" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button v-if="row.status === 'draft'" link type="primary" @click="handleEdit(row)"
              >编辑</el-button
            >
            <el-button v-if="row.status === 'draft'" link type="success" @click="handleStart(row)"
              >开始调拨</el-button
            >
            <el-button
              v-if="row.status === 'in_progress'"
              link
              type="warning"
              @click="handleComplete(row)"
              >完成调拨</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'TransferList',
})

import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Download, Refresh } from '@element-plus/icons-vue'
import { formatCurrency, formatNumber } from '@/utils/format'

const router = useRouter()

// 查询参数
const queryParams = reactive({
  current: 1,
  pageSize: 10,
  transferNo: '',
  transferType: '',
  status: '',
})

// 响应式数据
const loading = ref(false)
const transferList = ref<any[]>([])
const total = ref(0)

// 模拟数据
const mockTransferData = [
  {
    transferId: '1',
    transferNo: 'TR20240328001',
    transferType: 'warehouse',
    transferTypeName: '仓库调拨',
    fromWarehouseId: '1',
    fromWarehouseName: '原材料仓库',
    toWarehouseId: '2',
    toWarehouseName: '成品仓库',
    totalQuantity: 100,
    totalAmount: 5000,
    status: 'completed',
    statusName: '已完成',
    createBy: '张三',
    createTime: '2024-03-28 10:00:00',
  },
  {
    transferId: '2',
    transferNo: 'TR20240328002',
    transferType: 'location',
    transferTypeName: '库位调拨',
    fromWarehouseId: '2',
    fromWarehouseName: '成品仓库',
    toWarehouseId: '2',
    toWarehouseName: '成品仓库',
    fromLocationId: 'A01',
    fromLocationName: 'A01库位',
    toLocationId: 'B01',
    toLocationName: 'B01库位',
    totalQuantity: 50,
    totalAmount: 2500,
    status: 'in_progress',
    statusName: '调拨中',
    createBy: '李四',
    createTime: '2024-03-28 11:00:00',
  },
  {
    transferId: '3',
    transferNo: 'TR20240328003',
    transferType: 'urgent',
    transferTypeName: '紧急调拨',
    fromWarehouseId: '3',
    fromWarehouseName: '半成品仓库',
    toWarehouseId: '1',
    toWarehouseName: '原材料仓库',
    totalQuantity: 30,
    totalAmount: 1500,
    status: 'draft',
    statusName: '待调拨',
    createBy: '王五',
    createTime: '2024-03-28 12:00:00',
  },
]

// 获取调拨单列表
const getList = async () => {
  loading.value = true
  try {
    // 模拟API调用
    await new Promise((resolve) => setTimeout(resolve, 500))

    // 过滤数据
    let filteredData = [...mockTransferData]

    if (queryParams.transferNo) {
      filteredData = filteredData.filter((item) => item.transferNo.includes(queryParams.transferNo))
    }

    if (queryParams.transferType) {
      filteredData = filteredData.filter((item) => item.transferType === queryParams.transferType)
    }

    if (queryParams.status) {
      filteredData = filteredData.filter((item) => item.status === queryParams.status)
    }

    // 模拟分页
    const start = (queryParams.current - 1) * queryParams.pageSize
    const end = start + queryParams.pageSize
    transferList.value = filteredData.slice(start, end)
    total.value = filteredData.length
  } catch (error) {
    console.error('获取调拨单列表失败:', error)
    ElMessage.error('获取调拨单列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  queryParams.current = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.current = 1
  queryParams.transferNo = ''
  queryParams.transferType = ''
  queryParams.status = ''
  getList()
}

// 新建调拨单
const handleCreate = () => {
  router.push('/inventory/transfer/create')
}

// 导出
const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

// 刷新
const handleRefresh = () => {
  getList()
  ElMessage.success('数据已刷新')
}

// 查看详情
const handleView = (row: any) => {
  router.push(`/inventory/transfer/detail/${row.transferId}`)
}

// 编辑调拨单
const handleEdit = (row: any) => {
  router.push(`/inventory/transfer/edit/${row.transferId}`)
}

// 开始调拨
const handleStart = (row: any) => {
  ElMessage.success(`开始调拨: ${row.transferNo}`)
  getList()
}

// 完成调拨
const handleComplete = (row: any) => {
  ElMessage.success(`完成调拨: ${row.transferNo}`)
  getList()
}

// 获取调拨类型标签样式
const getTransferTypeTag = (
  type: string
): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const typeMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    warehouse: 'success',
    location: 'warning',
    urgent: 'danger',
  }
  return typeMap[type]
}

// 获取状态标签样式
const getStatusTag = (status: string): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const statusMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    draft: 'info',
    in_progress: 'warning',
    completed: 'success',
    cancelled: 'danger',
  }
  return statusMap[status]
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.transfer-list {
  padding: 20px;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}
</style>

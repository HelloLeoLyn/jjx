<template>
  <div class="outbound-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="出库单号">
          <el-input
            v-model="queryParams.outboundNo"
            placeholder="请输入出库单号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="出库类型">
          <el-select
            v-model="queryParams.outboundType"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="销售出库" value="sales" />
            <el-option label="生产领料" value="production" />
            <el-option label="退货出库" value="return" />
            <el-option label="调拨出库" value="transfer" />
            <el-option label="其他出库" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库">
          <el-select
            v-model="queryParams.warehouseId"
            placeholder="请选择仓库"
            clearable
            style="width: 150px"
          >
            <el-option label="原材料仓库" value="1" />
            <el-option label="成品仓库" value="2" />
            <el-option label="半成品仓库" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="待提交" :value="0" />
            <el-option label="待审批" :value="1" />
            <el-option label="已审批" :value="2" />
            <el-option label="已出库" :value="6" />
            <el-option label="已取消" :value="9" />
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
            <el-icon><Plus /></el-icon>新建出库单
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
      <el-table v-loading="loading" :data="outboundList" border style="width: 100%">
        <el-table-column label="出库单号" prop="outboundNo" width="150" />
        <el-table-column label="出库类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getOutboundTypeTag(row.outboundType)" size="small">
              {{ row.outboundTypeName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="仓库" prop="warehouseName" width="120" />
        <el-table-column label="客户" prop="customerName" width="150" show-overflow-tooltip />
        <el-table-column label="总数量" prop="totalQuantity" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.totalQuantity) }}
          </template>
        </el-table-column>
        <el-table-column label="总金额" prop="totalAmount" width="120" align="right">
          <template #default="{ row }"> ¥ {{ formatCurrency(row.totalAmount) }} </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" size="small">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createBy" width="100" />
        <el-table-column label="创建时间" prop="createTime" width="150" align="center" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button v-if="row.status === 0" link type="primary" @click="handleEdit(row)"
              >编辑</el-button
            >
            <el-button
              v-if="row.status === 2"
              link
              type="warning"
              @click="handleConfirm(row)"
              >确认出库</el-button
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
  name: 'OutboundList',
})

import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Download, Refresh } from '@element-plus/icons-vue'
import { outboundApi } from '@/api/inventory/outbound'
import { formatCurrency, formatNumber } from '@/utils/format'
import type { OutboundQueryParams, OutboundVO } from '@/types/inventory/outbound'

const router = useRouter()

// 查询参数
const queryParams = reactive<OutboundQueryParams>({
  current: 1,
  pageSize: 10,
  outboundNo: '',
  outboundType: '',
  warehouseId: '',
  status: '',
})

// 响应式数据
const loading = ref(false)
const outboundList = ref<OutboundVO[]>([])
const total = ref(0)

// 获取出库单列表
const getList = async () => {
  loading.value = true
  try {
    const res = await outboundApi.list(queryParams)
    outboundList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取出库单列表失败:', error)
    ElMessage.error('获取出库单列表失败')
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
  queryParams.outboundNo = ''
  queryParams.outboundType = ''
  queryParams.warehouseId = ''
  queryParams.status = ''
  getList()
}

// 新建出库单
const handleCreate = () => {
  router.push('/inventory/outbound/create')
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
const handleView = (row: OutboundVO) => {
  ElMessage.info(`查看出库单详情: ${row.outboundNo}`)
}

// 编辑出库单
const handleEdit = (row: OutboundVO) => {
  router.push(`/inventory/outbound/edit/${row.outboundId}`)
}

// 确认出库
const handleConfirm = (row: OutboundVO) => {
  ElMessage.success(`确认出库成功: ${row.outboundNo}`)
  getList()
}

// 获取出库类型标签样式
const getOutboundTypeTag = (
  type: string
): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const typeMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    sales: 'success',
    production: 'warning',
    return: 'info',
    transfer: 'danger',
    other: undefined,
  }
  return typeMap[type]
}

// 获取状态标签样式
const getStatusTag = (status: number): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const statusMap: Record<number, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    0: 'info',    // draft
    1: 'warning', // pending
    2: 'success', // approved
    4: 'warning', // processing
    6: 'success', // out_confirm
    9: 'danger',  // cancelled
  }
  return statusMap[status]
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.outbound-list {
  padding: 20px;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}
</style>

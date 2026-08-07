<template>
  <div class="purchase-order">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">采购订单管理</h1>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-section">
      <h3 class="stats-title">订单统计</h3>
      <el-row :gutter="20">
        <el-col v-for="card in statCards" :key="card.title" :span="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-content">
              <div class="stat-icon" :style="{ backgroundColor: card.color }">
                <el-icon>
                  <component :is="card.icon" />
                </el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ card.value }}</div>
                <div class="stat-label">{{ card.title }}</div>
                <div class="stat-description">{{ card.description }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 操作按钮区域 -->
    <el-card class="operation-card" shadow="never">
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="success"
            plain
            icon="Edit"
            :disabled="!single || !selectedOrderEditable"
            @click="() => handleUpdate()"
            >修改</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="info" plain icon="Document" :disabled="!single" @click="handleExportPdf">导出PDF</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="info"
            plain
            icon="Send"
            :disabled="!multiple || !selectedOrderSubmittable"
            @click="handleSubmitReview"
            >提交审批</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="success"
            plain
            icon="Check"
            :disabled="!single || !selectedOrderApprovable"
            @click="() => handleApprove()"
            >审批</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="primary"
            plain
            icon="Location"
            :disabled="!single || !selectedOrderReceivable"
            @click="() => handleReceive()"
            >收货</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="warning"
            plain
            icon="Money"
            :disabled="!single || !selectedOrderPayable"
            @click="() => handlePayment()"
            >付款</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="info"
            plain
            icon="CopyDocument"
            :disabled="!single"
            @click="() => handleCopy()"
            >复制</el-button
          >
        </el-col>
      </el-row>
    </el-card>

    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="100px">
        <el-form-item label="订单号" prop="orderNo">
          <el-input
            v-model="queryParams.orderNo"
            placeholder="请输入订单号"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="供应商名称" prop="supplierName">
          <el-input
            v-model="queryParams.supplierName"
            placeholder="请输入供应商名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="审批状态" prop="approvalStatus">
          <el-select
            v-model="queryParams.approvalStatus"
            placeholder="请选择审批状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="dict in approvalStatusOptions"
              :key="dict.value"
              :label="dict.label"
              :value="Number(dict.value as any)"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="orderList"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="订单号" align="center" prop="orderNo" width="160">
          <template #default="scope">
            <el-tooltip content="打印" placement="top">
              <el-button link type="info" @click="handlePrint(scope.row)">打印</el-button>
            </el-tooltip>
            <el-tooltip content="详情" placement="top">
              <el-button link type="primary" @click="() => handleView(scope.row)">{{
                scope.row.orderNo
              }}</el-button>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="供应商名称" align="center" prop="supplierName" width="120" />
        <el-table-column label="订单日期" align="center" prop="orderDate" width="120">
          <template #default="scope">
            <span>{{ formatDate(scope.row.orderDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="交货日期" align="center" prop="expectedDeliveryDate" width="120">
          <template #default="scope">
            <span>{{ formatDate(scope.row.expectedDeliveryDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审批状态" prop="approvalStatus" width="100">
          <template #default="scope">
            <el-tag :type="PurchaseEnum.approvalStatus.getTagProps(scope.row.approvalStatus).type">
              {{ PurchaseEnum.approvalStatus.getLabel(scope.row.approvalStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收货状态" prop="receiptStatus" width="100">
          <template #default="scope">
            <el-tag :type="PurchaseEnum.receiptStatus.getTagProps(scope.row.receiptStatus).type">
              {{ PurchaseEnum.receiptStatus.getLabel(scope.row.receiptStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="付款状态" prop="paymentStatus" width="100">
          <template #default="scope">
            <el-tag :type="PurchaseEnum.paymentStatus.getTagProps(scope.row.paymentStatus).type">
              {{ PurchaseEnum.paymentStatus.getLabel(scope.row.paymentStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="币种" align="center" prop="currency" width="60" />
        <el-table-column label="总金额" align="center" prop="orderTotalAmount" width="100">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.orderTotalAmount, scope.row.currency) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="紧急" align="center" prop="urgentFlag" width="60">
          <template #default="scope">
            <el-tag v-if="scope.row.urgentFlag" type="danger">紧急</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ formatDate(scope.row.createTime || '', 'YYYY-MM-DD HH:mm:ss') }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          align="left"
          class-name="small-padding fixed-width"
          width="300"
        >
          <template #default="scope">
            <!-- 复制按钮（始终显示） -->
            <el-tooltip content="复制" placement="top">
              <el-button
                link
                type="info"
                icon="CopyDocument"
                @click="() => handleCopy(scope.row)"
              ></el-button>
            </el-tooltip>
            <!-- 修改按钮（草稿和已拒绝可修改） -->
            <el-tooltip
              v-if="isOrderEditable(scope.row.approvalStatus)"
              content="修改"
              placement="top"
            >
              <el-button
                link
                type="primary"
                icon="Edit"
                @click="() => handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <!-- 取消按钮（草稿、待审批、已拒绝可取消） -->
            <el-tooltip
              v-if="isOrderCancellable(scope.row.approvalStatus)"
              content="取消"
              placement="top"
            >
              <el-button
                link
                type="danger"
                icon="Delete"
                @click="() => openPreview('purchase.cancel', scope.row)"
              ></el-button>
            </el-tooltip>
            <!-- 审批按钮（待审批可审批） -->
            <el-tooltip
              v-if="isOrderApprovable(scope.row.approvalStatus)"
              content="审批通过"
              placement="top"
            >
              <el-button
                link
                type="warning"
                icon="Check"
                @click="() => openPreview('purchase.approve', scope.row)"
              ></el-button>
            </el-tooltip>
            <!-- 驳回按钮（待审批可驳回） -->
            <el-tooltip
              v-if="isOrderApprovable(scope.row.approvalStatus)"
              content="审批驳回"
              placement="top"
            >
              <el-button
                link
                type="danger"
                icon="CloseBold"
                @click="() => openPreview('purchase.reject', scope.row)"
              ></el-button>
            </el-tooltip>
            <!-- 提交审核按钮（草稿可提交） -->
            <el-tooltip
              v-if="scope.row.approvalStatus === 1"
              content="提交审核"
              placement="top"
            >
              <el-button
                link
                type="primary"
                icon="Promotion"
                @click="() => openPreview('purchase.submitReview', scope.row)"
              ></el-button>
            </el-tooltip>
            <!-- 收货按钮（已批准且未完全收货） -->
            <el-tooltip
              v-if="isOrderReceivable(scope.row.approvalStatus, scope.row.receiptStatus)"
              content="收货"
              placement="top"
            >
              <el-button
                link
                type="success"
                icon="Location"
                @click="() => handleReceive(scope.row)"
              ></el-button>
            </el-tooltip>
            <!-- 付款按钮（已批准且未完全付款） -->
            <el-tooltip
              v-if="isOrderPayable(scope.row.approvalStatus, scope.row.paymentStatus)"
              content="付款"
              placement="top"
            >
              <el-button
                link
                type="primary"
                icon="Money"
                @click="() => handlePayment(scope.row)"
              ></el-button>
            </el-tooltip>
            <!-- 查看流水（DEV-569） -->
            <el-tooltip content="查看流水" placement="top">
              <el-button
                link
                type="info"
                icon="Connection"
                @click="showTrace(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 新增/修改对话框 -->
    <OrderFormDialog
      v-model:visible="formDialogVisible"
      :orderId="currentOrderId"
      @success="handleSuccess"
    />

    <!-- 审批对话框 -->
    <OrderApproveDialog
      v-model:visible="approveDialogVisible"
      :orderId="currentOrderId"
      :orderNo="currentOrderNo"
      @success="handleSuccess"
    />

    <!-- 收货对话框 -->
    <OrderReceiveDialog
      v-model:visible="receiveDialogVisible"
      :orderId="currentOrderId"
      :orderNo="currentOrderNo"
      @success="handleSuccess"
    />

    <!-- 付款对话框 -->
    <OrderPaymentDialog
      v-model:visible="paymentDialogVisible"
      :orderId="currentOrderId"
      :orderNo="currentOrderNo"
      :orderTotalAmount="currentOrderTotalAmount"
      :paidAmount="currentPaidAmount"
      :currency="currentCurrency"
      @success="handleSuccess"
    />

    <!-- 详情对话框 -->
    <OrderDetailDialog v-model:visible="detailDialogVisible" :orderId="currentOrderId" />

    <!-- 查看流水（DEV-569） -->
    <TraceTimeline v-model="traceDrawerVisible" :traceId="currentTraceId" />

    <!-- 操作预览器 -->
    <OperationPreviewDialog
      v-model="previewVisible"
      :operation="previewOperation"
      :biz-id="previewBizId"
      :biz-no="previewBizNo"
      :status-text-map="approvalStatusTextMap"
      @success="handleSuccess"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'PurchaseOrderList',
})

import { ref, reactive, onMounted, computed } from 'vue'
import type { FormInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { PurchaseOrderVO, PurchaseOrderQuery } from '@/types/purchase/order'
import { PurchaseEnum } from '@/enums/purchase'
import { formatCurrency, formatDate } from './utils/orderFormatters'
import {
  isOrderEditable,
  isOrderCancellable,
  isOrderApprovable,
  isOrderReceivable,
  isOrderPayable,
} from './utils/orderFormatters'
import { usePurchaseOrder } from './composables/usePurchaseOrder'

// 查看流水（DEV-569）
const traceDrawerVisible = ref(false)
const currentTraceId = ref('')
function showTrace(row: PurchaseOrderVO) {
  currentTraceId.value = row.traceId || ''
  traceDrawerVisible.value = true
}
import { usePurchaseOrderStats } from './composables/usePurchaseOrderStats'
import { usePurchaseOrderOperations } from './composables/usePurchaseOrderOperations'
import OrderFormDialog from './components/OrderFormDialog.vue'
import OrderApproveDialog from './components/OrderApproveDialog.vue'
import OrderReceiveDialog from './components/OrderReceiveDialog.vue'
import OrderPaymentDialog from './components/OrderPaymentDialog.vue'
import OrderDetailDialog from './components/OrderDetailDialog.vue'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import OperationPreviewDialog from '@/components/OperationPreviewDialog/index.vue'
import { getOperation } from '@/components/OperationPreviewDialog/registry'
import { copyOrder, exportOrder as apiExportOrder, exportOrderPdf, cancleOrder } from '@/api/purchase/order'
import { download } from '@/utils/format'

// 使用Composables
const { orderList, total, loading, loadData } = usePurchaseOrder()
const { stats, loadStats } = usePurchaseOrderStats()
const { submitForApproval, batchSubmitForApproval } = usePurchaseOrderOperations()

// 查询表单 ref
const queryForm = ref<FormInstance>()

// 查询参数
const queryParams = reactive<PurchaseOrderQuery>({
  pageNum: 1,
  pageSize: 10,
  orderNo: '',
  supplierName: '',
  approvalStatus: undefined,
  receiptStatus: undefined,
  paymentStatus: undefined,
})

// 选中的行
const selectedRows = ref<PurchaseOrderVO[]>([])
const single = computed(() => selectedRows.value.length === 1)
const multiple = computed(() => selectedRows.value.length > 0)

// 根据选中行的状态判断按钮是否可用（批量操作时检查所有选中行）
const selectedOrderEditable = computed(() => {
  if (selectedRows.value.length === 0) return false
  return selectedRows.value.every((row) => isOrderEditable(row.approvalStatus))
})

const selectedOrderDeletable = computed(() => {
  if (selectedRows.value.length === 0) return false
  return selectedRows.value.every((row) => isOrderDeletable(row.approvalStatus))
})

const selectedOrderSubmittable = computed(() => {
  if (selectedRows.value.length === 0) return false
  return selectedRows.value.every((row) => row.approvalStatus === 1)
})

const selectedOrderApprovable = computed(() => {
  if (selectedRows.value.length === 0) return false
  return selectedRows.value.every((row) => isOrderApprovable(row.approvalStatus))
})

const selectedOrderReceivable = computed(() => {
  if (selectedRows.value.length === 0) return false
  return selectedRows.value.every((row) => isOrderReceivable(row.approvalStatus, row.receiptStatus))
})

const selectedOrderPayable = computed(() => {
  if (selectedRows.value.length === 0) return false
  return selectedRows.value.every((row) => isOrderPayable(row.approvalStatus, row.paymentStatus))
})

// 状态选项
const approvalStatusOptions = PurchaseEnum.approvalStatus.items

// 对话框状态
const formDialogVisible = ref(false)
const approveDialogVisible = ref(false)
const receiveDialogVisible = ref(false)
const paymentDialogVisible = ref(false)
const detailDialogVisible = ref(false)

// 当前操作数据
const currentOrderId = ref<number | undefined>(undefined)
const currentOrderNo = ref('')
const currentOrderTotalAmount = ref(0)
const currentPaidAmount = ref(0)
const currentCurrency = ref('CNY')

// 统计卡片
const statCards = computed(() => {
  return [
    {
      title: '总订单数',
      value: stats.value.totalCount,
      icon: 'Document',
      color: '#409eff',
      description: '全部采购订单数量',
    },
    {
      title: '待审批',
      value: stats.value.pendingApprovalCount,
      icon: 'Clock',
      color: '#e6a23c',
      description: '等待审批的订单',
    },
    {
      title: '已审批',
      value: stats.value.approvedCount,
      icon: 'CircleCheck',
      color: '#67c23a',
      description: '审批通过的订单',
    },
    {
      title: '紧急订单',
      value: stats.value.urgentCount,
      icon: 'Warning',
      color: '#f56c6c',
      description: '标记为紧急的订单',
    },
  ]
})

// 生命周期
onMounted(() => {
  getList()
  loadStats()
})

// 获取列表数据
const getList = () => {
  loadData(queryParams)
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置
const resetQuery = () => {
  Object.assign(queryParams, {
    orderNo: '',
    supplierName: '',
    approvalStatus: undefined,
    receiptStatus: undefined,
    paymentStatus: undefined,
    pageNum: 1,
  })
  getList()
}

// 表格选择变化
const handleSelectionChange = (selection: PurchaseOrderVO[]) => {
  selectedRows.value = selection
}

// 排序变化
const handleSortChange = ({ prop, order }: { prop: string; order: string }) => {
  queryParams.sortField = prop
  queryParams.sortOrder =
    order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : undefined
  getList()
}

// 操作成功回调
const handleSuccess = () => {
  getList()
  loadStats()
}

// 新增
const handleAdd = () => {
  currentOrderId.value = undefined
  currentOrderNo.value = ''
  formDialogVisible.value = true
}

// 修改
const handleUpdate = (row?: PurchaseOrderVO) => {
  const order = row || (single.value ? selectedRows.value[0] : null)
  if (!order) {
    ElMessage.warning('请先选择要修改的订单')
    return
  }
  currentOrderId.value = Number(order.orderId as any)
  currentOrderNo.value = order.orderNo
  formDialogVisible.value = true
}

// 导出
const handleExport = () => {
  ElMessageBox.confirm('确定要导出采购订单列表吗？', '导出确认', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'info',
  }).then(async () => {
    try {
      await apiExportOrder(queryParams)
      ElMessage.success('导出成功')
    } catch (error) {
      console.error('导出失败:', error)
      ElMessage.error('导出失败')
    }
  })
}

// 导出PDF（单张表单，需选中一行）
const handleExportPdf = () => {
  const row = selectedRows.value[0]
  if (!row?.orderId) {
    ElMessage.warning('请先选中一行采购订单')
    return
  }
  exportOrderPdf(Number(row.orderId)).then((response: any) => {
    download(response, `采购订单_${row.orderNo || row.orderId}.pdf`)
  })
}

// 提交审批
const handleSubmitReview = () => {
  const orders = selectedRows.value
  if (!orders.length) {
    ElMessage.warning('请先选择要提交审批的订单')
    return
  }

  const names = orders.map((o) => o.orderNo).join(', ')
  ElMessageBox.confirm(
    `确定要提交 ${orders.length} 个订单（${names}）审批吗？`,
    '批量提交审批确认',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      if (orders.length === 1) {
        await submitForApproval(orders[0].orderId!)
      } else {
        const orderIds = orders.map((o) => o.orderId!)
        await batchSubmitForApproval(orderIds)
      }
      handleSuccess()
    } catch (error) {
      console.error('提交审批失败:', error)
      ElMessage.error('提交审批失败')
    }
  })
}

// 审批
const handleApprove = (row?: PurchaseOrderVO) => {
  const order = row || (single.value ? selectedRows.value[0] : null)
  if (!order) {
    ElMessage.warning('请先选择要审批的订单')
    return
  }
  currentOrderId.value = Number(order.orderId as any)
  currentOrderNo.value = order.orderNo
  approveDialogVisible.value = true
}

// 查看详情
// 打印采购订单（跳转独立打印页）
function handlePrint(row: PurchaseOrderVO) {
  window.open(`/print/purchase-order/${row.orderId}`, '_blank')
}
const handleView = (row: PurchaseOrderVO) => {
  currentOrderId.value = Number(row.orderId as any)
  currentOrderNo.value = row.orderNo
  detailDialogVisible.value = true
}

// 收货
const handleReceive = (row?: PurchaseOrderVO) => {
  const order = row || (single.value ? selectedRows.value[0] : null)
  if (!order) {
    ElMessage.warning('请先选择要收货的订单')
    return
  }
  currentOrderId.value = Number(order.orderId as any)
  currentOrderNo.value = order.orderNo
  receiveDialogVisible.value = true
}

// 付款
const handlePayment = (row?: PurchaseOrderVO) => {
  const order = row || (single.value ? selectedRows.value[0] : null)
  if (!order) {
    ElMessage.warning('请先选择要付款的订单')
    return
  }
  currentOrderId.value = Number(order.orderId as any)
  currentOrderNo.value = order.orderNo
  currentOrderTotalAmount.value = order.orderTotalAmount || 0
  currentPaidAmount.value = (order as any).paidAmount || 0
  currentCurrency.value = order.currency || 'CNY'
  paymentDialogVisible.value = true
}

// 复制订单
const handleCopy = async (row?: PurchaseOrderVO) => {
  const order = row || (single.value ? selectedRows.value[0] : null)
  if (!order) {
    ElMessage.warning('请先选择要复制的订单')
    return
  }

  ElMessageBox.confirm(`确定要复制订单 ${order.orderNo} 吗？`, '复制确认', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'info',
  }).then(async () => {
    try {
      await copyOrder(Number(order.orderId) as any)
      ElMessage.success('复制订单成功')
      handleSuccess()
    } catch (error) {
      console.error('复制订单失败:', error)
      ElMessage.error('复制订单失败')
    }
  })
}

// 取消订单
const handleCancle = async (row: PurchaseOrderVO) => {
  ElMessageBox.confirm(
    `确定要取消订单 ${row.orderNo} 吗？取消后订单状态将变为"已取消"。`,
    '取消订单确认',
    {
      confirmButtonText: '确认取消',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      await cancleOrder(Number(row.orderId) as any)
      ElMessage.success('取消订单成功')
      handleSuccess()
    } catch (error) {
      console.error('取消订单失败:', error)
      ElMessage.error('取消订单失败')
    }
  })
}

/**
 * 判断订单是否可删除（草稿和已拒绝可删除）
 */
function isOrderDeletable(approvalStatus: number): boolean {
  return approvalStatus === 1 || approvalStatus === 5
}

// ===== 操作预览器 =====
const previewVisible = ref(false)
const previewOperation = ref<any>(null)
const previewBizId = ref<number | null>(null)
const previewBizNo = ref('')

/** 审批状态文本映射（1草稿 2已取消 3待审批 4已批准 5已拒绝） */
const approvalStatusTextMap: Record<number, string> = Object.fromEntries(
  PurchaseEnum.approvalStatus.items.map((i) => [Number(i.value), i.label])
)

function openPreview(opKey: string, row?: PurchaseOrderVO) {
  const op = getOperation(opKey)
  if (!op) {
    ElMessage.warning(`未注册的操作：${opKey}`)
    return
  }
  previewOperation.value = op
  previewBizId.value = Number(row?.orderId)
  previewBizNo.value = row?.orderNo || ''
  previewVisible.value = true
}
</script>

<style scoped>
.purchase-order {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 500;
  color: #303133;
}

.stats-section {
  margin-bottom: 20px;
}

.stats-title {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 500;
  color: #303133;
}

.stat-card {
  height: 120px;
}

.stat-content {
  display: flex;
  align-items: center;
  height: 100%;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  flex-shrink: 0;
}

.stat-icon .el-icon {
  font-size: 28px;
  color: white;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 2px;
}

.stat-description {
  font-size: 12px;
  color: #909399;
}

.operation-card {
  margin-bottom: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}
</style>

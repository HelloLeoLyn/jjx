<template>
  <div class="production-order">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">生产订单管理</h1>
    </div>

    <!-- 视图切换 -->
    <OrderViewSwitcher :view-type="activeView" @change="handleViewChange" />

    <!-- 统计卡片 -->
    <OrderStatsCards :stats="stats" :loading="loading" @refresh="loadStats" />

    <!-- 批量操作 -->
    <div class="batch-print-action">
      <el-button icon="Printer" @click="handleBatchPrint">打印指令单</el-button>
    </div>
    <OrderBatchActions
      :selected-rows="selectedRows"
      :view-type="activeView"
      :loading="loading"
      :saving="saving"
      :deleting="deleting"
      @create="handleCreate"
      @refresh="refreshData"
      @export="handleExport"
      @batch-delete="handleBatchDelete"
      @batch-command="handleBatchCommand"
    />

    <!-- 搜索筛选 -->
    <OrderSearchFilter
      v-if="activeView !== 'gantt'"
      :search-form="searchForm"
      :view-type="activeView"
      :loading="loading"
      @search="handleSearch"
      @reset="handleReset"
      @update:search-form="handleSearchFormUpdate"
    />

    <!-- 订单表格 -->
    <OrderTable
      v-if="activeView !== 'gantt'"
      :order-list="orderList"
      :loading="loading"
      :total="total"
      :page-num="searchForm.pageNum"
      :page-size="searchForm.pageSize"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
      @page-change="handlePageChange"
      @view="handleViewOrder"
      @edit="handleEditOrder"
      @convert="handleConvertOrder"
      @start="handleStartOrder"
      @complete="handleCompleteOrder"
      @cancel="handleCancelOrder"
      @delete="handleDeleteOrder"
      @more-action="handleMoreAction"
      @trace="handleTrace"
      @production-trace="handleProductionTrace"
      @refresh="refreshData"
    />

    <!-- 甘特图视图 -->
    <GanttChart
      v-if="activeView === 'gantt'"
      ref="ganttRef"
      :order-type="searchForm.orderType"
      @view="handleViewOrder"
    />

    <!-- 订单表单对话框 -->
    <OrderFormDialog
      v-model:visible="formDialogVisible"
      :order="currentOrder"
      :loading="saving"
      @submit="handleFormSubmit"
      @close="handleFormClose"
    />

    <!-- 状态更新对话框 -->
    <OrderStatusDialog
      v-model:visible="statusDialogVisible"
      :order="currentOrder"
      :loading="saving"
      @submit="handleStatusSubmit"
      @close="handleStatusClose"
    />

    <!-- 计划转工单（可拆分）弹窗 -->
    <el-dialog
      v-model="convertDialogVisible"
      title="计划转工单（可拆分）"
      width="780px"
      append-to-body
      destroy-on-close
    >
      <template v-if="convertPlan">
        <el-alert type="info" :closable="false" style="margin-bottom: 12px">
          计划 {{ convertPlan.orderNo }}｜产品 {{ convertPlan.productName }}｜计划数量
          {{ convertPlan.plannedQuantity }}｜剩余可下达
          {{ convertRemaining }}——本次拆分合计不得超过剩余可下达
        </el-alert>
        <el-table :data="convertRows" border size="small">
          <el-table-column label="数量" width="150">
            <template #default="{ row }">
              <el-input-number
                v-model="row.plannedQuantity"
                :min="0"
                :precision="0"
                :controls="false"
                style="width: 110px"
              />
            </template>
          </el-table-column>
          <el-table-column label="计划开始" width="170">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.planStartDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 140px"
              />
            </template>
          </el-table-column>
          <el-table-column label="计划结束" width="170">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.planEndDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 140px"
              />
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="110">
            <template #default="{ row }">
              <el-select v-model="row.priority" size="small">
                <el-option label="高" value="high" />
                <el-option label="中" value="medium" />
                <el-option label="低" value="low" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="convertRows.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div
          style="
            margin-top: 10px;
            display: flex;
            justify-content: space-between;
            align-items: center;
          "
        >
          <span :style="{ color: convertTotalQty > convertRemaining ? '#f56c6c' : '#303133' }">
            合计：{{ convertTotalQty }} / 剩余可下达 {{ convertRemaining }}
          </span>
          <el-button size="small" @click="addConvertRow">＋ 添加拆分</el-button>
        </div>
      </template>
      <template #footer>
        <el-button @click="convertDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="converting"
          :disabled="convertTotalQty <= 0 || convertTotalQty > convertRemaining"
          @click="submitConvert"
        >
          转工单
        </el-button>
      </template>
    </el-dialog>

    <!-- 删除确认对话框 -->
    <OrderDeleteDialog
      v-model:visible="deleteDialogVisible"
      :order="currentOrder"
      :selected-rows="selectedRows"
      :loading="deleting"
      :require-reason="requireDeleteReason"
      @confirm="handleDeleteConfirm"
      @close="handleDeleteClose"
    />

    <!-- 查看流水（DEV-569） -->
    <TraceTimeline v-model="traceDrawerVisible" :traceId="currentTraceId" />

    <!-- P4-C：生产履历（只读时间线） -->
    <ProductionTraceDrawer v-model:visible="prodTraceVisible" :order-id="prodTraceOrderId" />

    <!-- 生产随工单详情抽屉（2026-08-11） -->
    <el-drawer v-model="workCardVisible" title="生产随工单" size="860px" destroy-on-close>
      <ProductionWorkCard v-if="workCardOrderId" :order-id="workCardOrderId" />
    </el-drawer>

    <!-- 2026-08-18：领料预览确认弹窗（A4打印样式） -->
    <PickPreviewDialog
      v-model="pickPreviewVisible"
      v-if="pickPreviewOrder"
      :work-order-id="pickPreviewOrder.orderId"
      :order-no="pickPreviewOrder.orderNo"
      :product-code="pickPreviewOrder.productCode"
      :product-name="pickPreviewOrder.productName"
      :planned-quantity="pickPreviewOrder.plannedQuantity"
      @success="handlePickCreated"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProductionOrderList',
})

import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrderViewSwitcher from './components/OrderViewSwitcher.vue'
import OrderStatsCards from './components/OrderStatsCards.vue'
import OrderBatchActions from './components/OrderBatchActions.vue'
import OrderSearchFilter from './components/OrderSearchFilter.vue'
import OrderTable from './components/OrderTable.vue'
import OrderFormDialog from './components/OrderFormDialog.vue'
import OrderStatusDialog from './components/OrderStatusDialog.vue'
import OrderDeleteDialog from './components/OrderDeleteDialog.vue'
import GanttChart from './components/GanttChart.vue'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import ProductionTraceDrawer from './components/ProductionTraceDrawer.vue'
import ProductionWorkCard from './components/ProductionWorkCard.vue'
import PickPreviewDialog from './components/PickPreviewDialog.vue'
import type {
  ProductionOrderVO,
  ProductionOrderQuery,
  ProductionOrderCreateDTO,
  ProductionOrderUpdateDTO,
  OrderStatusUpdateDTO,
  ProductionOrderStats,
} from '@/types/production/order'
import { OrderType, OrderStatus } from '@/types/production/order'
import { useProductionOrder } from './composables/useProductionOrder'
import { useProductionOrderStats } from './composables/useProductionOrderStats'
import { useOrderOperations } from './composables/useOrderOperations'
import {
  exportProductionOrder,
  batchUpdateOrderStatus,
  convertPlanToWorkOrders,
  completeExecution,
} from '@/api/production/order'
import { download } from '@/utils/format'
import { useRoute, useRouter } from 'vue-router'

// 视图状态：排程管理菜单(/production/schedule)默认进甘特图，生产订单菜单默认全部视图（2026-08-11）
const route = useRoute()
const router = useRouter()
const activeView = ref<'plan' | 'work_order' | 'all' | 'gantt'>(
  route.path.includes('schedule') ? 'gantt' : 'all'
)

// 搜索表单
const searchForm = reactive<ProductionOrderQuery>({
  orderNo: '',
  productName: '',
  productCode: '',
  orderType: 'all',
  planType: undefined,
  orderStatus: '',
  approvalStatus: '',
  executionStatus: '',
  planDateStart: '',
  planDateEnd: '',
  createTimeStart: '',
  createTimeEnd: '',
  salesOrderNo: '',
  instanceCode: '',
  pageNum: 1,
  pageSize: 20,
  sortField: '',
  sortOrder: undefined,
})

// 当前操作订单
const currentOrder = ref<ProductionOrderVO | null>(null)

// 选中的行
const selectedRows = ref<ProductionOrderVO[]>([])

// 对话框可见性
const formDialogVisible = ref(false)
const statusDialogVisible = ref(false)
const deleteDialogVisible = ref(false)

// 是否需要删除原因
const requireDeleteReason = ref(false)

// 使用Composables
const { orderList, total, loading, loadData } = useProductionOrder()

const { stats, loadStats } = useProductionOrderStats()

const {
  saving,
  deleting,
  createOrder,
  updateOrder,
  updateOrderStatus,
  deleteOrder,
  batchDeleteOrders,
} = useOrderOperations()

// 生命周期
onMounted(() => {
  loadData('all')
  loadStats()
})

// 监听视图变化
watch(activeView, (newView) => {
  if (newView !== 'gantt') {
    searchForm.orderType =
      newView === 'all' ? 'all' : newView === 'plan' ? OrderType.PLAN : OrderType.WORK_ORDER
    searchForm.pageNum = 1
    loadData(newView === 'all' ? 'all' : newView)
  }
})

// 搜索相关方法
const handleSearch = () => {
  if (activeView.value === 'gantt') return
  searchForm.pageNum = 1
  loadData(activeView.value === 'all' ? 'all' : activeView.value)
}

const handleReset = () => {
  if (activeView.value === 'gantt') return
  Object.assign(searchForm, {
    orderNo: '',
    productName: '',
    productCode: '',
    orderType:
      activeView.value === 'all'
        ? 'all'
        : activeView.value === 'plan'
          ? OrderType.PLAN
          : OrderType.WORK_ORDER,
    planType: undefined,
    orderStatus: '',
    approvalStatus: '',
    executionStatus: '',
    planDateStart: '',
    planDateEnd: '',
    createTimeStart: '',
    createTimeEnd: '',
    salesOrderNo: '',
    instanceCode: '',
    pageNum: 1,
    pageSize: 20,
    sortField: '',
    sortOrder: undefined,
  })
  loadData(activeView.value === 'all' ? 'all' : activeView.value)
}

const handleSearchFormUpdate = (value: ProductionOrderQuery) => {
  Object.assign(searchForm, value)
}

// 分页和排序
const handlePageChange = (page: number, size: number) => {
  if (activeView.value === 'gantt') return
  searchForm.pageNum = page
  searchForm.pageSize = size
  loadData(activeView.value === 'all' ? 'all' : activeView.value)
}

const handleSortChange = (prop: string, order: 'ascending' | 'descending' | null) => {
  if (activeView.value === 'gantt') return
  searchForm.sortField = prop
  searchForm.sortOrder = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : undefined
  loadData(activeView.value === 'all' ? 'all' : activeView.value)
}

// 视图切换
const handleViewChange = (view: 'plan' | 'work_order' | 'all' | 'gantt') => {
  activeView.value = view
}

// 刷新数据
const refreshData = () => {
  if (activeView.value !== 'gantt') {
    loadData(activeView.value === 'all' ? 'all' : activeView.value)
  }
  loadStats()
}

// 扫码定位（2026-08-12 DEV-979）：route.query.orderNo → 填入搜索并查询
watch(
  () => route.query.orderNo as string | undefined,
  (no) => {
    if (no && activeView.value !== 'gantt') {
      searchForm.orderNo = no
      searchForm.pageNum = 1
      loadData(activeView.value === 'all' ? 'all' : activeView.value)
    }
  },
  { immediate: true }
)

// 导出功能
const handleExport = async () => {
  try {
    const res = await exportProductionOrder({ ...searchForm })
    download(res, `生产订单_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

// 打印当前页勾选的工单；首个 ID 保持既有单工单路由兼容，完整集合由 query 传递。
const handleBatchPrint = () => {
  const orderIds = selectedRows.value.map((row) => row.orderId).filter(Boolean)
  if (orderIds.length === 0) {
    ElMessage.warning('请先勾选要打印的工单')
    return
  }
  const routeData = router.resolve({
    name: 'ProductionOrderPrint',
    params: { id: orderIds[0] },
    query: { ids: orderIds.join(',') },
  })
  window.open(routeData.href, '_blank')
}

// 批量操作
const handleBatchDelete = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的订单')
    return
  }
  deleteDialogVisible.value = true
}

const handleBatchCommand = async (command: string) => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择订单')
    return
  }

  try {
    switch (command) {
      case 'approve':
        await handleBatchApprove()
        break
      case 'start':
        await handleBatchStart()
        break
      case 'complete':
        await handleBatchComplete()
        break
      case 'cancel':
        await handleBatchCancel()
        break
      default:
        ElMessage.warning('暂不支持该批量操作')
    }
  } catch (error) {
    console.error('批量操作失败:', error)
    ElMessage.error('批量操作失败')
  }
}

const handleBatchApprove = async () => {
  const confirm = await ElMessageBox.confirm(
    `确定要批量审批 ${selectedRows.value.length} 个计划吗？`,
    '批量审批确认',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )

  if (confirm) {
    try {
      await batchUpdateOrderStatus({
        orderIds: selectedRows.value.map((row) => row.orderId),
        orderStatus: OrderStatus.APPROVED,
        remark: '批量审批',
      })
      ElMessage.success('批量审批成功')
      selectedRows.value = []
      refreshData()
    } catch (error) {
      console.error('批量审批失败:', error)
      ElMessage.error('批量审批失败')
    }
  }
}

const handleBatchStart = async () => {
  const confirm = await ElMessageBox.confirm(
    `确定要批量开始 ${selectedRows.value.length} 个工单吗？`,
    '批量开始确认',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )

  if (confirm) {
    try {
      await batchUpdateOrderStatus({
        orderIds: selectedRows.value.map((row) => row.orderId),
        orderStatus: OrderStatus.IN_PROGRESS,
        remark: '批量开始',
      })
      ElMessage.success('批量开始成功')
      selectedRows.value = []
      refreshData()
    } catch (error) {
      console.error('批量开始失败:', error)
      ElMessage.error('批量开始失败')
    }
  }
}

const handleBatchComplete = async () => {
  const confirm = await ElMessageBox.confirm(
    `确定要批量完成 ${selectedRows.value.length} 个工单吗？`,
    '批量完成确认',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )

  if (confirm) {
    // V1 Release Fix：批量完成逐单调用正式 completeOrder（后端 FQC/工序/数量 gate），不再走通用状态更新
    let okCount = 0
    let failCount = 0
    const failMsgs: string[] = []
    for (const row of selectedRows.value) {
      try {
        await completeExecution(String(row.orderId), { completedQuantity: 0 })
        okCount++
      } catch (e: any) {
        failCount++
        const msg = e?.msg || e?.message || '完成失败'
        failMsgs.push(`${row.orderNo || row.orderId}: ${msg}`)
      }
    }
    selectedRows.value = []
    refreshData()
    if (okCount > 0) ElMessage.success(`批量完成成功 ${okCount} 个`)
    if (failCount > 0) {
      ElMessage.error(`批量完成失败 ${failCount} 个（多为完工校验未通过）`)
      console.warn('批量完成失败明细:', failMsgs)
    }
  }
}

const handleBatchCancel = async () => {
  const confirm = await ElMessageBox.confirm(
    `确定要批量取消 ${selectedRows.value.length} 个订单吗？`,
    '批量取消确认',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )

  if (confirm) {
    try {
      await batchUpdateOrderStatus({
        orderIds: selectedRows.value.map((row) => row.orderId),
        orderStatus: OrderStatus.CANCELLED,
        remark: '批量取消',
      })
      ElMessage.success('批量取消成功')
      selectedRows.value = []
      refreshData()
    } catch (error) {
      console.error('批量取消失败:', error)
      ElMessage.error('批量取消失败')
    }
  }
}

// 表格操作
const handleSelectionChange = (selection: ProductionOrderVO[]) => {
  selectedRows.value = selection
}

const handleViewOrder = (order: any) => {
  // 打开生产随工单详情抽屉（2026-08-11 方案A：工单头+工序明细+领料+质检+签字区）
  workCardOrderId.value = order.orderId
  workCardVisible.value = true
}

const handleEditOrder = (order: any) => {
  currentOrder.value = order
  formDialogVisible.value = true
}

// 计划转工单（可拆分弹窗，2026-08-11）
const convertDialogVisible = ref(false)
const convertPlan = ref<any>(null)
const convertRows = ref<any[]>([])
const converting = ref(false)
const convertTotalQty = computed(() =>
  convertRows.value.reduce((s, r) => s + (Number(r.plannedQuantity) || 0), 0)
)

// V1 Fix Pack FIX-3：剩余可下达数量（计划 remaining_quantity；兼容旧数据无字段时回落 planned）
const convertRemaining = computed(() => {
  const v = convertPlan.value?.remainingQuantity
  if (v != null && Number(v) >= 0) return Number(v)
  return Number(convertPlan.value?.plannedQuantity || 0)
})

const handleConvertOrder = (order: any) => {
  convertPlan.value = order
  const today = new Date()
  const fmt = (d: Date) => d.toISOString().slice(0, 10)
  const remaining =
    order.remainingQuantity != null && Number(order.remainingQuantity) >= 0
      ? Number(order.remainingQuantity)
      : Number(order.plannedQuantity || 0)
  convertRows.value = [
    {
      productId: String(order.productId),
      productCode: order.productCode || '',
      productName: order.productName || '',
      plannedQuantity: Math.min(Number(order.plannedQuantity || 0), remaining),
      planStartDate: order.planStartDate ? String(order.planStartDate).slice(0, 10) : fmt(today),
      planEndDate: order.planEndDate
        ? String(order.planEndDate).slice(0, 10)
        : fmt(new Date(today.getTime() + 7 * 24 * 3600 * 1000)),
      priority: order.priority ? String(order.priority).toLowerCase() : 'medium',
      remark: '',
    },
  ]
  convertDialogVisible.value = true
}

// 添加拆分行
const addConvertRow = () => {
  const base = convertRows.value[0] || {}
  convertRows.value.push({ ...base, plannedQuantity: 0, remark: '' })
}

// 提交转工单
const submitConvert = async () => {
  converting.value = true
  try {
    const dto = {
      planId: String(convertPlan.value.orderId),
      workOrders: convertRows.value.map((r) => ({
        productId: r.productId,
        productCode: r.productCode,
        productName: r.productName,
        plannedQuantity: Number(r.plannedQuantity),
        planStartDate: r.planStartDate,
        planEndDate: r.planEndDate,
        priority: r.priority,
        remark: r.remark || '',
      })),
      batchConvert: convertRows.value.length > 1,
    }
    const res: any = await convertPlanToWorkOrders(dto)
    if (res.code === 200 || res.code === 0) {
      ElMessage.success('转为工单成功')
      convertDialogVisible.value = false
      refreshData()
    } else {
      ElMessage.error(res.msg || '转为工单失败')
    }
  } catch (error: any) {
    console.error('转为工单失败:', error)
    ElMessage.error(error?.msg || '转为工单失败')
  } finally {
    converting.value = false
  }
}

const handleStartOrder = (order: any) => {
  currentOrder.value = order
  statusDialogVisible.value = true
}

const handleCompleteOrder = async (order: any) => {
  // V1 Release Fix：订单正式完成必须走 completeOrder（后端 FQC/工序/数量 gate），不走通用状态更新
  const confirm = await ElMessageBox.confirm(
    `确定要完成工单 ${order.orderNo} 吗？`,
    '完成工单确认',
    {
      confirmButtonText: '确认完成',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).catch(() => null)
  if (!confirm) return
  try {
    await completeExecution(String(order.orderId), { completedQuantity: 0 })
    ElMessage.success('工单已完成')
    refreshData()
  } catch (e: any) {
    const msg = e?.msg || e?.message || '完成工单失败'
    if (
      msg.includes('FQC') ||
      msg.includes('质检') ||
      msg.includes('完工检验') ||
      msg.includes('校验不通过')
    ) {
      ElMessage.error('完工校验未通过，订单暂不能完成：' + msg)
    } else {
      ElMessage.error(msg)
    }
  }
}

const handleCancelOrder = (order: any) => {
  ElMessageBox.confirm(`确定要取消订单 ${order.orderNo} 吗？`, '取消订单确认', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await updateOrderStatus({
        orderId: order.orderId,
        orderStatus: OrderStatus.CANCELLED,
        remark: '用户手动取消',
      })
      ElMessage.success('取消订单成功')
      refreshData()
    } catch (error) {
      console.error('取消订单失败:', error)
      ElMessage.error('取消订单失败')
    }
  })
}

const handleDeleteOrder = (order: any) => {
  currentOrder.value = order
  deleteDialogVisible.value = true
}

const handleMoreAction = (order: ProductionOrderVO, command: string) => {
  switch (command) {
    case 'copy':
      handleCopyOrder(order)
      break
    case 'export':
      handleExportOrder(order)
      break
    case 'print':
      handlePrintOrder(order)
      break
    case 'history':
      handleViewHistory(order)
      break
    case 'pick-material':
      handlePickMaterial(order)
      break
    default:
      ElMessage.warning('暂不支持该操作')
  }
}

// 生成领料单（2026-08-18：先预览确认——A4打印样式弹窗展示BOM展开/可用量/替代料，可调实领数量）
const pickPreviewVisible = ref(false)
const pickPreviewOrder = ref<any>(null)

async function handlePickMaterial(order: any) {
  pickPreviewOrder.value = order
  pickPreviewVisible.value = true
}

// 预览确认生成成功 → 引导去确认发料
function handlePickCreated() {
  loadData()
  ElMessageBox.confirm('领料单已生成，是否前往【出库管理→生产领料】确认发料？', '生成领料单', {
    confirmButtonText: '去确认发料',
    cancelButtonText: '稍后',
    type: 'success',
  })
    .then(() => {
      router.push({ path: '/inventory/outbound', query: { outboundType: 'production' } })
    })
    .catch(() => {})
}

const handleCopyOrder = (order: any) => {
  ElMessage.info(`复制订单 ${order.orderNo}`)
}

const handleExportOrder = (order: any) => {
  ElMessage.info(`导出订单 ${order.orderNo}`)
}

const handlePrintOrder = (order: any) => {
  window.open(`/print/production-order/${order.orderId}`, '_blank')
}

const handleViewHistory = (order: any) => {
  ElMessage.info(`查看订单 ${order.orderNo} 的操作历史`)
}

// 查看流水（DEV-569）
const traceDrawerVisible = ref(false)
const currentTraceId = ref('')

// P4-C：生产履历（只读时间线）
const prodTraceVisible = ref(false)
const prodTraceOrderId = ref<number | null>(null)
const handleProductionTrace = (order: any) => {
  prodTraceOrderId.value = order?.orderId ?? null
  prodTraceVisible.value = true
}

// 生产随工单详情抽屉（2026-08-11）
const workCardVisible = ref(false)
const workCardOrderId = ref<string | number>('')
const handleTrace = (order: any) => {
  currentTraceId.value = order.traceId || ''
  traceDrawerVisible.value = true
}

// 创建订单
const handleCreate = () => {
  currentOrder.value = null
  formDialogVisible.value = true
}

// 表单提交
const handleFormSubmit = async (data: ProductionOrderCreateDTO | ProductionOrderUpdateDTO) => {
  try {
    if ('orderId' in data) {
      // 更新订单
      await updateOrder(data as ProductionOrderUpdateDTO)
      ElMessage.success('更新订单成功')
    } else {
      // 创建订单
      await createOrder(data as ProductionOrderCreateDTO)
      ElMessage.success('创建订单成功')
    }
    formDialogVisible.value = false
    refreshData()
  } catch (error) {
    console.error('保存订单失败:', error)
    ElMessage.error('保存订单失败')
  }
}

const handleFormClose = () => {
  currentOrder.value = null
}

// 状态更新
const handleStatusSubmit = async (data: OrderStatusUpdateDTO) => {
  try {
    // V1 Release Fix：目标为 COMPLETED 时必须走正式 completeOrder（FQC/工序/数量 gate）
    if (data.orderStatus === OrderStatus.COMPLETED) {
      await completeExecution(String(data.orderId), { completedQuantity: 0 })
      ElMessage.success('工单已完成')
      statusDialogVisible.value = false
      refreshData()
      return
    }
    await updateOrderStatus(data)
    ElMessage.success('更新状态成功')
    statusDialogVisible.value = false
    refreshData()
  } catch (error: any) {
    console.error('更新状态失败:', error)
    const msg = error?.msg || error?.message || '更新状态失败'
    if (
      msg.includes('FQC') ||
      msg.includes('质检') ||
      msg.includes('完工检验') ||
      msg.includes('校验不通过') ||
      msg.includes('完成操作')
    ) {
      ElMessage.error('完工校验未通过，订单暂不能完成：' + msg)
    } else {
      ElMessage.error(msg)
    }
  }
}

const handleStatusClose = () => {
  currentOrder.value = null
}

// 删除确认
const handleDeleteConfirm = async (reason?: string) => {
  try {
    if (currentOrder.value) {
      // 删除单个订单
      await deleteOrder(currentOrder.value.orderId, reason)
      ElMessage.success('删除订单成功')
    } else if (selectedRows.value.length > 0) {
      // 批量删除
      const orderIds = selectedRows.value.map((order) => order.orderId)
      await batchDeleteOrders(orderIds, reason)
      ElMessage.success(`批量删除 ${orderIds.length} 个订单成功`)
    }
    deleteDialogVisible.value = false
    selectedRows.value = []
    refreshData()
  } catch (error) {
    console.error('删除订单失败:', error)
    ElMessage.error('删除订单失败')
  }
}

const handleDeleteClose = () => {
  currentOrder.value = null
}
</script>

<style scoped>
.production-order {
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

.batch-print-action {
  margin-bottom: 8px;
}

.gantt-view {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f7fa;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}
</style>

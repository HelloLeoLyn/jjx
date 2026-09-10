<template>
  <el-card class="work-order-panel" shadow="never">
    <div class="panel-toolbar">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="当前工单" name="current" />
        <el-tab-pane label="历史工单" name="history" />
      </el-tabs>
      <el-segmented
        v-if="canViewAll"
        v-model="scope"
        :options="scopeOptions"
        @change="handleScopeChange"
      />
    </div>

    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="orders"
      row-key="orderId"
      highlight-current-row
      @row-click="selectOrder"
    >
      <el-table-column prop="orderNo" label="工单号" min-width="180" />
      <el-table-column prop="productName" label="产品名称" min-width="180" />
      <el-table-column label="计划数量" width="120" align="right">
        <template #default="{ row }">{{ fmtQty(row.plannedQuantity) }}</template>
      </el-table-column>
      <el-table-column label="完工数量" width="120" align="right">
        <template #default="{ row }">{{ fmtQty(row.completedQuantity) }}</template>
      </el-table-column>
      <el-table-column prop="planEndDate" label="计划交期" width="130" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.orderStatus)">{{ statusLabel(row.orderStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button
            v-if="completionMap[String(row.orderId)]?.authorized"
            type="warning"
            link
            icon="CircleCheck"
            :loading="completingId === String(row.orderId)"
            :disabled="!completionMap[String(row.orderId)]?.canComplete"
            @click.stop="handleCompleteOrder(row)"
            >完成</el-button
          >
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="scope === 'mine' ? '我的范围暂无工单' : '暂无工单'" />
      </template>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadOrders"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import type { TabsPaneContext } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductionOrderPage } from '@/api/production/order'
import { operationExecutionApi } from '@/api/production/operationExecution'
import { ProductionOrderStatusEnum } from '@/enums/production'
import type { ProductionOrderVO } from '@/types/production/order'
import type { OrderCompletionStatusVO } from '@/types/production/operationExecution'
import { fmtQty } from '../utils'

type WorkOrderTab = 'current' | 'history'
type WorkOrderScope = 'mine' | 'all'

defineProps<{ canViewAll: boolean }>()
const emit = defineEmits<{
  select: [order: ProductionOrderVO | null, scope: WorkOrderScope, tab: WorkOrderTab]
  completed: [orderId: number]
}>()

const tableRef = ref()
const loading = ref(false)
const orders = ref<ProductionOrderVO[]>([])
const total = ref(0)
const activeTab = ref<WorkOrderTab>('current')
// 默认范围一律「我的」（一级负责人名下含已完工工序由 includeCompleted 补全）；管理可切「全部」
const scope = ref<WorkOrderScope>('mine')
const pageNum = ref(1)
const pageSize = 10
const scopeOptions = [
  { label: '我的', value: 'mine' },
  { label: '全部', value: 'all' },
]
const currentStatuses = [
  ProductionOrderStatusEnum.PENDING_START.value,
  ProductionOrderStatusEnum.IN_PROGRESS.value,
  ProductionOrderStatusEnum.PAUSED.value,
]
const historyStatuses = [
  ProductionOrderStatusEnum.COMPLETED.value,
  ProductionOrderStatusEnum.CANCELLED.value,
  ProductionOrderStatusEnum.CLOSED.value,
]

const statusLabel = (status?: number) =>
  status === undefined ? '未知' : ProductionOrderStatusEnum.getLabel(status)
const statusTag = (status?: number) =>
  status === undefined ? 'info' : ProductionOrderStatusEnum.getTagProps(status).type

const selectOrder = (order: ProductionOrderVO | null) => {
  tableRef.value?.setCurrentRow(order)
  emit('select', order, scope.value, activeTab.value)
}
const selectFirstOrder = async () => {
  await nextTick()
  selectOrder(orders.value[0] || null)
}
const loadOrders = async () => {
  loading.value = true
  try {
    const result: any = await getProductionOrderPage({
      orderType: 'WORK_ORDER',
      orderStatuses: activeTab.value === 'current' ? currentStatuses : historyStatuses,
      myAssigned: scope.value === 'mine',
      pageNum: pageNum.value,
      pageSize,
    })
    const data = result?.data
    orders.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    orders.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
  await loadCompletionStatus()
  await selectFirstOrder()
}

/** 工单级收口状态（批量，决定「完成」按钮显隐） */
const completionMap = ref<Record<string, OrderCompletionStatusVO>>({})
const completingId = ref<string | null>(null)
const loadCompletionStatus = async () => {
  const ids = orders.value
    .map((o) => Number(o.orderId))
    .filter((v) => Number.isFinite(v))
  if (!ids.length) {
    completionMap.value = {}
    return
  }
  try {
    const res: any = await operationExecutionApi.getOrderCompletionStatus(ids)
    const map: Record<string, OrderCompletionStatusVO> = {}
    ;(res?.data || []).forEach((s: OrderCompletionStatusVO) => {
      map[String(s.orderId)] = s
    })
    completionMap.value = map
  } catch {
    completionMap.value = {}
  }
}

/** 工单级统一收口：一级负责人一次完成整张工单全部工序 */
const handleCompleteOrder = async (order: ProductionOrderVO) => {
  const key = String(order.orderId)
  const st = completionMap.value[key]
  if (!st?.canComplete) return
  try {
    await ElMessageBox.confirm(
      `确认对工单「${order.orderNo}」完成收口？\n将一次性完成该工单全部 ${st.pendingExecutionCount} 道工序；系统会逐工序校验前置（无待审报工 / 数量达标 / 无未分配剩余 / 子树完成），任一未就绪将整体拒绝。`,
      '完成工单确认',
      { type: 'warning', confirmButtonText: '确认完成', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  completingId.value = key
  try {
    await operationExecutionApi.completeOrder(Number(order.orderId))
    ElMessage.success('工单已收口；若为最后一道工序将自动生成完工检验（FQC）')
    await loadOrders()
    emit('completed', Number(order.orderId))
  } catch (e: any) {
    const msg = String(e?.msg || e?.message || '完成失败').replace(/\n/g, ' ')
    ElMessage.error(msg)
  } finally {
    completingId.value = null
  }
}
const handleTabChange = (_tab: TabsPaneContext['paneName']) => {
  pageNum.value = 1
  loadOrders()
}
const handleScopeChange = () => {
  pageNum.value = 1
  loadOrders()
}

onMounted(loadOrders)
</script>

<style scoped>
.work-order-panel {
  margin-bottom: 16px;
}
.panel-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.panel-toolbar :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

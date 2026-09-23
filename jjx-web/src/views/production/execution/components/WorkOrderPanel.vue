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
      <el-table-column label="阶段" width="130">
        <template #default="{ row }">
          <el-tooltip
            :content="`工单状态：${statusLabel(row.orderStatus)}` +
              (stageOf(row).nextAction ? ` · 下一步：${stageOf(row).nextAction}` : '')"
            placement="top"
          >
            <el-tag :type="stageTag(stageOf(row).stage)">{{ stageOf(row).label }}</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="进度" min-width="230">
        <template #default="{ row }">
          <span class="wo-progress">{{ progressText(row) }}</span>
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

const props = withDefaults(
  defineProps<{
    canViewAll: boolean
    /** dev-20260923-033：从不良台账「去派工」跳进来时带的目标工单（自动选中，只生效一次） */
    initialOrderId?: number | null
  }>(),
  { initialOrderId: null }
)
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

// ============ 完工阶段（派生，dev-20260918-015）============
const STAGE_TAG: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  IN_PRODUCTION: 'primary',
  PENDING_FQC: 'warning',
  PENDING_DISPOSITION: 'danger',
  // dev-20260923-028：报废已处置、良品未达计划 → 待补产
  PENDING_SUPPLEMENT: 'warning',
  READY_TO_COMPLETE: 'success',
  PENDING_INBOUND: 'warning',
  COMPLETED: 'success',
  PAUSED: 'info',
  NOT_STARTED: 'info',
  CANCELLED: 'info',
}
/** 阶段优先，取不到（接口未回）时回落到工单原始状态 */
const stageOf = (row: ProductionOrderVO) => {
  const st = completionMap.value[String(row.orderId)]
  return {
    stage: st?.stage || '',
    label: st?.stageLabel || statusLabel(row.orderStatus),
    nextAction: st?.nextAction || '',
  }
}
const stageTag = (stage?: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' =>
  STAGE_TAG[stage || ''] || 'info'
/** 进度：工序 x/y · 完工检验 待检 · 合格 a/b（有缺口时补一句「还缺 N 件（报废 M 件）」—— dev-20260923-028） */
const progressText = (row: ProductionOrderVO) => {
  const st = completionMap.value[String(row.orderId)]
  if (!st) return '—'
  const parts: string[] = []
  if (st.executionTotal != null) parts.push(`工序 ${st.executionDone ?? 0}/${st.executionTotal}`)
  if (st.fqcPendingCount && st.fqcPendingCount > 0) parts.push(`完工检验 待检 ${st.fqcPendingCount}`)
  parts.push(`合格 ${fmtQty(st.qualifiedQuantity)}/${fmtQty(st.plannedQuantity)}`)
  const gap = Number(st.shortfallQuantity || 0)
  if (gap > 0) {
    const scrap = Number(st.scrappedQuantity || 0)
    parts.push(`还缺 ${fmtQty(gap)} 件${scrap > 0 ? `（报废 ${fmtQty(scrap)} 件）` : ''}`)
  }
  return parts.join(' · ')
}

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
  if (!applyInitialOrder()) {
    await selectFirstOrder()
  }
}

/** dev-20260923-033：带目标工单跳进来时自动选中它（避免用户自己翻页找） */
const initialOrderApplied = ref(false)
const applyInitialOrder = () => {
  if (initialOrderApplied.value || !props.initialOrderId) return false
  const target = orders.value.find(
    (row) => Number(row.orderId) === Number(props.initialOrderId)
  )
  if (!target) return false
  initialOrderApplied.value = true
  selectOrder(target)
  return true
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
.wo-progress {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
</style>

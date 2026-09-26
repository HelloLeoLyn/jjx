<template>
  <el-card class="work-order-panel" shadow="never">
    <div class="panel-toolbar">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="当前工单" name="current" />
        <el-tab-pane label="历史工单" name="history" />
      </el-tabs>
      <div class="panel-actions">
        <el-segmented
          v-if="canViewAll"
          v-model="scope"
          :options="scopeOptions"
          @change="handleScopeChange"
        />
        <el-button
          v-if="allowClearSelection"
          :disabled="selectedOrderId == null"
          @click="selectOrder(null)"
        >查看全部</el-button>
      </div>
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
      <!-- dev-20260923-024：数量对账栏（计划/投入/良品/报废/返工在制/让步/在制/差数），口径唯一出处 -->
      <el-table-column label="对账" width="90" align="center">
        <template #default="{ row }">
          <el-button link size="small" @click.stop="openRecon(row)">数量对账</el-button>
        </template>
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

    <!-- 数量对账（dev-20260923-024）：口径 045 §1；报废/返工/让步 的件级下钻见 dev-20260924-004 -->
    <el-dialog v-model="reconVisible" title="工单数量对账" width="760px" append-to-body>
      <div class="recon-head">
        工单 {{ reconRow?.orderNo || '-' }} · 产品 {{ reconRow?.productName || '-' }}
      </div>
      <el-table :data="reconRows" border size="small">
        <el-table-column label="项目" width="130">
          <template #default="{ row }">{{ row.item }}</template>
        </el-table-column>
        <el-table-column label="数量" width="110" align="right">
          <template #default="{ row }">{{ fmtQty(row.value) }}</template>
        </el-table-column>
        <el-table-column label="来源 / 说明">
          <template #default="{ row }">{{ row.note }}</template>
        </el-table-column>
      </el-table>
      <div class="recon-section-title">工序投入与已审批产出（不跨串行工序相加）</div>
      <el-table :data="completionMap[String(reconRow?.orderId)]?.operationQuantities || []" border size="small">
        <el-table-column label="工序" min-width="150">
          <template #default="{ row }">{{ row.processName || `工序 ${row.processOrder ?? '-'}` }}</template>
        </el-table-column>
        <el-table-column label="计划投入" width="110" align="right">
          <template #default="{ row }">{{ fmtQty(row.plannedInputQuantity) }}</template>
        </el-table-column>
        <el-table-column label="已审批产出" width="120" align="right">
          <template #default="{ row }">{{ fmtQty(row.approvedOutputQuantity) }}</template>
        </el-table-column>
        <el-table-column label="工序段" width="80" align="center">
          <template #default="{ row }">{{ row.finalOperation ? '末道' : '中间' }}</template>
        </el-table-column>
      </el-table>
      <div class="recon-tip">
        口径：各工序计划投入与已审批报工分开展示；工单报工量取标准路线单道工序最大产出，不跨串行工序累加；末道产出、补产报工和有效FQC合格分别展示。普通工序实物在制无法从现有数据可靠推算，因此不以报工减FQC差额代替在制；这里只单列返工在制和待检FQC批次数。
        报废 / 返工 / 让步 的数字可在「质量管理 → 产品不良台账」按工单查看件级明细（不良件）。
      </div>
    </el-dialog>

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
    autoSelectFirst?: boolean
    allowClearSelection?: boolean
    defaultScope?: WorkOrderScope
  }>(),
  {
    initialOrderId: null,
    autoSelectFirst: true,
    allowClearSelection: false,
    defaultScope: 'mine',
  }
)
const emit = defineEmits<{
  select: [order: ProductionOrderVO | null, scope: WorkOrderScope, tab: WorkOrderTab]
  completed: [orderId: number]
}>()

const tableRef = ref()
const loading = ref(false)
const orders = ref<ProductionOrderVO[]>([])
const total = ref(0)
const selectedOrderId = ref<number | null>(null)
const activeTab = ref<WorkOrderTab>('current')
// 默认范围一律「我的」（一级负责人名下含已完工工序由 includeCompleted 补全）；管理可切「全部」
const scope = ref<WorkOrderScope>(props.defaultScope)
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
// ============ 数量对账（dev-20260923-024） ============
const reconVisible = ref(false)
const reconRow = ref<ProductionOrderVO | null>(null)
const reconRows = ref<Array<{ item: string; value: number; note: string }>>([])
/** 对账栏口径（045 §1）：工单完成 = 良品累计；差数由 补产/返工回收/让步 填平 */
const openRecon = (row: ProductionOrderVO) => {
  const st: any = completionMap.value[String(row.orderId)] || {}
  reconRow.value = row
  reconRows.value = [
    { item: '计划量', value: Number(st.plannedQuantity || row.plannedQuantity || 0), note: 'production_order.planned_quantity' },
    { item: '标准路线报工量', value: Number(st.reportedQuantity || 0), note: '各正常工序已审批产出的最大值，不把串行工序重复相加' },
    { item: '末道工序产出', value: Number(st.finalOperationOutputQuantity || 0), note: '末道正常工序的已审批合格+不良数量' },
    { item: '补产报工', value: Number(st.supplementReportedQuantity || 0), note: '补产来源任务按补料单分组，跨工序取最大产出后合计' },
    { item: '良品', value: Number(st.goodQuantity || 0), note: '有效 FQC 批合格累计（工单「完成」的判据）' },
    { item: '报废', value: Number(st.scrapQuantity || 0), note: '处置单 SCRAP 已完成（件级明细见「不良件」）' },
    { item: '返工在制', value: Number(st.reworkWipQuantity || 0), note: '处置单 REWORK 待执行/执行中' },
    { item: '让步接收', value: Number(st.concessionQuantity || 0), note: '处置单 CONCESSION 已完成（需客户确认）' },
    { item: '待检FQC批次', value: Number(st.fqcPendingCount || 0), note: '有效FQC批次中尚未判定的批次数（不是件数）' },
    { item: '差数', value: Number(st.diffQuantity || st.shortfallQuantity || 0), note: 'max(0, 计划 − 良品)；必须由 补产 / 返工回收 / 让步 填平' },
    // dev-20260923-026 / -027：物料侧（定额是基准不是天花板；超领走补料通道）
    { item: 'BOM应领', value: Number(st.materialRequired || 0), note: 'Σ(BOM 单耗 ×(1+损耗率) × 计划量)' },
    { item: '已领', value: Number(st.materialIssued || 0), note: '正常领料出库（未取消）' },
    { item: '补料', value: Number(st.materialSupplement || 0), note: '补料出库（超耗/报废补产/试制调机/来料不良）' },
    { item: '退料', value: Number(st.materialReturned || 0), note: '返工退料入库（RTN 单，已过账）' },
    {
      item: '超领率',
      value: Number(st.overPickRate || 0),
      note: 'max(0, 已领 + 补料 − BOM应领) / BOM应领 × 100（%）；金额口径待单价数据源（见 010）',
    },
  ]
  reconVisible.value = true
}

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
  selectedOrderId.value = order?.orderId ? Number(order.orderId) : null
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
    if (props.autoSelectFirst) {
      await selectFirstOrder()
    } else {
      selectOrder(null)
    }
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
.panel-actions {
  display: flex;
  align-items: center;
  gap: 12px;
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

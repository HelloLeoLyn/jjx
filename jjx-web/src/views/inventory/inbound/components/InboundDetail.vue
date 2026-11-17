<template>
  <div v-loading="loading" class="inbound-detail">
    <template v-if="inbound">
      <!-- 概览：单号 + 状态 + 关键指标 + 动作 -->
      <div class="detail-overview">
        <div class="overview-head">
          <span class="overview-no">{{ inbound.inboundNo }}</span>
          <el-tag v-if="isReverse" type="danger" size="small" effect="plain">红冲</el-tag>
          <el-tag :type="statusTagType" size="small">{{ inboundStatusText }}</el-tag>
          <el-tag type="info" size="small" effect="plain">{{ inboundTypeText }}</el-tag>
          <span class="overview-spacer" />
          <template v-if="showActions">
            <el-button v-if="canConfirm" type="primary" size="small" @click="emit('confirm')"
              >确认入库</el-button
            >
            <el-button size="small" @click="emit('trace')">查看流水</el-button>
            <el-button size="small" @click="emit('print')">打印</el-button>
          </template>
        </div>
        <div class="overview-stats">
          <div class="stat">
            <div class="stat-label">本单数量</div>
            <div class="stat-value">{{ formatNumber(inbound.totalQuantity) }}</div>
          </div>
          <div class="stat">
            <div class="stat-label">允收入库</div>
            <div class="stat-value">{{ dashNumber(inbound.acceptedQuantity) }}</div>
          </div>
          <div class="stat">
            <div class="stat-label">已入库</div>
            <div class="stat-value">{{ formatNumber(inbound.postedQuantity ?? 0) }}</div>
          </div>
          <div class="stat">
            <div class="stat-label">待入库</div>
            <div class="stat-value">{{ dashNumber(pendingQuantity) }}</div>
          </div>
          <div class="stat stat-money">
            <div class="stat-label">总金额</div>
            <div class="stat-value">¥ {{ formatCurrency(inbound.totalAmount) }}</div>
          </div>
        </div>
      </div>

      <!-- 基本信息 -->
      <div class="detail-section-title">基本信息</div>
      <el-descriptions :column="3" border class="detail-desc">
        <el-descriptions-item label="入库单号">{{ inbound.inboundNo }}</el-descriptions-item>
        <el-descriptions-item label="入库类型">{{ inboundTypeText }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType" size="small">{{ inboundStatusText }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="仓库">{{ inbound.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ inbound.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="入库日期">{{ inbound.inboundDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="本单数量">{{
          formatNumber(inbound.totalQuantity)
        }}</el-descriptions-item>
        <el-descriptions-item label="允收量">{{ dashNumber(inbound.acceptedQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="已入库">{{
          formatNumber(inbound.postedQuantity ?? 0)
        }}</el-descriptions-item>
        <el-descriptions-item label="总金额">
          <span class="detail-amount">¥ {{ formatCurrency(inbound.totalAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ inbound.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ inbound.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间" :span="3">{{
          inbound.updateTime || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ inbound.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 业务来源与来料检验 -->
      <template v-if="hasSourceInfo">
        <div class="detail-section-title">业务来源</div>
        <el-descriptions :column="3" border class="detail-desc">
          <el-descriptions-item label="业务来源" :span="hasInspection ? 1 : 3">{{
            sourceText
          }}</el-descriptions-item>
          <el-descriptions-item label="来源批次数量">{{
            dashNumber(inbound.sourceLotQuantity)
          }}</el-descriptions-item>
          <el-descriptions-item label="来源批不合格">{{
            dashNumber(inbound.sourceRejectedQuantity)
          }}</el-descriptions-item>
          <template v-if="hasInspection">
            <el-descriptions-item label="检验员">{{ inbound.inspectorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="检验时间">{{ inbound.inspectionTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="判定">
              <el-tag :type="inspectionTagType" size="small">{{ inspectionResultText }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="检验备注" :span="3">{{
              inbound.inspectionRemark || '-'
            }}</el-descriptions-item>
          </template>
        </el-descriptions>
      </template>

      <!-- 来源检验批及处置（已有组件：同批入库单、报废单） -->
      <InboundLotTrace :lots="inbound.sourceLots" :inbound-id="inbound.inboundId" />

      <!-- 入库明细 -->
      <div class="detail-section-title">
        入库明细
        <span class="section-extra">
          物料 {{ inbound.items?.length || 0 }} 种 · 本单数量
          {{ formatNumber(inbound.totalQuantity) }} · 总金额 ¥ {{ formatCurrency(inbound.totalAmount) }}
        </span>
      </div>
      <el-table
        :data="inbound.items || []"
        border
        size="small"
        style="width: 100%"
        show-summary
        :summary-method="summaryMethod"
      >
        <el-table-column label="序号" width="62" align="center" fixed="left">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="130" fixed="left" />
        <el-table-column label="物料名称" prop="materialName" min-width="160" show-overflow-tooltip />
        <el-table-column label="规格型号" prop="specification" width="120" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="70" align="center" />
        <el-table-column label="批次号" prop="batchNo" width="150" />
        <el-table-column v-if="hasOriginalBatch" label="原批次号" prop="originalBatchNo" width="150" />
        <el-table-column label="本单数量" prop="quantity" width="100" align="right">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="允收量" prop="acceptedQuantity" width="96" align="right">
          <template #default="{ row }">{{ dashNumber(row.acceptedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="已入库" prop="postedQuantity" width="96" align="right">
          <template #default="{ row }">{{ formatNumber(row.postedQuantity ?? 0) }}</template>
        </el-table-column>
        <template v-if="isPurchaseSource">
          <el-table-column label="合格" prop="qualifiedQuantity" width="90" align="right">
            <template #default="{ row }">{{ dashNumber(row.qualifiedQuantity) }}</template>
          </el-table-column>
          <el-table-column label="不良" prop="rejectedQuantity" width="90" align="right">
            <template #default="{ row }">{{ dashNumber(row.rejectedQuantity) }}</template>
          </el-table-column>
          <el-table-column
            label="不良/备注原因"
            prop="rejectReason"
            min-width="140"
            show-overflow-tooltip
          />
        </template>
        <template v-if="hasItemInspection">
          <el-table-column label="检验结论" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.inspectionResult" :type="resultTagType(row.inspectionResult)" size="small">
                {{ resultLabel(row.inspectionResult) }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="处置" prop="disposition" width="110" show-overflow-tooltip />
        </template>
        <el-table-column label="单价" prop="unitPrice" width="96" align="right">
          <template #default="{ row }">¥ {{ formatCurrency(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="金额" prop="amount" width="120" align="right">
          <template #default="{ row }">¥ {{ formatCurrency(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="库位" prop="locationCode" width="100" />
        <el-table-column label="生产日期" prop="productionDate" width="110" align="center" />
        <el-table-column label="到期日期" prop="expiryDate" width="110" align="center" />
        <el-table-column v-if="showRollColumn" label="卷信息" min-width="210">
          <template #default="{ row }">
            <div class="roll-cell">
              <span v-if="row.rollNo">{{ row.rollNo }}</span>
              <span v-if="rollSize(row)" class="roll-size">{{ rollSize(row) }}</span>
              <el-button
                v-if="canPlanRoll(row) || row.rollPlan?.length"
                link
                type="primary"
                @click="openPlan(row)"
              >
                {{ row.rollPlan?.length ? `卷计划 ${row.rollPlan.length} 卷` : '录入卷计划' }}
              </el-button>
              <span v-if="!row.rollNo && !row.rollPlan?.length && !canPlanRoll(row)">-</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
      </el-table>

      <!-- 收货票据：附件中心 refs（收货弹窗上传 → 本入库单只读查看；来源 purchase_receipt 的 ID 即入库单ID） -->
      <AttachmentDocList
        v-if="isPurchaseSource && inbound.inboundId"
        source-type="purchase_receipt"
        :source-id="Number(inbound.inboundId)"
        title="收货票据"
      />

      <!-- 库存流水（同一单据号；无流水时不占位） -->
      <template v-if="txLoading || transactions.length">
        <div class="detail-section-title">库存流水</div>
        <el-table v-loading="txLoading" :data="transactions" border size="small" style="width: 100%">
          <el-table-column label="物料编码" prop="materialCode" width="130" />
          <el-table-column label="物料名称" prop="materialName" min-width="150" show-overflow-tooltip />
          <el-table-column label="变动" prop="quantity" width="100" align="right">
            <template #default="{ row }">
              <span :class="Number(row.quantity) < 0 ? 'tx-minus' : 'tx-plus'">
                {{ Number(row.quantity) > 0 ? '+' : '' }}{{ formatNumber(row.quantity) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="变动前" prop="beforeQuantity" width="100" align="right" />
          <el-table-column label="变动后" prop="afterQuantity" width="100" align="right" />
          <el-table-column label="批次号" prop="batchNo" width="140" />
          <el-table-column label="操作人" prop="operatorName" width="100" />
          <el-table-column label="时间" prop="transactionTime" width="170" />
          <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
        </el-table>
      </template>

      <RollReceiptPlanDialog
        v-model="planVisible"
        :item="planItem"
        :readonly="!planEditable"
        @success="loadDetail"
      />
    </template>
    <el-empty v-else-if="!loading" description="未找到入库单数据" :image-size="80" />
  </div>
</template>

<script setup lang="ts">
/**
 * 入库单详情（唯一实现，列表弹窗使用）
 * - 单据级：基本信息 / 业务来源与来料检验 / 数量与金额（概览 + 描述项）
 * - 明细级：收（本单数量）、允收、已入库、单价金额、检验结论、卷信息 + 合计行
 * - 来源检验批（同批入库单/报废单）、采购票据、库存流水（同单号）
 * - 动作只 emit（confirm / trace / print），由调用方接现有弹窗与抽屉，避免第二套实现。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { getTransactionsByDocNo, type TransactionVO } from '@/api/inventory/transaction'
import AttachmentDocList from '@/components/attachment/AttachmentDocList.vue'
import { formatCurrency, formatNumber } from '@/utils/format'
import type { InboundItemVO, InboundVO } from '@/types/inventory/inbound'
import {
  InboundOrderStatusEnum,
  InspectionResultEnum,
  inboundSourceLabel,
  inboundStatusLabel,
  inboundTypeLabel,
  isReverseInbound,
} from '@/enums/inventory/InboundEnum'
import { useUserStore } from '@/store/modules/user'
import InboundLotTrace from './InboundLotTrace.vue'
import RollReceiptPlanDialog from '@/components/inventory/RollReceiptPlanDialog.vue'

const props = defineProps<{
  inboundId: number | string
  /** 是否显示概览右侧动作（确认入库/查看流水/打印）：只由能接住 emit 的调用方开启 */
  showActions?: boolean
}>()
const emit = defineEmits<{
  confirm: []
  trace: []
  print: []
}>()

const userStore = useUserStore()
const loading = ref(false)
const inbound = ref<InboundVO | null>(null)
const transactions = ref<TransactionVO[]>([])

const isPurchaseSource = computed(
  () =>
    !!inbound.value?.sourceId &&
    (['PURCHASE', 'PURCHASE_ORDER'].includes(inbound.value.sourceType?.toUpperCase() || '') ||
      inbound.value.inboundType?.toUpperCase() === 'PURCHASE')
)
const items = computed<InboundItemVO[]>(() => inbound.value?.items || [])

const isReverse = computed(() => isReverseInbound(inbound.value?.inboundNo, inbound.value?.totalQuantity))
const inboundTypeText = computed(() =>
  inboundTypeLabel(inbound.value?.inboundType, {
    fallbackName: inbound.value?.inboundTypeName,
    reverse: isReverse.value,
  })
)
const inboundStatusText = computed(() =>
  inboundStatusLabel(inbound.value?.status, {
    sourceType: inbound.value?.sourceType,
    inboundType: inbound.value?.inboundType,
    fallbackName: inbound.value?.statusName,
  })
)
const statusTagType = computed(
  () =>
    (inbound.value?.status == null
      ? undefined
      : InboundOrderStatusEnum.getTagProps(inbound.value.status).type) as
      | 'success'
      | 'warning'
      | 'info'
      | 'danger'
      | undefined
)
const sourceText = computed(() =>
  inboundSourceLabel(inbound.value?.sourceType, inbound.value?.sourceNo)
)
const hasSourceInfo = computed(
  () => !!(inbound.value?.sourceType || inbound.value?.sourceNo || inbound.value?.inspectionResult)
)
const hasInspection = computed(() => !!inbound.value?.inspectionResult)
const inspectionResultText = computed(() => resultLabel(inbound.value?.inspectionResult))
const inspectionTagType = computed(() => resultTagType(inbound.value?.inspectionResult))

/** 待入库 = 允收量（未下发时为整单数量）− 已入库 */
const pendingQuantity = computed(() => {
  const accepted = Number(inbound.value?.acceptedQuantity ?? inbound.value?.totalQuantity ?? 0)
  const posted = Number(inbound.value?.postedQuantity ?? 0)
  return Math.max(accepted - posted, 0)
})
/** 是否已过账且达到允收量（与列表 isPosted 同一口径） */
const isPosted = computed(() => {
  const posted = Number(inbound.value?.postedQuantity ?? 0)
  const accepted = Number(inbound.value?.acceptedQuantity ?? 0)
  return posted > 0 && posted >= accepted
})
const canConfirm = computed(() => {
  const status = inbound.value?.status
  if (status == null || !userStore.hasPermission('inventory:inbound:confirm')) return false
  if (isPurchaseSource.value) {
    return status === InboundOrderStatusEnum.APPROVED.value && !isPosted.value
  }
  const manualStatuses: number[] = [
    InboundOrderStatusEnum.DRAFT.value,
    InboundOrderStatusEnum.PENDING.value,
    InboundOrderStatusEnum.APPROVED.value,
  ]
  return manualStatuses.includes(status)
})

const hasOriginalBatch = computed(() => items.value.some((row) => !!row.originalBatchNo))
const hasItemInspection = computed(() =>
  items.value.some((row) => !!row.inspectionResult || !!row.disposition)
)
const hasRoll = computed(() =>
  items.value.some((row) => !!row.rollNo || !!row.rollWidthMm || !!row.rollPlan?.length)
)
const canPlanRoll = (row: InboundItemVO) =>
  userStore.hasPermission('inventory:inbound:edit') &&
  isPurchaseSource.value &&
  inbound.value?.status === InboundOrderStatusEnum.APPROVED.value &&
  Number(row.postedQuantity || 0) === 0
const showRollColumn = computed(
  () => hasRoll.value || (isPurchaseSource.value && userStore.hasPermission('inventory:inbound:edit'))
)

const planVisible = ref(false)
const planItem = ref<InboundItemVO>()
const planEditable = computed(
  () =>
    userStore.hasPermission('inventory:inbound:edit') &&
    inbound.value?.status === InboundOrderStatusEnum.APPROVED.value &&
    Number(planItem.value?.postedQuantity || 0) === 0
)
function openPlan(row: InboundItemVO) {
  planItem.value = row
  planVisible.value = true
}

function dashNumber(value?: number | null) {
  return value == null ? '—' : formatNumber(value)
}
function resultLabel(result?: string | null) {
  if (!result) return '-'
  const normalized = result.toUpperCase()
  return InspectionResultEnum.canDo(normalized) ? InspectionResultEnum.getLabel(normalized) : result
}
function resultTagType(result?: string | null) {
  if (!result) return 'info'
  const normalized = result.toUpperCase()
  return InspectionResultEnum.canDo(normalized)
    ? (InspectionResultEnum.getTagProps(normalized).type as 'success' | 'warning' | 'info' | 'danger')
    : 'info'
}
function rollSize(row: InboundItemVO) {
  if (!row.rollWidthMm || !row.rollLengthM) return ''
  return `${row.rollWidthMm}mm × ${row.rollLengthM}m`
}
/** 合计行：数量类与金额类汇总（列不固定时按 property 命中求和） */
const SUM_PROPS = [
  'quantity',
  'acceptedQuantity',
  'postedQuantity',
  'qualifiedQuantity',
  'rejectedQuantity',
  'amount',
]
function summaryMethod({ columns, data }: { columns: Array<{ property?: string }>; data: InboundItemVO[] }) {
  return columns.map((column, index) => {
    if (index === 0) return '合计'
    const prop = column.property || ''
    if (!SUM_PROPS.includes(prop)) return ''
    const total = data.reduce((sum, row) => sum + Number((row as any)[prop] ?? 0), 0)
    return prop === 'amount' ? `¥ ${formatCurrency(total)}` : formatNumber(total)
  })
}

const loadDetail = async () => {
  if (!props.inboundId) return
  loading.value = true
  try {
    const res = await inboundApi.getById(String(props.inboundId))
    inbound.value = res.data || null
    await loadTransactions()
  } catch (error) {
    console.error('加载入库单详情失败:', error)
    ElMessage.error('加载入库单详情失败')
  } finally {
    loading.value = false
  }
}

// 库存流水（按单据号）
const txLoading = ref(false)
const loadTransactions = async () => {
  transactions.value = []
  const docNo = inbound.value?.inboundNo
  if (!docNo) return
  txLoading.value = true
  try {
    const res = await getTransactionsByDocNo(docNo)
    transactions.value = res.data || []
  } catch (error) {
    console.error('加载库存流水失败:', error)
  } finally {
    txLoading.value = false
  }
}

watch(
  () => props.inboundId,
  () => {
    if (props.inboundId) loadDetail()
  },
  { immediate: true }
)

defineExpose({ refresh: loadDetail })
</script>

<style scoped>
.inbound-detail {
  min-height: 120px;
}
/* 概览：单号/状态 + 关键指标 */
.detail-overview {
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.overview-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.overview-no {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.overview-spacer {
  flex: 1;
}
.overview-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}
.stat {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
}
.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.stat-value {
  margin-top: 4px;
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  word-break: break-all;
}
.stat-money .stat-value {
  color: var(--el-color-danger);
}
/* 分段标题 */
.detail-section-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 18px 0 8px;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
  font-weight: 600;
  font-size: 14px;
  line-height: 16px;
}
.detail-section-title .section-extra {
  font-weight: 400;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
/* 描述项：标签固定宽度且不换行（避免「来源批次数量」被拆成竖排字） */
.detail-desc {
  margin-top: 4px;
}
.detail-desc :deep(.el-descriptions__cell.is-bordered-label) {
  width: 108px;
  min-width: 108px;
  white-space: nowrap;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}
.detail-desc :deep(.el-descriptions__label) {
  white-space: nowrap;
}
.detail-amount {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
/* 明细表：卷信息、流水正负色 */
.roll-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.roll-size {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.tx-plus {
  color: var(--el-color-success);
}
.tx-minus {
  color: var(--el-color-danger);
}
</style>

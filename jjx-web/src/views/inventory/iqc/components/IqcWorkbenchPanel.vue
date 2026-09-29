<template>
  <div class="iqc-page">
    <div class="detail-head">
      <span class="detail-head__no">{{ selectedInbound?.inboundNo }}</span>
      <span class="detail-head__mode">{{ mode === 'handle' ? '· 处理模式' : '· 查看模式' }}</span>
      <el-button v-if="batchRows.length" link type="primary" @click="lineageVisible = true"
        >批次溯源</el-button
      >
    </div>

    <el-card v-if="selectedInbound" v-loading="detailLoading" class="detail-card">
      <template #header><span>来料检验单 · 材料检验</span></template>
      <InspectionStageBar
        :stages="['录入检验', '提交检验', '主管复核', '确认入库']"
        :current="stageIndex"
        :hint="stageHint"
      />
      <el-descriptions :column="4" border>
        <el-descriptions-item label="入库单号">{{ selectedInbound.inboundNo }}</el-descriptions-item
        ><el-descriptions-item label="供应商">{{
          selectedInbound.supplierName || '-'
        }}</el-descriptions-item
        ><el-descriptions-item label="整单收货">{{
          selectedInbound.totalQuantity
        }}</el-descriptions-item>
        <el-descriptions-item label="状态"
          ><el-tag :type="InboundOrderStatusEnum.getTagProps(selectedInbound.orderStatus).type">{{
            orderStatusLabel(selectedInbound)
          }}</el-tag></el-descriptions-item
        >
      </el-descriptions>
      <el-alert v-if="isApproved" type="success" :closable="false" show-icon class="posting-guide"
        ><template #title
          ><div class="guide-content">
            <span
              >检验已全部通过，请到【库存管理 → 入库管理】对入库单
              {{ selectedInbound.inboundNo }} 执行确认入库</span
            ><el-button v-if="canConfirmInbound" type="success" size="small" @click="goPosting"
              >去确认入库</el-button
            >
          </div></template
        ></el-alert
      >
      <div :class="['summary-bar', { complete: isAllDecided }]">
        共 {{ workRows.length }} 个材料 · 已判定 {{ decidedCount }} · 通过 {{ passCount }} · 不良
        {{ failCount }}<span v-if="hasEditableRows"> · 请完成可编辑材料后提交</span
        ><span v-else-if="hasPendingRows"> · 等待品质主管审核</span
        ><span v-else-if="isApproved"> · 检验已批准，待确认入库</span
        ><span v-else-if="isCompleted"> · 入库流程已完成</span>
      </div>
      <div v-if="canJudge" class="batch-bar">
        <el-button type="primary" plain @click="openReview">复核窗口</el-button>
        <span class="batch-tip"
          >逐项审核/驳回、发起复检；若明细已全部审核、单据却仍停在待审批（历史并发复核留下的状态），
          在窗口内点「重算单据状态」收尾。</span
        >
      </div>
      <div v-if="isInspectMode && canInspect" class="batch-bar">
        <el-button
          type="primary"
          :disabled="!selectedEditableRows.length"
          @click="batchPassSelected"
          >整批合格（已选 {{ selectedEditableRows.length }} 行）</el-button
        >
        <el-button :disabled="!selectedEditableRows.length" @click="copyFromPreviousRow"
          >复制上一行</el-button
        >
        <el-button :disabled="!selectedRows.length" @click="clearSelectedRows">清空选中</el-button>
        <el-button type="primary" plain @click="openWholeInboundChecks">整单检验录入</el-button>
        <el-button
          v-if="isInspectMode && hasEditableRows"
          type="primary"
          :loading="submitting"
          @click="submitInspection"
          >提交检验</el-button
        >
        <span class="batch-tip"
          >勾选多行可整批合格；录入弹窗内 Tab 移动、Enter
          保存、可"保存并下一行"；实测记录可留空</span
        >
      </div>
      <IqcMaterialTable
        :rows="workRows"
        :can-edit="rowCanEdit"
        :row-class="rowClassName"
        :progress="checkProgress"
        :can-judge="canJudge"
        :is-completed="isCompleted"
        @selection-change="handleSelectionChange"
        @edit="openMaterialChecks"
        @review="openReview"
        @print="printRow"
        @history="openHistory"
        @disposition-history="openDispositionHistory"
      />
      <el-card class="workbench-section" shadow="never">
        <template #header>
          <div class="section-header">
            <span>待处理明细</span>
            <span class="section-tip"
              >剩余可处置量 &gt; 0 才是待处置；处置后剩余减到 0 即结清（结清构成见下方处置单历史）</span
            >
          </div>
        </template>
        <DispositionPendingTable
          :rows="pendingRows"
          :can-dispose="canDispose"
          :loading="detailLoading"
          @dispose="openPendingDisposition"
        />
        <el-empty v-if="!pendingRows.length" description="当前没有待处置的隔离品" />
      </el-card>
    </el-card>
    <el-empty v-else description="未选择来料批次" />
    <MaterialChecksDialog
      v-model:visible="checksVisible"
      :row="activeWorkRow"
      :next-label="nextEditableLabel"
      :readonly="!activeWorkRow || !rowCanEdit(activeWorkRow)"
      @saved="handleChecksSaved"
      @next="openNextEditableRow"
    />
    <IqcReviewDialog
      v-model:visible="reviewVisible"
      :inbound-id="activeInboundId"
      :inbound-no="activeInboundNo"
      @success="handleFlowSuccess"
    />
    <DispositionHistoryDialog
      v-model:visible="dispositionHistoryVisible"
      :material-label="dispositionHistoryLabel"
      :rows="dispositionHistoryRows"
      :can-dispose="canDispose"
      @approve="approveScrapRow"
      @complete-rework="completeReworkRow"
    />
    <QualityHistoryDrawer
      v-model:visible="historyVisible"
      :lot-no="historyLotNo"
      :rows="historyRows"
      :loading="historyLoading"
    />
    <BatchLineageDrawer
      v-model:visible="lineageVisible"
      :batch-no="''"
      :batches="batchRows"
    />
    <IqcQuarantineDialog
      v-model:visible="dispositionVisible"
      :inbound-id="activeInboundId"
      :inbound-no="activeInboundNo"
      :item-id="activeItemId"
      :quarantine-row="activeQuarantine"
      @success="handleFlowSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { hasPermi } from '@/directives'
import { iqcApi } from '@/api/inventory/iqc'
import { qualityLotApi, type QualityLotHistory, type QualityTraceView } from '@/api/quality/lot'
import { inboundApi } from '@/api/inventory/inbound'
import type { IqcPendingVO } from '@/types/inventory/inbound'
import IqcReviewDialog from '@/views/inventory/inbound/components/IqcReviewDialog.vue'
import IqcQuarantineDialog from '@/views/inventory/inbound/components/IqcQuarantineDialog.vue'
import MaterialChecksDialog from './MaterialChecksDialog.vue'
import IqcMaterialTable from './IqcMaterialTable.vue'
import DispositionPendingTable from './DispositionPendingTable.vue'
import BatchLineageDrawer from './BatchLineageDrawer.vue'
import QualityHistoryDrawer from './QualityHistoryDrawer.vue'
import DispositionHistoryDialog from './DispositionHistoryDialog.vue'
import InspectionStageBar from '@/components/InspectionStageBar.vue'
import {
  batchPassIqcRow,
  copyIqcChecks,
  deriveIqcReasonText,
  iqcRowProblems,
  syncIqcRowFromChecks,
} from '../iqcRowRules'
import {
  InboundOrderStatusEnum,
  InspectionResultEnum as InboundInspectionResultEnum,
} from '@/enums/inventory/InboundEnum'
import {
  InspectionResult as QualityInspectionResult,
  QualityReviewStatus,
  QualityReviewStatusEnum,
} from '@/enums/quality/InspectionEnum'
import { sanitize } from '@/utils/reasonSanitizer'

type FlowKey = 'ALL' | 'UNINSPECTED' | 'REVIEW' | 'APPROVED' | 'COMPLETED'
type WorkRow = {
  itemId: string
  inspectionId?: number
  lotId?: number
  materialCode: string
  materialName: string
  batchNo?: string
  remainingDispositionQuantity: number
  quantity: number
  qualifiedQuantity: number
  rejectedQuantity: number
  acceptedQuantity: number
  inspectionResult: string
  disposition?: string
  rejectReason: string
  reviewStatus?: string
  isReinspection: boolean
  reinspectionQuantity: number
  qualityLotNo?: string
  parentQualityLotNo?: string
  baseAcceptedQuantity: number
  locked: boolean
  inspectionItems: any[]
  trace?: QualityTraceView
}
/**
 * dev-20260929-007（单页工作台）：本组件不再自己读路由。
 *   页面把「当前来料批次 + 模式」传下来：mode=view 只读；mode=handle 可按权限处理当前待办。
 *   改动完成后 emit('changed')，由页面刷新左侧列表的待办列。
 */
const props = withDefaults(
  defineProps<{
    inboundId: number | string
    mode?: 'view' | 'handle'
    action?: 'inspect' | 'reinspect'
  }>(),
  { mode: 'view' }
)
const emit = defineEmits<{ (e: 'changed'): void }>()
const router = useRouter()
const isInspectMode = computed(() => props.mode === 'handle')
const canInspect = computed(() => isInspectMode.value && hasPermi('quality:lot:inspect'))
const canJudge = computed(() => hasPermi('quality:lot:judge'))
const canDispose = computed(() => hasPermi(['quality:ncr:dispose']))
const canConfirmInbound = computed(() => hasPermi('inventory:inbound:confirm'))
const flowOptions: Array<{
  key: FlowKey
  label: string
  orderStatus?: number
  fillInspection?: boolean
}> = [
  { key: 'ALL', label: '全部' },
  {
    key: 'UNINSPECTED',
    label: '待检验',
    orderStatus: InboundOrderStatusEnum.PENDING.value,
    fillInspection: false,
  },
  {
    key: 'REVIEW',
    label: '待审核',
    orderStatus: InboundOrderStatusEnum.PENDING.value,
    fillInspection: true,
  },
  { key: 'APPROVED', label: '已批准', orderStatus: InboundOrderStatusEnum.APPROVED.value },
  { key: 'COMPLETED', label: '已完成', orderStatus: InboundOrderStatusEnum.COMPLETED.value },
]
const listQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  inboundNo: '',
  flowStatus: 'ALL' as FlowKey,
})
const inboundRows = ref<IqcPendingVO[]>([]),
  listTotal = ref(0),
  listLoading = ref(false),
  detailLoading = ref(false)
const selectedInboundId = ref<string | number>(''),
  selectedInbound = ref<IqcPendingVO>(),
  workRows = ref<WorkRow[]>([]),
  dispositionRows = ref<any[]>([])
/** 隔离品（待处置/已处置）带上下文行 —— 与处置单历史同一读口（iqc-workbench）。 */
const quarantines = ref<any[]>([])
/** 该来料批次的批次链路（工作台一次带回，供「批次溯源」抽屉） */
const batchRows = ref<any[]>([])
const lineageVisible = ref(false)
/** 当前正在处置的那一行隔离品（传给弹窗，避免弹窗只有瘦实体、判不了） */
const activeQuarantine = ref<any>()
/** 处置历史弹窗：当前查看的材料行（按 inbound_item_id 过滤该行的处置单） */
const dispositionHistoryVisible = ref(false)
const dispositionHistoryItem = ref<WorkRow>()
const dispositionHistoryRows = computed(() => {
  const item = dispositionHistoryItem.value
  if (!item) return []
  return dispositionRows.value.filter((row: any) => {
    if (row.inboundItemId != null && item.itemId != null) {
      return String(row.inboundItemId) === String(item.itemId)
    }
    // 兼容早期未写 inbound_item_id 的行：回退按检验批匹配
    return item.lotId != null && String(row.lotId) === String(item.lotId)
  })
})
const dispositionHistoryLabel = computed(() => {
  const item = dispositionHistoryItem.value
  if (!item) return ''
  const lot = item.qualityLotNo ? ` · ${item.qualityLotNo}` : ''
  return `${item.materialCode || '-'} ${item.materialName || ''}${lot}`.trim()
})
const pendingRows = computed(() =>
  quarantines.value.filter((row) => Number(row.remainingQuantity || 0) > 0)
)
const historyRows = ref<QualityLotHistory[]>([])
const historyLoading = ref(false)
const historyVisible = ref(false)
const historyLotNo = ref('')
const inspectionRemark = ref(''),
  submitting = ref(false),
  checksVisible = ref(false),
  reviewVisible = ref(false),
  dispositionVisible = ref(false)
const activeWorkRow = ref<WorkRow>(),
  activeInboundId = ref<number>(),
  activeInboundNo = ref(''),
  activeItemId = ref<string>()
const num = (value?: number | string | null) =>
  value == null || value === ''
    ? '-'
    : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
/** dev-20260924-017：校验未通过的行（行级红标，提交时一次提示 + 定位第一处） */
const problemRowIds = ref<Set<number>>(new Set())
function rowClassName({ row }: { row: WorkRow }) {
  return problemRowIds.value.has(Number(row.itemId)) ? 'iqc-problem-row' : ''
}
const isApproved = computed(
  () =>
    workRows.value.length > 0 &&
    workRows.value.every((row) => row.reviewStatus === QualityReviewStatus.APPROVED)
)
const isCompleted = computed(
  () => selectedInbound.value?.orderStatus === InboundOrderStatusEnum.COMPLETED.value
)
const hasPendingRows = computed(() =>
  workRows.value.some((row) => row.reviewStatus === QualityReviewStatus.PENDING)
)
const hasEditableRows = computed(() => workRows.value.some(rowCanEdit))
const decidedCount = computed(
  () => workRows.value.filter((row) => Boolean(row.inspectionResult)).length
)
const passCount = computed(
  () =>
    workRows.value.filter((row) => row.inspectionResult === InboundInspectionResultEnum.PASS.value)
      .length
)
const failCount = computed(
  () =>
    workRows.value.filter((row) => row.inspectionResult === InboundInspectionResultEnum.FAIL.value)
      .length
)
const isAllDecided = computed(
  () => workRows.value.length > 0 && decidedCount.value === workRows.value.length
)
// dev-20260924-017 P2：流程显式化 —— 录入 → 提交 → 复核 → 入库
const stageIndex = computed(() => {
  if (isApproved.value || isCompleted.value) return 3
  if (hasPendingRows.value) return 2
  return hasEditableRows.value ? 0 : 1
})
const stageHint = computed(() => {
  const editable = workRows.value.filter(rowCanEdit).length
  const pending = workRows.value.filter(
    (row) => row.reviewStatus === QualityReviewStatus.PENDING
  ).length
  const parts: string[] = []
  if (editable) parts.push(`${editable} 行待录入`)
  if (pending) parts.push(`${pending} 行待复核`)
  return parts.length ? `本单：${parts.join(' / ')}` : ''
})

function selectedFlowOption() {
  return flowOptions.find((option) => option.key === listQuery.flowStatus) || flowOptions[0]
}
function orderStatusLabel(row: IqcPendingVO) {
  return row.orderStatus === InboundOrderStatusEnum.PENDING.value
    ? row.inspectionResult
      ? '待审核'
      : '待检验'
    : InboundOrderStatusEnum.getLabel(row.orderStatus)
}
function rowCanEdit(row: WorkRow) {
  if (!isInspectMode.value || !canInspect.value) return false
  if (
    row.reviewStatus === QualityReviewStatus.APPROVED ||
    row.reviewStatus === QualityReviewStatus.PENDING
  )
    return false
  return (
    selectedInbound.value?.orderStatus === InboundOrderStatusEnum.PENDING.value ||
    // 采购 IQC 的入库单先经采购审批后才进入检验，APPROVED 仍属于可录入阶段。
    selectedInbound.value?.orderStatus === InboundOrderStatusEnum.APPROVED.value ||
    (selectedInbound.value?.orderStatus === InboundOrderStatusEnum.COMPLETED.value &&
      row.reviewStatus === QualityReviewStatus.DRAFT)
  )
}
function createCheck(
  checkItem: string,
  standard: string,
  inspectionMethod: string,
  equipment: string
) {
  return {
    checkItem,
    standard,
    inspectionMethod,
    equipment,
    actualValue: '',
    result: QualityInspectionResult.PASS,
    crQuantity: 0,
    maQuantity: 0,
    miQuantity: 0,
    remark: '',
  }
}
function defaultInspectionItems() {
  return [
    createCheck('规格', '与采购订单及实物一致', '核对', '目视'),
    createCheck('颜色', '与标准样板无明显偏差', '比较样板', '目视/样板'),
    createCheck('外观', '无脏污、黑点、变形、折伤、刮伤、混料、晶点、毛边', '目视', '目视'),
    createCheck('长度', '符合图纸或采购要求', '测量', '钢直尺/卡尺'),
    createCheck('宽度', '符合图纸或采购要求', '测量', '钢直尺/卡尺'),
    createCheck('厚度', '符合图纸或采购要求', '测量', '千分尺'),
    createCheck('特性', '附着力及其他特性符合要求', '测试', '3M600胶'),
    createCheck('包装、标识', '包装完整，标识与订单及实物一致并符合环保要求', '目视', '目视'),
  ]
}
function normalizeInspectionItem(check: any) {
  return {
    checkItem: check.checkItem,
    standard: check.standard,
    inspectionMethod: check.inspectionMethod,
    equipment: check.equipment,
    actualValue: check.actualValue,
    result: check.result,
    crQuantity: Number(check.crQuantity || 0),
    maQuantity: Number(check.maQuantity || 0),
    miQuantity: Number(check.miQuantity || 0),
    remark: check.remark,
  }
}
function clearSelection() {
  selectedInboundId.value = ''
  selectedInbound.value = undefined
  workRows.value = []
  dispositionRows.value = []
  inspectionRemark.value = ''
  activeWorkRow.value = undefined
}
async function loadList(preserveSelection = false) {
  listLoading.value = true
  try {
    const filter = selectedFlowOption()
    const result = await inboundApi.iqcList({
      pageNum: listQuery.pageNum,
      pageSize: listQuery.pageSize,
      inboundNo: listQuery.inboundNo || undefined,
      orderStatus: filter.orderStatus,
      fillInspection: filter.fillInspection,
    })
    inboundRows.value = result.data?.records || []
    listTotal.value = result.data?.total || 0
    if (!preserveSelection) clearSelection()
    else if (selectedInbound.value)
      selectedInbound.value =
        inboundRows.value.find((row) => row.inboundId === selectedInboundId.value) ||
        selectedInbound.value
  } finally {
    listLoading.value = false
  }
}
function searchList() {
  listQuery.pageNum = 1
  loadList()
}
function handlePageChange() {
  loadList()
}
async function refreshAll() {
  const id = selectedInboundId.value
  if (id) await loadById(String(id))
}
async function loadById(id: string) {
  const { data } = await inboundApi.getById(id)
  if (!data) throw new Error('来料批次不存在或无权访问')
  selectedInboundId.value = data.inboundId
  selectedInbound.value = {
    ...data,
    materialCount: data.items?.length || 0,
    inspectedCount: 0,
    pendingReviewCount: 0,
    approvedCount: 0,
    failRowCount: 0,
    orderStatus: Number(data.status),
  }
  await loadInboundDetail(selectedInbound.value)
}
async function selectInbound(row?: IqcPendingVO) {
  if (!row || (selectedInboundId.value === row.inboundId && workRows.value.length)) return
  selectedInbound.value = row
  selectedInboundId.value = row.inboundId
  await loadInboundDetail(row)
}
async function loadInboundDetail(row: IqcPendingVO) {
  const requestedId = row.inboundId
  workRows.value = []
  inspectionRemark.value = ''
  detailLoading.value = true
  try {
    // dev-20260929-007：唯一读口 —— 单据头 / 隔离品(带上下文) / 处置单 一次取回，不再三路并发各自拼装
    const { data: workbench } = await inboundApi.getIqcWorkbench(String(requestedId))
    const data = workbench?.inbound
    quarantines.value = workbench?.quarantines || []
    dispositionRows.value = workbench?.dispositions || []
    batchRows.value = workbench?.batches || []
    const remainingByItemLot = new Map<string, number>()
    quarantines.value.forEach((record: any) => {
      const key = `${record.inboundItemId}:${record.lotId || ''}`
      remainingByItemLot.set(
        key,
        (remainingByItemLot.get(key) || 0) + Number(record.remainingQuantity || 0)
      )
    })
    // dev-20260929-007（L1）：检验批与检验项由工作台一次带回，材料行不再逐行请求（原为 2 请求/行）。
    // 字段口径与 api/production/quality.ts 的 toQualityVO 一致，避免出现第二套映射。
    const lotById = new Map<number, any>(
      (workbench?.lots || []).map((lot: any) => [Number(lot.lotId), lot])
    )
    const itemsByLot = new Map<number, any[]>()
    ;(workbench?.lotItems || []).forEach((item: any) => {
      const key = Number(item.lotId)
      if (!itemsByLot.has(key)) itemsByLot.set(key, [])
      itemsByLot.get(key)!.push(item)
    })
    const toLotView = (lot: any) =>
      lot
        ? {
            inspectionId: lot.lotId,
            inspectionNo: lot.lotNo,
            result: String(lot.result || QualityInspectionResult.PENDING).toLowerCase(),
            totalQty: lot.lotQuantity,
            passQty: lot.passQuantity,
            failQty: lot.failQuantity,
            previousInspectionId: lot.parentLotId,
            reviewStatus: lot.reviewStatus,
            materialCode: lot.materialCode,
            defectDesc: lot.defectReason,
            items: (itemsByLot.get(Number(lot.lotId)) || []).map((it: any) => ({ ...it })),
            trace: undefined as QualityTraceView | undefined,
          }
        : undefined
    const loadedRows = (data?.items || []).map((item: any): WorkRow => {
        // dev-20260922-009：新模型检验批在 lotId（inspectionId 已置空），优先取 lotId，回退旧字段
        const lotRef = item.lotId ?? item.inspectionId
        const quality = lotRef ? toLotView(lotById.get(Number(lotRef))) : undefined
        const previousQuality = quality?.previousInspectionId
          ? toLotView(lotById.get(Number(quality.previousInspectionId)))
          : undefined
        const isReinspection = Boolean(
            quality?.previousInspectionId && quality?.result === QualityInspectionResult.PENDING
          ),
          reinspectionQuantity = isReinspection
            ? Number(quality?.totalQty || previousQuality?.failQty || 0)
            : 0,
          baseAcceptedQuantity = Number(item.acceptedQuantity || 0),
          fresh = !quality
        return {
          itemId: String(item.inboundItemId || item.itemId),
          inspectionId: quality?.inspectionId,
          lotId: lotRef,
          materialCode: item.materialCode,
          materialName: item.materialName,
          batchNo: item.batchNo,
          qualityLotNo: quality?.inspectionNo,
          parentQualityLotNo: previousQuality?.inspectionNo,
          remainingDispositionQuantity:
            remainingByItemLot.get(`${item.inboundItemId || item.itemId}:${lotRef || ''}`) || 0,
          quantity: Number(item.quantity || 0),
          qualifiedQuantity: fresh
            ? 0
            : isReinspection
              ? Number(quality?.passQty || 0)
              : Number(item.qualifiedQuantity ?? item.quantity ?? 0),
          rejectedQuantity: fresh ? 0 : isReinspection ? 0 : Number(item.rejectedQuantity || 0),
          acceptedQuantity: fresh
            ? 0
            : isReinspection
              ? reinspectionQuantity
              : Number(item.acceptedQuantity ?? item.quantity ?? 0),
          inspectionResult: fresh
            ? ''
            : isReinspection
              ? quality?.result === QualityInspectionResult.PENDING
                ? ''
                : quality?.result === QualityInspectionResult.FAIL
                  ? InboundInspectionResultEnum.FAIL.value
                  : InboundInspectionResultEnum.PASS.value
              : item.inspectionResult || InboundInspectionResultEnum.PASS.value,
          disposition: isReinspection ? undefined : item.disposition,
          rejectReason: isReinspection ? '' : sanitize(item.rejectReason),
          reviewStatus: quality?.reviewStatus,
          isReinspection,
          reinspectionQuantity,
          baseAcceptedQuantity,
          locked:
            quality?.reviewStatus === QualityReviewStatus.PENDING ||
            quality?.reviewStatus === QualityReviewStatus.APPROVED,
          inspectionItems: quality?.items?.length
            ? quality.items.map(normalizeInspectionItem)
            : defaultInspectionItems(),
          trace: quality?.trace,
        }
      })
    if (selectedInboundId.value === requestedId) {
      workRows.value = loadedRows
      inspectionRemark.value = data?.inspectionRemark || ''
    }
  } finally {
    detailLoading.value = false
  }
}
watch(
  workRows,
  (rows) => {
    rows.forEach((row) => {
      if (row.rejectReason.length > 200) {
        row.rejectReason = row.rejectReason.slice(0, 200)
        ElMessage.warning('不合格补充说明最多 200 字，已截断')
      }
    })
  },
  { deep: true }
)
function handleResultChange(row: WorkRow) {
  if (row.inspectionResult === InboundInspectionResultEnum.PASS.value) {
    row.disposition = undefined
    row.acceptedQuantity = row.qualifiedQuantity
  } else if (row.inspectionResult === InboundInspectionResultEnum.FAIL.value) syncDisposition(row)
}
function syncDisposition(row: WorkRow) {
  row.acceptedQuantity = row.qualifiedQuantity
}
function recalRow(row: WorkRow) {
  row.qualifiedQuantity = Number(row.qualifiedQuantity || 0)
  row.rejectedQuantity = Number(row.rejectedQuantity || 0)
  row.acceptedQuantity = row.qualifiedQuantity
  row.inspectionResult =
    row.rejectedQuantity > 0
      ? InboundInspectionResultEnum.FAIL.value
      : row.qualifiedQuantity > 0
        ? InboundInspectionResultEnum.PASS.value
        : ''
  handleResultChange(row)
  if (row.inspectionResult === InboundInspectionResultEnum.FAIL.value) syncDisposition(row)
}
function checkProgress(row: WorkRow) {
  return row.inspectionItems.filter(
    (check: any) =>
      String(check.actualValue || '').trim() ||
      Number(check.crQuantity || 0) > 0 ||
      Number(check.maQuantity || 0) > 0 ||
      Number(check.miQuantity || 0) > 0 ||
      String(check.remark || '').trim()
  ).length
}
// ==================== 批量操作 + 连续录入（dev-20260916-008） ====================
const selectedRows = ref<WorkRow[]>([])
const selectedEditableRows = computed(() => selectedRows.value.filter((row) => rowCanEdit(row)))

function handleSelectionChange(rows: WorkRow[]) {
  selectedRows.value = rows
}

/** 整批合格：结论合格 + 实测记录留空 + CR/MA/MI 归零 + 合格/接收=收货数（全检口径） */
function batchPassSelected() {
  const rows = selectedEditableRows.value
  if (!rows.length) return
  rows.forEach((row) => batchPassIqcRow(row))
  ElMessage.success(`已对 ${rows.length} 行按整批合格填充（实测记录留空）`)
}

/** 复制上一行：只带 检验标准/方法/设备/结论，不带实测值与缺陷数 */
function copyFromPreviousRow() {
  const rows = selectedEditableRows.value
  if (!rows.length) return
  let copied = 0
  rows.forEach((row) => {
    const index = workRows.value.indexOf(row)
    const previous = index > 0 ? workRows.value[index - 1] : undefined
    if (!previous || !rowCanEdit(row)) return
    copyIqcChecks(previous, row)
    copied++
  })
  if (copied) ElMessage.success(`已把上一行检验项复制到 ${copied} 行`)
  else ElMessage.warning('选中的行没有可复制的上一行')
}

/** 清空选中行：检验项与行级结论复位（不改收货数量） */
function clearSelectedRows() {
  selectedEditableRows.value.forEach((row) => {
    ;(row.inspectionItems || []).forEach((check: any) => {
      check.actualValue = ''
      check.crQuantity = 0
      check.maQuantity = 0
      check.miQuantity = 0
      check.result = undefined
      check.remark = ''
    })
    syncIqcRowFromChecks(row)
  })
  selectedRows.value = []
}

/** 下一可编辑行（供弹窗"保存并下一行"） */
const nextEditableRow = computed(() => {
  const current = activeWorkRow.value
  if (!current) return undefined
  const index = workRows.value.indexOf(current)
  if (index < 0) return undefined
  return workRows.value.slice(index + 1).find((row) => rowCanEdit(row))
})
const nextEditableLabel = computed(() =>
  nextEditableRow.value ? nextEditableRow.value.materialCode : ''
)
function openNextEditableRow() {
  const next = nextEditableRow.value
  if (!next) {
    ElMessage.success('已是最后一个可编辑材料')
    checksVisible.value = false
    return
  }
  activeWorkRow.value = next
}

/** 整单检验录入：从第一个可编辑材料开始，逐行连续录入 */
function openWholeInboundChecks() {
  const first = workRows.value.find((row) => rowCanEdit(row))
  if (!first) {
    ElMessage.info('当前单据没有可编辑的材料行')
    return
  }
  openMaterialChecks(first)
}

function openMaterialChecks(row?: WorkRow) {
  if (!row) return
  activeWorkRow.value = row
  checksVisible.value = true
}
function handleChecksSaved() {
  if (activeWorkRow.value && rowCanEdit(activeWorkRow.value)) recalRow(activeWorkRow.value)
  if (activeWorkRow.value) problemRowIds.value.delete(Number(activeWorkRow.value.itemId))
}
function activateSelected() {
  activeInboundId.value = Number(selectedInbound.value?.inboundId)
  activeInboundNo.value = selectedInbound.value?.inboundNo || ''
}
function openReview() {
  activateSelected()
  reviewVisible.value = true
}
/** 材料行「处置历史」：只看这一行材料的处置单（含 通过/驳回/完成返工）。 */
function openDispositionHistory(row?: any) {
  if (!row) return
  dispositionHistoryItem.value = row as WorkRow
  dispositionHistoryVisible.value = true
}

/** 待处理明细行「处置」：带着这一行的完整上下文打开处置弹窗。 */
function openPendingDisposition(row: any) {
  activeInboundId.value = Number(selectedInbound.value?.inboundId)
  activeInboundNo.value = selectedInbound.value?.inboundNo || ''
  activeItemId.value = row.inboundItemId == null ? undefined : String(row.inboundItemId)
  activeQuarantine.value = row
  dispositionVisible.value = true
}

/** 处置单历史：报废审批（通过/驳回）—— 原先在列表页的独立弹窗，现就地对着处置单行处理。 */
async function approveScrapRow(row: any, approved: boolean) {
  if (!hasPermi('quality:ncr:dispose')) return
  let remark: string | undefined
  try {
    const result = await ElMessageBox.prompt(
      approved ? '审批意见（可选）' : '请填写驳回原因',
      approved ? '通过报废审批' : '驳回报废审批',
      { inputValidator: (value) => approved || Boolean(value?.trim()) || '请填写驳回原因' }
    )
    remark = result.value
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }
  await iqcApi.approveScrap(String(row.dispositionId), { approved, remark })
  ElMessage.success('处理完成')
  await handleFlowSuccess()
}

/** 处置单历史：确认返工完成（生成复检子批与待复检记录）。 */
async function completeReworkRow(row: any) {
  if (!hasPermi('quality:ncr:dispose')) return
  try {
    await ElMessageBox.confirm('确认返工已完成并生成待复检记录？', '完成返工')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }
  await iqcApi.completeRework(String(row.dispositionId))
  ElMessage.success('处理完成')
  await handleFlowSuccess()
}
async function openHistory(row: WorkRow) {
  if (!row.lotId) return
  historyLotNo.value = row.qualityLotNo || row.batchNo || `检验批 ${row.lotId}`
  historyLoading.value = true
  historyVisible.value = true
  try {
    const { data } = await qualityLotApi.history(row.lotId)
    historyRows.value = data || []
  } finally {
    historyLoading.value = false
  }
}
function printRow(row: WorkRow) {
  router.push({
    path: '/production/quality-print/iqc-report',
    query: { inboundId: selectedInbound.value?.inboundId, inspectionId: row.inspectionId },
  })
}
function goPosting() {
  router.push({ path: '/inventory/inbound', query: { bizId: selectedInbound.value?.inboundId } })
}
async function handleFlowSuccess() {
  await refreshAll()
  emit('changed')
}
async function submitInspection() {
  if (!selectedInbound.value) return
  if (!isInspectMode.value) {
    ElMessage.warning('当前为检验详情模式，不能提交检验')
    return
  }
  // dev-20260924-017：校验从"发现一处就 return"改为"收集全部问题 + 行级红标 + 一次性提示"
  const problems: string[] = []
  const badRowIds = new Set<number>()
  for (const item of workRows.value) {
    if (!rowCanEdit(item)) continue
    item.acceptedQuantity = Number(item.qualifiedQuantity || 0)
    const rowIssues = iqcRowProblems(item)
    if (rowIssues.length) {
      problems.push(`${item.materialCode}：${rowIssues.join('；')}`)
      badRowIds.add(Number(item.itemId))
    }
  }
  problemRowIds.value = badRowIds
  if (problems.length) {
    await nextTick()
    document
      .querySelector('.iqc-problem-row')
      ?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    const esc = (text: string) => text.replace(/</g, '&lt;').replace(/>/g, '&gt;')
    await ElMessageBox.alert(
      `<div style="max-height:320px;overflow:auto">${problems
        .map((p) => `<div>· ${esc(p)}</div>`)
        .join('')}</div>`,
      `还有 ${badRowIds.size} 行需要处理`,
      { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' }
    ).catch(() => undefined)
    return
  }
  const undecided = workRows.value.filter(
    (item) => rowCanEdit(item) && !item.inspectionResult
  ).length
  if (undecided) {
    try {
      await ElMessageBox.confirm(
        `还有 ${undecided} 行未判定；每行合格数量与不良数量之和须等于收货数量，是否继续？`,
        '提示',
        { confirmButtonText: '继续', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    const { data } = await inboundApi.submitApprove(String(selectedInbound.value.inboundId), {
      inspectionRemark: inspectionRemark.value || undefined,
      items: workRows.value.map(
        ({
          itemId,
          lotId,
          inspectionResult,
          disposition,
          qualifiedQuantity,
          rejectedQuantity,
          acceptedQuantity,
          rejectReason,
          inspectionItems,
        }) => ({
          itemId,
          lotId,
          inspectionResult,
          disposition,
          qualifiedQuantity,
          rejectedQuantity,
          acceptedQuantity,
          rejectReason: rejectReason || undefined,
          inspectionItems: inspectionItems.map(normalizeInspectionItem),
        })
      ),
    })
    if (data) {
      ElMessage.success('检验已逐项提交，等待品质主管复核')
      await refreshAll()
      emit('changed')
    } else ElMessage.error('检验提交未生效，请检查入库单状态或刷新后重试')
  } finally {
    submitting.value = false
  }
}
/** 载入当前批次；action=inspect/reinspect 时直接定位到对应可编辑行（原 URL 参数行为保持不变）。 */
async function loadCurrent() {
  const id = Number(props.inboundId)
  if (!id) return
  await loadById(String(id))
  if (props.mode !== 'handle' || (props.action !== 'inspect' && props.action !== 'reinspect')) return
  const reinspection = props.action === 'reinspect'
  const target = workRows.value.find(
    (row) => rowCanEdit(row) && row.isReinspection === reinspection
  )
  if (target) openMaterialChecks(target)
  else ElMessage.info('该项待办已变化，请查看当前材料状态')
}
onMounted(loadCurrent)
watch(() => props.inboundId, loadCurrent)
onBeforeUnmount(clearSelection)
</script>

<style scoped>
.iqc-page {
  padding: 20px;
}
/* dev-20260924-017：校验未通过的行 → 红底标记，便于提交时定位 */
:deep(.iqc-problem-row) > td {
  background: var(--el-color-danger-light-9) !important;
}
.header,
.guide-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  font-weight: 600;
}
.list-tip,
.check-progress {
  color: #909399;
  font-size: 12px;
}
.list-tip {
  margin-bottom: 10px;
}
.progress-part {
  margin-left: 10px;
  color: #606266;
}
.radio-cell {
  display: flex;
  min-height: 24px;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.detail-card {
  margin-top: 16px;
}
.workbench-section {
  margin-top: 16px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
}
.section-tip,
.muted {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}
.emphasis {
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
  font-weight: 600;
}
.posting-guide {
  margin-top: 16px;
}
.guide-content {
  width: 100%;
}
.summary-bar {
  margin: 16px 0 10px;
  padding: 10px 14px;
  color: #606266;
  background: #f4f4f5;
  border-radius: 4px;
}
.summary-bar.complete {
  color: #529b2e;
  background: #f0f9eb;
}
.material-table :deep(.el-input-number) {
  width: 112px;
}
/* 批量工具条（dev-20260916-008） */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.batch-tip {
  color: #909399;
  font-size: 12px;
}
.reason-input {
  margin-top: 6px;
}
.remark-form {
  margin-top: 16px;
}
.detail-actions {
  display: flex;
  justify-content: flex-end;
}
</style>

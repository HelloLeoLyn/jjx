<template>
  <div class="iqc-page">
    <div class="detail-head">
      <el-button link type="primary" @click="router.push('/inventory/iqc')">← 返回列表</el-button>
      <span class="detail-head__no">{{ selectedInbound?.inboundNo }}</span>
    </div>

    <el-card v-if="selectedInbound" v-loading="detailLoading" class="detail-card">
      <template #header><span>材料检验处理</span></template>
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
      <div v-if="canInspect" class="batch-bar">
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
        :can-dispose="canDispose"
        :is-completed="isCompleted"
        @selection-change="handleSelectionChange"
        @edit="openMaterialChecks"
        @review="openReview"
        @print="printRow"
        @history="openHistory"
        @go-disposition="goDisposition"
      />
      <el-card class="workbench-section" shadow="never">
        <template #header>
          <div class="section-header">
            <span>不良处置与复检链路</span>
            <span class="section-tip">处置记录属于当前来料批次；数量均为本次动作口径</span>
          </div>
        </template>
        <el-table :data="dispositionRows" border size="small">
          <el-table-column prop="dispositionNo" label="处置单号" width="180" />
          <el-table-column label="类型" width="140">
            <template #default="{ row }">
              <el-tag :type="IqcQuarantineActionEnum.getTagProps(row.action).type">
                {{ IqcQuarantineActionEnum.getLabel(row.action) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="materialCode" label="物料" min-width="150" />
          <el-table-column label="数量口径" width="130">
            <template #default="{ row }">本次处置量：{{ num(row.quantity) }}</template>
          </el-table-column>
          <el-table-column label="复检关系" min-width="220">
            <template #default="{ row }">
              <template v-if="row.action === 'REWORK'">
                <div>原批：{{ row.batchNo || '-' }}</div>
                <span class="muted">复检批：{{ row.childBatchNo || '待生成' }}</span>
                <div class="emphasis">该批只针对 {{ num(row.quantity) }} 件</div>
              </template>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag :type="IqcDispositionOrderStatusEnum.getTagProps(row.status).type">
                {{ IqcDispositionOrderStatusEnum.getLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>
        <el-empty v-if="!dispositionRows.length" description="当前暂无不良处置记录" />
      </el-card>
      <el-card v-if="historyLotId" class="workbench-section" shadow="never">
        <template #header>
          <div class="section-header">
            <span>{{ historyLotNo }} · 质量历史</span>
            <el-button link type="primary" @click="closeHistory">收起</el-button>
          </div>
        </template>
        <el-timeline v-loading="historyLoading">
          <el-timeline-item
            v-for="item in historyRows"
            :key="item.historyId"
            :timestamp="item.createTime || '-'">
            <div class="history-event">{{ historyEventLabel(item.eventType) }}</div>
            <div class="muted">操作人：{{ item.operatorName || '-' }} · {{ item.remark || '无备注' }}</div>
          </el-timeline-item>
          <el-empty v-if="!historyLoading && !historyRows.length" description="暂无质量历史" />
        </el-timeline>
      </el-card>
      <template v-if="hasEditableRows"
        ><el-form label-width="90px" class="remark-form"
          ><el-form-item label="整单备注"
            ><el-input
              v-model="inspectionRemark"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit /></el-form-item
        ></el-form>
        <div class="detail-actions">
          <el-button type="primary" :loading="submitting" @click="submitInspection"
            >提交检验</el-button
          >
        </div></template
      >
    </el-card>
    <el-empty v-else description="请选择上方一张采购入库单" />
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
    <IqcQuarantineDialog
      v-model:visible="dispositionVisible"
      :inbound-id="activeInboundId"
      :inbound-no="activeInboundNo"
      :item-id="activeItemId"
      @success="handleFlowSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { hasPermi } from '@/directives'
import { qualityApi } from '@/api/production/quality'
import { qualityLotApi, type QualityLotHistory, type QualityTraceView } from '@/api/quality/lot'
import { inboundApi } from '@/api/inventory/inbound'
import type { IqcPendingVO } from '@/types/inventory/inbound'
import IqcReviewDialog from '@/views/inventory/inbound/components/IqcReviewDialog.vue'
import IqcQuarantineDialog from '@/views/inventory/inbound/components/IqcQuarantineDialog.vue'
import MaterialChecksDialog from './components/MaterialChecksDialog.vue'
import IqcMaterialTable from './components/IqcMaterialTable.vue'
import InspectionStageBar from '@/components/InspectionStageBar.vue'
import {
  batchPassIqcRow,
  copyIqcChecks,
  deriveIqcReasonText,
  iqcRowProblems,
  syncIqcRowFromChecks,
} from './iqcRowRules'
import {
  InboundOrderStatusEnum,
  InspectionResultEnum as InboundInspectionResultEnum,
} from '@/enums/inventory/InboundEnum'
import {
  InspectionResult as QualityInspectionResult,
  QualityReviewStatus,
  QualityReviewStatusEnum,
} from '@/enums/quality/InspectionEnum'
import {
  IqcDispositionOrderStatusEnum,
  IqcQuarantineActionEnum,
} from '@/enums/inventory/IqcQuarantineEnum'
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
const router = useRouter()
const route = useRoute()
const canInspect = computed(() => hasPermi('quality:lot:inspect'))
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
const historyRows = ref<QualityLotHistory[]>([])
const historyLoading = ref(false)
const historyLotId = ref<number>()
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
  value == null || value === '' ? '-' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
/** dev-20260924-017：校验未通过的行（行级红标，提交时一次提示 + 定位第一处） */
const problemRowIds = ref<Set<number>>(new Set())
function rowClassName({ row }: { row: WorkRow }) {
  return problemRowIds.value.has(Number(row.itemId)) ? 'iqc-problem-row' : ''
}
const isApproved = computed(
  () => selectedInbound.value?.orderStatus === InboundOrderStatusEnum.APPROVED.value
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
  if (!canInspect.value) return false
  if (
    row.reviewStatus === QualityReviewStatus.APPROVED ||
    row.reviewStatus === QualityReviewStatus.PENDING
  )
    return false
  return (
    selectedInbound.value?.orderStatus === InboundOrderStatusEnum.PENDING.value ||
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
    const [{ data }, quarantineResult, dispositionResult] = await Promise.all([
      inboundApi.getById(String(requestedId)),
      inboundApi.listQuarantine(String(requestedId)),
      inboundApi.listDispositionOrders(String(requestedId)),
    ])
    dispositionRows.value = dispositionResult.data || []
    const remainingByItemLot = new Map<string, number>()
    ;(quarantineResult.data || []).forEach((record: any) => {
      const key = `${record.inboundItemId}:${record.lotId || ''}`
      remainingByItemLot.set(
        key,
        (remainingByItemLot.get(key) || 0) + Number(record.remainingQuantity || 0)
      )
    })
    const loadedRows = await Promise.all(
      (data?.items || []).map(async (item: any): Promise<WorkRow> => {
        // dev-20260922-009：新模型检验批在 lotId（inspectionId 已置空），优先取 lotId，回退旧字段
        const lotRef = item.lotId ?? item.inspectionId
        const quality = lotRef ? (await qualityApi.getById(Number(lotRef))).data : undefined
        const previousQuality = quality?.previousInspectionId
          ? (await qualityApi.getById(Number(quality.previousInspectionId))).data
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
    )
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
function goDisposition(row: WorkRow) {
  activeInboundId.value = Number(selectedInbound.value?.inboundId)
  activeInboundNo.value = selectedInbound.value?.inboundNo || ''
  activeItemId.value = row.itemId
  dispositionVisible.value = true
}
async function openHistory(row: WorkRow) {
  if (!row.lotId) return
  historyLotId.value = row.lotId
  historyLotNo.value = row.qualityLotNo || row.batchNo || `检验批 ${row.lotId}`
  historyLoading.value = true
  try {
    const { data } = await qualityLotApi.history(row.lotId)
    historyRows.value = data || []
  } finally {
    historyLoading.value = false
  }
}
function closeHistory() {
  historyLotId.value = undefined
  historyLotNo.value = ''
  historyRows.value = []
}
function historyEventLabel(value?: string) {
  const labels: Record<string, string> = {
    CREATED: '检验批创建',
    ITEMS_SAVED: '检验项保存',
    JUDGED: '检验批判定',
    REVIEWED: '检验批审核',
    REINSPECTED: '生成复检批',
    REOPENED: '检验批重开',
  }
  return labels[value || ''] || value || '质量事件'
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
}
async function submitInspection() {
  if (!selectedInbound.value) return
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
    } else ElMessage.error('检验提交未生效，请检查入库单状态或刷新后重试')
  } finally {
    submitting.value = false
  }
}
onMounted(async () => {
  const id = Number(route.params.inboundId)
  if (!id) {
    ElMessage.error('缺少单据 ID')
    return
  }
  await loadById(String(id))
  if (route.query.action === 'inspect' || route.query.action === 'reinspect') {
    const reinspection = route.query.action === 'reinspect'
    const target = workRows.value.find(row => rowCanEdit(row) && row.isReinspection === reinspection)
    if (target) openMaterialChecks(target)
    else ElMessage.info('该项待办已变化，请查看当前材料状态')
  }
})
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
.history-event {
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

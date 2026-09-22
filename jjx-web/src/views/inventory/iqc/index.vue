<template>
  <div class="iqc-page">
    <el-card>
      <template #header
        ><div class="header">
          <span>来料检验单据</span
          ><el-button :loading="listLoading" @click="refreshAll">刷新</el-button>
        </div></template
      >
      <el-form inline>
        <el-form-item label="流程状态"
          ><el-select v-model="listQuery.flowStatus" style="width: 150px" @change="searchList"
            ><el-option
              v-for="option in flowOptions"
              :key="option.key"
              :label="option.label"
              :value="option.key" /></el-select
        ></el-form-item>
        <el-form-item
          ><el-input
            v-model="listQuery.inboundNo"
            clearable
            placeholder="入库单号"
            @keyup.enter="searchList"
        /></el-form-item>
        <el-form-item><el-button type="primary" @click="searchList">查询</el-button></el-form-item>
      </el-form>
      <div class="list-tip">选择一张采购入库单，在下方按材料行继续处理</div>
      <el-table
        v-loading="listLoading"
        :data="inboundRows"
        border
        highlight-current-row
        @current-change="selectInbound"
      >
        <template #empty><el-empty description="暂无 IQC 采购入库单" /></template>
        <el-table-column width="50"
          ><template #default="{ row }"
            ><div class="radio-cell" @click.stop="selectInbound(row)">
              <el-radio
                :model-value="selectedInboundId"
                :value="row.inboundId"
                @change="selectInbound(row)"
                ><span
              /></el-radio></div></template
        ></el-table-column>
        <el-table-column prop="inboundNo" label="入库单号" min-width="180" /><el-table-column
          prop="supplierName"
          label="供应商"
          min-width="150"
        /><el-table-column prop="createTime" label="到货时间" width="180" /><el-table-column
          prop="totalQuantity"
          label="来料批量"
          width="105"
        /><el-table-column prop="materialCount" label="材料数" width="85" />
        <el-table-column label="状态" width="105"
          ><template #default="{ row }"
            ><el-tag :type="InboundOrderStatusEnum.getTagProps(row.orderStatus).type">{{
              orderStatusLabel(row)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="检验进度" min-width="210"
          ><template #default="{ row }"
            ><span>已检 {{ row.inspectedCount }}/{{ row.materialCount }}</span
            ><span class="progress-part">待审 {{ row.pendingReviewCount }}</span
            ><span class="progress-part">已审 {{ row.approvedCount }}</span></template
          ></el-table-column
        >
        <el-table-column label="FAIL 行" width="90" align="center"
          ><template #default="{ row }"
            ><el-tag v-if="row.failRowCount" type="danger">{{ row.failRowCount }}</el-tag
            ><span v-else>-</span></template
          ></el-table-column
        >
        <el-table-column label="操作" width="100" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click.stop="selectInbound(row)"
              >查看处理</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="listQuery.pageNum"
          v-model:page-size="listQuery.pageSize"
          :total="listTotal"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-card v-if="selectedInbound" v-loading="detailLoading" class="detail-card">
      <template #header><span>材料检验处理</span></template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="入库单号">{{ selectedInbound.inboundNo }}</el-descriptions-item
        ><el-descriptions-item label="供应商">{{
          selectedInbound.supplierName || '-'
        }}</el-descriptions-item
        ><el-descriptions-item label="来料批量">{{
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
      <el-table
        :data="workRows"
        border
        class="material-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" fixed="left" :selectable="rowCanEdit" />
        <el-table-column prop="materialCode" label="材料编码" min-width="125" /><el-table-column
          prop="materialName"
          label="材料名称"
          min-width="150"
        /><el-table-column prop="quantity" label="收货数量" width="90" />
        <el-table-column label="合格" width="130"
          ><template #default="{ row }"
            ><el-input-number
              v-if="rowCanEdit(row)"
              v-model="row.qualifiedQuantity"
              :min="0"
              :max="row.isReinspection ? row.reinspectionQuantity : row.quantity"
              controls-position="right"
              @change="recalRow(row)"
            /><span v-else>{{ row.qualifiedQuantity }}</span></template
          ></el-table-column
        >
        <el-table-column label="不良" width="130"
          ><template #default="{ row }"
            ><el-input-number
              v-if="rowCanEdit(row)"
              v-model="row.rejectedQuantity"
              :min="0"
              :max="row.isReinspection ? row.reinspectionQuantity : row.quantity"
              controls-position="right"
              @change="recalRow(row)"
            /><span v-else>{{ row.rejectedQuantity }}</span></template
          ></el-table-column
        >
        <el-table-column label="判定" width="115"
          ><template #default="{ row }"
            ><el-select
              v-if="rowCanEdit(row)"
              v-model="row.inspectionResult"
              placeholder="待判定"
              @change="handleResultChange(row)"
              ><el-option
                v-for="option in rowResultOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value" /></el-select
            ><el-tag
              v-else-if="row.inspectionResult"
              :type="InboundInspectionResultEnum.getTagProps(row.inspectionResult).type"
              >{{ InboundInspectionResultEnum.getLabel(row.inspectionResult) }}</el-tag
            ><span v-else>未检</span></template
          ></el-table-column
        >
        <el-table-column label="处置/原因" min-width="190"
          ><template #default="{ row }"
            ><template
              v-if="
                rowCanEdit(row) && row.inspectionResult === InboundInspectionResultEnum.FAIL.value
              "
              ><el-select
                v-model="row.disposition"
                placeholder="处置方式（必选）"
                @change="syncDisposition(row)"
                ><el-option
                  v-for="option in IqcDispositionEnum.items"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value" /></el-select
              ><el-input
                v-model="row.rejectReason"
                maxlength="500"
                placeholder="不合格原因（必填）"
                class="reason-input" /></template
            ><template v-else-if="row.inspectionResult === InboundInspectionResultEnum.FAIL.value"
              ><div>{{ IqcDispositionEnum.getLabel(row.disposition) }}</div>
              <small>{{ row.rejectReason || '-' }}</small></template
            ><span v-else>-</span></template
          ></el-table-column
        >
        <el-table-column label="接收数量" prop="acceptedQuantity" width="100" />
        <el-table-column label="检测项目" width="125"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openMaterialChecks(row)"
              >检测项目{{ checkProgress(row) }}/{{ row.inspectionItems.length }}</el-button
            >
          </template></el-table-column
        >
        <el-table-column label="行状态" width="105"
          ><template #default="{ row }"
            ><el-tag
              v-if="row.reviewStatus"
              :type="QualityReviewStatusEnum.getTagProps(row.reviewStatus).type"
              >{{ QualityReviewStatusEnum.getLabel(row.reviewStatus) }}</el-tag
            ><el-tag v-else type="info">未检</el-tag></template
          ></el-table-column
        >
        <el-table-column label="操作" width="205" fixed="right"
          ><template #default="{ row }"
            ><el-button v-if="rowCanEdit(row)" link type="primary" @click="openMaterialChecks(row)"
              >检验录入</el-button
            ><el-button
              v-if="canJudge && row.reviewStatus === QualityReviewStatus.PENDING"
              link
              type="success"
              @click="openReview"
              >审核/驳回</el-button
            ><el-button v-if="row.inspectionId" link type="primary" @click="printRow(row)"
              >打印</el-button
            ><el-button
              v-if="
                canDispose &&
                row.inspectionResult === InboundInspectionResultEnum.FAIL.value &&
                (row.reviewStatus === QualityReviewStatus.APPROVED || isCompleted)
              "
              link
              type="warning"
              @click="openQuarantine(row)"
              >隔离/处置</el-button
            ></template
          ></el-table-column
        >
      </el-table>
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
      v-model:visible="quarantineVisible"
      :inbound-id="activeInboundId"
      :inbound-no="activeInboundNo"
      :item-id="activeItemId"
      @success="handleFlowSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { hasPermi } from '@/directives'
import { qualityApi } from '@/api/production/quality'
import { inboundApi } from '@/api/inventory/inbound'
import type { IqcPendingVO } from '@/types/inventory/inbound'
import IqcReviewDialog from '@/views/inventory/inbound/components/IqcReviewDialog.vue'
import IqcQuarantineDialog from '@/views/inventory/inbound/components/IqcQuarantineDialog.vue'
import MaterialChecksDialog from './components/MaterialChecksDialog.vue'
import {
  batchPassIqcRow,
  copyIqcChecks,
  syncIqcRowFromChecks,
} from './iqcRowRules'
import {
  InboundOrderStatusEnum,
  InspectionResultEnum as InboundInspectionResultEnum,
  IqcDispositionEnum,
} from '@/enums/inventory/InboundEnum'
import {
  InspectionResult as QualityInspectionResult,
  QualityReviewStatus,
  QualityReviewStatusEnum,
} from '@/enums/quality/InspectionEnum'

type FlowKey = 'ALL' | 'UNINSPECTED' | 'REVIEW' | 'APPROVED' | 'COMPLETED'
type WorkRow = {
  itemId: string
  inspectionId?: number
  materialCode: string
  materialName: string
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
  baseAcceptedQuantity: number
  locked: boolean
  inspectionItems: any[]
}
const router = useRouter()
const route = useRoute()
const canInspect = computed(() => hasPermi(['quality:lot:inspect', 'inventory:inbound:edit']))
const canJudge = computed(() => hasPermi(['quality:lot:judge', 'inventory:inbound:approve']))
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
  workRows = ref<WorkRow[]>([])
const inspectionRemark = ref(''),
  submitting = ref(false),
  checksVisible = ref(false),
  reviewVisible = ref(false),
  quarantineVisible = ref(false)
const activeWorkRow = ref<WorkRow>(),
  activeInboundId = ref<number>(),
  activeInboundNo = ref(''),
  activeItemId = ref<string>()
const rowResultOptions = InboundInspectionResultEnum.items.filter(
  (item) => item.value !== InboundInspectionResultEnum.OTHER.value
)
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
  await loadList(Boolean(id))
  if (id && selectedInbound.value) await loadInboundDetail(selectedInbound.value)
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
    const { data } = await inboundApi.getById(String(requestedId))
    const loadedRows = await Promise.all(
      (data?.items || []).map(async (item: any): Promise<WorkRow> => {
        // dev-20260922-009：新模型检验批在 lotId（inspectionId 已置空），优先取 lotId，回退旧字段
        const lotRef = item.lotId ?? item.inspectionId
        const quality = lotRef
          ? (await qualityApi.getById(Number(lotRef))).data
          : undefined
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
          materialCode: item.materialCode,
          materialName: item.materialName,
          quantity: Number(item.quantity || 0),
          qualifiedQuantity: fresh
            ? 0
            : isReinspection
              ? reinspectionQuantity
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
              ? InboundInspectionResultEnum.PASS.value
              : item.inspectionResult || InboundInspectionResultEnum.PASS.value,
          disposition: isReinspection ? undefined : item.disposition,
          rejectReason: isReinspection ? '' : item.rejectReason || '',
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
}
function activateSelected() {
  activeInboundId.value = Number(selectedInbound.value?.inboundId)
  activeInboundNo.value = selectedInbound.value?.inboundNo || ''
}
function openReview() {
  activateSelected()
  reviewVisible.value = true
}
function openQuarantine(row: WorkRow) {
  activateSelected()
  activeItemId.value = row.itemId
  quarantineVisible.value = true
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
  for (const item of workRows.value) {
    if (!rowCanEdit(item)) continue
    item.acceptedQuantity = Number(item.qualifiedQuantity || 0)
    const inspectionQuantity = item.isReinspection ? item.reinspectionQuantity : item.quantity
    if (Number(item.qualifiedQuantity) + Number(item.rejectedQuantity) !== inspectionQuantity) {
      ElMessage.warning(`${item.materialCode}：合格数量与不良数量之和必须等于收货数量`)
      return
    }
    if (item.inspectionResult === InboundInspectionResultEnum.FAIL.value && !item.disposition) {
      ElMessage.warning(`${item.materialCode}：整批判定不合格时必须选择处置方式`)
      return
    }
    if (
      item.inspectionResult === InboundInspectionResultEnum.FAIL.value &&
      !String(item.rejectReason || '').trim()
    ) {
      ElMessage.warning(`${item.materialCode}：不合格必须填写不合格原因`)
      return
    }
    // 2026-09-16 dev-20260916-008：实测记录允许留空（配合"整批合格"口径），仅要求逐项给出合格/不合格结论
    const undecidedCheck = item.inspectionItems.find(
      (check: any) =>
        ![QualityInspectionResult.PASS, QualityInspectionResult.FAIL].includes(check.result)
    )
    if (undecidedCheck) {
      ElMessage.warning(
        `${item.materialCode}：请判定检测项目「${undecidedCheck.checkItem}」合格或不合格（实测记录可留空）`
      )
      return
    }
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
          inspectionResult,
          disposition,
          qualifiedQuantity,
          rejectedQuantity,
          acceptedQuantity,
          rejectReason,
          inspectionItems,
        }) => ({
          itemId,
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
  const inboundNo = typeof route.query.inboundNo === 'string' ? route.query.inboundNo : ''
  const itemId = typeof route.query.itemId === 'string' ? route.query.itemId : ''
  if (inboundNo) {
    listQuery.flowStatus = 'ALL'
    listQuery.inboundNo = inboundNo
  }
  await loadList()
  if (!inboundNo) return
  const targetInbound = inboundRows.value.find((row) => row.inboundNo === inboundNo)
  if (!targetInbound) {
    ElMessage.warning(`未找到入库单 ${inboundNo}`)
    return
  }
  await selectInbound(targetInbound)
  if (!itemId) return
  const targetRow = workRows.value.find((row) => row.itemId === itemId)
  if (targetRow && rowCanEdit(targetRow)) openMaterialChecks(targetRow)
  else ElMessage.warning('对应复检材料当前不可编辑，请检查检验权限或记录状态')
})
onBeforeUnmount(clearSelection)
</script>

<style scoped>
.iqc-page {
  padding: 20px;
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

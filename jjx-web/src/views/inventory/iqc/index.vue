<template>
  <div class="iqc-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="待检检验" name="pending">
        <el-card>
          <template #header
            ><div class="header">
              <span>待检采购入库单</span
              ><el-button :loading="pendingLoading" @click="loadPending">刷新</el-button>
            </div></template
          >
          <el-form inline>
            <el-form-item
              ><el-input
                v-model="pendingQuery.inboundNo"
                clearable
                placeholder="入库单号"
                @keyup.enter="searchPending"
            /></el-form-item>
            <el-form-item
              ><el-button type="primary" @click="searchPending">查询</el-button></el-form-item
            >
          </el-form>
          <div class="pending-tip">勾选一张待检采购入库单，在下方完成来料检验录入</div>
          <el-table
            v-loading="pendingLoading"
            :data="pendingRows"
            border
            highlight-current-row
            @current-change="handlePendingSelect"
          >
            <template #empty><el-empty description="暂无待检采购入库单" /></template>
            <el-table-column width="50"
              ><template #default="{ row }"
                ><div class="radio-cell" @click.stop="handlePendingSelect(row)">
                  <el-radio
                    :model-value="selectedInboundId"
                    :value="row.inboundId"
                    @change="handlePendingSelect(row)"
                    ><span
                  /></el-radio></div></template
            ></el-table-column>
            <el-table-column prop="inboundNo" label="入库单号" min-width="180" />
            <el-table-column prop="supplierName" label="供应商" min-width="160" />
            <el-table-column prop="totalQuantity" label="来料批量" width="120" />
            <el-table-column prop="materialCount" label="材料数" width="100" />
            <el-table-column prop="createTime" label="到货时间" width="180" />
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="pendingQuery.pageNum"
              :page-size="pendingQuery.pageSize"
              :total="pendingTotal"
              layout="total, prev, pager, next"
              @current-change="loadPending"
            />
          </div>
        </el-card>
        <el-card class="detail-card">
          <template #header><span>来料明细</span></template>
          <div v-if="selectedInbound" v-loading="detailLoading">
            <el-descriptions :column="4" border>
              <el-descriptions-item label="入库单号">{{
                selectedInbound.inboundNo
              }}</el-descriptions-item>
              <el-descriptions-item label="供应商">{{
                selectedInbound.supplierName || '-'
              }}</el-descriptions-item>
              <el-descriptions-item label="来料批量">{{
                selectedInbound.totalQuantity
              }}</el-descriptions-item>
              <el-descriptions-item label="材料数">{{
                selectedInbound.materialCount
              }}</el-descriptions-item>
            </el-descriptions>
            <div :class="['summary-bar', { complete: isAllDecided }]">
              共 {{ workRows.length }} 个材料 · 已判定 {{ decidedCount }} · 通过 {{ passCount }} ·
              不良 {{ failCount }}<span v-if="isAllDecided"> · 全部材料已判定，可提交检验</span>
            </div>
            <el-table :data="workRows" border class="material-table">
              <el-table-column prop="materialCode" label="材料编码" min-width="130" />
              <el-table-column prop="materialName" label="材料名称" min-width="170" />
              <el-table-column prop="quantity" label="收货数量" width="100" />
              <el-table-column label="抽检数量" width="110"
                ><template #default="{ row }"
                  ><el-tooltip content="=合格+不良"
                    ><span>{{ row.sampledQuantity }}</span></el-tooltip
                  ></template
                ></el-table-column
              >
              <el-table-column label="合格数量" width="140"
                ><template #default="{ row }"
                  ><el-input-number
                    v-model="row.qualifiedQuantity"
                    :min="0"
                    :max="row.quantity"
                    :disabled="row.locked"
                    controls-position="right"
                    @change="recalRow(row)" /></template
              ></el-table-column>
              <el-table-column label="不良数量" width="140"
                ><template #default="{ row }"
                  ><el-input-number
                    v-model="row.rejectedQuantity"
                    :min="0"
                    :max="row.quantity"
                    :disabled="row.locked"
                    controls-position="right"
                    @change="recalRow(row)" /></template
              ></el-table-column>
              <el-table-column label="判定" width="130"
                ><template #default="{ row }"
                  ><el-select
                    v-model="row.inspectionResult"
                    :disabled="row.locked"
                    placeholder="待判定"
                    @change="handleResultChange(row)"
                    ><el-option
                      v-for="option in rowResultOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value" /></el-select
                  ><el-tag v-if="!row.inspectionResult" type="info" size="small" class="pending-tag"
                    >待判定</el-tag
                  ></template
                ></el-table-column
              >
              <el-table-column label="处置方式" width="180"
                ><template #default="{ row }"
                  ><template v-if="row.inspectionResult === InboundInspectionResultEnum.FAIL.value"
                    ><el-select
                      v-model="row.disposition"
                      :disabled="row.locked"
                      placeholder="请选择"
                      @change="syncDisposition(row)"
                      ><el-option
                        v-for="option in IqcDispositionEnum.items"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value" /></el-select
                    ><span v-if="!row.disposition" class="required-tip">必选</span></template
                  ><span v-else>-</span></template
                ></el-table-column
              >
              <el-table-column label="检测项目" width="145" fixed="right"
                ><template #default="{ row }"
                  ><el-button link type="primary" @click="openMaterialChecks(row)"
                    >检测项目</el-button
                  ><span class="check-progress">{{
                    checkProgress(row)
                      ? `已录 ${checkProgress(row)}/${row.inspectionItems.length}`
                      : '未录'
                  }}</span></template
                ></el-table-column
              >
              <el-table-column label="IQC 状态" width="100" fixed="right"
                ><template #default="{ row }"
                  ><el-tag
                    v-if="row.reviewStatus"
                    :type="QualityReviewStatusEnum.getTagProps(row.reviewStatus).type"
                    >{{ QualityReviewStatusEnum.getLabel(row.reviewStatus) }}</el-tag
                  ><span v-else>-</span></template
                ></el-table-column
              >
            </el-table>
            <el-form label-width="90px" class="remark-form"
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
            </div>
          </div>
          <el-empty v-else description="请先勾选上方一张待检单" />
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="检验记录" name="records">
        <el-card>
          <template #header
            ><div class="header">
              <span>IQC进料检测</span><el-button :loading="loading" @click="load">刷新</el-button>
            </div></template
          >
          <el-form inline>
            <el-form-item label="检验状态"
              ><el-select v-model="query.result" clearable style="width: 130px" @change="load"
                ><el-option
                  v-for="item in QualityInspectionResultEnum.items"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value" /></el-select
            ></el-form-item>
            <el-form-item label="审核状态"
              ><el-select v-model="query.reviewStatus" clearable style="width: 130px" @change="load"
                ><el-option
                  v-for="item in QualityReviewStatusEnum.items"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value" /></el-select
            ></el-form-item>
            <el-form-item
              ><el-input
                v-model="query.inspectionNo"
                clearable
                placeholder="检验单号"
                @keyup.enter="load"
            /></el-form-item>
            <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
          </el-form>
          <el-table v-loading="loading" :data="rows" border>
            <el-table-column prop="inspectionNo" label="检验单号" min-width="190" />
            <el-table-column label="入库单" width="130"
              ><template #default="{ row }">{{
                inboundNames[row.sourceId] || `入库#${row.sourceId || '-'}`
              }}</template></el-table-column
            >
            <el-table-column prop="materialName" label="材料" min-width="160" />
            <el-table-column label="版本" width="70"
              ><template #default="{ row }"
                >V{{ row.inspectionVersion || 1 }}</template
              ></el-table-column
            >
            <el-table-column label="检验结果" width="100"
              ><template #default="{ row }"
                ><el-tag :type="QualityInspectionResultEnum.getTagProps(row.result).type">{{
                  QualityInspectionResultEnum.getLabel(row.result)
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column label="审核状态" width="100"
              ><template #default="{ row }"
                ><el-tag :type="QualityReviewStatusEnum.getTagProps(row.reviewStatus).type">{{
                  QualityReviewStatusEnum.getLabel(row.reviewStatus)
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column prop="inspectTime" label="检验时间" width="170" />
            <el-table-column label="操作" fixed="right" width="300"
              ><template #default="{ row }"
                ><el-button
                  v-if="
                    row.sourceId &&
                    (row.result === QualityInspectionResult.PENDING ||
                      row.reviewStatus === QualityReviewStatus.REJECTED)
                  "
                  link
                  type="primary"
                  @click="openInspection(row)"
                  >检验</el-button
                ><el-button
                  v-if="row.sourceId && row.reviewStatus === QualityReviewStatus.PENDING"
                  link
                  type="success"
                  @click="openReview(row)"
                  >审核</el-button
                ><el-button
                  v-if="row.sourceId && row.inspectionId"
                  link
                  type="primary"
                  @click="print(row)"
                  >打印报告</el-button
                ><el-button v-if="row.sourceId" link type="warning" @click="openQuarantine(row)"
                  >隔离/处置</el-button
                ></template
              ></el-table-column
            >
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="query.pageNum"
              v-model:page-size="query.pageSize"
              :total="total"
              layout="total, sizes, prev, pager, next"
              @current-change="load"
              @size-change="load"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
    <MaterialChecksDialog
      v-model:visible="checksVisible"
      :row="activeWorkRow"
      @saved="handleChecksSaved"
    />
    <InboundInspectionDialog
      v-model:visible="inspectionVisible"
      :inbound-id="activeInboundId"
      :item-id="activeItemId"
      @success="handleInspectionSuccess"
    />
    <IqcReviewDialog
      v-model:visible="reviewVisible"
      :inbound-id="activeInboundId"
      :inbound-no="activeInboundNo"
      @success="load"
    />
    <IqcQuarantineDialog
      v-model:visible="quarantineVisible"
      :inbound-id="activeInboundId"
      :inbound-no="activeInboundNo"
      @success="load"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { qualityApi, type QualityVO } from '@/api/production/quality'
import { inboundApi } from '@/api/inventory/inbound'
import type { IqcPendingVO } from '@/types/inventory/inbound'
import InboundInspectionDialog from '@/views/inventory/inbound/components/InboundInspectionDialog.vue'
import IqcReviewDialog from '@/views/inventory/inbound/components/IqcReviewDialog.vue'
import IqcQuarantineDialog from '@/views/inventory/inbound/components/IqcQuarantineDialog.vue'
import MaterialChecksDialog from './components/MaterialChecksDialog.vue'
import {
  InspectionResultEnum as InboundInspectionResultEnum,
  IqcDispositionEnum,
} from '@/enums/inventory/InboundEnum'
import {
  InspectionResult as QualityInspectionResult,
  InspectionResultEnum as QualityInspectionResultEnum,
  QualityReviewStatus,
  QualityReviewStatusEnum,
} from '@/enums/quality/InspectionEnum'

const router = useRouter()
const route = useRoute()
const activeTab = ref('pending')
const pendingRows = ref<IqcPendingVO[]>([])
const pendingLoading = ref(false)
const pendingTotal = ref(0)
const selectedInboundId = ref<string | number>('')
const selectedInbound = ref<IqcPendingVO>()
const detailLoading = ref(false)
const inspectionRemark = ref('')
const workRows = ref<any[]>([])
const submitting = ref(false)
const checksVisible = ref(false)
const activeWorkRow = ref<any>()
const pendingQuery = reactive({ pageNum: 1, pageSize: 10, inboundNo: '' })
const rows = ref<QualityVO[]>([])
const total = ref(0)
const loading = ref(false)
const inboundNames = reactive<Record<string, string>>({})
const query = reactive({
  pageNum: 1,
  pageSize: 20,
  inspectionType: 'IQC',
  inspectionNo: '',
  sourceId: undefined as number | undefined,
  result: '',
  reviewStatus: '',
})
const inspectionVisible = ref(false)
const reviewVisible = ref(false)
const quarantineVisible = ref(false)
const activeInboundId = ref<number>()
const activeItemId = ref<number>()
const activeInboundNo = ref('')
const rowResultOptions = InboundInspectionResultEnum.items.filter(
  (item) => item.value !== InboundInspectionResultEnum.OTHER.value
)
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

const defaultInspectionItems = () => [
  createCheck('规格', '与采购订单及实物一致', '核对', '目视'),
  createCheck('颜色', '与标准样板无明显偏差', '比较样板', '目视/样板'),
  createCheck('外观', '无脏污、黑点、变形、折伤、刮伤、混料、晶点、毛边', '目视', '目视'),
  createCheck('长度', '符合图纸或采购要求', '测量', '钢直尺/卡尺'),
  createCheck('宽度', '符合图纸或采购要求', '测量', '钢直尺/卡尺'),
  createCheck('厚度', '符合图纸或采购要求', '测量', '千分尺'),
  createCheck('特性', '附着力及其他特性符合要求', '测试', '3M600胶'),
  createCheck('包装、标识', '包装完整，标识与订单及实物一致并符合环保要求', '目视', '目视'),
]
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
}
async function loadPending() {
  pendingLoading.value = true
  try {
    const result = await inboundApi.iqcPending(pendingQuery)
    pendingRows.value = result.data?.records || []
    pendingTotal.value = result.data?.total || 0
    clearSelection()
  } finally {
    pendingLoading.value = false
  }
}
function searchPending() {
  pendingQuery.pageNum = 1
  loadPending()
}
async function handlePendingSelect(row?: IqcPendingVO) {
  if (!row || (selectedInbound.value?.inboundId === row.inboundId && workRows.value.length)) return
  selectedInbound.value = row
  selectedInboundId.value = row.inboundId
  workRows.value = []
  inspectionRemark.value = ''
  detailLoading.value = true
  try {
    const { data } = await inboundApi.getById(String(row.inboundId))
    const loadedRows = await Promise.all(
      (data?.items || []).map(async (item: any) => {
        const quality = item.inspectionId
          ? (await qualityApi.getById(Number(item.inspectionId))).data
          : undefined
        const previousQuality = quality?.previousInspectionId
          ? (await qualityApi.getById(Number(quality.previousInspectionId))).data
          : undefined
        const isReinspection =
          quality?.previousInspectionId && quality?.result === QualityInspectionResult.PENDING
        const reinspectionQuantity = Number(previousQuality?.failQty || 0)
        const fresh = !quality
        return {
          itemId: item.inboundItemId || item.itemId,
          materialCode: item.materialCode,
          materialName: item.materialName,
          quantity: Number(item.quantity || 0),
          sampledQuantity: fresh
            ? 0
            : isReinspection
              ? reinspectionQuantity
              : Number(item.sampledQuantity ?? item.quantity ?? 0),
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
          locked: quality?.reviewStatus === QualityReviewStatus.APPROVED,
          inspectionItems: quality?.items?.length
            ? quality.items.map(normalizeInspectionItem)
            : defaultInspectionItems(),
        }
      })
    )
    if (selectedInboundId.value === row.inboundId) workRows.value = loadedRows
  } finally {
    detailLoading.value = false
  }
}
function handleResultChange(row: any) {
  if (row.inspectionResult === InboundInspectionResultEnum.PASS.value) {
    row.disposition = undefined
    row.acceptedQuantity = Number(row.quantity || 0)
  } else if (row.inspectionResult === InboundInspectionResultEnum.FAIL.value)
    row.acceptedQuantity = Number(row.qualifiedQuantity || 0)
}
function syncDisposition(row: any) {
  if (row.disposition === IqcDispositionEnum.CONCESSION.value)
    row.acceptedQuantity = Number(row.quantity || 0)
  else if (
    row.disposition === IqcDispositionEnum.RETURN.value ||
    row.disposition === IqcDispositionEnum.SCRAP.value ||
    row.disposition === IqcDispositionEnum.REINSPECT.value ||
    row.disposition === IqcDispositionEnum.HOLD.value ||
    row.disposition === IqcDispositionEnum.SUPPLIER_REWORK.value
  )
    row.acceptedQuantity = 0
  else row.acceptedQuantity = Number(row.qualifiedQuantity || 0)
}
function recalRow(row: any) {
  row.qualifiedQuantity = Number(row.qualifiedQuantity || 0)
  row.rejectedQuantity = Number(row.rejectedQuantity || 0)
  row.sampledQuantity = row.qualifiedQuantity + row.rejectedQuantity
  if (row.rejectedQuantity > 0) row.inspectionResult = InboundInspectionResultEnum.FAIL.value
  else if (row.qualifiedQuantity > 0) row.inspectionResult = InboundInspectionResultEnum.PASS.value
  else row.inspectionResult = ''
  handleResultChange(row)
  if (row.inspectionResult === InboundInspectionResultEnum.FAIL.value) syncDisposition(row)
}
function checkProgress(row: any) {
  return row.inspectionItems.filter(
    (check: any) =>
      String(check.actualValue || '').trim() ||
      Number(check.crQuantity || 0) > 0 ||
      Number(check.maQuantity || 0) > 0 ||
      Number(check.miQuantity || 0) > 0 ||
      String(check.remark || '').trim()
  ).length
}
function openMaterialChecks(row: any) {
  activeWorkRow.value = row
  checksVisible.value = true
}
function handleChecksSaved() {
  if (activeWorkRow.value) recalRow(activeWorkRow.value)
}
async function submitInspection() {
  if (!selectedInbound.value) return
  for (const item of workRows.value) {
    if (
      Number(item.sampledQuantity) !==
      Number(item.qualifiedQuantity) + Number(item.rejectedQuantity)
    ) {
      ElMessage.warning(`${item.materialCode}：抽检数量须等于合格与不良数量之和`)
      return
    }
    if (Number(item.acceptedQuantity) > Number(item.quantity)) {
      ElMessage.warning(`${item.materialCode}：允收入库数量不能超过收货数量`)
      return
    }
  }
  const undecided = workRows.value.filter((item) => !item.inspectionResult).length
  const missingDisposition = workRows.value.filter(
    (item) => item.inspectionResult === InboundInspectionResultEnum.FAIL.value && !item.disposition
  ).length
  if (undecided || missingDisposition) {
    try {
      await ElMessageBox.confirm(
        `还有 ${undecided} 行未判定、${missingDisposition} 行不合格未选处置，确认提交？`,
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
          sampledQuantity,
          inspectionResult,
          disposition,
          qualifiedQuantity,
          rejectedQuantity,
          acceptedQuantity,
          rejectReason,
          inspectionItems,
        }) => ({
          itemId,
          sampledQuantity,
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
      await Promise.all([loadPending(), load()])
    } else ElMessage.error('检验提交未生效，请检查入库单状态或刷新后重试')
  } finally {
    submitting.value = false
  }
}
async function handleInspectionSuccess() {
  await Promise.all([loadPending(), load()])
}
async function load() {
  loading.value = true
  try {
    query.sourceId = route.query.inboundId ? Number(route.query.inboundId) : undefined
    const result = await qualityApi.page(query)
    rows.value = result.data?.records || []
    total.value = result.data?.total || 0
    await Promise.all(
      [...new Set(rows.value.map((row) => row.sourceId).filter(Boolean))].map(async (id) => {
        if (!inboundNames[String(id)]) {
          const inbound = await inboundApi.getById(String(id))
          inboundNames[String(id)] = inbound.data?.inboundNo || `入库#${id}`
        }
      })
    )
  } finally {
    loading.value = false
  }
}
function activate(row: QualityVO) {
  activeInboundId.value = row.sourceId
  activeItemId.value = row.sourceItemId
  activeInboundNo.value = inboundNames[String(row.sourceId)] || ''
}
function openInspection(row: QualityVO) {
  activate(row)
  inspectionVisible.value = true
}
function openReview(row: QualityVO) {
  activate(row)
  reviewVisible.value = true
}
function openQuarantine(row: QualityVO) {
  activate(row)
  quarantineVisible.value = true
}
function print(row: QualityVO) {
  router.push({
    path: '/production/quality-print/iqc-report',
    query: { inboundId: row.sourceId, inspectionId: row.inspectionId },
  })
}
onMounted(() => {
  load()
  loadPending()
})
</script>

<style scoped>
.iqc-page {
  padding: 20px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.pending-tip {
  margin-bottom: 10px;
  color: #909399;
  font-size: 12px;
}
.radio-cell {
  display: flex;
  width: 100%;
  min-height: 24px;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.radio-cell :deep(.el-radio) {
  width: 100%;
  margin-right: 0;
  justify-content: center;
}
.pager,
.detail-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.detail-card {
  margin-top: 16px;
}
.summary-bar {
  margin-top: 16px;
  padding: 10px 14px;
  color: #606266;
  background: #f5f7fa;
}
.summary-bar.complete {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}
.material-table {
  margin-top: 12px;
}
.pending-tag {
  margin-top: 4px;
}
.required-tip {
  margin-left: 6px;
  color: var(--el-color-danger);
  font-size: 12px;
}
.check-progress {
  margin-left: 4px;
  color: #909399;
  font-size: 12px;
}
.remark-form {
  margin-top: 16px;
}
</style>

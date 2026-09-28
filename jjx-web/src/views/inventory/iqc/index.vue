<template>
  <div class="iqc-page">
    <el-card>
      <template #header
        ><div class="header">
          <span>来料检验单据</span
          ><el-button :loading="listLoading" @click="loadList">刷新</el-button>
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
        @current-change="openDetail"
        @expand-change="handleExpandChange"
      >
        <template #empty><el-empty description="暂无 IQC 采购入库单" /></template>
        <el-table-column type="expand" width="46">
          <template #default="{ row }">
            <div v-loading="workspaceLoading[row.inboundId]" class="workspace-detail">
              <template v-if="workspaceByInbound[row.inboundId]">
                <div class="workspace-section-title">
                  {{ row.inboundNo }} · 材料明细
                  <span>检验以材料为单位；处置统一在本批次内展示</span>
                </div>
                <el-table :data="workspaceByInbound[row.inboundId].materials" border size="small">
                  <el-table-column prop="materialCode" label="材料" min-width="180">
                    <template #default="{ row: material }">
                      <b>{{ material.materialCode || '-' }}</b>
                      <div class="muted">{{ material.materialName || '' }} · 业务批次 {{ material.batchNo || '-' }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column prop="lotNo" label="检验批号" width="170" />
                  <el-table-column label="数量口径" min-width="260">
                    <template #default="{ row: material }">
                      <div>整批收货量：{{ num(material.quantity) }}</div>
                      <div>整批合格量：{{ num(material.qualifiedQuantity) }}</div>
                      <div>整批不良量：{{ num(material.rejectedQuantity) }}</div>
                      <div>已处置量：{{ num(material.disposedQuantity) }} · 剩余可处置量：{{ num(material.remainingQuantity) }}</div>
                      <div v-if="material.reworkChildNo" class="emphasis">
                        原批 {{ material.batchNo || '-' }} ↔ 复检批 {{ material.reworkChildNo }}；该批只针对 {{ num(material.reworkQuantity) }} 件
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="检验状态" width="160">
                    <template #default="{ row: material }">
                      <el-tag v-if="material.inspectionResult" :type="InspectionResultEnum.getTagProps(material.inspectionResult).type">
                        {{ InspectionResultEnum.getLabel(material.inspectionResult) }}
                      </el-tag>
                      <span v-else>待检验</span>
                      <div class="muted">{{ reviewStatusLabel(material.reviewStatus) }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="220" fixed="right">
                    <template #default="{ row: material }">
                      <el-button link type="primary" @click="openDetail(row)">检验处理</el-button>
                      <el-button v-if="material.remainingQuantity > 0" link type="warning" @click="goDisposition(row, material)">去处置</el-button>
                      <el-button v-if="material.lotId" link type="info" @click="toggleHistory(material)">质量历史</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <div v-if="historyLotId" class="history-panel">
                  <div class="workspace-section-title">
                    {{ historyLotNo || '检验批' }} · 质量时间线
                    <el-button link type="primary" :loading="historyLoading" @click="closeHistory">收起</el-button>
                  </div>
                  <el-timeline v-loading="historyLoading">
                    <el-timeline-item
                      v-for="item in historyByLot[historyLotId] || []"
                      :key="item.historyId"
                      :timestamp="item.createTime || '-'">
                      <div class="history-event">{{ historyEventLabel(item.eventType) }}</div>
                      <div class="muted">操作人：{{ item.operatorName || '-' }} · {{ item.remark || '无备注' }}</div>
                    </el-timeline-item>
                    <el-empty v-if="!historyLoading && !(historyByLot[historyLotId] || []).length" description="暂无已记录的质量历史" />
                  </el-timeline>
                </div>
                <div class="workspace-section-title disposition-title">
                  {{ row.inboundNo }} · 处置明细
                  <span>所有处置类型统一展示；数量均为本次动作口径</span>
                </div>
                <el-table :data="workspaceByInbound[row.inboundId].dispositions" border size="small">
                  <el-table-column prop="dispositionNo" label="处置单号" width="190" />
                  <el-table-column prop="actionLabel" label="类型" width="110" />
                  <el-table-column prop="materialCode" label="材料" width="150" />
                  <el-table-column label="数量口径" width="150"><template #default="{ row: disposition }">本次处置量：{{ num(disposition.quantity) }}</template></el-table-column>
                  <el-table-column prop="statusLabel" label="状态" width="130" />
                  <el-table-column prop="createTime" label="时间" min-width="170" />
                </el-table>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="inboundNo" label="来料批次" min-width="180" /><el-table-column
          prop="sourceNo"
          label="采购单号"
          min-width="180"
        /><el-table-column
          prop="supplierName"
          label="供应商"
          min-width="150"
        /><el-table-column prop="createTime" label="到货时间" width="180" /><el-table-column
          prop="totalQuantity"
          label="整单收货"
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
            ><el-button link type="primary" @click.stop="openDetail(row)"
              >处理</el-button>
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
  </div>
</template>

<script setup lang="ts">
/**
 * 来料检验——单据列表（dev-20260924-024 刀2 起只做列表；明细见 detail.vue 独立子页）
 * 列表 = 筛选工具条 + 一张单据表；点行或「处理」进入 /inventory/iqc-detail/:inboundId
 */
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { inboundApi } from '@/api/inventory/inbound'
import { qualityLotApi, type QualityLotHistory } from '@/api/quality/lot'
import type { IqcPendingVO } from '@/types/inventory/inbound'
import { InboundOrderStatusEnum, InspectionResultEnum } from '@/enums/inventory/InboundEnum'
import { IqcQuarantineActionEnum, IqcDispositionOrderStatusEnum } from '@/enums/inventory/IqcQuarantineEnum'
import { QualityReviewStatusEnum } from '@/enums/quality/InspectionEnum'

type FlowKey = 'ALL' | 'UNINSPECTED' | 'REVIEW' | 'APPROVED' | 'COMPLETED'
const router = useRouter()
const route = useRoute()
const flowOptions: Array<{
  key: FlowKey
  label: string
  orderStatus?: number
  fillInspection?: boolean
}> = [
  { key: 'ALL', label: '全部' },
  { key: 'UNINSPECTED', label: '待检验', orderStatus: InboundOrderStatusEnum.PENDING.value, fillInspection: false },
  { key: 'REVIEW', label: '待审核', orderStatus: InboundOrderStatusEnum.PENDING.value, fillInspection: true },
  { key: 'APPROVED', label: '已批准', orderStatus: InboundOrderStatusEnum.APPROVED.value },
  { key: 'COMPLETED', label: '已完成', orderStatus: InboundOrderStatusEnum.COMPLETED.value },
]
const listQuery = reactive({ pageNum: 1, pageSize: 10, inboundNo: '', flowStatus: 'ALL' as FlowKey })
const inboundRows = ref<IqcPendingVO[]>([])
const listTotal = ref(0)
const listLoading = ref(false)
const workspaceLoading = reactive<Record<string, boolean>>({})
const workspaceByInbound = reactive<Record<string, { materials: any[]; dispositions: any[] }>>({})
const historyByLot = reactive<Record<string, QualityLotHistory[]>>({})
const historyLoading = ref(false)
const historyLotId = ref<number>()
const historyLotNo = ref('')
const num = (value?: number | string | null) =>
  value == null || value === '' ? '-' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })

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
async function loadList() {
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
function openDetail(row?: IqcPendingVO) {
  if (!row) return
  router.push(`/inventory/iqc-detail/${row.inboundId}`)
}
async function handleExpandChange(row: IqcPendingVO, expanded: IqcPendingVO[]) {
  if (expanded.some((item) => item.inboundId === row.inboundId) && !workspaceByInbound[row.inboundId]) {
    workspaceLoading[row.inboundId] = true
    try {
      const { data } = await inboundApi.getIqcWorkbench(String(row.inboundId))
      const inbound = data?.inbound
      const quarantines = data?.quarantines || []
      const dispositions = data?.dispositions || []
      const materials = (inbound?.items || []).map((item: any) => {
        const itemQuarantines = quarantines.filter((q: any) => String(q.inboundItemId) === String(item.inboundItemId || item.itemId))
        const itemDispositions = dispositions.filter((d: any) => String(d.inboundItemId) === String(item.inboundItemId || item.itemId))
        const rework = itemDispositions.find((d: any) => d.action === 'REWORK' && d.childBatchNo)
        return {
          ...item,
          lotNo: item.lotNo || item.qualityLotNo || item.inspectionNo || '-',
          quantity: Number(item.quantity || 0),
          qualifiedQuantity: Number(item.qualifiedQuantity || 0),
          rejectedQuantity: Number(item.rejectedQuantity || 0),
          disposedQuantity: itemQuarantines.reduce((sum: number, q: any) => sum + Number(q.quantity || 0) - Number(q.remainingQuantity || 0), 0),
          remainingQuantity: itemQuarantines.reduce((sum: number, q: any) => sum + Number(q.remainingQuantity || 0), 0),
          reworkChildNo: rework?.childBatchNo,
          reworkQuantity: rework?.quantity,
        }
      })
      workspaceByInbound[row.inboundId] = {
        materials,
        dispositions: dispositions.map((item: any) => ({
          ...item,
          actionLabel: IqcQuarantineActionEnum.getLabel(item.action),
          statusLabel: IqcDispositionOrderStatusEnum.getLabel(item.status),
        })),
      }
    } finally {
      workspaceLoading[row.inboundId] = false
    }
  }
}
function reviewStatusLabel(value?: string) {
  return value ? QualityReviewStatusEnum.getLabel(value) : '未提交'
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
async function toggleHistory(material: any) {
  const lotId = Number(material.lotId)
  if (!lotId) return
  if (historyLotId.value === lotId) {
    closeHistory()
    return
  }
  historyLotId.value = lotId
  historyLotNo.value = material.lotNo || '-'
  if (historyByLot[lotId]) return
  historyLoading.value = true
  try {
    const { data } = await qualityLotApi.history(lotId)
    historyByLot[lotId] = data || []
  } finally {
    historyLoading.value = false
  }
}
function closeHistory() {
  historyLotId.value = undefined
  historyLotNo.value = ''
}
function goDisposition(row: IqcPendingVO, material: any) {
  router.push({
    path: '/inventory/iqc-quarantine',
    query: { inboundNo: row.inboundNo, materialKeyword: material.materialCode, batchNo: material.batchNo || undefined },
  })
}
onMounted(() => {
  const inboundNo = typeof route.query.inboundNo === 'string' ? route.query.inboundNo : ''
  if (inboundNo) listQuery.inboundNo = inboundNo
  loadList()
})
</script>

<style scoped>
.iqc-page {
  padding: 20px;
}
.workspace-detail {
  padding: 14px 18px 18px;
  background: var(--el-fill-color-lighter);
}
.workspace-section-title {
  margin: 4px 0 8px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}
.workspace-section-title span {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}
.disposition-title {
  margin-top: 18px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.emphasis {
  color: var(--el-color-warning-dark-2);
  font-size: 12px;
  font-weight: 600;
}
.history-panel {
  margin: 12px 0 8px;
  padding: 12px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}
.history-event {
  font-weight: 600;
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

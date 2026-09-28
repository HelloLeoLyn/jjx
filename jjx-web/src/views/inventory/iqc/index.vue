<template>
  <div class="iqc-page">
    <el-card>
      <template #header
        ><div class="header">
          <span>来料批次</span
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
            placeholder="来料批次"
            @keyup.enter="searchList"
        /></el-form-item>
        <el-form-item><el-button type="primary" @click="searchList">查询</el-button></el-form-item>
      </el-form>
      <div class="list-tip">点击来料批次查看详情；操作栏显示当前待办，可同时处理检验、处置与入库事项。</div>
      <el-table v-loading="listLoading" :data="inboundRows" border highlight-current-row>
        <template #empty><el-empty description="暂无 IQC 采购入库单" /></template>
        <el-table-column prop="inboundNo" label="来料批次" min-width="180">
          <template #default="{ row }">
            <el-button link type="primary" :aria-label="`查看 ${row.inboundNo} 详情`" @click="openDetail(row)">{{ row.inboundNo }}</el-button>
          </template>
        </el-table-column><el-table-column
          prop="sourceNo"
          label="采购单号"
          min-width="180"
        /><el-table-column
          prop="supplierName"
          label="供应商"
          min-width="150"
        /><el-table-column prop="createTime" label="到货时间" width="180" /><el-table-column
          prop="totalQuantity"
          label="整批收货量"
          width="105"
        /><el-table-column prop="materialCount" label="材料数" width="85" />
        <el-table-column label="状态" width="105"
          ><template #default="{ row }"
            ><el-tag :type="iqcBatchActions(row).length ? 'warning' : InboundOrderStatusEnum.getTagProps(row.orderStatus).type">{{
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
        <el-table-column label="操作" min-width="240" fixed="right">
          <template #default="{ row }">
            <div class="batch-actions">
              <el-button v-for="action in availableActions(row)" :key="action.key" link type="primary"
                @click="runAction(row, action.key)">{{ action.label }}</el-button>
              <span v-if="!availableActions(row).length" class="list-tip">
                {{ iqcBatchActions(row).length ? '待对应岗位处理' : '暂无待办' }}
              </span>
            </div>
          </template>
        </el-table-column>
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
    <IqcReviewDialog v-model:visible="reviewVisible" :inbound-id="Number(actionRow?.inboundId)"
      :inbound-no="actionRow?.inboundNo" @success="loadList" />
    <IqcQuarantineDialog v-model:visible="dispositionVisible" :inbound-id="Number(actionRow?.inboundId)"
      :inbound-no="actionRow?.inboundNo" @success="loadList" />
    <el-dialog v-model="taskVisible" :title="`${actionRow?.inboundNo || ''} · ${taskAction === 'scrap' ? '报废审批' : '完成返工'}`" width="800px">
      <el-table v-loading="taskLoading" :data="taskRows" border>
        <el-table-column prop="dispositionNo" label="处置单号" min-width="180" />
        <el-table-column prop="materialCode" label="材料" min-width="140" />
        <el-table-column prop="quantity" label="本次处置量" width="110" />
        <el-table-column label="操作" width="190">
          <template #default="{ row }">
            <template v-if="taskAction === 'scrap'">
              <el-button link type="success" :disabled="taskLoading" @click="finishTask(row, true)">通过</el-button>
              <el-button link type="danger" :disabled="taskLoading" @click="finishTask(row, false)">驳回</el-button>
            </template>
            <el-button v-else link type="primary" :disabled="taskLoading" @click="finishTask(row, true)">确认返工完成</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 来料检验——单据列表（dev-20260924-024 刀2 起只做列表；明细见 detail.vue 独立子页）
 * 列表 = 筛选工具条 + 一张单据表；点行或「处理」进入 /inventory/iqc-detail/:inboundId
 */
import { onActivated, onDeactivated, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { iqcApi } from '@/api/inventory/iqc'
import { hasPermi } from '@/directives'
import { ElMessage, ElMessageBox } from 'element-plus'
import IqcReviewDialog from '@/views/inventory/inbound/components/IqcReviewDialog.vue'
import IqcQuarantineDialog from '@/views/inventory/inbound/components/IqcQuarantineDialog.vue'
import { iqcBatchActions, type IqcBatchAction } from './iqcBatchActions'
import { IqcQuarantineAction, IqcDispositionOrderStatus } from '@/enums/inventory/IqcQuarantineEnum'
import { IqcReworkStatus } from '@/enums/inventory/IqcReworkEnum'
import { inboundApi } from '@/api/inventory/inbound'
import type { IqcPendingVO } from '@/types/inventory/inbound'
import { InboundOrderStatusEnum } from '@/enums/inventory/InboundEnum'

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
function selectedFlowOption() {
  return flowOptions.find((option) => option.key === listQuery.flowStatus) || flowOptions[0]
}
function orderStatusLabel(row: IqcPendingVO) {
  return row.orderStatus === InboundOrderStatusEnum.PENDING.value
    ? '检验处理中'
    : InboundOrderStatusEnum.getLabel(row.orderStatus)
}
async function loadList() {
  listLoading.value = true
  try {
    const filter = selectedFlowOption()
let returningToList = false
onDeactivated(() => { returningToList = true })
onActivated(() => {
  if (returningToList) {
    returningToList = false
    loadList()
  }
})
const actionRow = ref<IqcPendingVO>()
const reviewVisible = ref(false)
const dispositionVisible = ref(false)
const taskVisible = ref(false)
const taskLoading = ref(false)
const taskAction = ref<'scrap' | 'rework'>('scrap')
const taskRows = ref<any[]>([])
const availableActions = (row: IqcPendingVO) => iqcBatchActions(row).filter(action => hasPermi(action.permission))

async function runAction(row: IqcPendingVO, action: IqcBatchAction) {
  if (!availableActions(row).some(item => item.key === action)) return
  actionRow.value = row
  if (action === 'dispose') dispositionVisible.value = true
  else if (action === 'review') reviewVisible.value = true
  else if (action === 'inbound') await router.push({ path: '/inventory/inbound', query: { bizId: row.inboundId } })
  else if (action === 'scrap' || action === 'rework') {
    taskAction.value = action
    taskRows.value = []
    taskVisible.value = true
    await loadTasks()
  } else await router.push({ path: `/inventory/iqc-detail/${row.inboundId}`, query: { action } })
}
async function loadTasks() {
  if (!actionRow.value) return
  taskLoading.value = true
  try {
    const { data } = await inboundApi.listDispositionOrders(String(actionRow.value.inboundId))
    taskRows.value = (data || []).filter(row => taskAction.value === 'scrap'
      ? row.action === IqcQuarantineAction.SCRAP && row.status === IqcDispositionOrderStatus.PENDING_APPROVAL
      : row.action === IqcQuarantineAction.REWORK && row.status === IqcReworkStatus.CREATED)
  } finally { taskLoading.value = false }
}
async function finishTask(row: any, approved: boolean) {
  let remark: string | undefined
  try {
    if (taskAction.value === 'scrap') {
      const result = await ElMessageBox.prompt(approved ? '审批意见（可选）' : '请填写驳回原因', approved ? '通过报废审批' : '驳回报废审批', {
        inputValidator: (value) => approved || Boolean(value?.trim()) || '请填写驳回原因',
      })
      remark = result.value
    } else await ElMessageBox.confirm('确认返工已完成并生成待复检记录？', '完成返工')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }
  taskLoading.value = true
  try {
    if (taskAction.value === 'scrap') await iqcApi.approveScrap(String(row.dispositionId), { approved, remark })
    else await iqcApi.completeRework(String(row.dispositionId))
    ElMessage.success('处理完成')
    await Promise.all([loadTasks(), loadList()])
  } finally { taskLoading.value = false }
}
    const result = await inboundApi.iqcList({
      pageNum: listQuery.pageNum,
      pageSize: listQuery.pageSize,
      inboundNo: listQuery.inboundNo || undefined,
  if (Number(row.remainingDispositionQuantity) > 0) return '待处置'
  if (Number(row.pendingScrapCount) > 0) return '待报废审批'
  if (Number(row.pendingReworkCount) > 0) return '待返工'
  if (Number(row.pendingReviewCount) > 0) return '待审核'
  if (Number(row.pendingReinspectionCount) > 0) return '待复检'
  if (Number(row.pendingInspectionCount) > 0) return '待检验'
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
.batch-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.batch-actions .el-button { margin-left: 0; }
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

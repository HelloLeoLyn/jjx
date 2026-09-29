<template>
  <div class="iqc-workbench">
    <el-card>
      <template #header>
        <div class="header">
          <span>来料检验工作台</span>
          <el-button :loading="listLoading" @click="loadList()">刷新</el-button>
        </div>
      </template>
      <el-form inline @submit.prevent>
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
      <div class="list-tip">
        上方选来料批次，下方就是该批次的单据工作区（材料检验 / 待处理明细 / 处置单历史）；「待办」列写清这一批现在该做什么。
      </div>
      <el-table
        v-loading="listLoading"
        :data="inboundRows"
        border
        highlight-current-row
        :row-class-name="rowClass"
        @row-click="selectRow"
      >
        <template #empty><el-empty description="暂无 IQC 采购入库单" /></template>
        <el-table-column prop="inboundNo" label="来料批次" min-width="180" />
        <el-table-column prop="sourceNo" label="采购单号" min-width="180" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" />
        <el-table-column prop="createTime" label="到货时间" width="180" />
        <el-table-column prop="totalQuantity" label="整批收货量" width="105" />
        <el-table-column prop="materialCount" label="材料数" width="85" />
        <el-table-column label="状态" min-width="150">
          <template #default="{ row }">
            <el-tag
              :type="iqcBatchActions(row).length ? 'warning' : InboundOrderStatusEnum.getTagProps(row.orderStatus).type"
              >{{ orderStatusLabel(row) }}</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column label="待办" min-width="200">
          <template #default="{ row }">
            <span v-if="pendingLabels(row)" class="pending-labels">{{ pendingLabels(row) }}</span>
            <span v-else class="list-tip">暂无待办</span>
          </template>
        </el-table-column>
        <el-table-column label="检验进度" min-width="210">
          <template #default="{ row }"
            ><span>已检 {{ row.inspectedCount }}/{{ row.materialCount }}</span
            ><span class="progress-part">待审 {{ row.pendingReviewCount }}</span
            ><span class="progress-part">已审 {{ row.approvedCount }}</span></template
          >
        </el-table-column>
        <el-table-column label="FAIL 行" width="90" align="center">
          <template #default="{ row }"
            ><el-tag v-if="row.failRowCount" type="danger">{{ row.failRowCount }}</el-tag
            ><span v-else>-</span></template
          >
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="selectRow(row, true)">处理</el-button>
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
    </el-card>

    <IqcWorkbenchPanel
      v-if="selectedRow"
      :key="`${selectedRow.inboundId}-${panelMode}-${panelAction || ''}`"
      :inbound-id="selectedRow.inboundId"
      :mode="panelMode"
      :action="panelAction"
      @changed="loadList(true)"
    />
    <el-card v-else shadow="never">
      <el-empty description="请选择上方一张来料批次" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 来料检验工作台（dev-20260929-007，方案见 history/iqc-disposition-truth-rootfix-dev-20260929-003.md §6）
 *
 * 单页形态（与派工/工序执行页同构）：上方=来料批次列表（待办列说明「现在该做什么」），
 * 下方=该批次的单据工作区（组件 IqcWorkbenchPanel：单据头 / 材料检验 / 待处理明细 / 处置单历史）。
 * - 不再跳子页：旧链接 /inventory/iqc-detail/:inboundId 由路由重定向到本页并自动选中批次。
 * - 列表操作列只保留「处理」（导航语义）；具体动作在下方工作区按权限出现。
 * - 明细区锁定批次后不分页（沿用工序执行页口径：全量视图才分页）。
 */
import { computed, onActivated, onDeactivated, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { inboundApi } from '@/api/inventory/inbound'
import { hasPermi } from '@/directives'
import IqcWorkbenchPanel from './components/IqcWorkbenchPanel.vue'
import { iqcBatchActions } from './iqcBatchActions'
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
const inboundRows = ref<IqcPendingVO[]>([])
const listTotal = ref(0)
const listLoading = ref(false)
const selectedRow = ref<IqcPendingVO>()
const panelAction = ref<'inspect' | 'reinspect' | undefined>()
let returningToList = false
onDeactivated(() => {
  returningToList = true
})
onActivated(() => {
  if (!returningToList) return
  returningToList = false
  loadList(!!selectedRow.value)
})

/** 处理模式：该批次当前有待办且当前账号对至少一项待办有权限时，工作区才可写（view=只读）。 */
const panelMode = computed<'view' | 'handle'>(() => {
  const row = selectedRow.value
  if (!row) return 'view'
  return iqcBatchActions(row).some((action) => hasPermi(action.permission)) ? 'handle' : 'view'
})

function pendingLabels(row: IqcPendingVO) {
  return iqcBatchActions(row)
    .map((action) => action.label)
    .join(' / ')
}
function rowClass({ row }: { row: IqcPendingVO }) {
  return selectedRow.value?.inboundId === row.inboundId ? 'is-current-batch' : ''
}
function selectedFlowOption() {
  return flowOptions.find((option) => option.key === listQuery.flowStatus) || flowOptions[0]
}
function orderStatusLabel(row: IqcPendingVO) {
  if (Number(row.remainingDispositionQuantity) > 0) return '待处置'
  if (Number(row.pendingScrapCount) > 0) return '待报废审批'
  if (Number(row.pendingReworkCount) > 0) return '待返工'
  if (Number(row.pendingReviewCount) > 0) return '待审核'
  if (Number(row.pendingReinspectionCount) > 0) return '待复检'
  if (Number(row.pendingInspectionCount) > 0) return '待检验'
  return row.orderStatus === InboundOrderStatusEnum.PENDING.value
    ? '检验处理中'
    : InboundOrderStatusEnum.getLabel(row.orderStatus)
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
    if (preserveSelection && selectedRow.value) {
      selectedRow.value =
        inboundRows.value.find((row) => row.inboundId === selectedRow.value?.inboundId) ||
        selectedRow.value
    }
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
/** 选中批次（列表行点击 / 「处理」按钮 / URL 深链都走这里）。 */
function selectRow(row?: IqcPendingVO, forceHandle = false) {
  if (!row) return
  selectedRow.value = row
  const action = route.query.action
  panelAction.value =
    typeof action === 'string' && (action === 'inspect' || action === 'reinspect')
      ? (action as 'inspect' | 'reinspect')
      : undefined
  if (forceHandle || panelMode.value === 'handle') {
    router.replace({ path: '/inventory/iqc', query: { ...route.query, inboundId: String(row.inboundId) } })
  }
}
onMounted(async () => {
  const inboundNo = typeof route.query.inboundNo === 'string' ? route.query.inboundNo : ''
  if (inboundNo) listQuery.inboundNo = inboundNo
  const action = typeof route.query.action === 'string' ? route.query.action : ''
  panelAction.value = action === 'inspect' || action === 'reinspect' ? (action as 'inspect' | 'reinspect') : undefined
  const inboundId = typeof route.query.inboundId === 'string' ? route.query.inboundId : ''
  await loadList()
  if (!inboundId) return
  // 深链：/inventory/iqc?inboundId=xx（含旧子页重定向过来的链接）
  const target = inboundRows.value.find((row) => String(row.inboundId) === inboundId)
  if (target) selectRow(target, true)
  // 深链目标不在当前页：先放一个仅带 ID 的占位行，工作区自己按 ID 拉取单据
  else selectedRow.value = { inboundId: Number(inboundId) } as unknown as IqcPendingVO
})
</script>

<style scoped>
.iqc-workbench {
  padding: 20px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  font-weight: 600;
}
.list-tip,
.pending-labels {
  color: #909399;
  font-size: 12px;
}
.pending-labels {
  color: var(--el-color-warning-dark-2);
}
.list-tip {
  margin-bottom: 10px;
}
.progress-part {
  margin-left: 10px;
  color: #606266;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
:deep(.is-current-batch) > td {
  background: var(--el-color-primary-light-9) !important;
}
</style>

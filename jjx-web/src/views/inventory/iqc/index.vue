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
      >
        <template #empty><el-empty description="暂无 IQC 采购入库单" /></template>
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

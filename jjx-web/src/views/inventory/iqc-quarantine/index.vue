<template>
  <div class="iqc-ledger">
    <!-- dev-20260924-024：说明不再占一整条 alert，收成一行小字 + hover 展开 -->
    <div class="scope-tip">
      <el-tooltip placement="bottom-start">
        <template #content>
          <div class="scope-tip__pop">
            本页只处理来料检验判定不合格后的处置：让步接收（特采） / 退货 / 返工 / 报废。<br />
            「剩余数量」减到 0 才算结清，状态才会变成已让步接收 / 已退货 / 已返工 / 已报废。
          </div>
        </template>
        <span class="scope-tip__text"
          >❓ 本页处理来料不合格处置（让步接收 / 退货 / 返工 / 报废）</span
        >
      </el-tooltip>
      <el-button link type="primary" @click="router.push('/inventory/iqc')">返回来料检验</el-button>
      <el-button link type="primary" @click="router.push('/quality/ncr')"
        >查看产品不良台账</el-button
      >
    </div>
    <div class="filter-bar">
      <el-form inline @submit.prevent>
        <el-form-item label="来料批次">
          <el-input v-model="query.inboundNo" clearable placeholder="输入来料批次" />
        </el-form-item>
        <el-form-item label="采购单号">
          <el-input v-model="query.sourceNo" clearable placeholder="输入采购单号" />
        </el-form-item>
        <el-form-item label="物料">
          <el-input v-model="query.materialKeyword" clearable placeholder="编码或名称" />
        </el-form-item>
        <el-form-item label="批次">
          <el-input v-model="query.batchNo" clearable placeholder="输入批次号" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="query.supplierName" clearable placeholder="输入供应商名称" />
        </el-form-item>
        <el-form-item label="隔离状态"
          ><el-select v-model="query.status" clearable style="width: 150px" @change="search"
            ><el-option
              v-for="item in IqcQuarantineStatusEnum.items"
              :key="item.value"
              :label="item.label"
              :value="item.value" /></el-select
        ></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <!-- dev-20260928-019：所有处置类型统一为一张工作台表；状态筛选代替按底层表拆 Tab。 -->
    <el-card class="card">
      <template #header>
        <div class="card-title">
          <span>来料不合格处置</span>
          <span class="card-tip"
            >处置方式只有四种：让步接收（特采） / 退货 / 返工 / 报废。「剩余数量」减到 0
            才算结清，状态才会变成已让步接收 / 已退货 / 已返工 / 已报废。</span
          >
        </div>
      </template>
      <el-table v-loading="loading || ordersLoading || scrapLoading || reworkLoading" :data="workbenchRows" border>
        <el-table-column prop="dispositionNo" label="处置单号" width="190" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ row.actionLabel }}</template>
        </el-table-column>
        <el-table-column label="来料批次 / 采购单号" min-width="190">
          <template #default="{ row }">
            <div>{{ row.inboundNo || '-' }}</div>
            <span class="muted">采购：{{ row.sourceNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="检验批号 / 批次" min-width="190">
          <template #default="{ row }">
            <div>{{ row.lotNo || row.childBatchNo || '-' }}</div>
            <span class="muted">批次：{{ row.batchNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="物料" min-width="170">
          <template #default="{ row }">
            <div>{{ row.materialCode || '-' }}</div>
            <span class="muted">{{ row.materialName || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量口径" width="145" align="right">
          <template #default="{ row }">
            <div>{{ row.quantityLabel }}：{{ num(row.quantity) }}</div>
            <span v-if="row.kind === 'quarantine'" class="muted">整批不良：{{ num(row.totalFail) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="135">
          <template #default="{ row }">
            <el-tag :type="row.statusType">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下一步" width="210" fixed="right">
          <template #default="{ row }">
            <template v-if="row.kind === 'quarantine'">
              <el-button
                v-if="canDispose && row.status === IqcQuarantineStatus.PENDING && Number(row.remainingQuantity) > 0"
                type="primary"
                link
                @click="openDisposition(row)"
                >去处置</el-button
              >
              <el-tooltip v-else-if="!canDispose" content="当前账号无隔离处置权限，请联系品质主管授权">
                <span class="no-perm">无处置权限</span>
              </el-tooltip>
            </template>
            <template v-else-if="row.kind === 'scrap' && row.status === IqcScrapOrderStatus.PENDING_APPROVAL">
              <el-button v-if="canApproveScrap" link type="primary" @click="approveScrap(row, true)">通过</el-button>
              <el-button v-if="canApproveScrap" link type="danger" @click="approveScrap(row, false)">驳回</el-button>
            </template>
            <template v-else-if="row.kind === 'rework'">
              <el-button v-if="canDispose && row.status === IqcReworkStatus.CREATED" type="primary" link @click="completeRework(row)">完成返工</el-button>
              <el-button v-if="canInspect && row.status === IqcReworkStatus.PENDING_REINSPECTION" type="primary" link @click="goReinspect(row)">去复检</el-button>
            </template>
            <el-button v-if="row.batchNo" link type="primary" @click="openLineage(row)">批次溯源</el-button>
            <span v-if="!row.hasNextAction && !row.batchNo">-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="pagination"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="search"
        @current-change="load"
      />
    </el-card>

    <el-dialog
      v-model="lineageVisible"
      title="批次溯源"
      width="880px"
      append-to-body
    >
      <div class="lineage-tip">
        当前批次：{{ lineageBatchNo || '-' }}
        <span class="lineage-count">共 {{ lineageRows.length }} 个批次</span>
      </div>
      <el-table
        v-loading="batchLoading"
        :data="lineageRows"
        border
        size="small"
        max-height="460px"
      >
        <el-table-column prop="batchNo" label="批次" width="200" />
        <el-table-column prop="parentBatchNo" label="父批次" width="200" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ batchTypeLabel(row.batchType) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="批次数量" width="90" />
        <el-table-column prop="acceptedQuantity" label="合格数量" width="90" />
        <el-table-column prop="rejectedQuantity" label="不良数量" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="IqcBatchStatusEnum.getTagProps(row.status).type">{{
              IqcBatchStatusEnum.getLabel(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <IqcQuarantineDialog
      v-model:visible="dispositionVisible"
      :inbound-id="activeInboundId"
      :inbound-no="activeInboundNo"
      :item-id="activeItemId"
      @success="load"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { inboundApi } from '@/api/inventory/inbound'
import { iqcApi } from '@/api/inventory/iqc'
import {
  IqcQuarantineAction,
  IqcQuarantineActionEnum,
  IqcScrapOrderStatus,
  IqcScrapOrderStatusEnum,
  IqcDispositionOrderStatusEnum,
  IqcQuarantineStatus,
  IqcQuarantineStatusEnum,
} from '@/enums/inventory/IqcQuarantineEnum'
import { IqcReworkStatus, IqcReworkStatusEnum } from '@/enums/inventory/IqcReworkEnum'
import { IqcBatchStatusEnum, IqcBatchTypeEnum } from '@/enums/inventory/IqcBatchEnum'
import { hasPermi } from '@/directives'
import IqcQuarantineDialog from '@/views/inventory/inbound/components/IqcQuarantineDialog.vue'
const router = useRouter()
const route = useRoute()
// 2026-09-21 用户定口径 B（隔离处置只给品质主管一侧，见迁移 167）：
// 收回 INVENTORY 业务操作(23)/审核员(24) 的处置入口后，本页与来料检验页的处置按钮
// 只认 quality:ncr:dispose；无权限时显示「无处置权限」提示而不是留白。
const canDispose = computed(() => hasPermi(['quality:ncr:dispose']))
const canApproveScrap = computed(() => hasPermi(['quality:ncr:dispose']))
const canInspect = computed(() => hasPermi('quality:lot:inspect'))
const query = ref({
  pageNum: 1,
  pageSize: 20,
  status: undefined as string | undefined,
  materialKeyword: '',
  batchNo: '',
  inboundNo: '',
  sourceNo: '',
  supplierName: '',
})
const total = ref(0)
const rows = ref<any[]>([])
const orders = ref<any[]>([])
const reworkOrders = ref<any[]>([])
const batchRows = ref<any[]>([])
const loading = ref(false)
const ordersLoading = ref(false)
const reworkLoading = ref(false)
const batchLoading = ref(false)
const scrapLoading = ref(false)
const scrapOrders = ref<any[]>([])
const lineageVisible = ref(false)
const lineageRows = ref<any[]>([])
const lineageBatchNo = ref('')
const dispositionVisible = ref(false)
const activeInboundId = ref<number>()
const activeInboundNo = ref<string>()
const activeItemId = ref<string>()
const num = (value?: number | string | null) =>
  value == null || value === ''
    ? '-'
    : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
/** 已处置数量 = 原始隔离 − 剩余数量；后端部分处置时状态仍停在「待处置」，靠这两个数相减才能看出进度 */
const disposedQuantity = (row: any) =>
  Number(row.quantity || 0) - Number(row.remainingQuantity || 0)
const isPartial = (row: any) => Number(row.remainingQuantity || 0) > 0 && disposedQuantity(row) > 0
const batchTypeLabel = (value?: string) => (value ? IqcBatchTypeEnum.getLabel(value) : '-')
/** dev-20260924-024：行内「谱系」——按该行批次过滤溯源链（自身 + 以其为父批次的子批次） */
function openLineage(row: any) {
  lineageBatchNo.value = row?.batchNo || ''
  const all = batchRows.value || []
  const mine = all.filter(
    (b: any) => b.batchNo === row?.batchNo || b.parentBatchNo === row?.batchNo
  )
  lineageRows.value = mine.length ? mine : all
  lineageVisible.value = true
}
const actionLabel = (value?: string) => (value ? IqcQuarantineActionEnum.getLabel(value) : '-')
const dispositionStatus = (value?: string) => {
  const status = value as any
  const props = IqcDispositionOrderStatusEnum.getTagProps(status)
  return {
    statusLabel: IqcDispositionOrderStatusEnum.getLabel(status),
    statusType: props.type,
  }
}
const workbenchRows = computed(() => {
  const pending = rows.value.map((row: any) => ({
    ...row,
    kind: 'quarantine',
    dispositionNo: '-',
    actionLabel: '待处置',
    lotNo: row.lotNo || row.batchNo,
    quantity: row.remainingQuantity,
    quantityLabel: '剩余可处置',
    totalFail: row.quantity,
    statusLabel: IqcQuarantineStatusEnum.getLabel(row.status),
    statusType: IqcQuarantineStatusEnum.getTagProps(row.status).type,
    hasNextAction: canDispose.value && row.status === IqcQuarantineStatus.PENDING,
  }))
  const history = orders.value.map((row: any) => ({
    ...row,
    kind: 'disposition',
    actionLabel: actionLabel(row.action),
    quantityLabel: '本次处置',
    ...dispositionStatus(row.status),
    hasNextAction: false,
  }))
  const scraps = scrapOrders.value.map((row: any) => ({
    ...row,
    kind: 'scrap',
    dispositionNo: row.scrapNo || row.dispositionNo || '-',
    actionLabel: actionLabel(IqcQuarantineAction.SCRAP),
    quantityLabel: '本次报废',
    ...(() => {
      const props = IqcScrapOrderStatusEnum.getTagProps(row.status)
      return { statusLabel: IqcScrapOrderStatusEnum.getLabel(row.status), statusType: props.type }
    })(),
    hasNextAction: canApproveScrap.value && row.status === IqcScrapOrderStatus.PENDING_APPROVAL,
  }))
  const reworks = reworkOrders.value.map((row: any) => ({
    ...row,
    kind: 'rework',
    dispositionNo: row.reworkNo || '-',
    actionLabel: actionLabel(IqcQuarantineAction.REWORK),
    quantityLabel: '本批复检',
    lotNo: row.childBatchNo || row.batchNo,
    ...(() => {
      const props = IqcReworkStatusEnum.getTagProps(row.status)
      return { statusLabel: IqcReworkStatusEnum.getLabel(row.status), statusType: props.type }
    })(),
    hasNextAction:
      (canDispose.value && row.status === IqcReworkStatus.CREATED) ||
      (canInspect.value && row.status === IqcReworkStatus.PENDING_REINSPECTION),
  }))
  return [...pending, ...history, ...scraps, ...reworks]
})

async function load() {
  loading.value = true
  ordersLoading.value = true
  try {
    reworkLoading.value = true
    batchLoading.value = true
    const [{ data }, historyResult] = await Promise.all([
      inboundApi.pageIqcQuarantine(query.value),
      inboundApi.pageIqcDisposition({
        ...query.value,
        pageNum: query.value.pageNum,
        pageSize: query.value.pageSize,
      }),
    ])
    rows.value = data?.page?.records || []
    total.value = data?.page?.total || 0
    orders.value = historyResult.data?.records || []
    reworkOrders.value = data?.reworkOrders || []
    batchRows.value = data?.batches || []
    await loadScrapOrders()
  } finally {
    loading.value = false
    ordersLoading.value = false
    reworkLoading.value = false
    batchLoading.value = false
  }
}

async function loadScrapOrders() {
  scrapLoading.value = true
  try {
    const inboundIds = [...new Set(rows.value.map((row: any) => Number(row.inboundId)).filter(Boolean))]
    const results = await Promise.all(
      inboundIds.map((inboundId) => iqcApi.listScrapOrders(String(inboundId)))
    )
    const rowMeta = new Map(rows.value.map((row: any) => [Number(row.inboundId), row]))
    scrapOrders.value = results.flatMap((result: any, index) => {
      const meta = rowMeta.get(inboundIds[index]) || {}
      return (result.data || []).map((scrap: any) => ({
        ...scrap,
        inboundNo: meta.inboundNo,
        sourceNo: meta.sourceNo,
      }))
    })
  } finally {
    scrapLoading.value = false
  }
}

async function approveScrap(row: any, approved: boolean) {
  const result = await ElMessageBox.prompt(
    approved ? '审批意见（可选）' : '请输入驳回意见',
    approved ? '通过报废审批' : '驳回报废审批',
    {
      inputPlaceholder: approved ? '可填写审批意见' : '请说明驳回原因',
      inputValidator: (value) => (approved || value.trim() ? true : '驳回时必须填写意见'),
    }
  )
  await iqcApi.approveScrap(String(row.scrapId), {
    approved,
    remark: result.value,
  })
  ElMessage.success(approved ? '报废审批已通过' : '报废审批已驳回')
  await load()
}
function search() {
  query.value.pageNum = 1
  return load()
}
function resetQuery() {
  query.value = {
    pageNum: 1,
    pageSize: query.value.pageSize,
    status: undefined,
    materialKeyword: '',
    batchNo: '',
    inboundNo: '',
    sourceNo: '',
    supplierName: '',
  }
  return load()
}
async function openDisposition(row: any) {
  activeInboundId.value = Number(row.inboundId)
  activeItemId.value = String(row.inboundItemId)
  activeInboundNo.value = row.inboundNo || ''
  dispositionVisible.value = true
}
async function completeRework(row: any) {
  await ElMessageBox.confirm('完成返工后将生成新的IQC复检记录，确认继续吗？', '确认完成返工', {
    type: 'warning',
  })
  await iqcApi.completeRework(String(row.reworkId))
  ElMessage.success('返工已完成，已生成待复检记录')
  await load()
}
async function goReinspect(row: any) {
  const { data } = await inboundApi.getById(String(row.inboundId))
  await router.push({
    path: '/inventory/iqc',
    query: {
      inboundNo: data?.inboundNo,
      itemId: String(row.inboundItemId),
    },
  })
}
onMounted(() => {
  query.value.inboundNo = typeof route.query.inboundNo === 'string' ? route.query.inboundNo : ''
  query.value.materialKeyword =
    typeof route.query.materialKeyword === 'string' ? route.query.materialKeyword : ''
  query.value.batchNo = typeof route.query.batchNo === 'string' ? route.query.batchNo : ''
  load()
})
</script>
<style scoped>
/* dev-20260924-024：说明收成一行 + 筛选工具条（不再各占一张卡片） */
.scope-tip {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #909399;
}
.scope-tip__text {
  cursor: help;
  border-bottom: 1px dashed var(--el-border-color);
}
.scope-tip__pop {
  max-width: 420px;
  line-height: 1.6;
}
.lineage-tip {
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* dev-20260928-016：底部全宽抽屉内显示总条数，表格限高竖向滚动 */
.lineage-count {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.filter-bar {
  padding: 10px 12px 0;
  margin-bottom: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
}
.iqc-ledger {
  padding: 20px;
}
.scope-guide {
  margin-bottom: 16px;
}
.scope-guide__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}
.card {
  margin-top: 16px;
}
.card-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card-tip {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.5;
}
.partial {
  margin-left: 6px;
}
.no-perm {
  color: #c0c4cc;
  font-size: 12px;
  cursor: help;
}
.danger {
  color: #f56c6c;
}
.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

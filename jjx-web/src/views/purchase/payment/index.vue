<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="付款单号"
          ><el-input
            v-model="query.paymentNo"
            clearable
            placeholder="付款单号"
            style="width: 180px"
        /></el-form-item>
        <el-form-item label="采购订单号">
          <el-input
            v-model="query.orderNo"
            clearable
            placeholder="采购订单号"
            style="width: 180px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.paymentStatus"
            clearable
            style="width: 130px"
            placeholder="状态"
            @change="handleStatusFilterChange"
          >
            <el-option
              v-for="item in PaymentStatusEnum.items"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="付款方式">
          <el-select
            v-model="query.paymentMethod"
            clearable
            style="width: 140px"
            placeholder="付款方式"
          >
            <el-option
              v-for="item in PaymentMethodEnum.items"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="审批状态">
          <el-select
            v-model="query.approvalStatus"
            clearable
            style="width: 130px"
            placeholder="审批状态"
            @change="handleStatusFilterChange"
          >
            <el-option
              v-for="item in PaymentApprovalStatusEnum.items"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          ><el-button type="primary" @click="search">查询</el-button
          ><el-button @click="reset">重置</el-button></el-form-item
        >
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <el-button
          type="primary"
          plain
          icon="Plus"
          v-hasPermi="['purchase:payment:add']"
          @click="openCreate"
          >新增申请</el-button
        >
        <el-button v-if="userStore.hasPermission('purchase:payment:approve')" :disabled="!selectedPayments.length || submitting || loading" @click="openBatchApprove">批量审批（{{ selectedPayments.length }}）</el-button>
        <el-button v-if="userStore.hasPermission('purchase:payment:export')" :loading="exporting" :disabled="loading" @click="handleExport">导出当前筛选</el-button>
      </div>

      <el-tabs v-model="activeTab" class="payment-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="待审批" name="pending-approval" />
        <el-tab-pane label="待确认付款" name="pending-payment" />
        <el-tab-pane label="已付款" name="paid" />
      </el-tabs>

      <el-table
        ref="paymentTable"
        v-loading="loading"
        :data="rows"
        border
        :row-class-name="rowClassName"
        show-summary
        :summary-method="summaryMethod"
        @selection-change="selectedPayments = $event"
      >
        <el-table-column v-if="userStore.hasPermission('purchase:payment:approve')" type="selection" width="48" :selectable="isPendingPayment" />
        <el-table-column prop="paymentNo" label="付款单号" min-width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="detail(row)">{{ row.paymentNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="采购订单号" min-width="160">
          <template #default="{ row }">
            <el-link
              v-if="row.orderNo && row.orderId && userStore.hasPermission('purchase:order:view')"
              type="primary"
              @click="openSourceOrder(row)"
              >{{ row.orderNo }}</el-link
            >
            <span v-else>{{ row.orderNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip />
        <el-table-column prop="paymentDate" label="计划付款日" width="140">
          <template #default="{ row }">
            {{ row.paymentDate || '-' }}
            <el-tag v-if="isOverdue(row)" type="danger" size="small">逾期</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="paymentAmount" label="金额" width="110" align="right">
          <template #default="{ row }">{{ money(row.paymentAmount) }}</template>
        </el-table-column>
        <el-table-column label="付款方式" width="110">
          <template #default="{ row }">{{
            PaymentMethodEnum.getLabel(row.paymentMethod)
          }}</template>
        </el-table-column>
        <el-table-column label="阶段" width="100">
          <template #default="{ row }"
            ><el-tag :type="PaymentStageEnum.getTagProps(paymentStageOf(row)).type">{{
              PaymentStageEnum.getLabel(paymentStageOf(row))
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column prop="actualPaymentDate" label="实际付款日" width="110" />
        <TableActionColumn
          :actions="paymentActions"
          width="220"
          :max-visible="3"
          @action="handlePaymentAction"
        />
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </el-card>

    <OrderDetailDialog v-model:visible="sourceOrderVisible" :order-id="sourceOrderId" />

    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑付款申请' : '新增付款申请'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="付款单号" prop="paymentNo">
          <el-input v-model="form.paymentNo" :disabled="isEdit" placeholder="付款单号" />
        </el-form-item>
        <el-form-item label="采购订单" prop="orderId">
          <el-select
            v-model="form.orderId"
            filterable
            placeholder="选择采购订单"
            style="width: 100%"
            :disabled="isEdit"
          >
            <el-option
              v-for="o in pendingOrders"
              :key="o.orderId"
              :label="`${o.orderNo} - ${o.supplierName}`"
              :value="o.orderId"
            />
          </el-select>
        </el-form-item>
        <PaymentAmountSummary
          v-if="formVisible"
          :order-id="form.orderId"
          :excluded-payment-id="isEdit ? form.paymentId : undefined"
          @loaded="handleSummaryLoaded"
        />
        <el-row :gutter="16">
          <el-col :span="12"
            ><el-form-item label="计划付款日" prop="paymentDate"
              ><el-date-picker
                v-model="form.paymentDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="申请金额" prop="paymentAmount"
              ><el-input-number
                v-model="form.paymentAmount"
                :min="Number(paymentSummary?.availableAmount || 0) > 0 ? 0.01 : 0"
                :max="paymentSummary ? Number(paymentSummary.availableAmount) : undefined"
                :disabled="!paymentSummary || Number(paymentSummary.availableAmount) <= 0"
                :precision="2"
                :controls="false"
                style="width: 100%" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="付款方式" prop="paymentMethod">
              <el-select v-model="form.paymentMethod" style="width: 100%">
                <el-option
                  v-for="item in PaymentMethodEnum.items"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select> </el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="银行账号"><el-input v-model="form.bankAccount" /></el-form-item
          ></el-col>
        </el-row>
        <el-form-item label="备注"
          ><el-input v-model="form.remark" type="textarea" :rows="3"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="formVisible = false">取消</el-button
        ><el-button
          type="primary"
          :loading="submitting"
          :disabled="!paymentSummary || Number(paymentSummary.availableAmount) <= 0"
          @click="submitForm"
          >保存</el-button
        ></template
      >
    </el-dialog>

    <el-dialog v-model="approveVisible" :title="approveTargets.length > 1 ? '批量付款审批' : '付款审批'" width="800px" :close-on-click-modal="false" :close-on-press-escape="!submitting" :show-close="!submitting">
      <el-table :data="approveTargets" size="small" border max-height="300">
        <el-table-column prop="paymentNo" label="付款单号" min-width="140" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" />
        <el-table-column prop="orderNo" label="采购订单号" min-width="140" />
        <el-table-column label="申请金额" width="120"><template #default="{ row }">{{ money(row.paymentAmount) }}</template></el-table-column>
        <el-table-column prop="paymentDate" label="计划付款日" width="110" />
      </el-table>
      <p v-if="approveTargets.length > 1">本次选择 {{ approveTargets.length }} 笔，使用相同审批决定和意见；任一笔失败整批不保存。</p>
      <el-form :model="approveForm" label-width="90px">
        <el-form-item label="审批人"
          ><el-input :model-value="approverName" readonly
        /></el-form-item>
        <el-form-item label="审批意见"
          ><el-input v-model="approveForm.approvalComment" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="submitting" placeholder="驳回时必须填写意见"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="approveVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="submitting"
          @click="submitApprove(PaymentApprovalStatus.REJECTED)"
          >驳回</el-button
        >
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitApprove(PaymentApprovalStatus.APPROVED)"
          >通过</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="confirmVisible"
      title="确认付款"
      width="1200px"
      :close-on-click-modal="false"
    >
      <el-alert
        title="确认后付款单将标记为已付款、自动汇总到订单已付金额，且不可撤销。"
        type="warning"
        :closable="false"
        show-icon
        class="confirm-tip"
      />
      <el-descriptions :column="2" border class="confirm-summary">
        <el-descriptions-item label="付款单号">{{
          confirmTarget?.paymentNo || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="采购订单号">{{
          confirmTarget?.orderNo || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{
          confirmTarget?.supplierName || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="申请金额">{{
          money(confirmTarget?.paymentAmount)
        }}</el-descriptions-item>
        <el-descriptions-item label="计划付款日">{{
          confirmTarget?.paymentDate || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="付款方式">{{
          PaymentMethodEnum.getLabel(confirmTarget?.paymentMethod)
        }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="confirmFormRef" :model="confirmForm" :rules="confirmRules" label-width="100px">
        <el-form-item label="实际付款日" prop="actualPaymentDate"
          ><el-date-picker
            v-model="confirmForm.actualPaymentDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="转账流水号" prop="voucherNo"
          ><el-input
            v-model="confirmForm.voucherNo"
            maxlength="100"
            placeholder="银行转账流水号/回单号（必填）"
        /></el-form-item>
        <AttachmentCenter
          ref="confirmAttachRef"
          source-type="purchase_payment"
          :source-id="confirmTarget?.paymentId ?? null"
          :actions="['download', 'detail', 'remove', 'save']"
          :auto-bind="false"
          default-document-type="PAYMENT_RECEIPT"
          @save="onAttachSave"
          title="转账回单"
        />
      </el-form>
      <template #footer
        ><el-button @click="confirmVisible = false">取消</el-button
        ><el-button type="primary" :loading="submitting" @click="submitConfirm"
          >确认</el-button
        ></template
      >
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      title="付款详情"
      width="1200px"
      append-to-body
      class="payment-detail-dialog"
    >
      <template v-if="current">
        <div class="detail-section-title">付款信息</div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="付款单号">{{ current.paymentNo }}</el-descriptions-item>
          <el-descriptions-item label="采购订单号">
            <el-link
              v-if="
                current.orderNo && current.orderId && userStore.hasPermission('purchase:order:view')
              "
              type="primary"
              @click="openSourceOrder(current)"
              >{{ current.orderNo }}</el-link
            >
            <span v-else>{{ current.orderNo || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="供应商">{{
            current.supplierName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="计划付款日">{{
            current.paymentDate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="申请金额">
            <span class="detail-amount">{{ money(current.paymentAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="付款方式">{{
            PaymentMethodEnum.getLabel(current.paymentMethod)
          }}</el-descriptions-item>
          <el-descriptions-item label="银行账号" :span="3">{{
            current.bankAccount || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section-title">审批与状态</div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="阶段">
            <el-tag :type="PaymentStageEnum.getTagProps(paymentStageOf(current)).type">{{
              PaymentStageEnum.getLabel(paymentStageOf(current))
            }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审批人">{{
            current.approverName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="实际付款日">{{
            current.actualPaymentDate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="转账流水号">{{
            current.voucherNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{
            current.approvalTime || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="审批意见" :span="3">{{
            current.approvalComment || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section-title">转账回单</div>
        <div class="detail-attachment">
          <AttachmentCenter
            v-if="current?.paymentId"
            source-type="purchase_payment"
            :source-id="current.paymentId"
            readonly
          />
          <span v-else>-</span>
        </div>

        <div class="detail-section-title">备注</div>
        <div class="detail-remark">{{ current.remark || '-' }}</div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  listPayment,
  getPayment,
  addPayment,
  updatePayment,
  delPayment,
  approvePayment,
  confirmPayment,
  getPendingPaymentOrders,
  generatePaymentNo,
  batchApprove,
  exportPayment,
} from '@/api/purchase/payment'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'
import {
  PaymentStatusEnum,
  PaymentApprovalStatus,
  PaymentApprovalStatusEnum,
  PaymentMethodEnum,
  PaymentStageEnum,
  paymentStageOf,
} from '@/enums/purchase/payment'
import TableActionColumn from '@/components/common-ui/TableActionColumn/index.vue'
import PaymentAmountSummary from '@/views/purchase/components/PaymentAmountSummary.vue'
import OrderDetailDialog from '@/views/purchase/order/components/OrderDetailDialog.vue'
import type { PurchasePaymentSummary } from '@/api/purchase/payment'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'

defineOptions({ name: 'PurchasePayment' })

interface PendingOrder {
  orderId: number
  orderNo: string
  supplierName: string
}

const loading = ref(false)
const submitting = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const pendingOrders = ref<PendingOrder[]>([])
const formVisible = ref(false)
const sourceOrderVisible = ref(false)
const sourceOrderId = ref<number>()
function openSourceOrder(row: any) {
  if (!row.orderId || !userStore.hasPermission('purchase:order:view')) return
  sourceOrderId.value = Number(row.orderId)
  sourceOrderVisible.value = true
}
const isEdit = ref(false)
const detailVisible = ref(false)
const approveVisible = ref(false)
const confirmVisible = ref(false)
const current = ref<any>()
const approveTarget = ref<any>()
const approveTargets = ref<any[]>([])
const selectedPayments = ref<any[]>([])
const paymentTable = ref<any>()
const batchApproval = ref(false)
const exporting = ref(false)
const confirmTarget = ref<any>()
const formRef = ref<FormInstance>()

const query = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  paymentNo: '',
  orderNo: '',
  paymentStatus: undefined,
  approvalStatus: undefined,
  paymentMethod: undefined,
})
const form = reactive<any>({})
const paymentSummary = ref<PurchasePaymentSummary | null>(null)

type PaymentTabFilter = { approvalStatus?: string; paymentStatus?: number }
const activeTab = ref('all')
const TAB_FILTERS: Record<string, PaymentTabFilter> = {
  all: {},
  'pending-approval': { approvalStatus: PaymentApprovalStatus.PENDING },
  'pending-payment': {
    approvalStatus: PaymentApprovalStatus.APPROVED,
    paymentStatus: PaymentStatusEnum.PENDING.value,
  },
  paid: { paymentStatus: PaymentStatusEnum.COMPLETED.value },
}

function handleTabChange(name: string | number) {
  const filter = TAB_FILTERS[String(name)] ?? {}
  query.approvalStatus = filter.approvalStatus
  query.paymentStatus = filter.paymentStatus
  query.pageNum = 1
  void load()
}

/** 手工改状态筛选时回到「全部」页签，避免页签与筛选条件互相矛盾。 */
function handleStatusFilterChange() {
  if (activeTab.value !== 'all') activeTab.value = 'all'
}
function handleSummaryLoaded(summary: PurchasePaymentSummary | null) {
  paymentSummary.value = summary
  if (summary && !isEdit.value) form.paymentAmount = Number(summary.availableAmount)
  if (
    summary &&
    !pendingOrders.value.some((order) => String(order.orderId) === String(summary.orderId))
  ) {
    pendingOrders.value.push({
      orderId: summary.orderId,
      orderNo: summary.orderNo,
      supplierName: summary.supplierName,
    })
  }
}
const rules = {
  paymentNo: [{ required: true, message: '请输入付款单号', trigger: 'blur' }],
  orderId: [{ required: true, message: '请选择采购订单', trigger: 'change' }],
  paymentDate: [{ required: true, message: '请选择付款日期', trigger: 'change' }],
  paymentAmount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: '请选择付款方式', trigger: 'change' }],
}
const userStore = useUserStore()
const approverName = computed(
  () => userStore.userInfo?.realName?.trim() || userStore.userName || '-'
)
const approveForm = reactive({ approvalComment: '' })
const confirmForm = reactive({ actualPaymentDate: '', voucherNo: '' })
const confirmFormRef = ref<FormInstance>()
const confirmRules = {
  actualPaymentDate: [{ required: true, message: '请选择实际付款日', trigger: 'change' }],
  voucherNo: [{ required: true, message: '请填写转账流水号', trigger: 'blur' }],
}

const isPendingPayment = (row: any) =>
  row.approvalStatus === PaymentApprovalStatus.PENDING &&
  row.paymentStatus === PaymentStatusEnum.PENDING.value

/** 逾期 = 未付清、未驳回，且计划付款日早于今天。 */
function isOverdue(row: any) {
  if (!row?.paymentDate) return false
  if (row.paymentStatus === PaymentStatusEnum.COMPLETED.value) return false
  if (row.approvalStatus === PaymentApprovalStatus.REJECTED) return false
  return row.paymentDate < today()
}

function rowClassName({ row }: { row: any }) {
  return isOverdue(row) ? 'row-overdue' : ''
}

/** 本页合计：前端派生值，不新增后端接口。 */
function summaryMethod({ columns }: { columns: any[] }) {
  const totalAmount = rows.value.reduce((sum, row) => sum + Number(row.paymentAmount || 0), 0)
  return columns.map((column, index) => {
    if (index === 0) return `本页合计（${rows.value.length} 笔）`
    if (column.property === 'paymentAmount') return money(totalAmount)
    return ''
  })
}

const paymentActions: TableAction<any>[] = [
  {
    key: 'approve',
    label: '审批',
    type: 'success',
    permission: 'purchase:payment:approve',
    visible: ({ row }) => row.approvalStatus === PaymentApprovalStatus.PENDING,
    order: 20,
  },
  {
    key: 'confirm',
    label: '确认付款',
    type: 'warning',
    permission: 'purchase:payment:edit',
    visible: ({ row }) =>
      row.approvalStatus === PaymentApprovalStatus.APPROVED &&
      row.paymentStatus === PaymentStatusEnum.PENDING.value,
    order: 30,
  },
  {
    key: 'edit',
    label: '编辑',
    permission: 'purchase:payment:edit',
    visible: ({ row }) => isPendingPayment(row),
    order: 40,
  },
  {
    key: 'delete',
    label: '删除',
    type: 'danger',
    permission: 'purchase:payment:delete',
    visible: ({ row }) => isPendingPayment(row),
    order: 50,
  },
]

function handlePaymentAction(key: string, row: any) {
  const handlers: Record<string, () => void> = {
    approve: () => openApprove(row),
    confirm: () => openConfirm(row),
    edit: () => void openEdit(row),
    delete: () => void remove(row),
  }
  handlers[key]?.()
}

const money = (v?: number) =>
  v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

function today() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

async function load() {
  selectedPayments.value = []
  paymentTable.value?.clearSelection?.()
  loading.value = true
  try {
    const res: any = await listPayment({ ...query })
    const data = res.data || []
    rows.value = Array.isArray(data) ? data : data.records || []
    total.value = Array.isArray(data) ? data.length : data.total || 0
  } catch (e: any) {
    rows.value = []
    total.value = 0
    ElMessage.error(e?.message || '加载付款列表失败')
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}
function reset() {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 10,
    paymentNo: '',
    orderNo: '',
    paymentStatus: undefined,
    approvalStatus: undefined,
    paymentMethod: undefined,
  })
  search()
}

async function loadPendingOrders() {
  const res: any = await getPendingPaymentOrders()
  pendingOrders.value = res.data || []
}

function emptyForm() {
  return {
    paymentNo: '',
    paymentId: undefined,
    orderId: undefined,
    paymentDate: today(),
    paymentAmount: undefined,
    paymentMethod: 'bank',
    bankAccount: '',
    remark: '',
  }
}

async function openCreate() {
  await loadPendingOrders()
  paymentSummary.value = null
  isEdit.value = false
  Object.assign(form, emptyForm())
  try {
    const res: any = await generatePaymentNo()
    if (res.data) form.paymentNo = res.data
  } catch {
    /* 单号生成失败可手填 */
  }
  formVisible.value = true
}

async function openEdit(row: any) {
  paymentSummary.value = null
  isEdit.value = true
  const res: any = await getPayment(row.paymentId)
  Object.assign(form, emptyForm(), res.data || {})
  formVisible.value = true
}

async function submitForm() {
  if (
    !formRef.value ||
    !paymentSummary.value ||
    !(await formRef.value.validate().catch(() => false))
  )
    return
  if (
    Number(form.paymentAmount) <= 0 ||
    Number(form.paymentAmount) > Number(paymentSummary.value.availableAmount)
  ) {
    ElMessage.warning('申请金额必须大于0且不超过可申请金额')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updatePayment(form)
    } else {
      await addPayment(form)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

function openApprove(row: any) {
  if (submitting.value || !userStore.hasPermission('purchase:payment:approve')) return
  batchApproval.value = false
  approveTarget.value = row
  approveTargets.value = [row]
  Object.assign(approveForm, { approvalComment: '' })
  approveVisible.value = true
}
function openBatchApprove() {
  if (submitting.value || !userStore.hasPermission('purchase:payment:approve') || !selectedPayments.value.length) return
  if (selectedPayments.value.some((row) => !isPendingPayment(row))) {
    ElMessage.warning('批量审批只能选择待审批且未付款的付款单')
    return
  }
  batchApproval.value = true
  approveTarget.value = undefined
  approveTargets.value = [...selectedPayments.value]
  approveForm.approvalComment = ''
  approveVisible.value = true
}
async function submitApprove(decision: string) {
  if (submitting.value || !approveTargets.value.length || !userStore.hasPermission('purchase:payment:approve')) return
  if (decision === PaymentApprovalStatus.REJECTED && !approveForm.approvalComment.trim()) {
    ElMessage.warning('驳回意见不能为空')
    return
  }
  submitting.value = true
  try {
    const comment = approveForm.approvalComment.trim()
    if (batchApproval.value) await batchApprove(approveTargets.value.map((row) => ({ paymentId: row.paymentId, approvalStatus: decision, approvalComment: comment })))
    else await approvePayment(approveTarget.value.paymentId, decision, comment)
    ElMessage.success(decision === PaymentApprovalStatus.APPROVED ? '审批通过' : '已驳回')
    approveVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '审批失败')
  } finally {
    submitting.value = false
  }
}

async function handleExport() {
  if (exporting.value || !userStore.hasPermission('purchase:payment:export')) return
  exporting.value = true
  try {
    const { pageNum: _page, pageSize: _size, ...filters } = query
    const blob: any = await exportPayment(filters)
    if (!(blob instanceof Blob)) throw new Error('导出响应无效，请重试')
    if (blob.type.includes('json')) {
      const error = JSON.parse(await blob.text())
      throw new Error(error.msg || '导出失败')
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `采购付款_${today()}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
  } catch (cause: any) {
    ElMessage.error(cause?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

function openConfirm(row: any) {
  confirmTarget.value = row
  Object.assign(confirmForm, { actualPaymentDate: today(), voucherNo: row.voucherNo || '' })
  confirmVisible.value = true
  nextTick(() => confirmFormRef.value?.clearValidate())
}

const confirmAttachRef = ref<InstanceType<typeof AttachmentCenter> | null>(null)
/** 附件区「保存」（dev-20261016-039，承 014 §7-1 方案 A）：只提交附件信息类改动、不落定；落定归「确认」。 */
function onAttachSave() {
  ElMessage.success('附件信息已保存；回单随「确认」落定')
}

async function submitConfirm() {
  if (!confirmTarget.value) return
  const valid = await confirmFormRef.value?.validate().catch(() => false)
  if (!valid) return
  // 资料类型必选（dev-20261016-039，对齐 032）：缺二级类型的待绑定回单先拦
  const missingTypeCount = confirmAttachRef.value?.getMissingTypeCount?.() ?? 0
  if (missingTypeCount > 0) {
    ElMessage.warning(`有 ${missingTypeCount} 个附件未选择资料类型，请先选择后再确认`)
    return
  }
  try {
    await ElMessageBox.confirm(
      `将 ${confirmTarget.value.paymentNo} 标记为已付款（${money(confirmTarget.value.paymentAmount)}），确认后不可撤销。`,
      '确认付款',
      { type: 'warning', confirmButtonText: '确认付款', cancelButtonText: '再核对' }
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    const attachments = (confirmAttachRef.value?.pendingEntries ?? []).map((e: any) => ({
      attachmentId: e.attachmentId,
      documentType: e.documentType ?? null,
    }))
    await confirmPayment({
      paymentId: confirmTarget.value.paymentId,
      actualPaymentDate: confirmForm.actualPaymentDate,
      voucherNo: confirmForm.voucherNo,
      attachments,
    })
    ElMessage.success('确认付款成功')
    confirmVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '确认失败')
  } finally {
    submitting.value = false
  }
}

async function detail(row: any) {
  const res: any = await getPayment(row.paymentId)
  current.value = res.data
  detailVisible.value = true
}

async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除付款单【${row.paymentNo}】？`, '删除确认', {
    type: 'warning',
  })
  try {
    await delPayment(row.paymentId)
    ElMessage.success('删除成功')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(() => {
  load()
  loadPendingOrders()
})
</script>

<style scoped>
.search-card {
  margin-bottom: 16px;
}
.toolbar {
  margin-bottom: 14px;
}
.payment-tabs {
  margin-bottom: 4px;
}
.row-overdue :deep(td) {
  background-color: var(--el-color-danger-light-9) !important;
}
.confirm-tip {
  margin-bottom: 12px;
}
.confirm-summary {
  margin-bottom: 16px;
}
.payment-detail-dialog :deep(.el-dialog__body) {
  max-height: 68vh;
  overflow-y: auto;
}
.detail-section-title {
  margin: 18px 0 8px;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
  font-weight: 600;
  font-size: 14px;
  line-height: 16px;
  color: var(--el-text-color-primary);
}
.detail-section-title:first-of-type {
  margin-top: 0;
}
.detail-amount {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.detail-attachment {
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}
.detail-attachment :deep(.ac-title) {
  display: none;
}
.detail-remark {
  padding: 8px 12px;
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
}
</style>

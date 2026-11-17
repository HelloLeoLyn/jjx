<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="登记单号"
          ><el-input v-model="query.documentNo" clearable placeholder="INV 开头的系统编号"
        /></el-form-item>
        <el-form-item label="真实发票号码">
          <el-input v-model="query.invoiceNo" clearable placeholder="票面上的发票号码" />
        </el-form-item>
        <el-form-item label="供应商"
          ><el-select v-model="query.supplierId" clearable filterable style="width: 200px"
            ><el-option
              v-for="s in suppliers"
              :key="s.supplierId"
              :label="s.supplierName"
              :value="s.supplierId" /></el-select
        ></el-form-item>
        <el-form-item label="状态"
          ><el-select v-model="query.documentStatus" clearable style="width: 130px"
            ><el-option
              v-for="item in InvoiceStatusEnum.items"
              :key="item.value"
              :label="item.label"
              :value="item.value" /></el-select
        ></el-form-item>
        <el-form-item label="票面开票日期"
          ><el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
        /></el-form-item>
        <el-form-item
          ><el-button type="primary" @click="search">查询</el-button
          ><el-button @click="reset">重置</el-button></el-form-item
        >
      </el-form>
    </el-card>
    <el-card shadow="never">
      <div class="toolbar">
        <el-button
          v-hasPermi="['purchase:invoice:add']"
          type="primary"
          plain
          icon="Plus"
          @click="openCreate"
          >登记发票</el-button
        >
        <el-button
          v-hasPermi="['purchase:invoice:delete']"
          type="danger"
          plain
          icon="Delete"
          :disabled="!selectedIds.length"
          @click="batchDelete"
          >批量删除</el-button
        >
      </div>
      <el-table v-loading="loading" :data="rows" border @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="documentNo" label="登记单号" min-width="170"
          ><template #default="{ row }"
            ><el-link type="primary" @click="detail(row)">{{ row.documentNo }}</el-link></template
          ></el-table-column
        >
        <el-table-column prop="invoiceNo" label="真实发票号码" min-width="170"
          ><template #default="{ row }">{{ row.invoiceNo || '待补录' }}</template></el-table-column
        >
        <el-table-column label="供应商" min-width="140"
          ><template #default="{ row }">{{
            supplierName(row.supplierId)
          }}</template></el-table-column
        >
        <el-table-column label="价税合计" width="120" align="right"
          ><template #default="{ row }">{{
            row.invoiceNo ? money(row.documentAmount) : '待补录'
          }}</template></el-table-column
        >
        <el-table-column label="币种" width="80"
          ><template #default="{ row }">{{
            row.invoiceNo ? row.currency : '待补录'
          }}</template></el-table-column
        >
        <el-table-column label="票面开票日期" width="125"
          ><template #default="{ row }">{{
            row.invoiceNo ? row.documentDate : '待补录'
          }}</template></el-table-column
        >
        <el-table-column label="状态" width="90"
          ><template #default="{ row }"
            ><el-tag :type="InvoiceStatusEnum.getTagProps(row.documentStatus).type">{{
              InvoiceStatusEnum.getLabel(row.documentStatus)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column prop="verificationDate" label="核验日期" width="110" />
        <el-table-column label="附件" width="80"
          ><template #default="{ row }"
            ><el-button v-if="canViewDoc" link type="primary" @click="detail(row)">查看</el-button
            ><span v-else>无权限</span></template
          ></el-table-column
        >
        <TableActionColumn :actions="invoiceActions" width="220" @action="handleInvoiceAction" />
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </el-card>

    <el-dialog
      v-model="createVisible"
      :title="editingId ? '补录 / 编辑发票' : '登记发票（一张发票可附多个文件）'"
      width="1100px"
      destroy-on-close
      :close-on-click-modal="false"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
      @closed="resetCreate"
    >
      <el-form
        ref="registrationForm"
        :model="createForm"
        :rules="registrationRules"
        label-width="115px"
        :disabled="submitting"
      >
        <el-form-item label="采购订单" prop="orderId"
          ><el-select
            v-model="createForm.orderId"
            filterable
            :disabled="!!editingId"
            style="width: 100%"
            @change="onOrderChange"
            ><el-option
              v-for="o in editOrderOptions"
              :key="o.orderId"
              :label="o.orderNo + ' - ' + o.supplierName"
              :value="o.orderId" /></el-select
        ></el-form-item>
        <el-form-item label="供应商">{{ createSupplierName || '-' }}</el-form-item>
        <el-alert
          title="请按原票填写。订单金额仅作采购依据，本张发票不要求等于整单金额。"
          type="info"
          :closable="false"
          show-icon
          class="notice"
        />
        <el-row :gutter="20">
          <el-col :span="12"
            ><el-form-item label="真实发票号码" prop="invoiceNo"
              ><el-input v-model="createForm.invoiceNo" maxlength="50" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="票面开票日期" prop="documentDate"
              ><el-date-picker
                v-model="createForm.documentDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="销售方名称" prop="sellerName"
              ><el-input v-model="createForm.sellerName" maxlength="200" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="销售方税号" prop="sellerTaxNo"
              ><el-input v-model="createForm.sellerTaxNo" maxlength="50" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="购买方名称" prop="buyerName"
              ><el-input v-model="createForm.buyerName" maxlength="200" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="购买方税号" prop="buyerTaxNo"
              ><el-input v-model="createForm.buyerTaxNo" maxlength="50" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="不含税金额" prop="untaxedAmount"
              ><el-input-number
                v-model="createForm.untaxedAmount"
                :precision="2"
                :controls="false"
                style="width: 100%" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="税额" prop="taxAmount"
              ><el-input-number
                v-model="createForm.taxAmount"
                :precision="2"
                :controls="false"
                style="width: 100%" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="价税合计" prop="documentAmount"
              ><el-input-number
                v-model="createForm.documentAmount"
                :precision="2"
                :controls="false"
                style="width: 100%" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="币种" prop="currency"
              ><el-input
                v-model="createForm.currency"
                maxlength="10"
                placeholder="按票面填写，如 CNY" /></el-form-item
          ></el-col>
        </el-row>
        <el-form-item label="登记备注"
          ><el-input v-model="createForm.remark" type="textarea" maxlength="500"
        /></el-form-item>
        <InvoiceEvidence v-if="editingId && current && canViewDoc" :invoice="current" />
        <AttachmentCenter
          v-if="createVisible && createForm.orderId && canUploadDoc"
          :key="createDraftId"
          ref="createAttachments"
          source-type="purchase_invoice"
          :draft-id="createDraftId"
          variant="upload-only"
          title="发票原件 / 补充文件"
          :auto-bind="false"
          :actions="['remove']"
          :locked="submitting"
          :max-size-m-b="20"
          :accept="INVOICE_ACCEPT"
          default-document-type="VAT_SPECIAL_INVOICE"
          view-perm="purchase:invoice:doc:view"
          edit-perm="purchase:invoice:doc:add"
          drop-zone
        />
        <el-alert
          v-else-if="createForm.orderId"
          title="无发票文件上传权限"
          type="warning"
          :closable="false"
        />
      </el-form>
      <template #footer
        ><el-button :disabled="submitting" @click="createVisible = false">取消</el-button
        ><el-button type="primary" :loading="submitting" @click="submitCreate"
          >保存发票登记</el-button
        ></template
      >
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      :title="verifyMode ? '发票核验' : '发票详情'"
      width="1400px"
      destroy-on-close
      :close-on-click-modal="false"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
    >
      <template v-if="current">
        <el-alert
          v-if="!current.invoiceNo"
          title="历史记录尚未补录真实票面信息；原有默认日期、零金额不作为已核实票面信息。"
          type="warning"
          :closable="false"
          class="notice"
        />
        <div class="review-layout">
          <section>
            <h3>发票原件</h3>
            <InvoiceEvidence v-if="canViewDoc" :invoice="current" /><el-alert
              v-else
              title="无发票原件查看权限"
              type="warning"
              :closable="false"
            />
            <AttachmentCenter
              v-if="canViewDoc && !verifyMode"
              source-type="purchase_invoice"
              :source-id="current.documentId"
              title="附件资料"
              upload-blocked="请在补录 / 编辑中随发票登记保存文件"
              view-perm="purchase:invoice:doc:view"
              edit-perm="purchase:invoice:doc:add"
            />
            <el-button
              v-if="current.fileName && !carriedId(current.fileUrl)"
              link
              type="primary"
              @click="downloadFile(current)"
              >下载历史文件</el-button
            >
          </section>
          <section>
            <h3>票面信息与采购依据</h3>
            <InvoiceFacts :invoice="current" @order="openOrder" />
            <el-form
              v-if="verifyMode"
              label-width="100px"
              class="verify-form"
              :disabled="submitting"
            >
              <el-form-item label="核验日期"
                ><el-date-picker
                  v-model="verifyForm.verificationDate"
                  type="date"
                  value-format="YYYY-MM-DD"
              /></el-form-item>
              <el-form-item label="核验人">{{ operatorName }}（以实际提交账号为准）</el-form-item>
              <el-form-item label="核验意见"
                ><el-input
                  v-model="verifyForm.verificationRemark"
                  type="textarea"
                  :rows="3"
                  maxlength="500"
                  placeholder="记录与原票、采购订单核对的结果"
              /></el-form-item>
              <el-alert
                title="请确认原票、购销双方、号码及金额。本操作记录人工核验结果，不代表付款完成或税务查验通过。"
                type="info"
                :closable="false"
              />
            </el-form>
          </section>
        </div>
      </template>
      <template #footer>
        <el-button :disabled="submitting" @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="isPending(current)"
          v-hasPermi="['purchase:invoice:edit']"
          :disabled="submitting"
          @click="openEdit(current)"
          >补录 / 编辑</el-button
        >
        <el-button
          v-if="verifyMode"
          type="primary"
          :loading="submitting"
          :disabled="!current?.invoiceNo || !canViewDoc"
          @click="submitVerify"
          >确认核验</el-button
        >
      </template>
    </el-dialog>
    <OrderDetailDialog
      v-if="orderDetailVisible"
      v-model:visible="orderDetailVisible"
      :order-id="orderDetailId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'
import InvoiceFacts from './InvoiceFacts.vue'
import InvoiceEvidence from './InvoiceEvidence.vue'
import OrderDetailDialog from '@/views/purchase/order/components/OrderDetailDialog.vue'
import { useUserStore } from '@/store/modules/user'
import {
  listInvoice,
  getInvoice,
  delInvoice,
  verifyInvoice,
  getPendingInvoiceOrders,
  registerInvoice,
  downloadInvoiceFile,
  type InvoiceRegistration,
} from '@/api/purchase/invoice'
import { listSupplier } from '@/api/purchase/supplier'
import { InvoiceStatusEnum } from '@/enums/purchase/invoice'
import { download } from '@/utils/format'
import TableActionColumn from '@/components/common-ui/TableActionColumn/index.vue'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'

defineOptions({ name: 'PurchaseInvoice' })
interface PendingOrder {
  orderId: number
  orderNo: string
  supplierId: number
  supplierName: string
  currency?: string
}
const userStore = useUserStore()
const canUploadDoc = computed(() => userStore.hasPermission('purchase:invoice:doc:add'))
const canViewDoc = computed(() => userStore.hasPermission('purchase:invoice:doc:view'))
const operatorName = computed(
  () => userStore.userInfo?.realName || userStore.nickName || userStore.userName
)
const INVOICE_ACCEPT = [
  '.jpg',
  '.jpeg',
  '.png',
  '.gif',
  '.bmp',
  '.webp',
  '.pdf',
  '.doc',
  '.docx',
  '.xls',
  '.xlsx',
]
const loading = ref(false),
  submitting = ref(false)
const rows = ref<any[]>([]),
  suppliers = ref<any[]>([]),
  pendingOrders = ref<PendingOrder[]>([])
const total = ref(0),
  selectedIds = ref<number[]>([]),
  dateRange = ref<string[]>([])
const current = ref<any>(),
  editingId = ref<number>()
const detailVisible = ref(false),
  createVisible = ref(false),
  verifyMode = ref(false)
const orderDetailVisible = ref(false),
  orderDetailId = ref<number>()
const query = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  documentNo: '',
  invoiceNo: '',
  supplierId: undefined,
  documentStatus: undefined,
})
const emptyForm = (): InvoiceRegistration => ({
  orderId: undefined as unknown as number,
  invoiceNo: '',
  documentDate: '',
  sellerName: '',
  sellerTaxNo: '',
  buyerName: '',
  buyerTaxNo: '',
  untaxedAmount: null,
  taxAmount: null,
  documentAmount: null,
  currency: '',
  remark: '',
  attachments: [],
})
const createForm = reactive<InvoiceRegistration>(emptyForm())
const registrationForm = ref<FormInstance>()
const registrationRules: FormRules = Object.fromEntries(
  [
    'orderId',
    'invoiceNo',
    'documentDate',
    'sellerName',
    'sellerTaxNo',
    'buyerName',
    'buyerTaxNo',
    'untaxedAmount',
    'taxAmount',
    'documentAmount',
    'currency',
  ].map((key) => [key, [{ required: true, message: '请按原票填写此项', trigger: 'blur' }]])
)
const createAttachments = ref<InstanceType<typeof AttachmentCenter>>()
const createDraftId = ref('')
const createPending = computed(() => createAttachments.value?.pendingEntries || [])
const createSupplierName = computed(() =>
  editingId.value
    ? current.value?.supplierName
    : pendingOrders.value.find((o) => o.orderId === createForm.orderId)?.supplierName
)
const editOrderOptions = computed(() =>
  editingId.value && current.value
    ? [
        {
          orderId: current.value.orderId,
          orderNo: current.value.orderNo || String(current.value.orderId),
          supplierName: current.value.supplierName,
        },
      ]
    : pendingOrders.value
)
const verifyForm = reactive({ verificationDate: '', verificationRemark: '' })
const isPending = (row: any) => row?.documentStatus === InvoiceStatusEnum.PENDING.value
const invoiceActions: TableAction<any>[] = [
  { key: 'detail', label: '详情', order: 10 },
  {
    key: 'edit',
    label: '补录 / 编辑',
    permission: 'purchase:invoice:edit',
    visible: ({ row }) => isPending(row),
    order: 20,
  },
  {
    key: 'verify',
    label: '核验',
    type: 'success',
    permission: 'purchase:invoice:edit',
    visible: ({ row }) => isPending(row),
    order: 30,
  },
  {
    key: 'delete',
    label: '删除',
    type: 'danger',
    permission: 'purchase:invoice:delete',
    order: 40,
  },
]
function handleInvoiceAction(key: string, row: any) {
  const handlers: Record<string, () => void> = {
    detail: () => void detail(row),
    edit: () => void openEdit(row),
    verify: () => void detail(row, true),
    delete: () => void remove(row),
  }
  handlers[key]?.()
}
const money = (value?: number) =>
  value == null
    ? '待补录'
    : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
function supplierName(id?: number) {
  return (
    suppliers.value.find((s) => s.supplierId === id)?.supplierName ||
    (id == null ? '-' : String(id))
  )
}
function localDate() {
  const d = new Date()
  return [
    d.getFullYear(),
    String(d.getMonth() + 1).padStart(2, '0'),
    String(d.getDate()).padStart(2, '0'),
  ].join('-')
}
function newDraftId() {
  return (
    globalThis.crypto?.randomUUID?.() ||
    'invoice-' + Date.now() + '-' + Math.random().toString(36).slice(2)
  )
}
function carriedId(url?: string) {
  return /\/system\/attachment\/download\/(\d+)$/.exec(url || '')?.[1]
}
async function load() {
  loading.value = true
  try {
    const res: any = await listInvoice({
      ...query,
      documentDateStart: dateRange.value?.[0],
      documentDateEnd: dateRange.value?.[1],
    })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '发票列表加载失败')
  } finally {
    loading.value = false
  }
}
function search() {
  query.pageNum = 1
  void load()
}
function reset() {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 10,
    documentNo: '',
    invoiceNo: '',
    supplierId: undefined,
    documentStatus: undefined,
  })
  dateRange.value = []
  search()
}
function onSelectionChange(selection: any[]) {
  selectedIds.value = selection.map((row) => row.documentId)
}
async function loadPendingOrders() {
  const res: any = await getPendingInvoiceOrders()
  pendingOrders.value = res.data || []
}
function openCreate() {
  editingId.value = undefined
  Object.assign(createForm, emptyForm())
  createDraftId.value = newDraftId()
  createVisible.value = true
  void loadPendingOrders()
}
function resetCreate() {
  createAttachments.value?.clearPending()
  createDraftId.value = ''
}
function onOrderChange(id?: number) {
  createAttachments.value?.clearPending()
  createDraftId.value = newDraftId()
  createForm.currency = pendingOrders.value.find((o) => o.orderId === id)?.currency || ''
}
async function openEdit(row: any) {
  try {
    const res: any = await getInvoice(row.documentId)
    current.value = res.data
    editingId.value = current.value.documentId
    const values = emptyForm()
    for (const key of Object.keys(values) as (keyof InvoiceRegistration)[]) {
      if (key !== 'attachments' && current.value[key] != null)
        (values as any)[key] = current.value[key]
    }
    if (!current.value.invoiceNo)
      Object.assign(values, { documentDate: '', documentAmount: null, currency: '' })
    Object.assign(createForm, values)
    createDraftId.value = newDraftId()
    detailVisible.value = false
    createVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '发票信息加载失败')
  }
}
async function submitCreate() {
  if (submitting.value) return
  if (!(await registrationForm.value?.validate().catch(() => false))) return
  if (createAttachments.value?.uploading) {
    ElMessage.warning('文件上传中，请稍候')
    return
  }
  if (!editingId.value && !createPending.value.length) {
    ElMessage.warning('请上传发票原件')
    return
  }
  if (createAttachments.value?.getMissingTypeCount?.()) {
    ElMessage.warning('请选择每份文件的资料类型')
    return
  }
  submitting.value = true
  try {
    const attachments = createPending.value.map((file) => ({
      attachmentId: file.attachmentId,
      documentType: file.documentType!,
    }))
    await registerInvoice({ ...createForm, attachments }, editingId.value)
    ElMessage.success('发票登记已保存，附件已关联')
    createVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '登记保存失败')
  } finally {
    submitting.value = false
  }
}
async function detail(row: any, verify = false) {
  try {
    const res: any = await getInvoice(row.documentId)
    current.value = res.data
    verifyMode.value = verify
    Object.assign(verifyForm, { verificationDate: localDate(), verificationRemark: '' })
    detailVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '发票信息加载失败')
  }
}
async function submitVerify() {
  if (!current.value || submitting.value) return
  if (!verifyForm.verificationDate) {
    ElMessage.warning('请选择核验日期')
    return
  }
  submitting.value = true
  try {
    await verifyInvoice(
      current.value.documentId,
      verifyForm.verificationDate,
      '',
      verifyForm.verificationRemark
    )
    ElMessage.success('核验成功')
    detailVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '核验失败')
  } finally {
    submitting.value = false
  }
}
function openOrder(id: number) {
  if (!userStore.hasPermission('purchase:order:view')) {
    ElMessage.warning('无采购订单详情查看权限')
    return
  }
  orderDetailId.value = id
  orderDetailVisible.value = true
}
async function downloadFile(row: any) {
  try {
    const res: any = await downloadInvoiceFile(row.documentId)
    download(res, row.fileName || row.documentNo + '.pdf')
  } catch (e: any) {
    ElMessage.error(e?.message || '下载失败')
  }
}
async function remove(row: any) {
  try {
    await ElMessageBox.confirm('确认删除发票登记【' + row.documentNo + '】？', '删除确认', {
      type: 'warning',
    })
    await delInvoice(row.documentId)
    ElMessage.success('删除成功')
    await load()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '删除失败')
  }
}
async function batchDelete() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(
      '确认删除选中的 ' + selectedIds.value.length + ' 条发票登记？',
      '批量删除',
      { type: 'warning' }
    )
    await delInvoice(selectedIds.value)
    ElMessage.success('删除成功')
    await load()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '删除失败')
  }
}
onMounted(async () => {
  void load()
  try {
    const res: any = await listSupplier()
    suppliers.value = res.data?.records || res.data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '供应商加载失败')
  }
})
</script>

<style scoped>
.search-card {
  margin-bottom: 16px;
}
.toolbar,
.notice {
  margin-bottom: 16px;
}
.review-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(480px, 1.1fr);
  gap: 24px;
}
.review-layout section {
  min-width: 0;
}
.verify-form {
  margin-top: 20px;
}
@media (max-width: 1100px) {
  .review-layout {
    grid-template-columns: 1fr;
  }
}
</style>

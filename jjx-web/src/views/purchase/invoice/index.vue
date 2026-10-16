<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="发票号"><el-input v-model="query.documentNo" clearable placeholder="发票号" style="width: 180px" /></el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="query.supplierId" clearable filterable style="width: 200px" placeholder="供应商">
            <el-option v-for="s in suppliers" :key="s.supplierId" :label="s.supplierName" :value="s.supplierId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.documentStatus" clearable style="width: 130px" placeholder="状态">
            <el-option v-for="item in InvoiceStatusEnum.items" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开票日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['purchase:invoice:add']" @click="openCreate">新增发票</el-button>
        <el-button type="danger" plain icon="Delete" v-hasPermi="['purchase:invoice:delete']" :disabled="!selectedIds.length" @click="batchDelete">批量删除</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="documentNo" label="发票号" min-width="150" />
        <el-table-column label="供应商" min-width="120">
          <template #default="{ row }">{{ supplierName(row.supplierId) }}</template>
        </el-table-column>
        <el-table-column prop="documentAmount" label="金额" width="110" align="right">
          <template #default="{ row }">{{ money(row.documentAmount) }}</template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="70" />
        <el-table-column prop="documentDate" label="开票日期" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="InvoiceStatusEnum.getTagProps(row.documentStatus).type">{{ InvoiceStatusEnum.getLabel(row.documentStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="verificationDate" label="核销日期" width="110" />
        <el-table-column label="附件" width="80">
          <template #default="{ row }">
            <el-button v-if="row.fileName" link type="primary" @click="downloadFile(row)">下载</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <TableActionColumn
          :actions="invoiceActions"
          width="190"
          @action="handleInvoiceAction"
        />
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
    </el-card>

    <el-dialog v-model="createVisible" title="新增发票（按文件批量开票）" width="800px" destroy-on-close @closed="resetCreate">
      <el-form :model="createForm" label-width="90px" :disabled="submitting">
        <el-form-item label="采购订单" required>
          <el-select v-model="createForm.orderId" filterable placeholder="选择已收货的采购订单" style="width: 100%" @change="onOrderChange">
            <el-option v-for="o in pendingOrders" :key="o.orderId" :label="`${o.orderNo} - ${o.supplierName}`" :value="o.orderId" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">{{ createForm.supplierName || '-' }}</el-form-item>
        <el-form-item label="发票文件">
          <AttachmentCenter
            v-if="createVisible && createForm.orderId && canUploadDoc"
            :key="createDraftId"
            ref="createAttachments"
            source-type="purchase_invoice"
            :draft-id="createDraftId"
            variant="upload-only"
            title="发票文件"
            :auto-bind="false"
            :locked="submitting"
            :lock-reason="submitting ? '提交中，请稍候' : ''"
            :max-size-m-b="20"
            :accept="INVOICE_ACCEPT"
            view-perm="purchase:invoice:doc:view"
            edit-perm="purchase:invoice:doc:add"
          />
          <span v-else class="tip">{{ createForm.orderId ? '无发票文件上传权限（purchase:invoice:doc:add）' : '请先选择采购订单再上传' }}</span>
          <div class="tip">支持图片/PDF/Word/Excel，单文件不超过 20MB；生成发票后，文件在核销通过时建立引用。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!createForm.orderId || !createPending.length" @click="submitCreate">确认生成发票（{{ createPending.length }} 张）</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verifyVisible" title="发票核销" width="800px" :close-on-click-modal="false" @closed="resetVerify">
      <el-form :model="verifyForm" label-width="90px" :disabled="submitting">
        <el-form-item label="发票号">{{ verifyTarget?.documentNo || '-' }}</el-form-item>
        <el-form-item label="核销日期"><el-date-picker v-model="verifyForm.verificationDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="核销人"><el-input v-model="verifyForm.verifierName" placeholder="核销人姓名" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="verifyForm.verificationRemark" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="发票文件">
          <AttachmentCenter
            v-if="verifyVisible && verifyDraftId && canUploadDoc"
            :key="verifyDraftId"
            ref="verifyAttachments"
            source-type="purchase_invoice"
            :draft-id="verifyDraftId"
            variant="upload-only"
            title="本次核销文件"
            :auto-bind="false"
            :locked="submitting"
            :lock-reason="submitting ? '提交中，请稍候' : ''"
            :max-size-m-b="20"
            :accept="INVOICE_ACCEPT"
            view-perm="purchase:invoice:doc:view"
            edit-perm="purchase:invoice:doc:add"
          />
          <span v-else class="tip">无发票文件上传权限（purchase:invoice:doc:add）</span>
          <div class="tip">支持图片/PDF/Word/Excel，单文件不超过 20MB；核销通过后随核销事务建立引用。</div>
        </el-form-item>
      </el-form>
      <template #footer><el-button :disabled="submitting" @click="verifyVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitVerify">确认核销</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="发票详情" size="560px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="发票号">{{ current.documentNo }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ current.orderId }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ supplierName(current.supplierId) }}</el-descriptions-item>
        <el-descriptions-item label="开票日期">{{ current.documentDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ money(current.documentAmount) }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ current.currency || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ InvoiceStatusEnum.getLabel(current.documentStatus) }}</el-descriptions-item>
        <el-descriptions-item label="核销日期">{{ current.verificationDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ current.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">发票文件</el-divider>
      <AttachmentCenter
        v-if="detailVisible && current?.documentId && canViewDoc"
        :key="current.documentId"
        source-type="purchase_invoice"
        :source-id="current.documentId"
        title="发票文件"
        :upload-blocked="'发票文件在核销时上传；此处可查看、下载、解除引用'"
        view-perm="purchase:invoice:doc:view"
        edit-perm="purchase:invoice:doc:add"
      />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'
import { useUserStore } from '@/store/modules/user'
import { listInvoice, getInvoice, delInvoice, verifyInvoice, getPendingInvoiceOrders, batchConfirmInvoice, downloadInvoiceFile } from '@/api/purchase/invoice'
import { listSupplier } from '@/api/purchase/supplier'
import { InvoiceStatusEnum } from '@/enums/purchase/invoice'
import { download } from '@/utils/format'
import TableActionColumn from '@/components/common-ui/TableActionColumn/index.vue'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'

defineOptions({ name: 'PurchaseInvoice' })

interface PendingOrder { orderId: number; orderNo: string; supplierId: number; supplierName: string }

/** 发票文件公共附件上传接受类型（与后端 PurchaseInvoiceAttachmentService 校验一致）。 */
const INVOICE_ACCEPT = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp', '.pdf', '.doc', '.docx', '.xls', '.xlsx']

const userStore = useUserStore()
const canUploadDoc = computed(() => userStore.hasPermission('purchase:invoice:doc:add'))
const canViewDoc = computed(() => userStore.hasPermission('purchase:invoice:doc:view'))

const loading = ref(false)
const submitting = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const dateRange = ref<string[]>([])
const suppliers = ref<any[]>([])
const pendingOrders = ref<PendingOrder[]>([])
const selectedIds = ref<number[]>([])
const detailVisible = ref(false)
const current = ref<any>()
const createVisible = ref(false)
const verifyVisible = ref(false)
const verifyTarget = ref<any>()

const query = reactive<any>({ pageNum: 1, pageSize: 10, documentNo: '', supplierId: undefined, documentStatus: undefined })
const createForm = reactive<{ orderId?: number; supplierName: string }>({ orderId: undefined, supplierName: '' })
const verifyForm = reactive({ verificationDate: '', verifierName: '', verificationRemark: '' })

// 新建：发票文件走公共附件临时上传，生成发票时把附件ID随 batch-confirm 落库，核销时再建引用。
const createAttachments = ref<InstanceType<typeof AttachmentCenter>>()
const createDraftId = ref('')
const createPending = computed(() => createAttachments.value?.pendingEntries || [])

// 核销：本次核销上传的文件随核销事务建立引用。
const verifyAttachments = ref<InstanceType<typeof AttachmentCenter>>()
const verifyDraftId = ref('')

function newDraftId(prefix: string) {
  return globalThis.crypto?.randomUUID?.() || `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

const invoiceActions: TableAction<any>[] = [
  { key: 'detail', label: '详情', order: 10 },
  {
    key: 'verify',
    label: '核销',
    type: 'success',
    permission: 'purchase:invoice:edit',
    visible: ({ row }) => row.documentStatus !== InvoiceStatusEnum.VERIFIED.value,
    order: 20,
  },
  {
    key: 'delete',
    label: '删除',
    type: 'danger',
    permission: 'purchase:invoice:delete',
    order: 30,
  },
]

function handleInvoiceAction(key: string, row: any) {
  const handlers: Record<string, () => void> = {
    detail: () => void detail(row),
    verify: () => openVerify(row),
    delete: () => void remove(row),
  }
  handlers[key]?.()
}

const money = (v?: number) => v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

function supplierName(id?: number) {
  if (id == null) return '-'
  const s = suppliers.value.find((x) => x.supplierId === id)
  return s ? s.supplierName : String(id)
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
  } finally {
    loading.value = false
  }
}

function search() { query.pageNum = 1; load() }
function reset() {
  Object.assign(query, { pageNum: 1, pageSize: 10, documentNo: '', supplierId: undefined, documentStatus: undefined })
  dateRange.value = []
  search()
}
function onSelectionChange(selection: any[]) { selectedIds.value = selection.map((r) => r.documentId) }

async function loadSuppliers() {
  const res: any = await listSupplier()
  suppliers.value = res.data?.records || res.data || []
}
async function loadPendingOrders() {
  const res: any = await getPendingInvoiceOrders()
  pendingOrders.value = res.data || []
}

function openCreate() {
  Object.assign(createForm, { orderId: undefined, supplierName: '' })
  createDraftId.value = newDraftId('invoice-create')
  createVisible.value = true
}
function resetCreate() {
  createAttachments.value?.clearPending()
  createDraftId.value = ''
}
function onOrderChange(orderId?: number) {
  createAttachments.value?.clearPending()
  createDraftId.value = newDraftId('invoice-create')
  const o = pendingOrders.value.find((x) => x.orderId === orderId)
  createForm.supplierName = o?.supplierName || ''
}
async function submitCreate() {
  if (!createForm.orderId) { ElMessage.error('请选择采购订单'); return }
  if (!createPending.value.length) { ElMessage.warning('请先上传发票文件'); return }
  const o = pendingOrders.value.find((x) => x.orderId === createForm.orderId)
  if (!o) { ElMessage.error('请选择采购订单'); return }
  submitting.value = true
  try {
    const files = createPending.value.map((f) => ({ attachmentId: f.attachmentId, fileName: f.fileName, fileSize: f.fileSize }))
    await batchConfirmInvoice(createForm.orderId, o.supplierId, files)
    ElMessage.success(`已生成 ${files.length} 张发票`)
    createVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '生成失败')
  } finally {
    submitting.value = false
  }
}

function openVerify(row: any) {
  verifyTarget.value = row
  Object.assign(verifyForm, { verificationDate: new Date().toISOString().slice(0, 10), verifierName: '', verificationRemark: '' })
  verifyDraftId.value = newDraftId('invoice-verify')
  verifyVisible.value = true
}
function resetVerify() {
  verifyAttachments.value?.clearPending()
  verifyDraftId.value = ''
}
async function submitVerify() {
  if (!verifyTarget.value) return
  if (!verifyForm.verificationDate || !verifyForm.verifierName) { ElMessage.warning('请填写核销日期和核销人'); return }
  submitting.value = true
  try {
    const attachmentIds = (verifyAttachments.value?.pendingEntries || []).map((f) => f.attachmentId)
    await verifyInvoice(verifyTarget.value.documentId, verifyForm.verificationDate, verifyForm.verifierName, verifyForm.verificationRemark, attachmentIds)
    ElMessage.success('核销成功')
    verifyVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '核销失败')
  } finally {
    submitting.value = false
  }
}

async function detail(row: any) {
  const res: any = await getInvoice(row.documentId)
  current.value = res.data
  detailVisible.value = true
}
/** 老磁盘票据行的下载（新链路文件在详情/核销走公共附件组件）。 */
async function downloadFile(row: any) {
  try {
    const res: any = await downloadInvoiceFile(row.documentId)
    download(res, row.fileName || `${row.documentNo}.pdf`)
  } catch (e: any) {
    ElMessage.error(e?.message || '下载失败')
  }
}
async function remove(row: any) {
  await ElMessageBox.confirm(`确认删除发票【${row.documentNo}】？`, '删除确认', { type: 'warning' })
  try {
    await delInvoice(row.documentId)
    ElMessage.success('删除成功')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}
async function batchDelete() {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 张发票？`, '批量删除', { type: 'warning' })
  try {
    await delInvoice(selectedIds.value)
    ElMessage.success('删除成功')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(() => { load(); loadSuppliers(); loadPendingOrders() })
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.toolbar { margin-bottom: 14px; }
.tip { color: #909399; font-size: 12px; margin-top: 6px; }
</style>

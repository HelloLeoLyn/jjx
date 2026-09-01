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
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="detail(row)">详情</el-button>
            <el-button v-if="row.documentStatus !== InvoiceStatusEnum.VERIFIED.value" v-hasPermi="['purchase:invoice:edit']" link type="success" @click="openVerify(row)">核销</el-button>
            <el-button v-hasPermi="['purchase:invoice:delete']" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
    </el-card>

    <el-dialog v-model="createVisible" title="新增发票（按文件批量开票）" width="640px" destroy-on-close>
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="采购订单" required>
          <el-select v-model="createForm.orderId" filterable placeholder="选择已收货的采购订单" style="width: 100%" @change="onOrderChange">
            <el-option v-for="o in pendingOrders" :key="o.orderId" :label="`${o.orderNo} - ${o.supplierName}`" :value="o.orderId" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">{{ createForm.supplierName || '-' }}</el-form-item>
        <el-form-item label="发票文件">
          <el-upload :http-request="doUpload" :show-file-list="false" accept=".pdf,.jpg,.jpeg,.png,.gif,.bmp,.webp,.doc,.docx,.xls,.xlsx" :disabled="!createForm.orderId">
            <el-button type="primary" plain :loading="uploading" :disabled="!createForm.orderId">上传发票文件</el-button>
          </el-upload>
          <div v-for="(f, i) in createForm.files" :key="f.fileUrl" class="file-item">
            <el-icon><Document /></el-icon>
            <span class="file-name">{{ f.fileName }}</span>
            <el-button link type="danger" @click="removeTempFile(i)">删除</el-button>
          </div>
          <div v-if="!createForm.orderId" class="tip">请先选择采购订单再上传</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!createForm.files.length" @click="submitCreate">确认生成发票（{{ createForm.files.length }} 张）</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verifyVisible" title="发票核销" width="480px">
      <el-form :model="verifyForm" label-width="90px">
        <el-form-item label="核销日期"><el-date-picker v-model="verifyForm.verificationDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="核销人"><el-input v-model="verifyForm.verifierName" placeholder="核销人姓名" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="verifyForm.verificationRemark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="verifyVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitVerify">确认核销</el-button></template>
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
        <el-descriptions-item label="附件" :span="2">
          <el-button v-if="current.fileName" link type="primary" @click="downloadFile(current)">{{ current.fileName }}</el-button>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ current.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { listInvoice, getInvoice, delInvoice, verifyInvoice, getPendingInvoiceOrders, downloadInvoiceFile, uploadInvoiceTemp, batchConfirmInvoice, deleteInvoiceTempFile } from '@/api/purchase/invoice'
import { listSupplier } from '@/api/purchase/supplier'
import { InvoiceStatusEnum } from '@/enums/purchase/invoice'
import { download } from '@/utils/format'

defineOptions({ name: 'PurchaseInvoice' })

interface TempFile { fileName: string; fileUrl: string; fileSize: number }
interface PendingOrder { orderId: number; orderNo: string; supplierId: number; supplierName: string }

const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)
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
const createForm = reactive<{ orderId?: number; supplierName: string; files: TempFile[] }>({ orderId: undefined, supplierName: '', files: [] })
const verifyForm = reactive({ verificationDate: '', verifierName: '', verificationRemark: '' })

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
  Object.assign(createForm, { orderId: undefined, supplierName: '', files: [] })
  createVisible.value = true
}
function onOrderChange(orderId?: number) {
  createForm.files = []
  const o = pendingOrders.value.find((x) => x.orderId === orderId)
  createForm.supplierName = o?.supplierName || ''
}
async function doUpload(options: any) {
  if (!createForm.orderId) return
  uploading.value = true
  try {
    const res: any = await uploadInvoiceTemp(createForm.orderId, options.file)
    const info = res.data || {}
    createForm.files.push({ fileName: info.fileName || options.file.name, fileUrl: info.fileUrl, fileSize: info.fileSize || 0 })
    ElMessage.success('上传成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}
async function removeTempFile(index: number) {
  const f = createForm.files[index]
  try {
    if (f.fileUrl) await deleteInvoiceTempFile(f.fileUrl)
  } catch { /* 临时文件可能已不存在，忽略 */ }
  createForm.files.splice(index, 1)
}
async function submitCreate() {
  if (!createForm.orderId || !createForm.files.length) return
  const o = pendingOrders.value.find((x) => x.orderId === createForm.orderId)
  if (!o) { ElMessage.error('请选择采购订单'); return }
  submitting.value = true
  try {
    await batchConfirmInvoice(createForm.orderId, o.supplierId, createForm.files)
    ElMessage.success(`已生成 ${createForm.files.length} 张发票`)
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
  verifyVisible.value = true
}
async function submitVerify() {
  if (!verifyTarget.value) return
  if (!verifyForm.verificationDate || !verifyForm.verifierName) { ElMessage.warning('请填写核销日期和核销人'); return }
  submitting.value = true
  try {
    await verifyInvoice(verifyTarget.value.documentId, verifyForm.verificationDate, verifyForm.verifierName, verifyForm.verificationRemark)
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
.file-item { display: flex; align-items: center; gap: 8px; margin-top: 8px; font-size: 13px; }
.file-item .file-name { max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tip { color: #909399; font-size: 12px; margin-top: 6px; }
</style>

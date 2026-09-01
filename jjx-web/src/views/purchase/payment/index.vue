<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="付款单号"><el-input v-model="query.paymentNo" clearable placeholder="付款单号" style="width: 180px" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.paymentStatus" clearable style="width: 130px" placeholder="状态">
            <el-option v-for="item in PaymentStatusEnum.items" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款方式">
          <el-select v-model="query.paymentMethod" clearable style="width: 140px" placeholder="付款方式">
            <el-option v-for="item in PaymentMethodEnum.items" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['purchase:payment:add']" @click="openCreate">新增付款</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="paymentNo" label="付款单号" min-width="150" />
        <el-table-column prop="orderId" label="订单ID" width="90" />
        <el-table-column prop="paymentDate" label="付款日期" width="110" />
        <el-table-column prop="paymentAmount" label="金额" width="110" align="right">
          <template #default="{ row }">{{ money(row.paymentAmount) }}</template>
        </el-table-column>
        <el-table-column label="付款方式" width="110">
          <template #default="{ row }">{{ PaymentMethodEnum.getLabel(row.paymentMethod) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="PaymentStatusEnum.getTagProps(row.paymentStatus).type">{{ PaymentStatusEnum.getLabel(row.paymentStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="actualPaymentDate" label="实际付款日" width="110" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="detail(row)">详情</el-button>
            <el-button v-if="row.paymentStatus === PaymentStatusEnum.PENDING.value" v-hasPermi="['purchase:payment:approve']" link type="success" @click="openApprove(row)">审批</el-button>
            <el-button v-if="row.paymentStatus === PaymentStatusEnum.PENDING.value" v-hasPermi="['purchase:payment:edit']" link type="warning" @click="openConfirm(row)">确认付款</el-button>
            <el-button v-hasPermi="['purchase:payment:edit']" link @click="openEdit(row)">编辑</el-button>
            <el-button v-hasPermi="['purchase:payment:delete']" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
    </el-card>

    <el-dialog v-model="formVisible" :title="isEdit ? '编辑付款' : '新增付款'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="付款单号" prop="paymentNo">
          <el-input v-model="form.paymentNo" :disabled="isEdit" placeholder="付款单号" />
        </el-form-item>
        <el-form-item label="采购订单" prop="orderId">
          <el-select v-model="form.orderId" filterable placeholder="选择采购订单" style="width: 100%" :disabled="isEdit">
            <el-option v-for="o in pendingOrders" :key="o.orderId" :label="`${o.orderNo} - ${o.supplierName}`" :value="o.orderId" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="付款日期" prop="paymentDate"><el-date-picker v-model="form.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="金额" prop="paymentAmount"><el-input-number v-model="form.paymentAmount" :min="0.01" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款方式" prop="paymentMethod">
            <el-select v-model="form.paymentMethod" style="width: 100%">
              <el-option v-for="item in PaymentMethodEnum.items" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="银行账号"><el-input v-model="form.bankAccount" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="formVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="approveVisible" title="付款审批" width="480px">
      <el-form :model="approveForm" label-width="90px">
        <el-form-item label="审批人"><el-input v-model="approveForm.approverName" placeholder="审批人姓名" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="approveForm.approvalComment" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitApprove('rejected')">驳回</el-button>
        <el-button type="primary" :loading="submitting" @click="submitApprove('approved')">通过</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="confirmVisible" title="确认付款" width="480px">
      <el-form :model="confirmForm" label-width="90px">
        <el-form-item label="实际付款日"><el-date-picker v-model="confirmForm.actualPaymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="凭证号"><el-input v-model="confirmForm.voucherNo" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="confirmVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitConfirm">确认</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="付款详情" size="560px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="付款单号">{{ current.paymentNo }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ current.orderId }}</el-descriptions-item>
        <el-descriptions-item label="付款日期">{{ current.paymentDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ money(current.paymentAmount) }}</el-descriptions-item>
        <el-descriptions-item label="付款方式">{{ PaymentMethodEnum.getLabel(current.paymentMethod) }}</el-descriptions-item>
        <el-descriptions-item label="银行账号">{{ current.bankAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ PaymentStatusEnum.getLabel(current.paymentStatus) }}</el-descriptions-item>
        <el-descriptions-item label="实际付款日">{{ current.actualPaymentDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="凭证号">{{ current.voucherNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批时间">{{ current.approvalTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ current.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPayment, getPayment, addPayment, updatePayment, delPayment, approvePayment, confirmPayment, getPendingPaymentOrders, generatePaymentNo } from '@/api/purchase/payment'
import { PaymentStatusEnum, PaymentMethodEnum } from '@/enums/purchase/payment'

defineOptions({ name: 'PurchasePayment' })

interface PendingOrder { orderId: number; orderNo: string; supplierId: number; supplierName: string }

const loading = ref(false)
const submitting = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const pendingOrders = ref<PendingOrder[]>([])
const formVisible = ref(false)
const isEdit = ref(false)
const detailVisible = ref(false)
const approveVisible = ref(false)
const confirmVisible = ref(false)
const current = ref<any>()
const approveTarget = ref<any>()
const confirmTarget = ref<any>()

const query = reactive<any>({ pageNum: 1, pageSize: 10, paymentNo: '', paymentStatus: undefined, paymentMethod: undefined })
const form = reactive<any>({})
const rules = {
  paymentNo: [{ required: true, message: '请输入付款单号', trigger: 'blur' }],
  orderId: [{ required: true, message: '请选择采购订单', trigger: 'change' }],
  paymentDate: [{ required: true, message: '请选择付款日期', trigger: 'change' }],
  paymentAmount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: '请选择付款方式', trigger: 'change' }],
}
const approveForm = reactive({ approverName: '', approvalComment: '' })
const confirmForm = reactive({ actualPaymentDate: '', voucherNo: '' })

const money = (v?: number) => v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

function today() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

async function load() {
  loading.value = true
  try {
    const res: any = await listPayment({ ...query })
    const data = res.data || []
    rows.value = Array.isArray(data) ? data : data.records || []
    total.value = Array.isArray(data) ? data.length : data.total || 0
  } finally {
    loading.value = false
  }
}

function search() { query.pageNum = 1; load() }
function reset() { Object.assign(query, { pageNum: 1, pageSize: 10, paymentNo: '', paymentStatus: undefined, paymentMethod: undefined }); search() }

async function loadPendingOrders() {
  const res: any = await getPendingPaymentOrders()
  pendingOrders.value = res.data || []
}

function emptyForm() {
  return { paymentNo: '', orderId: undefined, paymentDate: today(), paymentAmount: undefined, paymentMethod: 'bank', bankAccount: '', remark: '' }
}

async function openCreate() {
  isEdit.value = false
  Object.assign(form, emptyForm())
  try {
    const res: any = await generatePaymentNo()
    if (res.data) form.paymentNo = res.data
  } catch { /* 单号生成失败可手填 */ }
  formVisible.value = true
}

async function openEdit(row: any) {
  isEdit.value = true
  const res: any = await getPayment(row.paymentId)
  Object.assign(form, res.data || {})
  formVisible.value = true
}

async function submitForm() {
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
  approveTarget.value = row
  Object.assign(approveForm, { approverName: '', approvalComment: '' })
  approveVisible.value = true
}
async function submitApprove(decision: string) {
  if (!approveTarget.value) return
  if (!approveForm.approverName) { ElMessage.warning('请填写审批人'); return }
  submitting.value = true
  try {
    await approvePayment(approveTarget.value.paymentId, decision, approveForm.approverName, approveForm.approvalComment)
    ElMessage.success(decision === 'approved' ? '审批通过' : '已驳回')
    approveVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '审批失败')
  } finally {
    submitting.value = false
  }
}

function openConfirm(row: any) {
  confirmTarget.value = row
  Object.assign(confirmForm, { actualPaymentDate: today(), voucherNo: row.voucherNo || '' })
  confirmVisible.value = true
}
async function submitConfirm() {
  if (!confirmTarget.value) return
  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('paymentId', String(confirmTarget.value.paymentId))
    fd.append('actualPaymentDate', confirmForm.actualPaymentDate)
    if (confirmForm.voucherNo) fd.append('voucherNo', confirmForm.voucherNo)
    await confirmPayment(fd)
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
  await ElMessageBox.confirm(`确认删除付款单【${row.paymentNo}】？`, '删除确认', { type: 'warning' })
  try {
    await delPayment(row.paymentId)
    ElMessage.success('删除成功')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(() => { load(); loadPendingOrders() })
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.toolbar { margin-bottom: 14px; }
</style>

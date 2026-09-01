<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" :model="query">
        <el-form-item label="单号"><el-input v-model="query.receiptNo" clearable placeholder="收款单号" /></el-form-item>
        <el-form-item label="客户"><el-input v-model="query.customerName" clearable placeholder="客户名称" /></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable style="width: 120px"><el-option v-for="item in SalesFinanceDocumentStatusEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
      <div class="toolbar"><el-button type="primary" @click="openCreate">新增收款</el-button></div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="receiptNo" label="单号" min-width="150" />
        <el-table-column prop="orderId" label="订单" width="100" />
        <el-table-column prop="customerName" label="客户" min-width="150" />
        <el-table-column prop="receiptDate" label="日期" width="120" />
        <el-table-column label="收款方式" width="120"><template #default="{ row }">{{ SalesReceiptPaymentMethodEnum.getLabel(row.paymentMethod) }}</template></el-table-column>
        <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ money(row.receiptAmount) }}</template></el-table-column>
        <el-table-column label="实收" width="120" align="right"><template #default="{ row }">{{ money(row.actualAmount) }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="SalesFinanceDocumentStatusEnum.getTagProps(row.status).type">{{ SalesFinanceDocumentStatusEnum.getLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="print(row.receiptId)">打印</el-button><el-button link @click="detail(row.receiptId)">详情</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
    </el-card>

    <el-dialog v-model="createVisible" title="新增收款" width="680px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="销售订单" prop="orderId">
          <el-select v-model="createForm.orderId" filterable remote reserve-keyword :remote-method="searchOrders" :loading="ordersLoading" placeholder="输入订单号或客户名称" style="width: 100%" @change="selectOrder">
            <el-option v-for="order in orderOptions" :key="order.orderId" :label="`${order.orderNo} - ${order.customerName}`" :value="order.orderId" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedOrder" label="订单金额">
          <el-descriptions :column="3" border style="width: 100%">
            <el-descriptions-item label="应收">{{ money(selectedOrder.finalAmount) }}</el-descriptions-item>
            <el-descriptions-item label="已收">{{ money(selectedOrder.paidAmount) }}</el-descriptions-item>
            <el-descriptions-item label="欠款">{{ money(selectedOrder.unpaidAmount) }}</el-descriptions-item>
          </el-descriptions>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="收款日期" prop="receiptDate"><el-date-picker v-model="createForm.receiptDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="收款类型" prop="receiptType"><el-select v-model="createForm.receiptType" style="width: 100%"><el-option v-for="item in ReceiptTypeEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="收款方式" prop="paymentMethod"><el-select v-model="createForm.paymentMethod" style="width: 100%"><el-option v-for="item in SalesReceiptPaymentMethodEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="币种" prop="currency"><el-input v-model="createForm.currency" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="收款金额" prop="receiptAmount"><el-input-number v-model="createForm.receiptAmount" :min="0" :precision="2" :controls="false" style="width: 100%" @change="syncActualAmount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="实收金额" prop="actualAmount"><el-input-number v-model="createForm.actualAmount" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="createForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCreate">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="收款单详情" width="620px"><el-descriptions v-if="current" :column="2" border><el-descriptions-item v-for="item in detailItems" :key="item.label" :label="item.label">{{ item.value }}</el-descriptions-item></el-descriptions></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { salesReceiptApi, type SalesReceipt } from '@/api/sales/receipt'
import { orderApi } from '@/api/sales/order'
import type { SalesOrderVO } from '@/types/sales/order'
import { ReceiptTypeEnum, SalesFinanceDocumentStatusEnum, SalesReceiptPaymentMethodEnum } from '@/enums/sales'

type CreateReceiptForm = {
  orderId?: number
  customerId?: number
  customerName?: string
  receiptDate: string
  receiptType: number
  paymentMethod: number
  receiptAmount?: number
  actualAmount?: number
  currency: string
  remark: string
}

const loading = ref(false)
const rows = ref<SalesReceipt[]>([])
const total = ref(0)
const dateRange = ref<string[]>([])
const detailVisible = ref(false)
const current = ref<SalesReceipt>()
const createVisible = ref(false)
const createFormRef = ref<FormInstance>()
const submitting = ref(false)
const ordersLoading = ref(false)
const orderOptions = ref<SalesOrderVO[]>([])
const selectedOrder = ref<SalesOrderVO>()
const query = reactive({ pageNum: 1, pageSize: 10, receiptNo: '', customerName: '', status: undefined as number | undefined })
const createForm = reactive<CreateReceiptForm>(newCreateForm())
const createRules: FormRules<CreateReceiptForm> = {
  orderId: [{ required: true, message: '请选择销售订单', trigger: 'change' }],
  receiptDate: [{ required: true, message: '请选择收款日期', trigger: 'change' }],
  receiptType: [{ required: true, message: '请选择收款类型', trigger: 'change' }],
  paymentMethod: [{ required: true, message: '请选择收款方式', trigger: 'change' }],
  receiptAmount: [{ required: true, message: '请输入收款金额', trigger: 'blur' }],
  actualAmount: [{ required: true, message: '请输入实收金额', trigger: 'blur' }],
  currency: [{ required: true, message: '请输入币种', trigger: 'blur' }],
}

function today() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function newCreateForm(): CreateReceiptForm {
  return {
    receiptDate: today(),
    receiptType: ReceiptTypeEnum.DEPOSIT.value,
    paymentMethod: SalesReceiptPaymentMethodEnum.BANK_TRANSFER.value,
    currency: 'CNY',
    remark: '',
  }
}

const money = (value?: number) => value == null ? '-' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

async function load() {
  loading.value = true
  try {
    const response: any = await salesReceiptApi.page({ ...query, startDate: dateRange.value?.[0], endDate: dateRange.value?.[1] })
    rows.value = response.data?.records || []
    total.value = response.data?.total || 0
  } finally {
    loading.value = false
  }
}

function search() { query.pageNum = 1; load() }
function reset() { query.receiptNo = ''; query.customerName = ''; query.status = undefined; dateRange.value = []; search() }
function print(id: number) { window.open(`/sales/receipt/print/${id}`, '_blank') }

async function detail(id: number) {
  const response: any = await salesReceiptApi.detail(id)
  current.value = response.data
  detailVisible.value = true
}

function openCreate() {
  Object.assign(createForm, newCreateForm())
  delete createForm.orderId
  delete createForm.customerId
  delete createForm.customerName
  delete createForm.receiptAmount
  delete createForm.actualAmount
  selectedOrder.value = undefined
  orderOptions.value = []
  createVisible.value = true
  searchOrders('')
}

async function searchOrders(keyword: string) {
  ordersLoading.value = true
  try {
    const response = await orderApi.getOrders({ pageNum: 1, pageSize: 20, orderNo: keyword || undefined })
    orderOptions.value = response.data?.records || []
    if (keyword && orderOptions.value.length === 0) {
      const customerResponse = await orderApi.getOrders({ pageNum: 1, pageSize: 20, customerName: keyword })
      orderOptions.value = customerResponse.data?.records || []
    }
  } finally {
    ordersLoading.value = false
  }
}

async function selectOrder(orderId?: number) {
  selectedOrder.value = undefined
  if (orderId == null) return
  const response = await orderApi.getOrder(orderId)
  const order = response.data
  if (!order) return
  selectedOrder.value = order
  createForm.customerId = order.customerId
  createForm.customerName = order.customerName
  createForm.currency = order.currency || 'CNY'
}

function syncActualAmount(value?: number) {
  createForm.actualAmount = value
}

function generateReceiptNo() {
  const timestamp = new Date().toISOString().replace(/\D/g, '').slice(0, 17)
  return `SK${timestamp}`
}

async function submitCreate() {
  if (!createFormRef.value || !await createFormRef.value.validate()) return
  submitting.value = true
  try {
    await request.post('/sales/receipt', {
      ...createForm,
      receiptNo: generateReceiptNo(),
      status: SalesFinanceDocumentStatusEnum.NORMAL.value,
    })
    ElMessage.success('收款单创建成功')
    createVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

const detailItems = computed(() => {
  const item = current.value
  if (!item) return []
  return [
    { label: '单号', value: item.receiptNo },
    { label: '客户', value: item.customerName || '-' },
    { label: '日期', value: item.receiptDate || '-' },
    { label: '收款方式', value: SalesReceiptPaymentMethodEnum.getLabel(item.paymentMethod!) },
    { label: '应收', value: money(item.receiptAmount) },
    { label: '实收', value: money(item.actualAmount) },
    { label: '状态', value: SalesFinanceDocumentStatusEnum.getLabel(item.status) },
    { label: '备注', value: item.remark || '-' },
  ]
})

onMounted(load)
</script>

<style scoped>
.toolbar { margin-bottom: 16px; }
</style>

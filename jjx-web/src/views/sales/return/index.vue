<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="退货单号"><el-input v-model="query.returnNo" clearable placeholder="退货单号" style="width: 160px" /></el-form-item>
        <el-form-item label="客户"><el-input v-model="query.customerName" clearable placeholder="客户名称" style="width: 160px" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.returnStatus" clearable style="width: 130px" placeholder="状态">
            <el-option v-for="item in SalesReturnStatusEnum.items" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="退货日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['sales:return:add']" @click="openCreate">新增退货</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="returnNo" label="退货单号" min-width="150" />
        <el-table-column prop="orderId" label="订单ID" width="90" />
        <el-table-column prop="customerName" label="客户" min-width="140" />
        <el-table-column prop="returnDate" label="退货日期" width="110" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ SalesReturnTypeEnum.getLabel(row.returnType) }}</template>
        </el-table-column>
        <el-table-column prop="totalQuantity" label="数量" width="80" align="right" />
        <el-table-column prop="totalAmount" label="金额" width="110" align="right">
          <template #default="{ row }">{{ money(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="SalesReturnStatusEnum.getTagProps(row.returnStatus).type">{{ SalesReturnStatusEnum.getLabel(row.returnStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="approveTime" label="审核时间" width="150" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="detail(row)">详情</el-button>
            <el-button v-if="row.returnStatus === SalesReturnStatusEnum.APPLYING.value" v-hasPermi="['sales:return:approve']" link type="success" @click="openApprove(row)">审核</el-button>
            <el-button v-if="row.returnStatus === SalesReturnStatusEnum.APPROVED.value" v-hasPermi="['sales:return:edit']" link type="warning" @click="openReceive(row)">收货</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
    </el-card>

    <el-dialog v-model="createVisible" title="新增退货单" width="560px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="销售订单" prop="orderId">
          <el-select v-model="createForm.orderId" filterable remote reserve-keyword :remote-method="searchOrders" :loading="ordersLoading" placeholder="输入订单号或客户搜索" style="width: 100%">
            <el-option v-for="o in orderOptions" :key="o.orderId" :label="`${o.orderNo} - ${o.customerName}`" :value="o.orderId" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedOrder" label="订单信息">
          <el-descriptions :column="2" border size="small" style="width: 100%">
            <el-descriptions-item label="客户">{{ selectedOrder.customerName }}</el-descriptions-item>
            <el-descriptions-item label="金额">{{ money(selectedOrder.finalAmount ?? selectedOrder.totalAmount) }}</el-descriptions-item>
          </el-descriptions>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="退货日期" prop="returnDate"><el-date-picker v-model="createForm.returnDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="退货类型" prop="returnType">
            <el-select v-model="createForm.returnType" style="width: 100%">
              <el-option v-for="item in SalesReturnTypeEnum.items" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="数量"><el-input-number v-model="createForm.totalQuantity" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="金额"><el-input-number v-model="createForm.totalAmount" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="退货原因" prop="returnReason"><el-input v-model="createForm.returnReason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="备注"><el-input v-model="createForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCreate">提交退货申请</el-button></template>
    </el-dialog>

    <el-dialog v-model="approveVisible" title="退货审核" width="480px">
      <el-form :model="approveForm" label-width="90px">
        <el-form-item label="审核人"><el-input v-model="approveForm.approverName" placeholder="审核人姓名" /></el-form-item>
        <el-form-item label="审核意见"><el-input v-model="approveForm.approveRemark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitApprove('reject')">驳回</el-button>
        <el-button type="primary" :loading="submitting" @click="submitApprove('approve')">通过</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="receiveVisible" title="收货确认（联动退货入库）" width="480px">
      <el-form :model="receiveForm" label-width="90px">
        <el-form-item label="收货人"><el-input v-model="receiveForm.receiverName" placeholder="收货人姓名" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="receiveForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="receiveVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitReceive">确认收货并入库</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="退货单详情" size="560px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="退货单号">{{ current.returnNo }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ current.orderId }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ current.customerName }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ current.returnDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ SalesReturnTypeEnum.getLabel(current.returnType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ SalesReturnStatusEnum.getLabel(current.returnStatus) }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ current.totalQuantity ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ money(current.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{ current.approverName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ current.approveTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核意见" :span="2">{{ current.approveRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收货人">{{ current.receiveName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收货时间">{{ current.receiveTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="退货原因" :span="2">{{ current.returnReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ current.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageSalesReturn, getSalesReturn, createSalesReturn, approveSalesReturn, rejectSalesReturn, receiveSalesReturn } from '@/api/sales/return'
import { orderApi } from '@/api/sales/order'
import { SalesReturnStatusEnum, SalesReturnTypeEnum } from '@/enums/sales'

defineOptions({ name: 'SalesReturn' })

const loading = ref(false)
const submitting = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const dateRange = ref<string[]>([])
const createVisible = ref(false)
const approveVisible = ref(false)
const receiveVisible = ref(false)
const detailVisible = ref(false)
const current = ref<any>()
const approveTarget = ref<any>()
const receiveTarget = ref<any>()
const ordersLoading = ref(false)
const orderOptions = ref<any[]>([])
const selectedOrder = ref<any>()
const createFormRef = ref()

const query = reactive<any>({ pageNum: 1, pageSize: 10, returnNo: '', customerName: '', returnStatus: undefined })
const createForm = reactive<any>({})
const createRules = {
  orderId: [{ required: true, message: '请选择销售订单', trigger: 'change' }],
  returnDate: [{ required: true, message: '请选择退货日期', trigger: 'change' }],
  returnType: [{ required: true, message: '请选择退货类型', trigger: 'change' }],
  returnReason: [{ required: true, message: '请填写退货原因', trigger: 'blur' }],
}
const approveForm = reactive({ approverName: '', approveRemark: '' })
const receiveForm = reactive({ receiverName: '', remark: '' })

const money = (v?: number) => v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

function today() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

async function load() {
  loading.value = true
  try {
    const res: any = await pageSalesReturn({
      ...query,
      returnDateStart: dateRange.value?.[0],
      returnDateEnd: dateRange.value?.[1],
    })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}
function search() { query.pageNum = 1; load() }
function reset() { Object.assign(query, { pageNum: 1, pageSize: 10, returnNo: '', customerName: '', returnStatus: undefined }); dateRange.value = []; search() }

async function searchOrders(keyword: string) {
  ordersLoading.value = true
  try {
    const res: any = await orderApi.getOrders({ pageNum: 1, pageSize: 20, orderNo: keyword || undefined, customerName: keyword || undefined } as any)
    orderOptions.value = res.data?.records || []
  } finally {
    ordersLoading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, { orderId: undefined, returnDate: today(), returnType: 1, totalQuantity: 0, totalAmount: undefined, returnReason: '', remark: '' })
  selectedOrder.value = undefined
  createVisible.value = true
}

async function submitCreate() {
  if (!createFormRef.value || !await createFormRef.value.validate()) return
  submitting.value = true
  try {
    await createSalesReturn(createForm)
    ElMessage.success('退货申请已提交')
    createVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

function openApprove(row: any) {
  approveTarget.value = row
  Object.assign(approveForm, { approverName: '', approveRemark: '' })
  approveVisible.value = true
}
async function submitApprove(decision: string) {
  if (!approveTarget.value) return
  submitting.value = true
  try {
    if (decision === 'approve') {
      await approveSalesReturn(approveTarget.value.returnId, approveForm.approverName, approveForm.approveRemark)
      ElMessage.success('审核通过')
    } else {
      await rejectSalesReturn(approveTarget.value.returnId, approveForm.approverName, approveForm.approveRemark)
      ElMessage.success('已驳回')
    }
    approveVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '审核失败')
  } finally {
    submitting.value = false
  }
}

function openReceive(row: any) {
  receiveTarget.value = row
  Object.assign(receiveForm, { receiverName: '', remark: '' })
  receiveVisible.value = true
}
async function submitReceive() {
  if (!receiveTarget.value) return
  submitting.value = true
  try {
    await receiveSalesReturn(receiveTarget.value.returnId, receiveForm.receiverName, receiveForm.remark)
    ElMessage.success('收货成功，已联动退货入库')
    receiveVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '收货失败')
  } finally {
    submitting.value = false
  }
}

async function detail(row: any) {
  const res: any = await getSalesReturn(row.returnId)
  current.value = res.data
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.toolbar { margin-bottom: 14px; }
</style>

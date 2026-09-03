<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="发货单号"><el-input v-model="query.deliveryNo" clearable /></el-form-item>
        <el-form-item label="客户名称"><el-input v-model="query.customerName" clearable /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.deliveryStatus" clearable style="width: 140px">
            <el-option v-for="item in DeliveryStatusEnum.items" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="发货日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="records" border>
        <el-table-column prop="deliveryNo" label="单号" min-width="160" />
        <el-table-column prop="orderId" label="订单ID" width="100" />
        <el-table-column prop="customerName" label="客户" min-width="150" />
        <el-table-column prop="deliveryMethod" label="交货方式" width="110" />
        <el-table-column prop="deliveryDate" label="发货日期" width="120" />
        <el-table-column label="发货状态" width="100">
          <template #default="{ row }"><el-tag :type="DeliveryStatusEnum.getTagProps(row.deliveryStatus).type">{{ DeliveryStatusEnum.getLabel(row.deliveryStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="receiverName" label="签收人" width="110" />
        <el-table-column prop="receiveTime" label="签收时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button v-if="row.deliveryStatus !== DeliveryStatusEnum.RECEIVED.value" link type="success" @click="openReceive(row)">签收</el-button>
            <el-button link type="primary" @click="printDelivery(row)">打印</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load" />
    </el-card>

    <el-drawer v-model="detailVisible" title="发货单详情" size="720px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="发货单号">{{ current.deliveryNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ current.customerName }}</el-descriptions-item>
        <el-descriptions-item label="收货地址">{{ current.deliveryAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ current.contactPerson || '-' }} {{ current.contactPhone || '' }}</el-descriptions-item>
        <el-descriptions-item label="承运商">{{ current.carrier || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物流单号">{{ current.trackingNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="签收人">{{ current.receiverName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="签收时间">{{ current.receiveTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="签收备注" :span="2">{{ current.receiveRemark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="items" border style="margin-top: 18px">
        <el-table-column prop="productCode" label="产品编码" />
        <el-table-column prop="productName" label="产品名称" />
        <el-table-column prop="specification" label="规格" />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column prop="unitPrice" label="单价" width="100" />
        <el-table-column prop="amount" label="金额" width="110" />
      </el-table>
    </el-drawer>

    <el-dialog v-model="receiveVisible" title="发货单签收" width="480px">
      <el-form :model="receiveForm" label-width="90px">
        <el-form-item label="签收人"><el-input v-model="receiveForm.receiverName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="receiveForm.receiverPhone" /></el-form-item>
        <el-form-item label="签收备注"><el-input v-model="receiveForm.receiveRemark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="receiveVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitReceive">确认签收</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { deliveryApi, type SalesDeliveryQueryDTO, type SalesDeliveryVO } from '@/api/sales/delivery'
import { orderApi } from '@/api/sales/order'
import { DeliveryStatusEnum } from '@/enums/sales/DeliveryEnum'

defineOptions({ name: 'SalesDelivery' })
const router = useRouter()
const loading = ref(false), submitting = ref(false), total = ref(0)
const records = ref<SalesDeliveryVO[]>([]), items = ref<any[]>([])
const dateRange = ref<string[]>([]), detailVisible = ref(false), receiveVisible = ref(false)
const current = ref<SalesDeliveryVO>(), receiveDeliveryId = ref<number>()
const query = reactive<SalesDeliveryQueryDTO>({ pageNum: 1, pageSize: 10 })
const receiveForm = reactive({ receiverName: '', receiverPhone: '', receiveRemark: '' })

async function load() {
  loading.value = true
  try {
    query.deliveryDateStart = dateRange.value?.[0]
    query.deliveryDateEnd = dateRange.value?.[1]
    const res = await deliveryApi.list(query)
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
function search() { query.pageNum = 1; load() }
function reset() { Object.assign(query, { pageNum: 1, pageSize: 10, deliveryNo: undefined, customerName: undefined, deliveryStatus: undefined, deliveryDateStart: undefined, deliveryDateEnd: undefined }); dateRange.value = []; load() }
async function showDetail(row: SalesDeliveryVO) {
  const [detail, order] = await Promise.all([deliveryApi.getById(row.deliveryId), orderApi.getOrder(row.orderId)])
  current.value = detail.data || undefined
  items.value = order.data?.items || []
  detailVisible.value = true
}
function openReceive(row: SalesDeliveryVO) { receiveDeliveryId.value = row.deliveryId; Object.assign(receiveForm, { receiverName: '', receiverPhone: '', receiveRemark: '' }); receiveVisible.value = true }
async function submitReceive() {
  if (!receiveDeliveryId.value) return
  submitting.value = true
  try { await deliveryApi.receive(receiveDeliveryId.value, receiveForm); ElMessage.success('签收成功'); receiveVisible.value = false; await load() }
  catch (e: any) { ElMessage.error(e?.message || '签收失败') }
  finally { submitting.value = false }
}
function printDelivery(row: SalesDeliveryVO) { router.push({ path: '/sales/delivery/print', query: { deliveryId: row.deliveryId } }) }
onMounted(load)
</script>

<style scoped>.search-card{margin-bottom:16px}</style>

<template>
  <div class="print-page">
    <div class="toolbar no-print"><el-button @click="router.back()">返回</el-button><el-button type="primary" icon="Printer" @click="print">打印</el-button></div>
    <A4Canvas v-if="info" :padding-mm="15">
      <PrintCompanyHeader variant="center" /><h1>送 货 单</h1>
      <div class="info"><div>单号：{{ info.deliveryNo }}</div><div>日期：{{ info.deliveryDate || '-' }}</div><div>客户：{{ info.customerName || '-' }}</div><div>收货地址：{{ info.deliveryAddress || '-' }}</div><div>联系人：{{ info.contactPerson || '-' }}</div><div>联系电话：{{ info.contactPhone || '-' }}</div></div>
      <table><thead><tr><th>产品</th><th>规格</th><th>数量</th><th>单价</th><th>金额</th></tr></thead><tbody><tr v-for="item in items" :key="item.id"><td>{{ item.productName || item.productCode || '-' }}</td><td>{{ item.specification || '-' }}</td><td class="right">{{ item.quantity ?? '-' }}</td><td class="right">{{ money(item.unitPrice) }}</td><td class="right">{{ money(item.amount) }}</td></tr><tr v-if="!items.length"><td colspan="5" class="center">无订单明细</td></tr></tbody></table>
      <div class="amount"><b>合计金额：{{ money(info.totalAmount) }}</b></div>
      <div class="sign"><span>收货人：{{ info.receiverName || '________________' }}</span><span>日期：{{ receiveDate }}</span></div>
    </A4Canvas>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { deliveryApi, type SalesDeliveryVO } from '@/api/sales/delivery'
import { orderProductApi } from '@/api/sales/orderProduct'
import { createQualityTemplatePrintLog } from '@/api/production/qualityTemplate'
const route = useRoute(), router = useRouter(), info = ref<SalesDeliveryVO>(), items = ref<any[]>([])
const deliveryId = Number(route.query.deliveryId)
const money = (value?: number) => value == null ? '-' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
const receiveDate = computed(() => info.value?.receiveTime?.slice(0, 10) || '________________')
async function print() { try { /* 26 = JJX-QR-026 送货单 */ await createQualityTemplatePrintLog(26, 'sales_delivery', deliveryId); window.print() } catch { ElMessage.error('打印留痕失败，请重试') } }
onMounted(async () => { if (!deliveryId) { ElMessage.error('发货单ID缺失'); return } const detail = await deliveryApi.getById(deliveryId); info.value = detail.data || undefined; if (info.value?.orderId) { const products = await orderProductApi.getListByOrderId(info.value.orderId); items.value = products.data || [] } })
</script>
<style scoped>.print-page{min-height:100vh;background:#eef0f3;padding:20px}.toolbar{max-width:794px;margin:0 auto 16px;display:flex;justify-content:space-between}h1{text-align:center;letter-spacing:12px;border-bottom:2px solid #2b5aa7;padding-bottom:10px}.info{display:grid;grid-template-columns:1fr 1fr;gap:8px;font-size:11px;margin:14px 0}table{width:100%;border-collapse:collapse;font-size:11px}th,td{border:1px solid #bbb;padding:7px}th{background:#2b5aa7;color:#fff}.right{text-align:right}.center{text-align:center}.amount{text-align:right;margin-top:16px}.sign{display:flex;justify-content:space-between;margin-top:65px;font-size:12px}@media print{.print-page{padding:0;background:#fff}}</style>

<template>
  <div class="print-page">
    <div class="toolbar no-print">
      <el-button @click="router.back()">返回</el-button>
      <el-button type="primary" icon="Printer" :disabled="!order" @click="print">打印</el-button>
    </div>

    <A4Canvas v-if="order" :padding-mm="14">
      <PrintCompanyHeader variant="center" />
      <h1>{{ title }}</h1>

      <section class="order-info">
        <div>订单号：{{ order.orderNo || '-' }}</div>
        <div>客户：{{ order.customerName || '-' }}</div>
        <div>订单日期：{{ formatDate(order.orderDate) }}</div>
        <div>订单金额：{{ money(order.finalAmount ?? order.totalAmount) }} {{ order.currency || '' }}</div>
      </section>

      <h2>评审记录</h2>
      <table>
        <thead>
          <tr><th>阶段</th><th>审核人</th><th>审核时间</th><th>审核结果</th><th>意见</th></tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.recordId">
            <td>{{ record.stageName || '-' }}</td>
            <td>{{ record.reviewerName || '-' }}</td>
            <td>{{ formatTime(record.reviewTime) }}</td>
            <td>{{ record.resultDescription || '-' }}</td>
            <td class="comment">{{ record.reviewComment || '-' }}</td>
          </tr>
          <tr v-if="!records.length"><td colspan="5" class="center">无评审记录</td></tr>
        </tbody>
      </table>

      <h2>审核历史</h2>
      <table>
        <thead>
          <tr><th>动作</th><th>操作人</th><th>时间</th><th>意见</th></tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in reviewHistory" :key="item.flowId || index">
            <td>{{ item.actionName || item.actionCode || '-' }}</td>
            <td>{{ item.operatorName || '-' }}</td>
            <td>{{ formatTime(item.createTime) }}</td>
            <td class="comment">{{ item.comment || '-' }}</td>
          </tr>
          <tr v-if="!reviewHistory.length"><td colspan="4" class="center">无审核历史</td></tr>
        </tbody>
      </table>

      <div class="signatures">
        <span>评审人签字：________________</span>
        <span>批准人签字：________________</span>
        <span>日期：________________</span>
      </div>
    </A4Canvas>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { orderApi, type OrderReviewProcessRecord } from '@/api/sales/order'
import { createQualityTemplatePrintLog } from '@/api/production/qualityTemplate'
import type { SalesOrderVO } from '@/types/sales/order'

interface ReviewHistoryItem {
  flowId?: string
  actionCode?: string
  actionName?: string
  operatorName?: string
  comment?: string
  createTime?: string
}

const route = useRoute()
const router = useRouter()
const order = ref<SalesOrderVO>()
const records = ref<OrderReviewProcessRecord[]>([])
const reviewHistory = ref<ReviewHistoryItem[]>([])
const orderId = Number(route.query.orderId)
const requestedTemplateId = Number(route.query.templateId)
const templateId = requestedTemplateId === 53 ? 53 : 47
const title = computed(() => templateId === 53 ? '合同更改评审单' : '合同评审记录表')

const formatDate = (value?: string) => value ? value.slice(0, 10) : '-'
const formatTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 19) : '-'
const money = (value?: number) => value == null
  ? '-'
  : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

async function print() {
  try {
    await createQualityTemplatePrintLog(templateId, 'sales_order_review', orderId)
    window.print()
  } catch {
    ElMessage.error('打印留痕失败，请重试')
  }
}

onMounted(async () => {
  if (!orderId) {
    ElMessage.error('订单ID缺失')
    return
  }

  try {
    const [orderResponse, recordsResponse, historyResponse] = await Promise.all([
      orderApi.getOrder(orderId),
      orderApi.reviewRecords(orderId),
      request.get<{ data?: ReviewHistoryItem[] }>('/api/trace/reviews', {
        params: { bizType: 'order', bizId: orderId },
      }),
    ])
    order.value = orderResponse.data || undefined
    records.value = recordsResponse.data || []
    reviewHistory.value = historyResponse.data || []
  } catch {
    ElMessage.error('订单评审打印数据加载失败')
  }
})
</script>

<style scoped>
.print-page { min-height: 100vh; padding: 20px; background: #eef0f3; }
.toolbar { display: flex; justify-content: space-between; max-width: 794px; margin: 0 auto 16px; }
h1 { margin: 12px 0 18px; padding-bottom: 10px; border-bottom: 2px solid #2b5aa7; text-align: center; letter-spacing: 8px; }
h2 { margin: 18px 0 8px; font-size: 14px; }
.order-info { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 18px; font-size: 11px; }
table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 10px; }
th, td { padding: 6px; border: 1px solid #aaa; overflow-wrap: anywhere; }
th { background: #2b5aa7; color: #fff; }
.comment { text-align: left; }
.center { text-align: center; }
.signatures { display: flex; justify-content: space-between; margin-top: 50px; font-size: 11px; }
@media print { .print-page { padding: 0; background: #fff; } }
</style>

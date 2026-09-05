<template>
  <div class="order-confirm-print-page">
    <div class="print-toolbar no-print">
      <div class="toolbar-left">
        <el-button @click="router.back()">返回</el-button>
        <span class="toolbar-tip">打印预览 - {{ info?.orderNo || '' }}</span>
      </div>
      <el-button type="primary" icon="Printer" @click="handlePrint">打印</el-button>
    </div>

    <A4Canvas v-if="info" :padding-mm="15">
      <PrintCompanyHeader variant="center" />

      <div class="doc-title">销售订单确认书</div>

      <div class="doc-info">
        <div class="info-item"><span class="info-label">订单号</span>{{ info.orderNo }}</div>
        <div class="info-item">
          <span class="info-label">订单日期</span>{{ info.orderDate || '-' }}
        </div>
        <div class="info-item">
          <span class="info-label">客户名称</span>{{ info.customerName || '-' }}
        </div>
        <div class="info-item"><span class="info-label">联系人</span>{{ contactText }}</div>
        <div class="info-item">
          <span class="info-label">交货日期</span>{{ info.deliveryDate || '-' }}
        </div>
        <div class="info-item"><span class="info-label">币种</span>{{ currencyText }}</div>
        <div class="info-item"><span class="info-label">付款条件</span>{{ paymentTermsText }}</div>
        <div class="info-item">
          <span class="info-label">销售负责人</span>{{ info.salesManagerName || '-' }}
        </div>
        <div class="info-item info-item-wide">
          <span class="info-label">收货人</span>{{ contactText }}
        </div>
        <div class="info-item info-item-wide">
          <span class="info-label">收货地址</span>{{ deliveryAddressText }}
        </div>
      </div>

      <table class="doc-items">
        <thead>
          <tr>
            <th style="width: 6%">序号</th>
            <th style="width: 13%">产品编码</th>
            <th>产品名称 / 规格</th>
            <th style="width: 8%">数量</th>
            <th style="width: 6%">单位</th>
            <th style="width: 12%">单价</th>
            <th style="width: 13%">金额</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, idx) in itemsList" :key="item.id || idx">
            <td class="col-center">{{ idx + 1 }}</td>
            <td>{{ item.productCode || '' }}</td>
            <td class="col-spec">{{ buildSpec(item) }}</td>
            <td class="col-right">{{ item.quantity }}</td>
            <td class="col-center">{{ item.unitDesc || item.unit || '' }}</td>
            <td class="col-right">{{ fmt(item.unitPrice) }}</td>
            <td class="col-right">{{ fmt(item.amount) }}</td>
          </tr>
          <tr v-if="!itemsList.length">
            <td colspan="7" class="col-center">无明细</td>
          </tr>
        </tbody>
      </table>

      <div class="doc-amounts">
        <div class="amount-row">
          <span>总数量</span><span>{{ info.totalQuantity ?? '' }}</span>
        </div>
        <div class="amount-row">
          <span>总金额</span><span>{{ money(info.totalAmount) }}</span>
        </div>
        <div v-if="showFinalAmount" class="amount-row amount-total">
          <span>合计</span><span>{{ money(info.finalAmount) }}</span>
        </div>
      </div>

      <div v-if="info.remark" class="doc-remark">备注：{{ info.remark }}</div>

      <div class="doc-signs">
        <div class="sign-item">
          <div class="sign-line">销售方（盖章/签字）：</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">客户确认签字：</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">日期：</div>
          <div class="sign-underline"></div>
        </div>
      </div>
    </A4Canvas>

    <div v-else v-loading="loading" style="height: 400px"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { orderApi } from '@/api/sales/order'
import type { SalesOrderVO } from '@/types/sales/order'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { useOrderForm } from './composables/useOrderForm'

const route = useRoute()
const router = useRouter()
const info = ref<SalesOrderVO | null>(null)
const loading = ref(false)
const { paymentTermsOptions } = useOrderForm()

const itemsList = computed<any[]>(() => info.value?.items || [])

const contactText = computed(() => {
  const person = info.value?.contactPerson || ''
  const phone = info.value?.contactPhone || ''
  return [person, phone].filter(Boolean).join(' ') || '-'
})

const currencyText = computed(() => info.value?.currency || 'CNY')

const paymentTermsText = computed(() => {
  const code = info.value?.paymentTerms
  if (!code) return '-'
  return paymentTermsOptions.value.find((item) => item.value === code)?.label || code
})

const deliveryAddressText = computed(() => {
  const address = info.value?.deliveryAddress?.trim()
  if (!address) return '-'
  if (!address.startsWith('{')) return address
  try {
    const parsed = JSON.parse(address)
    return (
      [parsed.country, parsed.province, parsed.city, parsed.street].filter(Boolean).join(' ') ||
      address
    )
  } catch {
    return address
  }
})

const showFinalAmount = computed(() => {
  if (info.value?.finalAmount === null || info.value?.finalAmount === undefined) return false
  return Number(info.value.finalAmount) !== Number(info.value.totalAmount)
})

const fmt = (value?: number | string | null): string => {
  if (value === null || value === undefined || value === '') return ''
  const number = Number(value)
  return Number.isNaN(number)
    ? String(value)
    : number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const money = (value?: number | string | null): string => {
  const amount = fmt(value)
  if (!amount) return ''
  const currency = info.value?.currency || 'CNY'
  return currency === 'CNY' ? `¥${amount}` : `${currency} ${amount}`
}

const buildSpec = (item: any): string => {
  const name = item.productName || ''
  const specification = item.specification || ''
  return [name, specification].filter(Boolean).join(' / ')
}

async function loadData() {
  const orderId = Number(route.params.orderId)
  if (!orderId) {
    ElMessage.error('缺少订单ID')
    return
  }
  loading.value = true
  try {
    const res = await orderApi.getOrder(orderId)
    if (res.code === 200 && res.data) {
      info.value = res.data
    } else {
      ElMessage.error(res.msg || '加载销售订单失败')
    }
  } catch {
    ElMessage.error('加载销售订单失败')
  } finally {
    loading.value = false
  }
}

function handlePrint() {
  window.print()
}

onMounted(loadData)
</script>

<style scoped>
.order-confirm-print-page {
  min-height: 100vh;
  background: #eef0f3;
  padding: 20px;
}

.print-toolbar {
  max-width: 794px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-tip {
  font-size: 14px;
  color: #606266;
}

.doc-title {
  text-align: center;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 8px;
  margin: 14px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #2b5aa7;
}

.doc-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 24px;
  margin-bottom: 12px;
  font-size: 11px;
}

.info-item {
  display: flex;
}

.info-item-wide {
  grid-column: 1 / -1;
}

.info-label {
  width: 70px;
  color: #888;
  flex-shrink: 0;
}

.doc-items {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
  margin-bottom: 10px;
}

.doc-items th {
  background: #2b5aa7;
  color: #fff;
  padding: 6px 4px;
  font-weight: 600;
  border: 1px solid #2b5aa7;
}

.doc-items td {
  border: 1px solid #dcdfe6;
  padding: 5px 4px;
}

.doc-items tr:nth-child(even) td {
  background: #f7f9fc;
}

.col-center {
  text-align: center;
}

.col-right {
  text-align: right;
}

.col-spec {
  font-size: 10px;
}

.doc-amounts {
  width: 45%;
  margin-left: auto;
  margin-bottom: 12px;
  font-size: 11px;
}

.amount-row {
  display: flex;
  justify-content: space-between;
  padding: 3px 8px;
  border: 1px solid #dcdfe6;
}

.amount-row + .amount-row {
  border-top: none;
}

.amount-total {
  background: #2b5aa7;
  color: #fff;
  font-weight: 700;
  font-size: 13px;
}

.doc-remark {
  font-size: 10px;
  color: #555;
  margin-bottom: 20px;
}

.doc-signs {
  display: flex;
  justify-content: space-between;
  margin-top: 40px;
  padding: 0 20px;
}

.sign-item {
  width: 30%;
  text-align: center;
  font-size: 11px;
}

.sign-line {
  padding-bottom: 4px;
}

.sign-underline {
  border-bottom: 1px solid #999;
}

@media print {
  .no-print {
    display: none !important;
  }

  .order-confirm-print-page {
    padding: 0;
    background: #fff;
  }
}
</style>

<template>
  <div class="print-page">
    <PrintToolbar :title="`送货单打印-${info?.deliveryNo || ''}`">
      <template #actions>
        <el-radio-group :model-value="layout" size="small" @change="handleLayoutChange">
          <el-radio-button value="system">系统版</el-radio-button>
          <el-radio-button value="qr026">纸版(QR-026)</el-radio-button>
        </el-radio-group>
        <el-button type="primary" icon="Printer" @click="print">打印</el-button>
      </template>
    </PrintToolbar>

    <A4Canvas v-if="info" :padding-mm="15">
      <PrintQrCode :text="info.deliveryNo || ''" :size="64" />

      <template v-if="layout === 'system'">
        <PrintCompanyHeader variant="center" />
        <h1>送 货 单</h1>
        <div class="info">
          <div>单号：{{ info.deliveryNo }}</div>
          <div>日期：{{ info.deliveryDate || '-' }}</div>
          <div>客户：{{ info.customerName || '-' }}</div>
          <div>收货地址：{{ info.deliveryAddress || '-' }}</div>
          <div>联系人：{{ info.contactPerson || '-' }}</div>
          <div>联系电话：{{ info.contactPhone || '-' }}</div>
        </div>
        <table>
          <thead>
            <tr>
              <th>序号</th>
              <th>品名(料号)</th>
              <th>规格</th>
              <th>单位</th>
              <th>数量</th>
              <th>单价</th>
              <th>金额</th>
              <th>订单号码</th>
              <th>备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in items" :key="item.id">
              <td class="center">{{ index + 1 }}</td>
              <td>{{ item.productName || item.productCode || '-' }}</td>
              <td>{{ item.specification || '-' }}</td>
              <td>{{ item.unit || '-' }}</td>
              <td class="right">{{ item.quantity ?? '-' }}</td>
              <td class="right">{{ money(item.unitPrice) }}</td>
              <td class="right">{{ money(item.amount) }}</td>
              <td>{{ orderNo || '-' }}</td>
              <td>{{ item.remark || item.lineRemark || '-' }}</td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="9" class="center">无订单明细</td>
            </tr>
          </tbody>
        </table>
        <div class="amount">
          <b>合计金额：{{ money(info.totalAmount) }}</b>
        </div>
        <div class="sign">
          <span>收货人：{{ info.receiverName || '________________' }}</span
          ><span>日期：{{ receiveDate }}</span>
        </div>
      </template>

      <section v-else class="qr026-layout">
        <header class="qr026-company-header">
          <div class="qr026-company-name">{{ company.name || '-' }}</div>
          <div class="qr026-company-address">地址：{{ company.address || '-' }}</div>
        </header>
        <div class="qr026-title-row">
          <div class="qr026-title">送&nbsp;&nbsp;货&nbsp;&nbsp;单</div>
          <div class="qr026-document-info">
            <div>NO: {{ info.deliveryNo || '-' }}</div>
            <div>DATE: {{ info.deliveryDate || '-' }}</div>
          </div>
        </div>
        <div class="qr026-recipient">
          <div>TO: {{ info.customerName || '-' }}</div>
          <div>
            Attm: {{ info.contactPerson || '-'
            }}<span v-if="info.contactPhone">&nbsp;&nbsp;{{ info.contactPhone }}</span>
          </div>
        </div>
        <table class="qr026-items">
          <thead>
            <tr>
              <th style="width: 5%">NO</th>
              <th style="width: 18%">品名(料号)</th>
              <th style="width: 14%">规格</th>
              <th style="width: 7%">单位</th>
              <th style="width: 9%">数量</th>
              <th style="width: 10%">单价</th>
              <th style="width: 11%">金额</th>
              <th style="width: 15%">订单号码</th>
              <th style="width: 11%">备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in items" :key="item.id">
              <td class="center">{{ index + 1 }}</td>
              <td>{{ item.productName || item.productCode || '-' }}</td>
              <td>{{ item.specification || '-' }}</td>
              <td class="center">{{ item.unit || '-' }}</td>
              <td class="right">{{ item.quantity ?? '-' }}</td>
              <td class="right">{{ money(item.unitPrice) }}</td>
              <td class="right">{{ money(item.amount) }}</td>
              <td>{{ orderNo || '-' }}</td>
              <td>{{ item.remark || item.lineRemark || '-' }}</td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="9" class="center">无订单明细</td>
            </tr>
            <tr class="qr026-total-row">
              <td colspan="6"></td>
              <th>合计金额：</th>
              <td colspan="2" class="right">{{ money(info.totalAmount) }}</td>
            </tr>
          </tbody>
        </table>
        <div class="qr026-terms">
          如上列货品有不符问题，请在10天内通知。方便我司处理，过期恕不负责。
        </div>
        <div class="qr026-signatures">
          <div>送货单位经手人：<span class="qr026-sign-line"></span></div>
          <div>收货单位经手人：<span class="qr026-sign-line"></span></div>
        </div>
        <div class="qr026-footer-company">{{ company.name || '-' }}</div>
      </section>
    </A4Canvas>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import PrintQrCode from '@/components/print/PrintQrCode.vue'
import PrintToolbar from '@/components/print/PrintToolbar.vue'
import { deliveryApi, type SalesDeliveryVO } from '@/api/sales/delivery'
import { orderApi } from '@/api/sales/order'
import { useCompanyConfig } from '@/composables/useCompanyConfig'
import { usePrintLayout, usePrintLog } from '@/composables/usePrint'

type PrintLayout = 'system' | 'qr026'
type DeliveryItem = Record<string, any>
const money = (value?: number) =>
  value == null ? '-' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

const route = useRoute()
const info = ref<SalesDeliveryVO>()
const items = ref<DeliveryItem[]>([])
const orderNo = ref('')
const deliveryId = Number(route.query.deliveryId)
const { company } = useCompanyConfig()
const { layout, setLayout } = usePrintLayout<PrintLayout>('delivery-print-layout', [
  { value: 'system', label: '系统版' },
  { value: 'qr026', label: '纸版(QR-026)' },
])
const { log: logPrint } = usePrintLog('sales_delivery')
const receiveDate = computed(() => info.value?.receiveTime?.slice(0, 10) || '________________')

function handleLayoutChange(value: string | number | boolean | undefined) {
  if (value === 'system' || value === 'qr026') setLayout(value)
}
async function print() {
  try {
    await logPrint(deliveryId)
    window.print()
  } catch {
    ElMessage.error('打印留痕失败，请重试')
  }
}

onMounted(async () => {
  if (!deliveryId) {
    ElMessage.error('发货单ID缺失')
    return
  }
  const detail = await deliveryApi.getById(deliveryId)
  info.value = detail.data || undefined
  if (info.value?.orderId) {
    try {
      const order = await orderApi.getOrder(info.value.orderId)
      items.value = order.data?.items || []
      orderNo.value = order.data?.orderNo || ''
    } catch {
      items.value = []
      ElMessage.warning('订单明细加载失败，可继续打印')
    }
  }
})
</script>

<style scoped>
.print-page {
  min-height: 100vh;
  background: #eef0f3;
  padding: 20px;
}
.print-page :deep(.a4-canvas) {
  position: relative;
}
h1 {
  text-align: center;
  letter-spacing: 12px;
  border-bottom: 2px solid #2b5aa7;
  padding-bottom: 10px;
}
.info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  font-size: 11px;
  margin: 14px 0;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}
th,
td {
  border: 1px solid #bbb;
  padding: 7px;
}
th {
  background: #2b5aa7;
  color: #fff;
}
.right {
  text-align: right;
}
.center {
  text-align: center;
}
.amount {
  text-align: right;
  margin-top: 16px;
}
.sign {
  display: flex;
  justify-content: space-between;
  margin-top: 65px;
  font-size: 12px;
}
.qr026-layout {
  position: relative;
  color: #000;
  font-family: SimSun, '宋体', serif;
  font-size: 11px;
}
.qr026-company-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  min-height: 30px;
  padding-right: 72px;
  line-height: 1.4;
}
.qr026-company-name {
  font-size: 16px;
  font-weight: 700;
}
.qr026-company-address {
  max-width: 42%;
  text-align: right;
}
.qr026-title-row {
  position: relative;
  min-height: 47px;
  margin-top: 4px;
}
.qr026-title {
  padding-top: 6px;
  text-align: center;
  font-size: 22px;
  font-weight: 700;
}
.qr026-document-info {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 185px;
  font-size: 12px;
  line-height: 1.55;
}
.qr026-recipient {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin: 5px 0;
  font-size: 12px;
  line-height: 1.5;
}
.qr026-items {
  table-layout: fixed;
  font-size: 10px;
}
.qr026-items th,
.qr026-items td {
  height: 23px;
  padding: 3px 4px;
  border: 1px solid #000;
  color: #000;
  background: transparent;
  overflow-wrap: anywhere;
}
.qr026-items th {
  text-align: center;
  font-size: 11px;
  font-weight: 700;
}
.qr026-total-row th,
.qr026-total-row td {
  height: 25px;
}
.qr026-terms {
  margin-top: 8px;
  font-size: 11px;
  line-height: 1.5;
}
.qr026-signatures {
  display: flex;
  justify-content: space-between;
  margin-top: 30px;
  font-size: 12px;
}
.qr026-sign-line {
  display: inline-block;
  width: 100px;
  border-bottom: 1px solid #000;
}
.qr026-footer-company {
  margin-top: 28px;
  text-align: center;
  font-size: 13px;
  font-weight: 700;
}
@media print {
  .print-page {
    padding: 0;
    background: #fff;
  }
}
</style>

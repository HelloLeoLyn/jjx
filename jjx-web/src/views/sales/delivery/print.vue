<template>
  <div class="print-page">
    <PrintToolbar :title="`送货单打印-${info?.deliveryNo || ''}`">
      <template #actions>
        <el-radio-group :model-value="layout" size="small" @change="handleLayoutChange">
          <el-radio-button value="qr026">纸版(QR-026)</el-radio-button>
          <el-radio-button value="triplicate">三联纸(241×140)</el-radio-button>
        </el-radio-group>
        <el-button icon="Download" :loading="exporting" :disabled="!info" @click="exportExcel">导出Excel</el-button>
        <el-button type="primary" icon="Printer" @click="print">打印</el-button>
      </template>
    </PrintToolbar>

    <Qr026DeliverySheet
      v-if="info && layout === 'qr026'"
      :info="info"
      :items="items"
      :order-no="orderNo"
      :company="company"
    />
    <main v-else-if="info && layout === 'triplicate'" class="triplicate-pages">
      <article v-for="(pageItems, pageIndex) in triplicatePages" :key="pageIndex" class="triplicate-page">
        <div class="triplicate-company-name">{{ company.name || '-' }}</div>
        <div class="triplicate-title-row">
          <div>送　货　单</div>
          <div>地址：{{ company.address || '-' }}</div>
        </div>
        <div class="triplicate-field-row">
          <div>TO: {{ info.customerName || '-' }}</div>
          <div>NO: {{ info.deliveryNo || '-' }}</div>
        </div>
        <div class="triplicate-field-row">
          <div>
            Attm: {{ info.contactPerson || '-'
            }}<span v-if="info.contactPhone">&nbsp;&nbsp;{{ info.contactPhone }}</span>
          </div>
          <div>DATE: {{ info.deliveryDate || '-' }}</div>
        </div>
        <table class="triplicate-items">
          <thead>
            <tr>
              <th>NO</th><th>品名(料号)</th><th>规格</th><th>单位</th><th>数量</th>
              <th>单价</th><th>金额</th><th>订单号码</th><th>备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, itemIndex) in pageItems" :key="item.id || `${pageIndex}-${itemIndex}`">
              <td class="center">{{ pageIndex * 6 + itemIndex + 1 }}</td>
              <td>{{ item.customerMaterialNo || item.productName || item.productCode || '-' }}</td>
              <td>{{ item.specification || '-' }}</td>
              <td class="center">{{ item.unit || '-' }}</td>
              <td class="right">{{ item.quantity ?? '-' }}</td>
              <td class="right">{{ money(item.unitPrice) }}</td>
              <td class="right">{{ money(item.amount) }}</td>
              <td>{{ item.orderNo || orderNo || '-' }}</td>
              <td>{{ item.remark || item.lineRemark || '-' }}</td>
            </tr>
            <tr v-if="!items.length"><td colspan="9" class="center">无订单明细</td></tr>
          </tbody>
        </table>
        <template v-if="pageIndex === triplicatePages.length - 1">
          <div v-if="info.freightAmount" class="triplicate-total-row">
            <span>运费：</span><span>{{ money(info.freightAmount) }}</span>
          </div>
          <div class="triplicate-total-row">
            <span>合计金额：</span><span>{{ money(info.totalAmount) }}</span>
          </div>
          <div class="triplicate-terms">
            如上列貨品有不符问题，请在10天内通知。方便我司处理，过期恕不负责。
          </div>
          <div class="triplicate-signatures">
            <div>送货单位经手人：<span class="triplicate-sign-line"></span></div>
            <div>收货单位经手人：<span class="triplicate-sign-line"></span></div>
          </div>
          <div class="triplicate-footer-company">{{ company.name || '-' }}</div>
        </template>
      </article>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import Qr026DeliverySheet from './Qr026DeliverySheet.vue'
import PrintToolbar from '@/components/print/PrintToolbar.vue'
import { deliveryApi, type SalesDeliveryVO } from '@/api/sales/delivery'
import { orderApi } from '@/api/sales/order'
import { useCompanyConfig } from '@/composables/useCompanyConfig'
import { usePrintLayout } from '@/composables/usePrint'
import { download } from '@/utils/format'

type PrintLayout = 'qr026' | 'triplicate'
type DeliveryItem = Record<string, any>
const money = (value?: number) =>
  value == null ? '-' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

const route = useRoute()
const info = ref<SalesDeliveryVO>()
const items = ref<DeliveryItem[]>([])
const orderNo = ref('')
const exporting = ref(false)
const deliveryId = Number(route.query.deliveryId)
const { company } = useCompanyConfig()
const { layout, setLayout } = usePrintLayout<PrintLayout>('delivery-print-layout', [
  { value: 'qr026', label: '纸版(QR-026)' },
  { value: 'triplicate', label: '三联纸(241×140)' },
])
const triplicatePages = computed(() => {
  const rows = items.value
  if (!rows.length) return [[]]
  const pages: DeliveryItem[][] = []
  for (let index = 0; index < rows.length; index += 6) pages.push(rows.slice(index, index + 6))
  return pages
})

function handleLayoutChange(value: string | number | boolean | undefined) {
  if (value === 'qr026' || value === 'triplicate') setLayout(value)
}
async function exportExcel() {
  if (!info.value || exporting.value) return
  exporting.value = true
  try {
    const blob = await deliveryApi.exportExcel(deliveryId)
    // 全局拦截器直接透传 Blob；业务错误可能仍是 HTTP 200 的 JSON，不能下载成损坏的 Excel。
    if (blob.type.includes('json')) {
      const error = JSON.parse(await blob.text())
      throw new Error(error.msg || '送货单导出失败')
    }
    if (!blob.size) throw new Error('导出文件为空，请重试')
    const deliveryNo = info.value.deliveryNo.replace(/[\\/:*?"<>|]/g, '_')
    download(blob, `送货单_${deliveryNo}.xlsx`)
    ElMessage.success('Excel已导出，可自行调整格式和打印设置')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '送货单导出失败')
  } finally {
    exporting.value = false
  }
}
async function print() {
  await nextTick()
  await document.fonts.ready
  // 口径 D3：打印必留痕（谁/何时/第几次/哪张单）；留痕失败不阻断打印本身
  try {
    await deliveryApi.printLog(deliveryId)
  } catch {
    ElMessage.warning('打印留痕失败（不影响打印）')
  }
  window.print()
}

onMounted(async () => {
  if (!deliveryId) {
    ElMessage.error('发货单ID缺失')
    return
  }
  const detail = await deliveryApi.getById(deliveryId)
  info.value = detail.data || undefined
  // 分批发货（2026-09-21 dev-20260921-039）：送货单只打印「本次发货明细」，不再从订单带全量明细
  const deliveryItems: any[] = detail.data?.items || []
  if (deliveryItems.length) {
    items.value = deliveryItems
  }
  if (info.value?.orderId) {
    try {
      const order = await orderApi.getOrder(info.value.orderId)
      if (!deliveryItems.length) {
        items.value = order.data?.items || []
      }
      orderNo.value = order.data?.orderNo || ''
    } catch {
      if (!deliveryItems.length) {
        items.value = []
      }
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
table { width: 100%; border-collapse: collapse; font-size: 11px; }
th, td { border: 1px solid #bbb; padding: 7px; }
th { background: var(--doc-theme, #2b5aa7); color: #fff; }
.center { text-align: center; }
.right { text-align: right; }
.triplicate-pages {
  color: #000;
}
.triplicate-page {
  width: 241mm;
  min-height: 140mm;
  box-sizing: border-box;
  padding: 3mm 12mm 4mm;
  background: #fff;
  color: #000;
  font-family: SimSun, '宋体', serif;
  font-size: 2.2mm;
  line-height: 3mm;
  page-break-after: always;
  break-after: page;
}
.triplicate-page:last-child {
  page-break-after: auto;
  break-after: auto;
}
.triplicate-company-name {
  height: 5mm;
  font-size: 3.2mm;
  line-height: 4mm;
  font-weight: 700;
}
.triplicate-title-row,
.triplicate-field-row {
  display: flex;
  justify-content: space-between;
  gap: 6mm;
  min-height: 4mm;
  line-height: 4mm;
}
.triplicate-title-row {
  font-size: 3.2mm;
  font-weight: 700;
}
.triplicate-title-row > div:last-child,
.triplicate-field-row > div:last-child {
  text-align: right;
}
.triplicate-items {
  width: 100%;
  margin-top: 2mm;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 2.2mm;
  line-height: 3mm;
}
.triplicate-items th,
.triplicate-items td {
  height: 6mm;
  box-sizing: border-box;
  padding: 1mm;
  border: .25mm solid #000;
  overflow-wrap: anywhere;
}
.triplicate-items th {
  height: 7mm;
  text-align: center;
  font-weight: 700;
}
.triplicate-items th:nth-child(1) { width: 6%; }
.triplicate-items th:nth-child(2) { width: 18%; }
.triplicate-items th:nth-child(3) { width: 14%; }
.triplicate-items th:nth-child(4) { width: 7%; }
.triplicate-items th:nth-child(5) { width: 9%; }
.triplicate-items th:nth-child(6) { width: 10%; }
.triplicate-items th:nth-child(7) { width: 11%; }
.triplicate-items th:nth-child(8) { width: 15%; }
.triplicate-items th:nth-child(9) { width: 10%; }
.triplicate-total-row {
  display: flex;
  justify-content: flex-end;
  gap: 2mm;
  min-height: 6mm;
  padding: 1mm;
  box-sizing: border-box;
  border: .25mm solid #000;
  border-top: 0;
  line-height: 4mm;
}
.triplicate-total-row span:last-child {
  width: 25mm;
  text-align: right;
}
.triplicate-terms {
  margin-top: 2mm;
  line-height: 4mm;
}
.triplicate-signatures {
  display: flex;
  justify-content: space-between;
  margin-top: 5mm;
  line-height: 4mm;
}
.triplicate-sign-line {
  display: inline-block;
  width: 25mm;
  border-bottom: .25mm solid #000;
}
.triplicate-footer-company {
  margin-top: 5mm;
  text-align: center;
  font-size: 2.8mm;
  font-weight: 700;
}
@media print {
  .print-page {
    padding: 0;
    background: #fff;
  }
  @page { size: auto; margin: 0; }
  html, body, #app {
    margin: 0 !important;
    padding: 0 !important;
    background: #fff !important;
  }
  body * { visibility: hidden; }
  .triplicate-page, .triplicate-page * { visibility: visible; }
  .no-print { display: none !important; }
  .triplicate-pages { padding: 0; }
  .triplicate-page { position: absolute; inset: 0; }
}
</style>

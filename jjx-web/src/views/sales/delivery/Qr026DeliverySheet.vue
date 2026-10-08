<template>
  <main class="qr026-sheets">
    <article v-for="(pageItems, pageIndex) in pages" :key="pageIndex" class="qr026-sheet">
      <header class="qr026-header">
        <div class="qr026-company-name">{{ company.name || '-' }}</div>
        <div class="qr026-code"><PrintQrCode :text="info.deliveryNo || ''" :size="56" /></div>
      </header>
      <div class="qr026-title-row">
        <h1>送　货　单</h1>
        <div class="qr026-company-address">地址：{{ company.address || '-' }}</div>
      </div>
      <div class="qr026-fields">
        <div class="qr026-customer">TO: {{ info.customerName || '-' }}</div>
        <div class="qr026-number">NO: {{ info.deliveryNo || '-' }}</div>
        <div class="qr026-contact">
          Attm: {{ info.contactPerson || '-' }}<span v-if="info.contactPhone">　{{ info.contactPhone }}</span>
        </div>
        <div class="qr026-date">DATE: {{ info.deliveryDate || '-' }}</div>
      </div>
      <table class="qr026-table">
        <colgroup>
          <col v-for="(width, index) in columnWidths" :key="index" :style="{ width: `${width}%` }" />
        </colgroup>
        <thead>
          <tr><th>NO</th><th>品名(料号)</th><th>规格</th><th>单位</th><th>数量</th><th>单价</th><th>金额</th><th>订单号码</th><th>备注</th></tr>
        </thead>
        <tbody>
          <tr v-for="(item, itemIndex) in pageItems" :key="item?.itemId ?? `${pageIndex}-${itemIndex}`" class="qr026-item-row">
            <td class="qr026-center">{{ item ? pageIndex * ROWS_PER_PAGE + itemIndex + 1 : '' }}</td>
            <td>{{ item ? item.productName || item.productCode || '-' : '' }}</td>
            <td>{{ item?.specification || '' }}</td>
            <td class="qr026-center">{{ item?.unit || '' }}</td>
            <td class="qr026-right">{{ item?.quantity ?? '' }}</td>
            <td class="qr026-right">{{ item ? money(item.unitPrice) : '' }}</td>
            <td class="qr026-right">{{ item ? money(item.amount) : '' }}</td>
            <td>{{ item ? orderNo || '-' : '' }}</td>
            <td>{{ item?.remark || item?.lineRemark || '' }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="!items.length" class="qr026-empty">无订单明细</div>
      <section v-if="pageIndex === pages.length - 1" class="qr026-summary">
        <div class="qr026-totals">
          <div v-if="info.freightAmount"><span>运费：</span><strong>{{ money(info.freightAmount) }}</strong></div>
          <div><span>合计金额：</span><strong>{{ money(info.totalAmount) }}</strong></div>
        </div>
        <p class="qr026-terms">如上列貨品有不符问题，请在10天内通知。方便我司处理，过期恕不负责。</p>
        <div class="qr026-signatures">
          <div>送货单位经手人：<span></span></div>
          <div>收货单位经手人：<span></span></div>
        </div>
      </section>
      <div v-else class="qr026-continuation">明细续下页</div>
    </article>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import PrintQrCode from '@/components/print/PrintQrCode.vue'
import type { SalesDeliveryVO, SalesDeliveryItem } from '@/api/sales/delivery'

type PrintableItem = SalesDeliveryItem & { lineRemark?: string }
const props = defineProps<{
  info: SalesDeliveryVO
  items: PrintableItem[]
  orderNo: string
  company: { name?: string; address?: string }
}>()
const ROWS_PER_PAGE = 5
// 对照 Excel A:I 列宽，表头和明细共用同一栏位。
const columnWidths = [7, 16, 13, 9, 10, 10, 10, 15, 10]
const money = (value?: number) => value == null ? '-' : Number(value).toLocaleString('zh-CN', {
  minimumFractionDigits: 2, maximumFractionDigits: 2,
})
const pages = computed(() => {
  const result: Array<Array<PrintableItem | null>> = []
  for (let start = 0; start < Math.max(props.items.length, 1); start += ROWS_PER_PAGE) {
    const rows: Array<PrintableItem | null> = props.items.slice(start, start + ROWS_PER_PAGE)
    while (rows.length < ROWS_PER_PAGE) rows.push(null)
    result.push(rows)
  }
  return result
})
</script>

<style scoped>
.qr026-sheets { color: #000; }
.qr026-sheet {
  --qr026-columns: 7% 16% 13% 9% 10% 10% 10% 15% 10%;
  width: 210mm;
  min-height: 297mm;
  box-sizing: border-box;
  padding: 12mm 15mm;
  margin: 0 auto 6mm;
  background: #fff;
  box-shadow: 0 2px 12px #0002;
  font-family: SimSun, '宋体', 'Songti SC', 'Noto Serif CJK SC', serif;
  font-size: 11pt;
  line-height: 1.35;
}
.qr026-sheet * { box-sizing: border-box; }
.qr026-header {
  display: grid;
  grid-template-columns: 15mm minmax(0, 1fr) 15mm;
  align-items: center;
  min-height: 15mm;
}
.qr026-company-name {
  grid-column: 2;
  text-align: center;
  font-size: 20pt;
  font-weight: 400;
  line-height: 1.2;
  overflow-wrap: anywhere;
}
.qr026-code { grid-column: 3; justify-self: end; }
.qr026-code :deep(.print-qrcode) {
  display: block;
  position: static;
  width: 14mm !important;
  height: 14mm !important;
  border: 0;
  padding: 0;
}
.qr026-title-row, .qr026-fields { display: grid; grid-template-columns: var(--qr026-columns); }
.qr026-title-row { align-items: center; min-height: 12mm; margin: 2mm 0; }
.qr026-title-row h1 {
  grid-column: 4 / 6;
  margin: 0;
  font-size: 16pt;
  font-weight: 700;
  line-height: 1.25;
  text-align: center;
  white-space: nowrap;
}
.qr026-company-address { grid-column: 7 / 10; overflow-wrap: anywhere; }
.qr026-fields { row-gap: 1mm; margin-bottom: 2mm; align-items: start; }
.qr026-customer, .qr026-contact { grid-column: 1 / 8; padding-right: 3mm; overflow-wrap: anywhere; }
.qr026-number, .qr026-date { grid-column: 8 / 10; overflow-wrap: anywhere; }
.qr026-table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 11pt; line-height: 1.25; }
.qr026-table th, .qr026-table td {
  border: .25mm solid #000;
  padding: 1mm;
  vertical-align: middle;
  overflow-wrap: anywhere;
  color: #000;
  background: transparent;
}
.qr026-table th { height: 12mm; font-weight: 400; text-align: center; }
.qr026-item-row { height: 9.5mm; }
.qr026-center { text-align: center; }
.qr026-right { text-align: right; font-variant-numeric: tabular-nums; }
.qr026-empty { margin-top: 2mm; }
.qr026-totals { border: .25mm solid #000; border-top: 0; }
.qr026-totals > div { display: flex; justify-content: flex-end; align-items: center; min-height: 8mm; padding: 1mm 2mm; gap: 2mm; }
.qr026-totals > div + div { border-top: .25mm solid #000; }
.qr026-totals strong { min-width: 35mm; font-weight: 400; text-align: right; font-variant-numeric: tabular-nums; }
.qr026-terms { margin: 4mm 0; }
.qr026-signatures { display: grid; grid-template-columns: 1fr 1fr; gap: 6mm; margin-top: 5mm; }
.qr026-signatures > div { display: flex; align-items: baseline; white-space: nowrap; }
.qr026-signatures span { flex: 1; min-width: 12mm; height: 5mm; border-bottom: .25mm solid #000; }
.qr026-continuation { margin-top: 3mm; text-align: right; }
@page qr026Delivery { size: A4 portrait; margin: 12mm 15mm; }
@media print {
  .qr026-sheet {
    page: qr026Delivery;
    width: auto;
    min-height: 0;
    margin: 0;
    padding: 0;
    box-shadow: none;
    break-after: page;
  }
  .qr026-sheet:last-child { break-after: auto; }
  .qr026-header, .qr026-title-row, .qr026-fields, .qr026-summary, .qr026-totals, .qr026-terms, .qr026-signatures { break-inside: avoid; }
  .qr026-table thead { display: table-header-group; }
  .qr026-table tr { break-inside: avoid; }
}
</style>

<style>
/* 仅在 QR-026 存在时清除应用外壳的打印占位，不影响其他单据。 */
@media print {
  body:has(.qr026-sheets) { margin: 0 !important; padding: 0 !important; background: #fff !important; }
  body:has(.qr026-sheets) > :not(#app) { display: none !important; }
  body:has(.qr026-sheets) #app,
  body:has(.qr026-sheets) #app *:has(.qr026-sheets) {
    display: block !important;
    position: static !important;
    width: auto !important;
    height: auto !important;
    min-height: 0 !important;
    margin: 0 !important;
    padding: 0 !important;
    border: 0 !important;
    overflow: visible !important;
    transform: none !important;
    background: #fff !important;
  }
  body:has(.qr026-sheets) #app *:not(:has(.qr026-sheets)):not(.qr026-sheets):not(.qr026-sheets *) { display: none !important; }
}
</style>

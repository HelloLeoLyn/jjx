<template>
  <main class="qr026-sheets" :class="{ 'qr026-preview': preview }">
    <article v-for="(pageItems, pageIndex) in pages" :key="pageIndex" class="qr026-sheet" :class="{ 'qr026-triplicate': paperFormat === 'triplicate' }">
      <header class="qr026-header">
        <h3>{{ company.name || '—' }}</h3>
        <div v-if="company.phone || company.fax" class="qr026-company-contact">
          <span v-if="company.phone">TEL：{{ company.phone }}</span>
          <span v-if="company.fax">FAX：{{ company.fax }}</span>
        </div>
      </header>
      <div class="qr026-title-row"><h1>送　货　单</h1><span>编号：JJX-QR-026</span></div>
      <div class="qr026-fields">
        <div>TO：{{ info.customerName || '—' }}<br />ATTN：{{ info.contactPerson || '—' }} <span v-if="info.contactPhone">{{ info.contactPhone }}</span></div>
        <div>NO：{{ info.deliveryNo || '创建后生成' }}<br />DATE：{{ info.deliveryDate || '—' }}</div>
      </div>
      <table class="qr026-table">
        <colgroup><col v-for="(width, index) in columnWidths" :key="index" :style="{ width: `${width}%` }" /></colgroup>
        <thead><tr><th>NO</th><th>物料料号</th><th>品名规格</th><th>单位</th><th>数量</th><th>销售单号</th><th v-if="showAmount">金额</th><th>订单号码</th><th v-if="showWeight">单重 g</th></tr></thead>
        <tbody>
          <tr v-for="(item, itemIndex) in pageItems" :key="item?.itemId ?? `${pageIndex}-${itemIndex}`" class="qr026-item-row">
            <td>{{ item ? pageIndex * ROWS_PER_PAGE + itemIndex + 1 : '' }}</td>
            <td>{{ item ? item.customerMaterialNo || item.productName || item.productCode || '—' : '' }}</td>
            <td>{{ item ? productDescription(item) : '' }}</td>
            <td>{{ item?.unit || '' }}</td>
            <td class="qr026-number">{{ item?.quantity ?? '' }}</td>
            <td>{{ item ? item.orderNo || orderNo || '' : '' }}</td>
            <td v-if="showAmount" class="qr026-number">{{ item ? money(item.amount) : '' }}</td>
            <td>{{ item?.customerOrderNo || '' }}</td>
            <td v-if="showWeight" class="qr026-number">{{ item?.unitWeight ?? '' }}</td>
          </tr>
          <tr v-if="!items.length"><td :colspan="columnWidths.length" class="qr026-empty">请先选择本次发货明细</td></tr>
        </tbody>
      </table>
      <section v-if="pageIndex === pages.length - 1" class="qr026-summary">
        <p class="qr026-remark">备注：{{ info.remark || '以上货品有不符问题，请在10天内通知，方便我司处理，过期恕不负责。' }}</p>
        <div class="qr026-signatures">
          <div>送货单位经手人：<span>{{ info.deliveryPersonName || '' }}</span></div>
          <div>收货单位经手人：<span>{{ info.receiverName || '' }}</span></div>
        </div>
      </section>
      <div v-if="pages.length > 1" class="qr026-page-number">第 {{ pageIndex + 1 }} / {{ pages.length }} 页</div>
    </article>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SalesDeliveryVO, SalesDeliveryItem } from '@/api/sales/delivery'

const props = withDefaults(defineProps<{
  info: Partial<SalesDeliveryVO>
  items: SalesDeliveryItem[]
  orderNo?: string
  company: { name?: string; address?: string; phone?: string; fax?: string }
  showAmount?: boolean
  showWeight?: boolean
  preview?: boolean
  paperFormat?: 'a4' | 'triplicate'
}>(), { orderNo: '', showAmount: false, showWeight: true, preview: false, paperFormat: 'a4' })
const ROWS_PER_PAGE = 6
const columnWidths = computed(() => {
  const widths = [5, 17, 23, 6, 8, 14, ...(props.showAmount ? [9] : []), 13, ...(props.showWeight ? [7] : [])]
  const total = widths.reduce((sum, width) => sum + width, 0)
  return widths.map(width => width / total * 100)
})
const productDescription = (item: SalesDeliveryItem) => [item.productName, item.specification]
  .filter((value, index, values) => !!value && values.indexOf(value) === index).join(' ')
const money = (value?: number) => value == null ? '' : Number(value).toLocaleString('zh-CN', {
  minimumFractionDigits: 2, maximumFractionDigits: 2,
})
const pages = computed(() => {
  const result: Array<Array<SalesDeliveryItem | null>> = []
  for (let start = 0; start < Math.max(props.items.length, 1); start += ROWS_PER_PAGE) {
    const rows: Array<SalesDeliveryItem | null> = props.items.slice(start, start + ROWS_PER_PAGE)
    if (props.items.length) while (rows.length < ROWS_PER_PAGE) rows.push(null)
    result.push(rows)
  }
  return result
})
</script>

<style scoped>
.qr026-sheets { color:#222; }
.qr026-sheet { width:210mm; min-height:297mm; box-sizing:border-box; padding:12mm 15mm; margin:0 auto 16px; background:#fff; color:#222; box-shadow:0 2px 12px #0002; font-family:SimSun,'宋体','Songti SC','Noto Serif CJK SC',serif; font-size:12px; line-height:1.7; }
.qr026-sheet * { box-sizing:border-box; }
.qr026-header h3 { text-align:center; font-size:20px; font-weight:600; margin:0 0 5px; line-height:1.4; overflow-wrap:anywhere; }
.qr026-company-contact { display:flex; justify-content:center; gap:20px; font-size:11px; }
.qr026-title-row { position:relative; margin:14px 0; }
.qr026-title-row h1 { text-align:center; font-size:22px; margin:0; font-weight:600; }
.qr026-title-row > span { position:absolute; right:0; bottom:0; font-size:12px; }
.qr026-fields { display:flex; justify-content:space-between; gap:12px; margin-bottom:10px; }
.qr026-fields > div { overflow-wrap:anywhere; }
.qr026-fields > div:last-child { flex-shrink:0; }
.qr026-table { width:100%; border-collapse:collapse; table-layout:fixed; font-size:12px; line-height:1.5; }
.qr026-table th,.qr026-table td { border:1px solid #555; padding:9px 5px; text-align:center; vertical-align:middle; overflow-wrap:anywhere; background:#fff; color:#222; }
.qr026-table th { font-weight:600; white-space:nowrap; }
.qr026-item-row { height:36px; }
.qr026-table .qr026-number { text-align:right; font-variant-numeric:tabular-nums; }
.qr026-table .qr026-empty { height:70px; color:#888; }
.qr026-remark { border:1px solid #555; border-top:0; margin:0; padding:9px; }
.qr026-signatures { display:flex; justify-content:space-between; gap:20px; margin-top:18px; }
.qr026-signatures > div { display:flex; align-items:baseline; flex:1; white-space:nowrap; }
.qr026-signatures span { flex:1; min-width:40px; border-bottom:1px solid #555; }
.qr026-page-number { text-align:right; color:#666; font-size:11px; margin-top:16px; }
.qr026-preview .qr026-sheet { width:100%; min-width:760px; min-height:0; padding:24px 22px; box-shadow:none; }
.qr026-triplicate { width:241mm; min-height:140mm; padding:3mm 12mm 4mm; font-size:2.2mm; line-height:3mm; }
.qr026-triplicate .qr026-header h3 { font-size:3.2mm; margin-bottom:1mm; }
.qr026-triplicate .qr026-company-contact { font-size:2mm; }
.qr026-triplicate .qr026-title-row { margin:2mm 0; }
.qr026-triplicate .qr026-title-row h1 { font-size:3.2mm; }
.qr026-triplicate .qr026-title-row > span { font-size:2.2mm; }
.qr026-triplicate .qr026-fields { margin-bottom:2mm; }
.qr026-triplicate .qr026-table { font-size:2.2mm; line-height:3mm; }
.qr026-triplicate .qr026-table th,.qr026-triplicate .qr026-table td { padding:1mm; }
.qr026-triplicate .qr026-item-row { height:6mm; }
.qr026-triplicate .qr026-remark { padding:1mm; }
.qr026-triplicate .qr026-signatures { margin-top:5mm; }
.qr026-triplicate .qr026-page-number { font-size:2mm; margin-top:2mm; }
@page qr026Delivery { size:A4 portrait; margin:12mm 15mm; }
@page qr026Triplicate { size:241mm 140mm; margin:3mm 12mm 4mm; }
@media print {
  .qr026-sheet { page:qr026Delivery; width:auto; min-height:0; margin:0; padding:0; box-shadow:none; font-size:11pt; break-after:page; }
  .qr026-sheet:last-child { break-after:auto; }
  .qr026-sheet .qr026-table { font-size:inherit; }
  .qr026-header h3 { font-size:20pt; }
  .qr026-title-row h1 { font-size:16pt; }
  .qr026-triplicate { page:qr026Triplicate; font-size:2.2mm; }
  .qr026-triplicate .qr026-header h3,.qr026-triplicate .qr026-title-row h1 { font-size:3.2mm; }
  .qr026-header,.qr026-title-row,.qr026-fields,.qr026-summary,.qr026-signatures { break-inside:avoid; }
  .qr026-table thead { display:table-header-group; }
  .qr026-table tr { break-inside:avoid; }
}
</style>

<style>
@media print {
  body:has(.qr026-sheets) { margin:0 !important; padding:0 !important; background:#fff !important; }
  body:has(.qr026-sheets) > :not(#app) { display:none !important; }
  body:has(.qr026-sheets) #app,body:has(.qr026-sheets) #app *:has(.qr026-sheets) {
    display:block !important; position:static !important; width:auto !important; height:auto !important; min-height:0 !important; margin:0 !important; padding:0 !important; border:0 !important; overflow:visible !important; transform:none !important; background:#fff !important;
  }
  body:has(.qr026-sheets) #app *:not(:has(.qr026-sheets)):not(.qr026-sheets):not(.qr026-sheets *) { display:none !important; }
}
</style>

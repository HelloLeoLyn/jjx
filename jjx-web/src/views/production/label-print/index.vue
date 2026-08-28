<template>
  <div class="label-print-page">
    <div class="toolbar no-print">
      <span>{{ title }}（{{ labels.length }} 张）</span>
      <el-button type="primary" :disabled="loading || !labels.length" @click="handlePrint">打印</el-button>
    </div>

    <div v-if="loading" class="message no-print">正在生成标签...</div>
    <div v-else-if="errorMessage" class="message no-print">{{ errorMessage }}</div>
    <main v-else class="label-sheet" :class="`label-sheet--${labelType}`">
      <article v-for="label in labels" :key="label.key" class="label-card">
        <div class="label-content">
          <div class="label-title">{{ label.title }}</div>
          <div v-for="field in label.fields" :key="field.name" class="label-field">
            <span class="field-name">{{ field.name }}</span><span class="field-value">{{ field.value }}</span>
          </div>
        </div>
        <img :src="label.qrDataUrl" class="label-qrcode" alt="追溯二维码" />
      </article>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import QRCode from 'qrcode'
import { getProductInfo } from '@/api/product'
import { materialApi } from '@/api/inventory/material'
import { outboundApi } from '@/api/inventory/outbound'
import { recordLabelPrint } from '@/api/production/labelPrint'
import { readLabelPrintPayload, type LabelPrintPayload, type LabelType } from '@/utils/labelPrint'
import type { OutboundVO } from '@/types/inventory/outbound'

interface LabelField { name: string; value: string }
interface PrintLabel { key: string; title: string; fields: LabelField[]; qrDataUrl: string; traceValue: string }

// 本期固定参数，后续可由标签配置中心接管。
const LABEL_LAYOUT = {
  product: { width: 50, height: 30, columns: 3 },
  material: { width: 40, height: 30, columns: 4 },
  box: { width: 50, height: 30, columns: 3 },
} as const

const route = useRoute()
const loading = ref(true)
const errorMessage = ref('')
const labels = ref<PrintLabel[]>([])
const labelType = ref<LabelType>('product')
const payload = ref<LabelPrintPayload | null>(null)
const title = computed(() => ({ product: '产品标签', material: '物料标签', box: '箱标/托盘标' })[labelType.value])

function text(value: unknown, fallback = '-') { return value === null || value === undefined || value === '' ? fallback : String(value) }
function dateOnly(value: unknown) { return text(value).slice(0, 10) }
function productSpec(value: unknown) {
  if (!value) return '-'
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    const specs = parsed?.specifications
    if (Array.isArray(specs)) return specs.map((item: any) => `${item.name}:${item.value}${item.unit || ''}`).join(' ')
  } catch { /* 保留原始规格文本 */ }
  return text(value)
}

async function makeLabel(key: string, titleText: string, fields: LabelField[], traceValue: string): Promise<PrintLabel> {
  return {
    key,
    title: titleText,
    fields,
    traceValue,
    qrDataUrl: await QRCode.toDataURL(traceValue, { width: 112, margin: 1, errorCorrectionLevel: 'M' }),
  }
}

async function buildProductLabels(source: LabelPrintPayload) {
  const rows = source.data?.length
    ? source.data
    : await Promise.all((source.productIds || []).map(async id => (await getProductInfo(id)).data))
  return Promise.all(rows.map((row: any, index) => {
    const batch = text(row.batchNo)
    const trace = `${text(row.productCode)}|${batch}`
    return makeLabel(`product-${row.productId || index}-${batch}`, '产品标签', [
      { name: '编码', value: text(row.productCode) },
      { name: '名称', value: text(row.productName) },
      { name: '规格', value: productSpec(row.specification || row.specJson) },
      { name: '批次', value: batch },
      { name: '数量', value: `${text(row.quantity, '1')} ${text(row.unit, '')}`.trim() },
      { name: '日期', value: dateOnly(row.productionDate || row.createTime || new Date().toISOString()) },
    ], trace)
  }))
}

async function buildMaterialLabels(source: LabelPrintPayload) {
  const rows = source.data?.length
    ? source.data
    : await Promise.all((source.materialIds || []).map(async id => (await materialApi.getInfo(String(id))).data))
  return Promise.all(rows.map((row: any, index) => makeLabel(`material-${row.materialId || index}`, '物料标签', [
    { name: '编码', value: text(row.materialCode) },
    { name: '名称', value: text(row.materialName) },
    { name: '规格', value: text(row.specification) },
  ], text(row.materialCode))))
}

async function buildBoxLabels(source: LabelPrintPayload) {
  const outbound = source.data?.[0] as OutboundVO | undefined
    || (source.outboundId ? (await outboundApi.getById(source.outboundId)).data : undefined)
  if (!outbound) return []
  const items = outbound.items || []
  return Promise.all(items.map((item, index) => {
    const boxNo = `${outbound.outboundNo}-${String(index + 1).padStart(3, '0')}`
    return makeLabel(`box-${item.itemId || index}`, '箱标 / 托盘标', [
      { name: '单号', value: text(outbound.outboundNo) },
      { name: '箱号', value: boxNo },
      { name: '产品', value: `${text(item.materialCode)} ${text(item.materialName, '')}`.trim() },
      { name: '数量', value: `${text(item.quantity)} ${text(item.unit, '')}`.trim() },
    ], `${boxNo}|${outbound.outboundNo}`)
  }))
}

async function loadLabels() {
  const key = text(route.query.key, '')
  const stored = key ? readLabelPrintPayload(key) : null
  const type = text(stored?.type || route.query.type, 'product') as LabelType
  if (!['product', 'material', 'box'].includes(type)) throw new Error('不支持的标签类型')
  labelType.value = type
  payload.value = stored || {
    type,
    productIds: text(route.query.productIds, '').split(',').filter(Boolean).map(Number),
    materialIds: text(route.query.materialIds, '').split(',').filter(Boolean).map(Number),
    outboundId: text(route.query.outboundId, ''),
  }
  labels.value = type === 'product'
    ? await buildProductLabels(payload.value)
    : type === 'material' ? await buildMaterialLabels(payload.value) : await buildBoxLabels(payload.value)
  if (!labels.value.length) throw new Error('没有可打印的标签数据')
}

async function handlePrint() {
  const bizId = payload.value?.outboundId
    || payload.value?.productIds?.join(',')
    || payload.value?.materialIds?.join(',')
    || labels.value.map(item => item.key).join(',')
  try { await recordLabelPrint(bizId || labelType.value) } catch (error) { console.warn('标签打印留痕失败', error) }
  window.print()
}

onMounted(async () => {
  try { await loadLabels() } catch (error: any) { errorMessage.value = error?.message || '标签生成失败' } finally { loading.value = false }
  const config = LABEL_LAYOUT[labelType.value]
  document.documentElement.style.setProperty('--label-width', `${config.width}mm`)
  document.documentElement.style.setProperty('--label-height', `${config.height}mm`)
  document.documentElement.style.setProperty('--label-columns', String(config.columns))
})
</script>

<style>
:root { --label-width: 50mm; --label-height: 30mm; --label-columns: 3; }
body { margin: 0; }
.label-print-page { min-height: 100vh; background: #f3f4f6; padding: 16px; box-sizing: border-box; }
.toolbar { display: flex; justify-content: space-between; align-items: center; max-width: 1100px; margin: 0 auto 16px; padding: 12px 16px; background: #fff; border-radius: 6px; }
.message { padding: 48px; text-align: center; color: #606266; }
.label-sheet { display: grid; grid-template-columns: repeat(var(--label-columns), var(--label-width)); gap: 3mm; justify-content: center; }
.label-card { width: var(--label-width); height: var(--label-height); box-sizing: border-box; display: flex; overflow: hidden; border: .25mm solid #222; padding: 1.5mm; background: #fff; color: #000; font-family: Arial, 'Microsoft YaHei', sans-serif; page-break-inside: avoid; break-inside: avoid; }
.label-content { min-width: 0; flex: 1; display: flex; flex-direction: column; justify-content: center; }
.label-title { font-size: 3.2mm; line-height: 4mm; font-weight: 700; text-align: center; }
.label-field { display: flex; min-width: 0; font-size: 2.2mm; line-height: 3mm; white-space: nowrap; }
.field-name { flex: 0 0 7mm; font-weight: 600; }
.field-value { overflow: hidden; text-overflow: ellipsis; }
.label-qrcode { flex: 0 0 auto; width: 20mm; height: 20mm; align-self: center; image-rendering: crisp-edges; }
.label-sheet--material .label-qrcode { width: 17mm; height: 17mm; }
.label-sheet--material .field-name { flex-basis: 6mm; }
@media print {
  @page { size: auto; margin: 0; }
  html, body, #app { margin: 0 !important; padding: 0 !important; background: #fff !important; }
  body * { visibility: hidden; }
  .label-sheet, .label-sheet * { visibility: visible; }
  .no-print { display: none !important; }
  .label-print-page { min-height: 0; padding: 0; background: #fff; }
  .label-sheet { position: absolute; inset: 0; display: grid; gap: 0; justify-content: start; align-content: start; }
  .label-card { border: 0; }
}
</style>

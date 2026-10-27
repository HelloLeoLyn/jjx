<template>
  <div ref="sheetRoot" class="doc-sheets" :class="{ 'cell-font-editable': cellFontEditable }">
    <div v-if="specTemplate === 'B'" ref="flowSource" class="flow-source" aria-hidden="true">
      <WorkSpecPaper ref="flowPaper" :data="data" :page="workSpecFlowSource(data)" :page-index="0" :page-count="1"
        :structure-image="structureImage" :structure-caption="structureCaption" :font-settings="fontSettings" :cell-fonts="cellFonts" spec-template="B" />
    </div>
    <div v-for="(page, pageIndex) in pages" :key="page.key" :id="`doc-page-${pageIndex}`" class="sheet-slot">
      <div class="page-caption no-print"><span>{{ page.title }}</span><span>{{ pageIndex + 1 }} / {{ pages.length }}</span></div>
      <A4Canvas :class="{ 'spec-canvas': page.kind === 'spec' || page.section === 'print', 'work-spec-canvas': page.kind === 'spec' || (page.kind === 'spec-details' && page.section === 'spec'), 'measured-canvas': !!page.flowHtml || page.flowPending }" :padding-mm="9" :scale="1">
        <div v-if="page.flowHtml" class="measured-page" v-html="workSpecFlowPageHtml(page.flowHtml, pageIndex, pages.length)" />
        <div v-else-if="page.flowPending" class="layout-pending">正在按 A4 测量分页…</div>
        <WorkSpecPaper v-else-if="page.kind === 'spec'" ref="workPapers" @overflow="emit('layout', page.key, $event)" :data="data" :page="page" :page-index="pageIndex" :page-count="pages.length" :structure-image="structureImage" :structure-caption="structureCaption" :font-settings="fontSettings" :cell-fonts="cellFonts" :spec-template="specTemplate" :generate-overflow-appendix="generateOverflowAppendix" />
        <InkMixingPaper v-else-if="page.kind === 'ink'" :product-code="data.product.productCode" />
        <article v-else class="paper-form" :class="{ 'appendix-form': page.kind === 'spec-details' && page.section === 'spec' }">
          <header v-if="page.section !== 'customer' && page.section !== 'atlas'" class="paper-heading">
            <h1>{{ page.title }}<small v-if="page.continuation">（续）</small></h1>
            <div v-if="page.section !== 'print'" class="paper-meta"><span>{{ data.product.productCode }}</span><span>{{ data.product.customerName || '' }}</span></div>
          </header>

          <template v-if="page.kind === 'spec-details'">
            <div class="spec-detail-lines"><div v-for="(line, i) in page.detailLines" :key="i" :style="{ color: line.color }">{{ line.text }}</div></div>
          </template>
          <PrintSpecPaper v-else-if="page.kind === 'print'" ref="printPapers" :page="page" @overflow="emit('layout', page.key, $event)" />
          <ColorCheckPaper v-else-if="page.kind === 'color'" :data="data" />

          <template v-else-if="page.kind === 'image'">
            <div class="attachment-name">{{ page.file ? documentFileCaption(page.file) : '' }}</div>
            <div class="attachment-artwork"><img :src="page.image?.url" :alt="page.file?.name" /></div>
          </template>

          <template v-else-if="page.kind === 'flow'">
            <table class="paper-grid reference-grid"><thead><tr><th>顺序</th><th>工序名称</th><th>作业说明</th><th>人工工时（h）</th></tr></thead><tbody><tr v-for="(row, i) in page.rows" :key="row.itemId || i"><td>{{ row.processOrder }}</td><td>{{ plain(row.processName) }}</td><td>{{ plain(row.workInstruction || row.description) }}</td><td>{{ labor(row) }}</td></tr></tbody></table>
            <div v-if="!page.rows?.length" class="empty-paper">暂无工序记录</div>
          </template>
          <div v-else class="empty-paper"><div class="empty-paper-frame"><span>{{ page.title }}</span><p>{{ page.emptyMessage || '暂无已选文件' }}</p></div></div>

          <footer class="paper-footer"><span>名称：{{ data.product.productName }}</span><span>编号：{{ data.product.productCode }}</span><span v-if="data.routing.routingVersion">路线：{{ data.routing.routingVersion }}</span><span>{{ pageIndex + 1 }} / {{ pages.length }} 页</span></footer>
        </article>
      </A4Canvas>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import A4Canvas from '@/components/A4Canvas/index.vue'
import WorkSpecPaper from './WorkSpecPaper.vue'
import PrintSpecPaper from './PrintSpecPaper.vue'
import ColorCheckPaper from './ColorCheckPaper.vue'
import InkMixingPaper from './InkMixingPaper.vue'
import { plain, documentFileCaption, workSpecFlowSource, paginateMeasuredWorkSpec, type DocsetData, type DocPage, type PaperTextLine } from './docset'
import { workSpecFlowPageHtml } from './workSpecFlowPagination'
import type { WorkSpecFontSettings, WorkSpecTemplate } from './workSpecFont'
import type { WorkSpecCellFonts } from './workSpecCellFont'

const props = withDefaults(defineProps<{ data: DocsetData; pages: DocPage[]; structureImage?: string; structureCaption?: string; fontSettings: WorkSpecFontSettings; cellFonts?: WorkSpecCellFonts; selectedCellKey?: string; cellFontEditable?: boolean; specTemplate?: WorkSpecTemplate; generateOverflowAppendix?: boolean }>(), { specTemplate: 'A', generateOverflowAppendix: false })
const emit = defineEmits<{ layout: [key: string, lines: PaperTextLine[]]; flowLayout: [pages: string[]]; flowError: [message: string] }>()
const workPapers = ref<InstanceType<typeof WorkSpecPaper>[]>([])
const printPapers = ref<InstanceType<typeof PrintSpecPaper>[]>([])
const flowSource = ref<HTMLElement>()
const flowPaper = ref<InstanceType<typeof WorkSpecPaper>>()
const sheetRoot = ref<HTMLElement>()
let generation = 0
let disposed = false
async function measureFlow() {
  const current = ++generation
  if (props.specTemplate !== 'B') return
  try {
    await nextTick()
    await document.fonts.ready
    await flowPaper.value?.measure()
    const source = flowSource.value?.querySelector<HTMLElement>('.work-paper')
    if (!source?.clientWidth) return
    await Promise.all(Array.from(source.querySelectorAll('img'), image => image.decode()))
    if (disposed || current !== generation) return
    emit('flowLayout', paginateMeasuredWorkSpec(source))
  } catch (error) {
    if (!disposed && current === generation)
      emit('flowError', error instanceof Error ? error.message : '作业规范分页失败')
  }
}
watch(() => [props.data, props.specTemplate, props.fontSettings, props.cellFonts, props.structureImage, props.structureCaption], () => {
  emit('flowLayout', [])
  void measureFlow()
}, { deep: true, flush: 'post' })
watch(() => [props.pages, props.selectedCellKey, props.cellFontEditable], async () => {
  await nextTick()
  for (const cell of sheetRoot.value?.querySelectorAll<HTMLElement>('[data-work-spec-cell]') || []) {
    cell.toggleAttribute('data-cell-font-selected', !!props.cellFontEditable && cell.dataset.workSpecCell === props.selectedCellKey)
    if (!props.cellFontEditable) cell.removeAttribute('tabindex')
  }
}, { immediate: true, flush: 'post' })
onMounted(() => {
  document.fonts.addEventListener('loadingdone', measureFlow)
  void measureFlow()
})
onBeforeUnmount(() => {
  disposed = true
  generation++
  document.fonts.removeEventListener('loadingdone', measureFlow)
})
defineExpose({ measureLayout: async () => {
  await measureFlow()
  await Promise.all([...workPapers.value, ...printPapers.value].map(paper => paper.measure()))
} })
function labor(row?: Record<string, any> | null): string {
  if (!row) return ''
  if (row.children?.length) return totalLabor(row.children)
  const value = row.customLaborHours ?? row.standardLaborHours
  return value == null ? '' : String(Number(value))
}
function totalLabor(rows: Record<string, any>[]): string {
  if (!rows.some((row) => labor(row) !== '')) return ''
  return String(Number(rows.reduce((sum, row) => sum + Number(labor(row) || 0), 0).toFixed(2)))
}

</script>

<style scoped>
.flow-source { position:fixed; left:-100000px; top:0; width:192mm; visibility:hidden; pointer-events:none; }
.measured-canvas :deep(.a4-canvas) { height:297mm; min-height:297mm; overflow:hidden; }
.measured-page { height:278mm; }
.layout-pending { height:278mm; }
.cell-font-editable :deep(td[data-work-spec-cell]) { cursor:pointer; }
@media screen {
  .cell-font-editable :deep(td[data-work-spec-cell]:hover),
  .cell-font-editable :deep(td[data-cell-font-selected]) { outline:2px solid #409eff; outline-offset:-2px; }
  :deep(td[data-cell-font-overflow]) { box-shadow:inset 0 0 0 2px #e6a23c; }
}
.doc-sheets { display:flex; flex-direction:column; align-items:center; gap:24px; }
.spec-canvas :deep(.a4-canvas) { width:210mm; border:0; }
.work-spec-canvas :deep(.a4-canvas) { width:210mm; height:297mm; min-height:297mm; border:0; }
.sheet-slot { width:794px; scroll-margin-top:24px; }
.page-caption { display:flex; justify-content:space-between; color:#798493; font-size:12px; padding:0 2px 8px; }
.paper-form { min-height:1053px; box-sizing:border-box; display:flex; flex-direction:column; border:1.4px solid #262626; color:#252525; font-family:'SimSun','Songti SC',serif; }
.paper-heading { padding:12px 16px 8px; border-bottom:1.4px solid #262626; }
.paper-heading h1 { margin:0; font-weight:700; font-size:27px; letter-spacing:6px; text-align:center; line-height:1.6; }
.paper-heading small { font-size:15px; letter-spacing:1px; }
.paper-meta { display:flex; justify-content:space-between; margin-top:2px; font-size:11px; color:#555; }
.spec-detail-lines { flex:1; padding:16px; font-size:13px; }
.spec-detail-lines div { min-height:18px; line-height:18px; white-space:pre-wrap; overflow-wrap:anywhere; }
.paper-grid { width:100%; border-collapse:collapse; table-layout:fixed; font-size:12px; }
.paper-grid th,.paper-grid td { border:1px solid #343434; padding:3px 5px; line-height:1.4; overflow-wrap:anywhere; }
.paper-grid th { font-weight:500; font-size:13px; height:29px; }
.paper-grid td { height:23px; box-sizing:border-box; }
.center { text-align:center; }
.material-area { display:flex; }
.material-grid { width:70%; }
.conditions { width:30%; border-left:1px solid #333; }
.block-title { height:30px; text-align:center; line-height:30px; border-bottom:1px solid #333; font-size:14px; }
.conditions .paper-grid td { height:27px; font-size:11px; padding:3px; }
.condition-value { width:31%; }
.die-locations { padding:10px; line-height:1.8; font-size:11px; }
.die-locations strong { display:block; font-weight:500; margin-bottom:5px; }
.blank-note { color:#a0a0a0; font-size:11px; }
.flow-area { display:flex; border-top:1.4px solid #333; }
.flow-column { width:30%; }
.middle-flow { width:40%; border-left:1px solid #333; border-right:1px solid #333; }
.flow-grid th { font-size:12px; padding:3px 2px; }
.flow-grid td { height:24px; padding:2px 3px; }
.flow-grid tfoot td { height:29px; font-size:11px; }
.flow-operation { display:flex; align-items:center; flex-wrap:wrap; gap:3px; min-height:20px; }
.operation-symbol { display:inline-flex; align-items:center; font-size:11px; position:relative; }
.operation-symbol sub { font-size:9px; }
.operation-note { font-size:10px; }
.flow-plus { font-family:Arial,sans-serif; }
.structure-image { height:174px; display:flex; align-items:center; justify-content:center; padding:10px; box-sizing:border-box; }
.structure-caption { height:12px; padding:0 6px; font-size:8px; line-height:12px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; box-sizing:border-box; }
.structure-image img { width:100%; height:100%; object-fit:contain; }
.hours-total { min-height:30px; border-top:1px solid #333; border-bottom:1px solid #333; padding:5px 10px; display:flex; gap:24px; align-items:center; box-sizing:border-box; }
.issue-area { display:flex; min-height:90px; flex:1; }
.vertical-label { width:28px; display:flex; align-items:center; justify-content:center; writing-mode:vertical-rl; letter-spacing:7px; border-right:1px solid #333; padding:6px 0; }
.change-note { flex:1; padding:9px; border-right:1px solid #333; font-size:11px; }
.issue-blank { width:130px; }
.attachment-name { padding:8px 12px; font-size:11px; color:#555; overflow-wrap:anywhere; }
.attachment-artwork { flex:1; height:870px; min-height:0; padding:10px 14px; display:flex; align-items:center; justify-content:center; box-sizing:border-box; }
.attachment-artwork img { width:100%; height:100%; object-fit:contain; }
.reference-grid th { height:35px; }.reference-grid td { height:32px; }
.empty-paper { flex:1; display:flex; align-items:center; justify-content:center; min-height:700px; color:#a0a0a0; }
.empty-paper-frame { border:1px dashed #c8c8c8; padding:48px 90px; text-align:center; font-family:'Microsoft YaHei',sans-serif; }
.empty-paper-frame span { font-size:17px; }.empty-paper-frame p { font-size:12px; margin-top:15px; }
.paper-footer { margin-top:auto; display:flex; gap:10px; align-items:center; justify-content:space-between; border-top:1.3px solid #333; padding:7px 8px; font-size:11px; }
.paper-footer span { overflow-wrap:anywhere; }.paper-footer span:last-child { flex-shrink:0; }
.appendix-form { height:278mm; min-height:0; border:0; }
.appendix-form .paper-heading, .appendix-form .spec-detail-lines { outline:1.4px solid #262626; outline-offset:-1.4px; }
.appendix-form .spec-detail-lines { flex:0 0 auto; }
@media print {
  .flow-source { display:none; }
  .no-print { display:none !important; }
  .doc-sheets { display:block; }
  .sheet-slot { width:210mm; margin:0; break-after:page; page-break-after:always; }
  .sheet-slot:last-child { break-after:auto; page-break-after:auto; }
  .paper-form { min-height:279mm; }
  .appendix-form { min-height:0; }
  .sheet-slot :deep(.a4-canvas) { min-height:297mm; }
}
</style>

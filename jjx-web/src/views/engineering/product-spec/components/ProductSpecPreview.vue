<template>
  <div class="doc-preview" :class="{ standalone }">
    <PrintToolbar v-if="standalone" title="产品电子文档集 · 预览与打印">
      <template #actions><span class="toolbar-product">{{ data?.product.productCode }}</span><el-button type="primary" icon="Printer" :disabled="!canPrint" @click="printDocuments">打印所选文档</el-button></template>
    </PrintToolbar>
    <div class="preview-toolbar no-print">
      <div><strong>{{ data?.product.productName || '产品电子文档集' }}</strong><span class="toolbar-caption">{{ pages.length }} 页 · A4</span></div>
      <div class="preview-actions"><el-button text @click="zoom = Math.max(50, zoom - 10)">−</el-button><span>{{ zoom }}%</span><el-button text @click="zoom = Math.min(120, zoom + 10)">＋</el-button><el-button @click="load">重新加载</el-button><el-button v-if="!standalone" type="primary" icon="Printer" :disabled="!canPrint" @click="printDocuments">打印所选文档</el-button></div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="preview-message no-print" />
    <el-alert v-if="data?.warnings.length" :title="data.warnings.join('；')" type="warning" :closable="false" class="preview-message no-print" />
    <el-alert v-if="paperOnly && structureFile && fileErrors[structureFile.key]" :title="'结构图展开失败：' + fileErrors[structureFile.key]" type="error" :closable="false" class="preview-message no-print" />
    <div class="preview-layout" :class="{ 'paper-only': paperOnly }" v-loading="loading">
      <aside v-if="!paperOnly" class="document-nav no-print">
        <div class="nav-heading"><strong>文档目录</strong><el-button link @click="selectAll">全选</el-button></div>
        <p class="nav-tip">勾选要预览和打印的文档</p>
        <el-switch v-model="showHistory" size="small" active-text="查看原稿／全部版本" />
        <p class="nav-tip">工程图纸默认选择现行已下发版的打印件；原稿、历史及待归集文件可手动勾选。</p>
        <el-alert v-if="selectedDrafts.length" title="已手动选择非现行、未下发或待归集图纸，打印页会标注其状态。" type="warning" :closable="false" />
        <el-checkbox-group v-model="selectedSections">
          <div v-for="section in documentSections" :key="section.key" class="nav-section" :class="{ chosen: selectedSections.includes(section.key) }">
            <el-checkbox :value="section.key" :label="section.key"><span class="section-label">{{ section.label }}</span></el-checkbox>
            <div class="section-note" @click="jumpTo(section.key)">{{ section.note }}<span>{{ sectionPageCount(section.key) }} 页</span></div>
            <el-checkbox-group v-if="selectedSections.includes(section.key) && filesFor(section.key).length" v-model="selectedFiles" class="file-choices">
              <div v-for="file in filesFor(section.key)" :key="file.key" class="file-choice">
                <el-checkbox :value="file.key" :label="file.key" :disabled="file.kind === 'other'"><span :title="documentFileCaption(file)">{{ documentFileCaption(file) }}</span></el-checkbox>
                <div v-if="file.kind === 'other'" class="file-help"><span>此格式请使用原文件</span><el-link type="primary" @click="downloadOriginal(file)">下载原文件</el-link></div>
                <div v-if="fileErrors[file.key]" class="file-error">{{ fileErrors[file.key] }}</div>
              </div>
            </el-checkbox-group>
          </div>
        </el-checkbox-group>
        <div class="nav-foot">已选 {{ selectedSections.length }} 项 · {{ pages.length }} 页</div>
      </aside>
      <main class="paper-stage">
        <div v-if="pendingCount" class="render-progress no-print">正在展开附件，还有 {{ pendingCount }} 个文件…</div>
        <div v-if="data && pages.length" class="paper-zoom" :style="{ zoom: `${zoom}%` }"><ProductSpecSheets :data="data" :pages="pages" :structure-image="structureImage" :structure-caption="structureCaption" /></div>
        <el-empty v-else-if="!loading && !error" description="勾选左侧文档，查看纸张预览" />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PrintToolbar from '@/components/print/PrintToolbar.vue'
import { downloadFile } from '@/components/product/productFilePreview'
import ProductSpecSheets from './ProductSpecSheets.vue'
import { documentSections, loadDocset, makePages, renderFile, defaultDocumentFiles, documentFileCaption, isEngineeringFile, type DocsetData, type DocFile, type FileImage } from './docset'
import { DrawingCurrentFlagEnum, DrawingReleaseFlagEnum } from '@/enums/product/drawing'

const props = withDefaults(defineProps<{ productId: number; standalone?: boolean; autoPrint?: boolean; paperOnly?: boolean; initialSections?: string[]; initialFiles?: string[] }>(), { standalone: false, autoPrint: false, paperOnly: false })
const emit = defineEmits<{ loaded: [data: DocsetData] }>()
const data = shallowRef<DocsetData>()
const loading = ref(false)
const error = ref('')
const selectedSections = ref<string[]>(props.initialSections ?? documentSections.map((section) => section.key))
const selectedFiles = ref<string[]>([])
const showHistory = ref(false)
const images = shallowRef<Record<string, FileImage[]>>({})
const fileErrors = ref<Record<string, string>>({})
const zoom = ref(props.paperOnly ? 100 : 80)
let generation = 0
let queueRunning = false
let disposed = false
let printPageStyle: HTMLStyleElement | undefined
let autoPrinted = false

const structureFile = computed(() => data.value?.workSpec.structureFileId
  ? data.value.files.find(file => file.id === data.value?.workSpec.structureFileId && file.category === '结构图' && file.kind !== 'other')
  : undefined)
const defaultFileKeys = computed(() => new Set(defaultDocumentFiles(data.value?.files || []).map(file => file.key)))
const selectedDrafts = computed(() => requestedFiles.value.filter(file => isEngineeringFile(file) && (!file.drawingNo || file.isCurrent !== DrawingCurrentFlagEnum.CURRENT.value || file.released !== DrawingReleaseFlagEnum.RELEASED.value)))
const visibleFiles = computed(() => (data.value?.files || []).filter((file) => selectedFiles.value.includes(file.key) && selectedSections.value.includes(file.section)))
const requestedFiles = computed(() => {
  const files = [...visibleFiles.value]
  if (selectedSections.value.includes('spec') && structureFile.value && !files.some((file) => file.key === structureFile.value?.key)) files.push(structureFile.value)
  return files
})
const pendingCount = computed(() => requestedFiles.value.filter((file) => !images.value[file.key] && !fileErrors.value[file.key]).length)
const pages = computed(() => data.value ? makePages(data.value, selectedSections.value, selectedFiles.value, images.value) : [])
const structureImage = computed(() => structureFile.value ? images.value[structureFile.value.key]?.[0]?.url : '')
const structureCaption = computed(() => structureFile.value ? documentFileCaption({ ...structureFile.value, name: '' }) : '')
const canPrint = computed(() => !!data.value && !loading.value && !error.value && pages.value.length > 0 && pendingCount.value === 0 && !requestedFiles.value.some((file) => fileErrors.value[file.key]))

function releaseImages() { Object.values(images.value).flat().forEach((image) => URL.revokeObjectURL(image.url)); images.value = {} }
async function load() {
  const current = ++generation
  releaseImages()
  data.value = undefined
  fileErrors.value = {}
  error.value = ''
  loading.value = true
  try {
    if (!props.productId) throw new Error('请先选择产品')
    const result = await loadDocset(props.productId)
    if (current !== generation || disposed) return
    selectedFiles.value = (props.initialFiles ? result.files.filter(file => file.kind !== 'other' && props.initialFiles!.includes(file.key)) : defaultDocumentFiles(result.files)).map(file => file.key)
    data.value = result
    emit('loaded', result)
    if (result.workSpec.structureFileId && !structureFile.value) throw new Error('所选结构图已不可用，请在产品作业规范中重新选择')
  } catch (e) {
    if (current === generation && !disposed) error.value = e instanceof Error ? e.message : '文档加载失败'
  } finally { if (current === generation && !disposed) loading.value = false }
}
async function expandFiles() {
  if (queueRunning || disposed) return
  queueRunning = true
  try {
    while (!disposed) {
      const file = requestedFiles.value.find((file) => !images.value[file.key] && !fileErrors.value[file.key])
      if (!file) break
      const current = generation
      try {
        const result = await renderFile(file)
        if (current !== generation || disposed) result.forEach((image) => URL.revokeObjectURL(image.url))
        else images.value = { ...images.value, [file.key]: result }
      } catch (e) {
        if (current === generation && !disposed) fileErrors.value[file.key] = e instanceof Error ? e.message : '附件展开失败，可取消勾选或重新加载'
      }
    }
  } finally { queueRunning = false }
}
function filesFor(section: string): DocFile[] {
  return data.value?.files.filter(file => file.section === section && (showHistory.value || !isEngineeringFile(file) || defaultFileKeys.value.has(file.key) || selectedFiles.value.includes(file.key))) || []
}
function sectionPageCount(section: string) { return pages.value.filter((page) => page.section === section).length }
async function downloadOriginal(file: DocFile) {
  try { await downloadFile({ id: file.id, fileName: file.name }) }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '下载失败') }
}
function selectAll() { selectedSections.value = documentSections.map((section) => section.key) }
function jumpTo(section: string) {
  const i = pages.value.findIndex((page) => page.section === section)
  if (i >= 0) document.getElementById(`doc-page-${i}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
async function printDocuments() {
  if (!canPrint.value) return
  if (!props.standalone) {
    const params = new URLSearchParams({ sections: selectedSections.value.join(','), files: selectedFiles.value.join(','), print: '1' })
    window.open(`/print/product-spec/${props.productId}?${params}`, '_blank')
    return
  }
  await nextTick()
  await document.fonts.ready
  const imgs = Array.from(document.querySelectorAll<HTMLImageElement>('.doc-sheets img'))
  try { await Promise.all(imgs.map((image) => image.decode())); window.print() }
  catch { ElMessage.error('图片尚未加载完成，请重新加载后打印') }
}
watch(() => props.productId, load, { immediate: true })
defineExpose({ reload: load })
watch(requestedFiles, expandFiles)
watch(canPrint, (ready) => {
  if (ready && props.standalone && props.autoPrint && !autoPrinted && !selectedDrafts.value.length) {
    autoPrinted = true
    printDocuments()
  }
})
onMounted(() => {
  if (!props.standalone) return
  printPageStyle = document.createElement('style')
  printPageStyle.textContent = '@media print { @page { size: A4 portrait; margin: 0; } body { margin: 0 !important; padding: 0 !important; } }'
  document.head.appendChild(printPageStyle)
})
onBeforeUnmount(() => { disposed = true; generation++; releaseImages(); printPageStyle?.remove() })
</script>

<style scoped>
.doc-preview { color:#303b49; }
.standalone { min-height:100vh; background:#edf0f4; padding:20px; }
.standalone :deep(.print-toolbar) { max-width:1320px; }
.toolbar-product { font-size:12px; color:#8b95a3; }
.preview-toolbar { display:flex; align-items:center; justify-content:space-between; padding:14px 22px; background:#fff; border:1px solid #e3e8ef; border-radius:8px 8px 0 0; gap:12px; }
.preview-toolbar strong { font-size:15px; }.toolbar-caption { color:#97a0ad; font-size:12px; margin-left:16px; }
.preview-actions { display:flex; align-items:center; gap:8px; }.preview-actions > span { font-size:12px; min-width:35px; text-align:center; }
.preview-message { margin:8px 0; }
.preview-layout { display:flex; background:#edf0f4; border:1px solid #e0e5ed; border-top:0; height:70vh; min-height:540px; overflow:hidden; }
.standalone .preview-layout { height:calc(100vh - 150px); }
.preview-layout.paper-only { height:auto; min-height:0; overflow:visible; }
.paper-only .paper-stage { padding:20px; overflow:auto; }
.document-nav { width:245px; flex-shrink:0; background:#fff; padding:20px 14px; border-right:1px solid #e1e6ed; overflow-y:auto; box-sizing:border-box; }
.nav-heading { display:flex; align-items:center; justify-content:space-between; padding:0 6px; font-size:14px; }
.nav-tip { margin:6px 6px 16px; font-size:11px; color:#98a1ad; }
.nav-section { border:1px solid transparent; border-radius:6px; padding:7px 10px; margin-bottom:5px; }
.nav-section.chosen { background:#f4f7fc; border-color:#e7edf5; }
.nav-section :deep(.el-checkbox) { margin-right:0; width:100%; }
.section-label { color:#48566a; font-weight:500; font-size:13px; }
.section-note { display:flex; justify-content:space-between; gap:4px; color:#909baa; padding-left:23px; font-size:10px; cursor:pointer; }.section-note span { flex-shrink:0; }
.file-choices { margin:8px 0 1px 22px; }.file-choice :deep(.el-checkbox__label) { overflow:hidden; text-overflow:ellipsis; font-size:11px; }.file-choice :deep(.el-checkbox) { height:27px; }
.file-help { display:flex; gap:8px; margin:2px 0 5px; font-size:10px; color:#929ba9; }.file-help :deep(.el-link) { font-size:10px; }
.file-error { font-size:10px; color:#d75d52; margin-bottom:5px; }
.nav-foot { font-size:11px; color:#8793a4; padding:14px 6px; margin-top:18px; border-top:1px solid #edf0f5; }
.paper-stage { flex:1; overflow:auto; padding:26px 32px 40px; min-width:0; }
.paper-zoom { width:fit-content; margin:auto; }
.render-progress { text-align:center; font-size:12px; color:#78879b; margin-bottom:16px; }
@media (max-width:950px) { .document-nav { width:200px; }.preview-toolbar { flex-wrap:wrap; }.paper-stage { padding:18px; } }
@media print {
  .no-print { display:none !important; }
  .standalone,.doc-preview { background:#fff; padding:0; }
  .preview-layout { display:block; border:0; height:auto !important; min-height:0; overflow:visible; }
  .paper-stage { padding:0; overflow:visible; }
  .paper-zoom { zoom:100% !important; width:auto; margin:0; }
}
</style>

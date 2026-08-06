<!-- src/components/PrintDialog/index.vue -->
<!--
  DEV-662：统一打印组件（双模式）
  模式1 pdf-blob：传入后端生成的 PDF Blob → pdfjs 渲染预览 + 下载 + 打印
  模式2 插槽：插槽 #content 渲染打印内容 → window.print() 打印（@media print 只显示打印区）
-->
<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="720px"
    append-to-body
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    @open="handleOpen"
    @closed="handleClosed"
  >
    <!-- 模式1：PDF 预览（pdf-blob） -->
    <div v-if="pdfBlob" class="pdf-preview" v-loading="pdfLoading">
      <div v-if="pdfError" class="pdf-error">
        <el-empty description="PDF 加载失败" :image-size="80" />
      </div>
      <div v-else class="pdf-pages">
        <div v-for="(page, idx) in pdfPages" :key="idx" class="pdf-page-wrap">
          <canvas :ref="(el) => setCanvasRef(idx, el)" class="pdf-canvas" />
          <div class="pdf-page-num">{{ idx + 1 }} / {{ pdfPages.length }}</div>
        </div>
      </div>
    </div>

    <!-- 模式2：插槽打印内容 -->
    <div v-else class="print-slot-wrap">
      <slot name="content"></slot>
    </div>

    <!-- 打印区（两种模式共用，@media print 时显示） -->
    <div class="print-area">
      <!-- PDF 模式：打印时用 iframe 承载 blob 调浏览器打印 -->
      <iframe v-if="pdfBlob && iframeUrl" ref="pdfPrintFrame" class="print-frame" :src="iframeUrl"></iframe>
      <!-- 插槽模式：直接渲染插槽内容 -->
      <slot v-else name="content"></slot>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
      <el-button v-if="pdfBlob" @click="handleDownload">下载</el-button>
      <el-button type="primary" icon="Printer" @click="handlePrint">打印</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { download } from '@/utils/format'

defineOptions({ name: 'PrintDialog' })

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '打印' },
  /** 后端生成的 PDF Blob（有则走 PDF 预览模式，无则走插槽模式） */
  pdfBlob: { type: Blob as unknown as () => Blob | null, default: null },
  /** 下载文件名（PDF 模式） */
  fileName: { type: String, default: 'document.pdf' },
})

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'printed'): void
}>()

// ===== PDF.js 预览 =====
const pdfjs = ref<any>(null)
const pdfLoading = ref(false)
const pdfError = ref(false)
const pdfPages = ref<number[]>([])
const pdfDoc = ref<any>(null)
const canvasRefs = ref<HTMLCanvasElement[]>([])
const iframeUrl = ref('')

function setCanvasRef(idx: number, el: any) {
  if (el) canvasRefs.value[idx] = el as HTMLCanvasElement
}

async function loadPdfjs() {
  if (pdfjs.value) return pdfjs.value
  const mod = await import('pdfjs-dist')
  // v6：ESM + worker 配置
  const worker = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).toString()
  mod.GlobalWorkerOptions.workerSrc = worker
  pdfjs.value = mod
  return mod
}

async function renderPdf() {
  if (!props.pdfBlob) return
  pdfLoading.value = true
  pdfError.value = false
  try {
    const mod = await loadPdfjs()
    const data = await props.pdfBlob.arrayBuffer()
    const doc = await mod.getDocument({ data }).promise
    pdfDoc.value = doc
    const numPages = doc.numPages
    pdfPages.value = Array.from({ length: numPages }, (_, i) => i)
    // blob URL 用于 iframe 打印
    iframeUrl.value = URL.createObjectURL(props.pdfBlob)
    await nextTick()
    for (let i = 0; i < numPages; i++) {
      const page = await doc.getPage(i + 1)
      const canvas = canvasRefs.value[i]
      if (!canvas) continue
      const ctx = canvas.getContext('2d')!
      const viewport = page.getViewport({ scale: 1.2 })
      const dpr = window.devicePixelRatio || 1
      canvas.width = viewport.width * dpr
      canvas.height = viewport.height * dpr
      canvas.style.width = viewport.width + 'px'
      canvas.style.height = viewport.height + 'px'
      ctx.scale(dpr, dpr)
      await page.render({ canvasContext: ctx, viewport }).promise
    }
  } catch (e) {
    console.error('PDF 渲染失败:', e)
    pdfError.value = true
  } finally {
    pdfLoading.value = false
  }
}

// ===== 下载 =====
function handleDownload() {
  if (!props.pdfBlob) return
  download(props.pdfBlob, props.fileName)
}

// ===== 打印 =====
function handlePrint() {
  if (props.pdfBlob) {
    // PDF 模式：iframe 加载 blob 后调 iframe 打印
    const frame = document.querySelector('.print-frame') as HTMLIFrameElement | null
    if (frame?.contentWindow) {
      frame.contentWindow.focus()
      frame.contentWindow.print()
    } else {
      // 兜底：打开新窗口打印
      const w = window.open()
      if (w && iframeUrl.value) {
        w.location.href = iframeUrl.value
      }
    }
  } else {
    window.print()
  }
  emit('printed')
}

// ===== 生命周期 =====
function handleOpen() {
  if (props.pdfBlob) {
    renderPdf()
  }
}

function handleClosed() {
  pdfDoc.value?.destroy?.()
  pdfDoc.value = null
  pdfPages.value = []
  canvasRefs.value = []
  if (iframeUrl.value) {
    URL.revokeObjectURL(iframeUrl.value)
    iframeUrl.value = ''
  }
}

// 外部可能动态传 blob，监听变化
watch(() => props.pdfBlob, () => {
  if (props.modelValue && props.pdfBlob) renderPdf()
})

defineExpose({ print: handlePrint })
</script>

<style scoped lang="scss">
.pdf-preview {
  max-height: 60vh;
  overflow-y: auto;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;

  .pdf-pages {
    display: flex;
    flex-direction: column;
    gap: 12px;
    align-items: center;
  }

  .pdf-page-wrap {
    position: relative;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
    background: #fff;

    .pdf-canvas {
      display: block;
    }

    .pdf-page-num {
      position: absolute;
      bottom: 4px;
      right: 6px;
      font-size: 12px;
      color: #909399;
      background: rgba(255, 255, 255, 0.85);
      padding: 0 6px;
      border-radius: 3px;
    }
  }
}

.print-slot-wrap {
  max-height: 60vh;
  overflow-y: auto;
}

// 打印区：屏幕隐藏，仅打印时显示
.print-area {
  display: none;
}

@media print {
  // 隐藏弹窗以外的所有内容
  body * {
    visibility: hidden;
  }

  .print-area,
  .print-area * {
    visibility: visible;
  }

  .print-area {
    display: block !important;
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
  }

  .print-frame {
    width: 100%;
    height: 100vh;
    border: none;
  }
}
</style>

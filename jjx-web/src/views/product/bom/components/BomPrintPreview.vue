<template>
  <el-dialog
    v-model="visible"
    :title="`BOM 材料表预览 · ${bomCode || ''}`"
    width="850px"
    append-to-body
    :close-on-click-modal="false"
    destroy-on-close
    class="bom-print-dialog"
    @opened="layoutPages"
  >
    <div class="print-toolbar">
      <el-button type="primary" icon="Printer" :disabled="measuring || printing" @click="doPrint"
        >打印</el-button
      >
      <span> A4 纵向 · 仅打印材料表 · 共 {{ pages.length }} 页</span>
    </div>
    <div class="preview-scroll">
      <div ref="printContent" class="bom-print-content">
        <article v-for="(page, pageIndex) in pages" :key="pageIndex" class="bom-paper">
          <header class="paper-header">
            <h1>产 品 作 业 规 范<span v-if="pageIndex">（续）</span></h1>
            <div class="paper-meta">
              产品：{{ productName || '-' }}（{{ productCode || '-' }}） · BOM：{{
                bomCode || '-'
              }}
              V{{ bomVersion || '-' }}
            </div>
          </header>
          <table class="material-table">
            <colgroup>
              <col style="width: 7%" />
              <col style="width: 19%" />
              <col style="width: 29%" />
              <col style="width: 30%" />
              <col style="width: 15%" />
            </colgroup>
            <thead>
              <tr>
                <th>序号</th>
                <th>项目</th>
                <th>材料</th>
                <th>规格及模数</th>
                <th>刀模位置</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in page" :key="row.key" data-material-row>
                <td v-if="row.span" :rowspan="row.span" class="center">{{ row.number }}</td>
                <td v-if="row.span" :rowspan="row.span" class="center">
                  <small>{{ row.label }}</small>
                  <div class="project">
                    <ProcessIcon
                      :icon="processFor(row.item)?.icon"
                      :name="row.item.processName || processFor(row.item)?.processName || '未指定'"
                      :category="row.item.processCategory || processFor(row.item)?.processCategory"
                      :size="24"
                    />
                    <span>{{
                      row.item.processName || processFor(row.item)?.processName || '-'
                    }}</span>
                  </div>
                </td>
                <td>{{ row.item.materialName || '-' }}</td>
                <td>{{ bomMaterialSpec(row.item) }}</td>
                <td>{{ row.item.dieLocation || '-' }}</td>
              </tr>
              <tr v-if="!page.length">
                <td colspan="5" class="center">暂无物料</td>
              </tr>
            </tbody>
          </table>
          <footer class="paper-footer">{{ pageIndex + 1 }}／{{ pages.length }}页</footer>
        </article>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ProcessIcon from '@/components/ProcessIcon.vue'
import { useEnabledProcesses } from '@/composables/useEnabledProcesses'
import {
  bomMaterialSpec,
  bomPrintRows,
  paginateBomRows,
  type BomPrintItem,
  type BomPrintRow,
} from '@/components/print/bomPrintLayout'

const props = defineProps<{
  modelValue: boolean
  items?: BomPrintItem[]
  bomCode?: string
  bomName?: string
  bomVersion?: string
  productId?: number | null
  productCode?: string
  productName?: string
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const { processes } = useEnabledProcesses()
const processMap = computed(
  () => new Map(processes.value.map((item) => [String(item.processId), item]))
)
const processFor = (item: BomPrintItem) => processMap.value.get(String(item.processId))
const rows = computed(() => bomPrintRows(props.items || []))
const pages = ref<BomPrintRow[][]>([[]])
const printContent = ref<HTMLElement>()
const measuring = ref(true)
const printing = ref(false)
let layoutVersion = 0

async function layoutPages() {
  const version = ++layoutVersion
  if (!visible.value) return
  measuring.value = true
  // 先在与纸张相同宽度下测量每行；测量时不合并，给长项目名称预留足够高度。
  pages.value = [rows.value.map((row) => ({ ...row, span: 1 }))]
  await nextTick()
  await document.fonts.ready
  if (version !== layoutVersion || !visible.value) return
  const paper = printContent.value?.querySelector<HTMLElement>('.bom-paper')
  if (!paper) return
  const height = (selector: string) =>
    paper.querySelector(selector)?.getBoundingClientRect().height || 0
  const rowHeights = Array.from(
    paper.querySelectorAll('[data-material-row]'),
    (row) => row.getBoundingClientRect().height
  )
  // 276mm 比 A4 的可打印高度（297 - 2*10）少 1mm，另留边框/舍入余量。
  const capacity =
    (276 * 96) / 25.4 - height('.paper-header') - height('thead') - height('.paper-footer') - 10
  pages.value = paginateBomRows(rows.value, rowHeights, capacity)
  await nextTick()
  if (version === layoutVersion) measuring.value = false
}

watch(
  () => [
    props.modelValue,
    props.items,
    props.productName,
    props.productCode,
    props.bomCode,
    props.bomVersion,
    processes.value,
  ],
  layoutPages,
  { deep: true, flush: 'post' }
)

async function doPrint() {
  await layoutPages()
  if (measuring.value || !printContent.value) return
  printing.value = true
  // 独立文档打印，避免 BOM 编辑弹窗和主页面混入，也不改变父页面的全局打印样式。
  const frame = document.createElement('iframe')
  frame.style.cssText = 'position:fixed;width:0;height:0;border:0;'
  frame.title = 'BOM 材料表打印'
  document.body.appendChild(frame)
  try {
    const doc = frame.contentDocument!
    const win = frame.contentWindow!
    doc.open()
    doc.write(
      '<!doctype html><html><head><meta charset="UTF-8"><title>BOM 材料表</title></head><body></body></html>'
    )
    doc.close()
    const styles = Array.from(document.querySelectorAll('style, link[rel="stylesheet"]'))
    const loaded: Promise<void>[] = []
    for (const style of styles) {
      const clone = style.cloneNode(true) as HTMLElement
      if (clone.tagName === 'LINK')
        loaded.push(
          new Promise((resolve, reject) => {
            clone.onload = () => resolve()
            clone.onerror = () => reject(new Error('打印样式加载失败'))
          })
        )
      doc.head.appendChild(clone)
    }
    const printStyle = doc.createElement('style')
    printStyle.textContent =
      '@page { size: A4 portrait; margin: 10mm; } html, body { margin: 0; padding: 0; background: white; } .bom-print-content, .bom-print-content * { visibility: visible !important; }'
    doc.head.appendChild(printStyle)
    // SvgIcon 使用页面内 symbol；打印文档也需要同一份 sprite。
    document.querySelectorAll('svg').forEach((svg) => {
      if (!svg.querySelector('symbol')) return
      const sprite = svg.cloneNode(true) as SVGElement
      sprite.style.cssText = 'position:absolute;width:0;height:0;overflow:hidden;'
      doc.body.appendChild(sprite)
    })
    doc.body.appendChild(printContent.value.cloneNode(true))
    await Promise.all(loaded)
    await doc.fonts.ready
    await new Promise<void>((resolve) => win.requestAnimationFrame(() => resolve()))
    win.addEventListener('afterprint', () => frame.remove(), { once: true })
    win.focus()
    win.print()
  } catch (error) {
    console.error('BOM 材料表打印失败:', error)
    frame.remove()
    ElMessage.error('打印准备失败，请重试')
  } finally {
    printing.value = false
  }
}
</script>

<style scoped>
.print-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-size: 12px;
}
.preview-scroll {
  overflow: auto;
  background: #eee;
}
.bom-print-content {
  color: #252525;
  font-family: SimSun, 'Songti SC', serif;
  font-size: 13px;
  line-height: 1.2;
}
.bom-paper {
  box-sizing: border-box;
  width: 190mm;
  min-height: 276mm;
  margin: 12px auto;
  padding: 0;
  background: white;
  display: flex;
  flex-direction: column;
}
.paper-header {
  flex: none;
  overflow-wrap: anywhere;
}
h1 {
  height: 65px;
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 27px;
  font-weight: 400;
  letter-spacing: 5px;
  text-decoration: underline;
  text-underline-offset: 7px;
}
h1 span {
  font-size: 13px;
  letter-spacing: 0;
}
.paper-meta {
  padding: 6px 4px;
}
.material-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  flex: none;
}
.material-table th,
.material-table td {
  border: 1px solid #333;
  box-sizing: border-box;
  padding: 2px 4px;
  font-weight: 400;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.material-table th {
  height: 32px;
  font-size: 14px;
}
.material-table td {
  height: 22px;
}
.center {
  text-align: center;
}
.project {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.project span {
  min-width: 0;
}
.paper-footer {
  margin-top: auto;
  text-align: right;
  padding: 6px 0;
  flex: none;
  font-size: 12px;
}
@media print {
  .bom-paper {
    margin: 0;
    break-after: page;
  }
  .bom-paper:last-child {
    break-after: auto;
  }
  .material-table thead {
    display: table-header-group;
  }
  .material-table tr {
    break-inside: avoid;
  }
  .bom-print-content {
    print-color-adjust: exact;
    -webkit-print-color-adjust: exact;
  }
}
</style>

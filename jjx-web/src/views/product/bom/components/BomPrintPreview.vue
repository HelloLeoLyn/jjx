<template>
  <el-dialog
    v-model="visible"
    :title="`BOM 作业指导书预览 · ${bomCode || ''}`"
    width="1200px"
    append-to-body
    :close-on-click-modal="false"
    destroy-on-close
    class="bom-print-dialog"
  >
    <div class="print-toolbar no-print">
      <el-button type="primary" icon="Printer" @click="doPrint">🖨 打印</el-button>
      <span class="print-tip">打印时仅输出作业指导书内容（@media print 控制）</span>
    </div>

    <!-- ==================== 打印区域（57.webp 样式） ==================== -->
    <div class="guide-sheet">
      <div class="gs-header">
        <div class="gs-title">薄膜开关 作业指导书</div>
        <div class="gs-sub">
          产品：{{ productName || '-' }}（{{ productCode || '-' }}） · BOM：{{ bomCode || '-' }} V{{ bomVersion || '-' }}
        </div>
      </div>

      <div class="gs-body">
        <!-- 左栏：物料清单 -->
        <div class="gs-left">
          <div class="gs-section-title">一、物料清单</div>
          <table class="gs-table">
            <thead>
              <tr>
                <th style="width:36px">序号</th>
                <th style="width:70px">项目</th>
                <th style="width:110px">材料</th>
                <th style="width:120px">规格及模数</th>
                <th style="width:70px">用量</th>
                <th style="width:56px">单位</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(it, i) in materialList" :key="i">
                <td class="c">{{ i + 1 }}</td>
                <td>{{ it.remark || '-' }}</td>
                <td>{{ it.materialName || '-' }}</td>
                <td>{{ buildSpec(it) }}</td>
                <td class="c">{{ fmt(it.quantity) }}</td>
                <td class="c">{{ it.unit || '-' }}</td>
              </tr>
              <tr v-if="!materialList.length">
                <td colspan="6" class="empty">暂无物料</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 右栏：作业流程 -->
        <div class="gs-right">
          <div class="gs-section-title">二、作业流程</div>
          <div v-for="group in flowGroups" :key="group.label" class="flow-group">
            <div class="flow-label">{{ group.label }}</div>
            <ol class="flow-list">
              <li v-for="(p, i) in group.items" :key="i">
                <span class="flow-name">{{ p.processName || '-' }}</span>
                <span v-if="p.customLaborHours || p.standardLaborHours" class="flow-time">
                  {{ fmtHours(p.customLaborHours || p.standardLaborHours) }}h
                </span>
                <span v-if="p.description" class="flow-note">{{ p.description }}</span>
              </li>
              <li v-if="!group.items.length" class="flow-empty">—</li>
            </ol>
          </div>
        </div>
      </div>

      <div class="gs-footer">
        <span>工程确认：__________ 日期：__________</span>
        <span class="gs-date">打印时间：{{ printTime }}</span>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { productRouteApi } from '@/api/product/routing'

/**
 * BOM 作业指导书打印预览（57.webp 样式）
 * 左栏：物料清单（来自当前 BOM items）
 * 右栏：作业流程（来自产品当前工艺路线，按 process_category 分组）
 */
const props = defineProps<{
  modelValue: boolean
  items?: any[]
  bomCode?: string
  bomName?: string
  bomVersion?: string
  productId?: number | null
  productCode?: string
  productName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const printTime = ref('')
const routingItems = ref<any[]>([])

// 物料清单（当前 BOM items）
const materialList = computed(() => props.items || [])

// 规格及模数：specification + 模数（如 285×360mm=4PCS）
function buildSpec(it: any): string {
  const parts: string[] = []
  if (it.specification) parts.push(it.specification)
  if (it.moduleQty) parts.push(`模数${it.moduleQty}`)
  return parts.join(' ') || '-'
}

// 作业流程：按 process_category 分组
const flowGroups = computed(() => {
  const cats = [
    { key: 'PANEL', label: '面板作业流程' },
    { key: 'UP_LINE', label: '上线作业流程' },
    { key: 'DOWN_LINE', label: '下线作业流程' },
  ]
  const sorted = [...routingItems.value].sort(
    (a, b) => (a.processOrder || 999) - (b.processOrder || 999)
  )
  return cats.map((c) => ({
    label: c.label,
    items: sorted.filter((p) => (p.processCategory || '') === c.key),
  }))
})

function fmt(v: any): string {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : String(n)
}

function fmtHours(v: any): string {
  const n = Number(v) || 0
  return String(n)
}

// 打开时拉取产品当前工艺路线（作业流程数据源）
watch(
  () => props.modelValue,
  async (v) => {
    if (!v) return
    printTime.value = new Date().toLocaleString('zh-CN')
    routingItems.value = []
    if (!props.productId) return
    try {
      const res = await productRouteApi.getCurrentProductRoute(props.productId)
      routingItems.value = res?.data?.items || []
    } catch (e: any) {
      ElMessage.warning('加载产品工艺路线失败，作业流程可能为空')
    }
  }
)

// 打印
function doPrint() {
  window.print()
}
</script>

<style scoped>
.print-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.print-tip {
  font-size: 12px;
  color: #909399;
}

/* ==================== 作业指导书（57.webp 样式） ==================== */
.guide-sheet {
  background: #fff;
  border: 2px solid #333;
  padding: 18px 20px;
  font-size: 13px;
  color: #000;
  line-height: 1.6;
}
.gs-header {
  text-align: center;
  border-bottom: 2px solid #333;
  padding-bottom: 8px;
  margin-bottom: 12px;
}
.gs-title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 4px;
}
.gs-sub {
  font-size: 12px;
  margin-top: 4px;
}
.gs-body {
  display: flex;
  gap: 16px;
}
.gs-left {
  flex: 1.1;
  min-width: 0;
}
.gs-right {
  flex: 1;
  min-width: 0;
  border-left: 2px solid #333;
  padding-left: 16px;
}
.gs-section-title {
  font-weight: 700;
  font-size: 14px;
  margin-bottom: 8px;
  border-bottom: 1px solid #333;
  padding-bottom: 4px;
}
.gs-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.gs-table th,
.gs-table td {
  border: 1px solid #333;
  padding: 4px 6px;
  text-align: left;
}
.gs-table th {
  background: #eee;
  font-weight: 600;
}
.gs-table .c {
  text-align: center;
}
.gs-table .empty {
  text-align: center;
  color: #999;
  padding: 12px 0;
}
.flow-group {
  margin-bottom: 10px;
}
.flow-label {
  font-weight: 700;
  font-size: 13px;
  background: #eee;
  border: 1px solid #333;
  padding: 3px 8px;
  margin-bottom: 4px;
}
.flow-list {
  margin: 0;
  padding-left: 20px;
  font-size: 12px;
}
.flow-list li {
  margin-bottom: 3px;
}
.flow-name {
  font-weight: 600;
}
.flow-time {
  color: #555;
  margin-left: 6px;
  font-size: 11px;
}
.flow-note {
  display: block;
  color: #666;
  font-size: 11px;
}
.flow-empty {
  color: #999;
  list-style: none;
}
.gs-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
  border-top: 1px dashed #333;
  padding-top: 8px;
  font-size: 12px;
}

/* ==================== 打印控制 ==================== */
@media print {
  .no-print {
    display: none !important;
  }
  .bom-print-dialog {
    position: static !important;
  }
  .bom-print-dialog :deep(.el-dialog) {
    width: 100% !important;
    max-height: none !important;
    box-shadow: none !important;
    border: none !important;
  }
  .bom-print-dialog :deep(.el-dialog__header),
  .bom-print-dialog :deep(.el-dialog__footer) {
    display: none !important;
  }
  .bom-print-dialog :deep(.el-overlay) {
    position: static !important;
    background: #fff !important;
    overflow: visible !important;
  }
  body {
    print-color-adjust: exact;
    -webkit-print-color-adjust: exact;
  }
}
</style>

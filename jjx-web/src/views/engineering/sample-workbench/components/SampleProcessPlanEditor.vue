<template>
  <div class="plan-editor">
    <!-- 标准工序库：左侧竖栏，默认 300px，可拖动调宽（220–400）（dev-20261024-010） -->
    <div class="picker-col" :class="{ collapsed: pickerCollapsed }" :style="{ width: pickerColWidth }">
      <el-card class="picker-card" shadow="never">
        <div class="picker-bar">
          <div class="picker-bar-title">
            <span class="picker-title">标准工序</span>
            <span class="desc">点选 / 拖到右侧计划</span>
          </div>
          <el-button class="picker-toggle" size="small" link @click="togglePicker">
            {{ pickerCollapsed ? '展开' : '收起' }}
          </el-button>
        </div>
        <div v-show="!pickerCollapsed" class="picker-body" data-test="picker-body">
          <WorkProjectPicker :readonly="readonly" />
        </div>
      </el-card>
    </div>

    <div
      v-show="!pickerCollapsed"
      class="picker-resizer"
      data-test="picker-resizer"
      title="拖动调整标准工序栏宽度"
      @mousedown.prevent="startResize"
    ></div>

    <el-card class="col-plan" shadow="never">
      <template #header>
        <span style="font-weight: 600">打样工序计划</span>
        <span class="desc">{{
          hasUnsavedChanges ? '有未保存修改（需保存打样方案）' : '已保存'
        }}</span>
        <el-button
          v-if="!readonly"
          size="small"
          :type="batchMode ? 'warning' : 'default'"
          icon="Grid"
          @click="toggleBatchMode"
          style="float: right; margin-top: -2px"
        >
          {{ batchMode ? '退出批量编辑' : '批量编辑' }}
        </el-button>
        <el-button
          v-if="!readonly"
          type="success"
          size="small"
          :loading="savingPlan"
          title="保存打样方案：一并提交组装工序、印刷工序、BOM 独立材料与工程备注"
          @click="savePlan"
          style="float: right; margin-top: -2px"
          >💾 保存打样方案</el-button
        >
      </template>

      <!-- 常用物料快捷区 -->
      <FrequentMaterialsBar :frequent-materials="frequentMaterials" @add="addFrequentMaterial" />

      <el-tabs v-model="activePlanTab" type="border-card" style="min-height: 420px">
        <el-tab-pane
          v-for="tab in planTabs"
          :key="tab.value"
          :name="tab.value"
          :label="`${tab.label}（${cardsByTab(tab.value).length}）`"
        >
          <div class="plan-scroll" @dragover.prevent @drop="onPlanDrop">
            <SampleProcessPlanItem
              v-for="(pc, idx) in cardsByTab(tab.value)"
              :key="pc.uid"
              :pc="pc"
              :index="idx"
              :batch-mode="batchMode"
              :batch-selected="batchSelected.has(pc.uid)"
              :save-state-text="saveStateText"
              :parse-materials="parseMaterials"
              :readonly="readonly"
              @toggle-select="(v: boolean) => toggleBatchSelect(pc, v)"
              @remove-item="(i: number) => removeCardItem(pc, i)"
              @update-index="(it: any, n: number) => onUpdateIndex(pc, it, n)"
              @open-picker="openCardPicker(pc)"
              @search-material="(q: string, row: any) => searchMaterials(q, row)"
              @material-selected="(row: any, v: any) => onMaterialSelected(row, v)"
              @material-cleared="(row: any) => onMaterialCleared(row)"
              @load-more-material="(row: any) => onMaterialLoadMore(row)"
              @select-visible="(row: any, v: boolean) => onSelectVisibleChange(row, v)"
              @add-material-row="(key: string) => addMaterialRow(pc, key)"
              @create-material="(m: any) => openMaterialCreate(pc, m)"
              @edit="startEdit(pc)"
              @cancel-edit="cancelEdit(pc)"
              @delete="removePlanCard(pc)"
              @finish-edit="finishEdit(pc)"
              @card-dragover="onCardDragOver"
              @card-dragleave="onCardDragLeave"
              @card-drop="onCardDrop"
            />
            <div v-if="!cardsByTab(tab.value).length" class="plan-drop-hint">🖐 拖拽到这里</div>
            <!-- 有卡片时底部保留新建卡片 drop 区 -->
            <div v-else class="plan-drop-hint plan-drop-hint-small">＋ 拖拽工序到这里新建卡片</div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 批量操作栏（含全选） -->
      <BatchToolbar
        v-if="!readonly"
        :batch-mode="batchMode"
        :batch-selected="batchSelected"
        :batch-category="batchCategory"
        :category-options="categoryOptions"
        :current-tab-cards="cardsByTab(activePlanTab || '')"
        @select-all="toggleBatchSelectAll"
        @batch-material="openBatchMaterial"
        @batch-delete="batchDelete"
        @update:batch-category="(v: string | null) => (batchCategory = v)"
        @apply-category="applyBatchCategory"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRefs } from 'vue'
import WorkProjectPicker from '@/views/sales/sample-order/components/WorkProjectPicker.vue'
import { clampPickerWidth, readPickerWidth, savePickerWidth } from './samplePlanLayout'
import SampleProcessPlanItem from './SampleProcessPlanItem.vue'
import FrequentMaterialsBar from './FrequentMaterialsBar.vue'
import BatchToolbar from './BatchToolbar.vue'

/**
 * 工序计划面板（dev-20260811-008 组件化）
 * 左：标准工序选择器；右：工序卡片列表（拖拽/编辑/批量）
 * 计划控制器提供共享草稿操作；工作台只接入一次业务边界。
 */
export interface PlanEditorController {
  batchMode: boolean
  batchSelected: Set<string>
  batchCategory: string | null
  savingPlan: boolean
  hasUnsavedChanges: boolean
  frequentMaterials: any[]
  planTabs: any[]
  categoryOptions: any[]
  cardsByTab: (v: string) => any[]
  saveStateText: (pc: any) => string
  parseMaterials: (json?: string | null) => any[]
  toggleBatchMode: () => void
  toggleBatchSelect: (pc: any, v: boolean) => void
  toggleBatchSelectAll: () => void
  savePlan: () => void
  addFrequentMaterial: (fm: any) => void
  onPlanDrop: (e: DragEvent) => void
  onCardDrop: (e: DragEvent, pc: any) => void
  onCardDragOver: (pc: any) => void
  onCardDragLeave: (pc: any) => void
  removeCardItem: (pc: any, idx: number) => void
  removePlanCard: (pc: any) => void
  cancelEdit: (pc: any) => void
  finishEdit: (pc: any) => void
  addMaterialRow: (pc: any, itemKey?: string) => void
  startEdit: (pc: any) => void
  openCardPicker: (pc: any) => void
  openBatchMaterial: () => void
  batchDelete: () => void
  applyBatchCategory: (cat: string | undefined) => void
  onUpdateIndex: (pc: any, item: any, n: number) => void
  searchMaterials: (q: string, row: any) => void
  onMaterialSelected: (row: any, v: any) => void
  onMaterialCleared: (row: any) => void
  onMaterialLoadMore: (row: any) => void
  onSelectVisibleChange: (row: any, v: boolean) => void
  openMaterialCreate: (pc: any, m: any) => void
}
const props = defineProps<{ controller: PlanEditorController; readonly?: boolean }>()
const {
  batchMode,
  batchSelected,
  batchCategory,
  savingPlan,
  hasUnsavedChanges,
  frequentMaterials,
  planTabs,
  categoryOptions,
  cardsByTab,
  saveStateText,
  parseMaterials,
  toggleBatchMode,
  toggleBatchSelect,
  toggleBatchSelectAll,
  savePlan,
  addFrequentMaterial,
  onPlanDrop,
  onCardDrop,
  onCardDragOver,
  onCardDragLeave,
  removeCardItem,
  removePlanCard,
  cancelEdit,
  finishEdit,
  addMaterialRow,
  startEdit,
  openCardPicker,
  openBatchMaterial,
  batchDelete,
  applyBatchCategory,
  onUpdateIndex,
  searchMaterials,
  onMaterialSelected,
  onMaterialCleared,
  onMaterialLoadMore,
  onSelectVisibleChange,
  openMaterialCreate,
} = toRefs(props.controller)

// activePlanTab 双向绑定（v-model:active-plan-tab）
const activePlanTab = defineModel<string>('activePlanTab')

/** 顶部标准工序库默认收起，避免挤压下方工序材料编辑区（dev-20261022-014）。 */
// 左侧标准工序栏：默认展开、宽 300（dev-20261024-010）
const pickerCollapsed = ref(false)
const pickerWidth = ref(readPickerWidth(typeof localStorage === 'undefined' ? null : localStorage))
const pickerColWidth = computed(() => `${pickerCollapsed.value ? 40 : pickerWidth.value}px`)
const togglePicker = () => {
  pickerCollapsed.value = !pickerCollapsed.value
}

let resizeStartX = 0
let resizeStartWidth = 0
const onResizeMove = (event: MouseEvent) => {
  pickerWidth.value = clampPickerWidth(resizeStartWidth + (event.clientX - resizeStartX))
}
const stopResize = () => {
  window.removeEventListener('mousemove', onResizeMove)
  window.removeEventListener('mouseup', stopResize)
  savePickerWidth(typeof localStorage === 'undefined' ? null : localStorage, pickerWidth.value)
}
const startResize = (event: MouseEvent) => {
  if (pickerCollapsed.value) return
  resizeStartX = event.clientX
  resizeStartWidth = pickerWidth.value
  window.addEventListener('mousemove', onResizeMove)
  window.addEventListener('mouseup', stopResize)
}
onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onResizeMove)
  window.removeEventListener('mouseup', stopResize)
})

// 模板中直接使用 props 定义的属性名（Vue 自动可用，保持响应性）
// 注意：不在此处解构 props，避免失去响应式
</script>

<style scoped>
.col-plan :deep(.el-card__header) {
  position: sticky;
  top: 0;
  z-index: 5;
  background: #fff;
}

.plan-editor {
  display: flex;
  align-items: stretch;
  gap: 0;
  margin-bottom: 14px;
}
.picker-col {
  flex: 0 0 auto;
  min-width: 40px;
  max-width: 400px;
  display: flex;
  transition: width 0.15s ease;
}
.picker-col.collapsed .picker-title,
.picker-col.collapsed .desc {
  display: none;
}
.picker-col.collapsed .picker-bar {
  justify-content: center;
  padding: 0;
}
.picker-col .el-card {
  height: 100%;
}
.picker-card {
  width: 100%;
}
.picker-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  /* 左栏很窄，左右内边距压到 5px，把宽度让给工序卡片（dev-20261024-011） */
  padding-left: 5px;
  padding-right: 5px;
}
.picker-resizer {
  flex: 0 0 6px;
  cursor: col-resize;
  border-left: 1px solid #ebeef5;
}
.picker-resizer:hover {
  background: #f2f6fc;
}
.picker-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.picker-bar-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}
.picker-toggle {
  flex-shrink: 0;
}
.picker-body {
  margin-top: 10px;
  min-height: 0;
  flex: 1;
  overflow: auto;
}
.col-plan {
  flex: 1 1 auto;
  min-width: 0;
  margin-left: 10px;
}
.desc {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}
.plan-scroll {
  max-height: 560px;
  min-height: 260px;
  overflow-y: auto;
  padding-right: 6px;
  padding-bottom: 40px;
  scrollbar-width: thin;
}
.plan-scroll::-webkit-scrollbar {
  width: 6px;
}
.plan-scroll::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;
}
.plan-drop-hint {
  border: 2px dashed #c0c4cc;
  border-radius: 10px;
  padding: 60px 0;
  text-align: center;
  color: #909399;
  font-size: 16px;
  background: #fafbfc;
}
.plan-drop-hint-small {
  padding: 20px 0;
  font-size: 13px;
}
</style>

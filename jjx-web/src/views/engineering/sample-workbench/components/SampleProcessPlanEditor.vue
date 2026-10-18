<template>
  <div class="mid-row" :class="{ 'picker-collapsed': !pickerExpanded }">
    <el-card class="col-picker" shadow="never">
      <template #header>
        <span style="font-weight: 600">添加标准工序</span>
        <span class="desc">选结构 → 拖拽工序到右侧卡片组合</span>
        <el-button
          size="small"
          class="picker-toggle"
          style="float: right; margin-top: -2px"
          :title="pickerExpanded ? '收起标准工序库，释放编辑宽度' : '展开标准工序库，拖拽或点选标准工序'"
          @click="pickerExpanded = !pickerExpanded"
        >
          {{ pickerExpanded ? '收起标准工序库' : '展开标准工序库' }}
        </el-button>
      </template>
      <div v-show="pickerExpanded" class="picker-body" data-test="picker-body">
        <WorkProjectPicker :readonly="readonly" />
      </div>
    </el-card>

    <el-card class="col-plan" shadow="never">
      <template #header>
        <span style="font-weight: 600">打样工序计划</span>
        <span class="desc">{{ hasUnsavedChanges ? '有未保存修改（需保存打样方案）' : '已保存' }}</span>
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
              @select-visible="(row: any, v: boolean) => onSelectVisibleChange(row, v)"
              @add-material-row="addMaterialRow(pc)"
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
import { ref, toRefs } from 'vue'
import WorkProjectPicker from '@/views/sales/sample-order/components/WorkProjectPicker.vue'
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
  addMaterialRow: (pc: any) => void
  startEdit: (pc: any) => void
  openCardPicker: (pc: any) => void
  openBatchMaterial: () => void
  batchDelete: () => void
  applyBatchCategory: (cat: string | undefined) => void
  onUpdateIndex: (pc: any, item: any, n: number) => void
  searchMaterials: (q: string, row: any) => void
  onMaterialSelected: (row: any, v: any) => void
  onSelectVisibleChange: (row: any, v: boolean) => void
  openMaterialCreate: (pc: any, m: any) => void
}
const props = defineProps<{ controller: PlanEditorController; readonly?: boolean }>()
const { batchMode, batchSelected, batchCategory, savingPlan, hasUnsavedChanges, frequentMaterials, planTabs, categoryOptions, cardsByTab, saveStateText, parseMaterials, toggleBatchMode, toggleBatchSelect, toggleBatchSelectAll, savePlan, addFrequentMaterial, onPlanDrop, onCardDrop, onCardDragOver, onCardDragLeave, removeCardItem, removePlanCard, cancelEdit, finishEdit, addMaterialRow, startEdit, openCardPicker, openBatchMaterial, batchDelete, applyBatchCategory, onUpdateIndex, searchMaterials, onMaterialSelected, onSelectVisibleChange, openMaterialCreate } = toRefs(props.controller)


// activePlanTab 双向绑定（v-model:active-plan-tab）
const activePlanTab = defineModel<string>('activePlanTab')

// 标准工序选择器：默认收起，只保留可发现的「展开标准工序库」入口；展开后仍可拖拽/点选
const pickerExpanded = ref(false)

// 模板中直接使用 props 定义的属性名（Vue 自动可用，保持响应性）
// 注意：不在此处解构 props，避免失去响应式
</script>

<style scoped>
.col-plan :deep(.el-card__header) { position: sticky; top: 0; z-index: 5; background: #fff; }

.mid-row {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
}
.mid-row.picker-collapsed {
  flex-direction: column;
}
.mid-row.picker-collapsed .col-picker {
  width: 100%;
}
.picker-body {
  min-height: 240px;
}
.col-picker {
  width: 400px;
  flex-shrink: 0;
}
.col-plan {
  flex: 1;
  min-width: 0;
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

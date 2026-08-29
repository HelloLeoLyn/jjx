<template>
  <div class="mid-row">
    <el-card class="col-picker" shadow="never">
      <template #header>
        <span style="font-weight: 600">选择作业项目</span>
        <span class="desc">选结构 → 拖拽工序到右侧卡片组合</span>
      </template>
      <WorkProjectPicker />
    </el-card>

    <el-card class="col-plan" shadow="never">
      <template #header>
        <span style="font-weight: 600">打样工序计划</span>
        <span class="desc">在哪个标签编辑，卡片就属于哪个项目结构</span>
        <el-button
          size="small"
          :type="batchMode ? 'warning' : 'default'"
          icon="Grid"
          @click="toggleBatchMode"
          style="float: right; margin-top: -2px"
        >
          {{ batchMode ? '退出批量编辑' : '批量编辑' }}
        </el-button>
        <el-button
          type="success"
          size="small"
          :loading="savingPlan"
          @click="savePlan"
          style="float: right; margin-top: -2px"
          >💾 保存工序计划</el-button
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
            <ProcessCard
              v-for="(pc, idx) in cardsByTab(tab.value)"
              :key="pc.uid"
              :pc="pc"
              :index="idx"
              :batch-mode="batchMode"
              :batch-selected="batchSelected.has(pc.uid)"
              :save-state-text="saveStateText"
              :parse-materials="parseMaterials"
              @toggle-select="(v: boolean) => toggleBatchSelect(pc, v)"
              @advance="advancePlan(pc)"
              @remove-item="(i: number) => removeCardItem(pc, i)"
              @update-index="(it: any, n: number) => onUpdateIndex(pc, it, n)"
              @open-picker="openCardPicker(pc)"
              @search-material="(q: string, row: any) => searchMaterials(q, row)"
              @material-selected="(row: any, v: any) => onMaterialSelected(row, v)"
              @select-visible="(row: any, v: boolean) => onSelectVisibleChange(row, v)"
              @add-material-row="addMaterialRow(pc)"
              @create-material="(m: any) => openMaterialCreate(pc, m)"
              @edit="startEdit(pc)"
              @cancel-edit="pc.editing = false"
              @delete="removePlanCard(pc)"
              @save="saveCard(pc)"
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
import WorkProjectPicker from '@/views/sales/sample-order/components/WorkProjectPicker.vue'
import ProcessCard from './ProcessCard.vue'
import FrequentMaterialsBar from './FrequentMaterialsBar.vue'
import BatchToolbar from './BatchToolbar.vue'

/**
 * 工序计划面板（dev-20260811-008 组件化）
 * 左：作业项目选择器；右：工序卡片列表（拖拽/编辑/批量）
 * 全部数据与逻辑来自 composable（props/emits 透传）
 */
const props = defineProps<{
  batchMode: boolean
  batchSelected: Set<string>
  batchCategory: string | null
  savingPlan: boolean
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
  advancePlan: (pc: any) => void
  saveCard: (pc: any) => void
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
  readonly?: boolean
}>()

// activePlanTab 双向绑定（v-model:active-plan-tab）
const activePlanTab = defineModel<string>('activePlanTab')

// 模板中直接使用 props 定义的属性名（Vue 自动可用，保持响应性）
// 注意：不在此处解构 props，避免失去响应式
</script>

<style scoped>
.mid-row {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
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

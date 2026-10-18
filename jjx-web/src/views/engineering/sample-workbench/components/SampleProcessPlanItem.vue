<template>
  <div
    class="plan-card"
    :class="{ 'drag-over': pc.draggingOver, 'batch-selected': batchSelected }"
    @dragover.prevent="onCardDragOver(pc)"
    @dragleave="onCardDragLeave(pc)"
    @drop.stop="onCardDrop($event, pc)"
  >
    <!-- 行1：序号 + 状态 + 操作 -->
    <div class="pc-head">
      <el-checkbox
        v-if="batchMode"
        :model-value="batchSelected"
        @change="(v: boolean | string | number) => $emit('toggle-select', !!v)"
        class="batch-check"
      />
      <span class="pc-num">{{ index + 1 }}</span>
      <span class="save-state" :class="`save-${pc.saveState || 'synced'}`">{{
        saveStateText(pc)
      }}</span>
      <div v-if="!readonly" class="pc-head-right">
        <el-button v-if="!pc.editing" size="small" link type="primary" @click="$emit('edit')">编辑</el-button>
        <el-dropdown trigger="click" @command="$emit('delete')">
          <el-button size="small" link aria-label="更多操作">更多 ⋯</el-button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item command="delete">删除卡片</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </div>
    </div>
    <ProcessContentEditor
      :pc="pc" :readonly="readonly"
      @remove-item="(index) => $emit('remove-item', index)"
      @update-index="(item, value) => $emit('update-index', item, value)"
      @open-picker="$emit('open-picker')"
    />
    <div v-if="!pc.editing && materialSummary" class="material-summary">
      <span>{{ materialSummary }}</span>
      <el-button size="small" link type="primary" @click="materialsExpanded = !materialsExpanded">{{ materialsExpanded ? '收起' : '展开' }}</el-button>
    </div>
    <ProcessMaterialEditor v-if="pc.editing || materialsExpanded"
      :pc="pc" :readonly="readonly" :parse-materials="parseMaterials"
      @search-material="(q, row) => $emit('search-material', q, row)"
      @material-selected="(row, value) => $emit('material-selected', row, value)"
      @material-cleared="(row) => $emit('material-cleared', row)"
      @load-more-material="(row) => $emit('load-more-material', row)"
      @select-visible="(row, visible) => $emit('select-visible', row, visible)"
      @add-material-row="$emit('add-material-row')"
      @remove-material="(index) => pc.materialRows.splice(index, 1)"
      @create-material="(row) => $emit('create-material', row)"
    />
    <!-- 行4：整道工序备注；单工序属于自身，复合工序属于整个组合 -->
    <div v-if="pc.editing || pc.operationRemark" class="pc-row">
      <el-input
        v-if="!readonly && pc.editing"
        v-model="pc.operationRemark"
        type="textarea"
        :rows="2"
        placeholder="可选，如：一车一模；复合工序时作用于整道工序"
      />
      <div v-else class="pc-desc-readonly">{{ pc.operationRemark }}</div>
    </div>
    <div v-if="!readonly && pc.editing" class="pc-footer">
      <span class="edit-hint">完成编辑只结束本地编辑，尚未保存；请点「保存打样方案」保存后生效</span>
      <el-button size="small" @click="$emit('cancel-edit')">取消修改</el-button>
      <el-button
        size="small"
        type="primary"
        title="只结束本卡编辑，不代表已保存；点「保存打样方案」保存后才生效"
        @click="$emit('finish-edit')"
        >完成编辑</el-button
      >
    </div>
  </div>
</template>

<script setup lang="ts">
import ProcessContentEditor from './ProcessContentEditor.vue'
import ProcessMaterialEditor from './ProcessMaterialEditor.vue'
import { computed, ref } from 'vue'

/**
 * 打样工序计划项（dev-20260811-008 组件化核心）
 * 自包含：状态/标准工序/材料/描述/编辑操作
 * 预留：卡片类型字段（标准/自定义），印刷工序后续扩展
 */
const props = defineProps<{
  pc: any
  index: number
  batchMode: boolean
  batchSelected: boolean
  saveStateText: (pc: any) => string
  parseMaterials: (json?: string | null) => any[]
  readonly?: boolean
}>()

const materialsExpanded = ref(false)
const materialSummary = computed(() => {
  const rows = props.parseMaterials(props.pc.materials)
  if (!rows.length) return ''
  return `${rows.slice(0, 2).map((row: any) => row.name).join('、')}${rows.length > 2 ? '…' : ''}（${rows.length}项材料）`
})

const emit = defineEmits<{
  (e: 'toggle-select', v: boolean): void
  (e: 'remove-item', idx: number): void
  (e: 'update-index', item: any, n: number): void
  (e: 'open-picker'): void
  (e: 'search-material', q: string, row: any): void
  (e: 'material-selected', row: any, v: any): void
  (e: 'material-cleared', row: any): void
  (e: 'load-more-material', row: any): void
  (e: 'select-visible', row: any, v: boolean): void
  (e: 'add-material-row'): void
  (e: 'create-material', m: any): void
  (e: 'edit'): void
  (e: 'cancel-edit'): void
  (e: 'delete'): void
  (e: 'finish-edit'): void
  (e: 'card-dragover', pc: any): void
  (e: 'card-dragleave', pc: any): void
  (e: 'card-drop', ev: DragEvent, pc: any): void
}>()

function onCardDragOver(pc: any) {
  emit('card-dragover', pc)
}
function onCardDragLeave(pc: any) {
  emit('card-dragleave', pc)
}
function onCardDrop(e: DragEvent, pc: any) {
  emit('card-drop', e, pc)
}
</script>

<style scoped>
.material-summary { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: #606266; font-size: 12px; }

.plan-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 8px 12px;
  margin-bottom: 8px;
  box-shadow: 0 1px 4px rgba(31, 45, 61, 0.05);
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
}
.plan-card.drag-over {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.25);
  background: #f5f9ff;
}
.plan-card.batch-selected {
  border-color: #e6a23c;
  box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.25);
}
.plan-card:hover {
  box-shadow: 0 4px 12px rgba(31, 45, 61, 0.1);
  border-color: #c6d9f5;
}

.pc-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.pc-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #79bbff);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex-shrink: 0;
}
.pc-head-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.batch-check {
  margin-right: 2px;
}
.save-state {
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.save-synced {
  color: #67c23a;
}
.save-dirty {
  color: #909399;
}
.save-saving {
  color: #409eff;
}
.save-error {
  color: #f56c6c;
}

.pc-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 8px;
}
.pc-row-label {
  font-size: 12px;
  color: #909399;
  width: 72px;
  flex-shrink: 0;
  line-height: 26px;
}
.pc-items {
  flex: 1;
  min-width: 0;
}
.process-item-tag {
  margin-right: 6px;
  margin-bottom: 4px;
  cursor: default;
}
.instruction-trigger {
  display: inline-flex;
  cursor: text;
}
.pc-mat {
  flex: 1;
  min-width: 0;
}
.pc-desc-readonly {
  flex: 1;
  font-size: 12px;
  color: #606266;
  line-height: 1.7;
  background: #fafbfc;
  border-radius: 4px;
  padding: 6px 8px;
  min-height: 26px;
  white-space: pre-wrap;
}
.pc-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px dashed #e8ecf1;
  padding-top: 8px;
}
.edit-hint {
  margin-right: auto;
  font-size: 12px;
  color: #e6a23c;
  line-height: 1.5;
}
</style>

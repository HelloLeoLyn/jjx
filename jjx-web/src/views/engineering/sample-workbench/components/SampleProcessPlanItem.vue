<template>
  <div
    class="plan-card"
    :class="{ 'drag-over': pc.draggingOver, 'batch-selected': batchSelected }"
    @dragover.prevent="onCardDragOver(pc)"
    @dragleave="onCardDragLeave(pc)"
    @drop.stop="onCardDrop($event, pc)"
  >
    <ProcessMaterialGroupEditor
      :pc="pc"
      :index="index"
      :batch-mode="batchMode"
      :batch-selected="batchSelected"
      :save-state-text="saveStateText"
      @toggle-select="(selected: boolean) => $emit('toggle-select', selected)"
      @edit="$emit('edit')"
      @delete="$emit('delete')"
      :readonly="readonly"
      :parse-materials="parseMaterials"
      @remove-item="(index: number) => $emit('remove-item', index)"
      @open-picker="$emit('open-picker')"
      @material-selected="(row: any, value: any) => $emit('material-selected', row, value)"
      @material-cleared="(row: any) => $emit('material-cleared', row)"
      @add-material-row="(key: string) => $emit('add-material-row', key)"
      @create-material="(row: any) => $emit('create-material', row)"
    />
    <div v-if="!readonly && pc.editing" class="pc-footer">
      <span class="edit-hint"
        >完成编辑只结束本地编辑，尚未保存；请点「保存打样方案」保存后生效</span
      >
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
import ProcessMaterialGroupEditor from './ProcessMaterialGroupEditor.vue'

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

const emit = defineEmits<{
  (e: 'toggle-select', v: boolean): void
  (e: 'remove-item', idx: number): void
  (e: 'update-index', item: any, n: number): void
  (e: 'open-picker'): void
  (e: 'material-selected', row: any, v: any): void
  (e: 'material-cleared', row: any): void
  (e: 'add-material-row', itemKey: string): void
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

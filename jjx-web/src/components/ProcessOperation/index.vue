<template>
  <div class="process-operation" :class="{ 'is-composite': items.length > 1 }">
    <div class="operation-children">
      <template v-for="(item, index) in items" :key="item.key ?? index">
        <span v-if="dropIndex === index" class="drop-insert-line" aria-hidden="true"></span>
        <span v-if="index > 0" class="plus">+</span>
        <div
          class="operation-child"
          :class="{ 'is-draggable': draggable }"
          :data-item-index="index"
          @dragover="onDragOver($event, index)"
          @drop="onDrop($event, index)"
        >
          <span
            v-if="draggable"
            class="drag-handle"
            draggable="true"
            title="拖动调整工序顺序或移动到其他组合"
            @dragstart="emit('item-dragstart', $event, index)"
            @dragend="emit('item-dragend')"
            >⋮⋮</span
          >
          <div class="icon-cell">
            <el-popover
              v-if="
                editable && item.icon && (item.workInstruction || item.hasWorkInstruction === 1)
              "
              placement="bottom-start"
              :teleported="false"
              :fallback-placements="['bottom-start']"
              :offset="4"
              :width="300"
              trigger="click"
            >
              <el-input
                :model-value="item.workInstruction"
                clearable
                maxlength="80"
                placeholder="作业说明，如：冲窗口灯孔"
                @input="(value: string) => emit('update:work-instruction', index, value)"
              />
              <div class="common-work-instructions">
                <span>常用：</span>
                <button
                  v-for="text in commonWorkInstructions"
                  :key="text"
                  type="button"
                  @click="selectWorkInstruction(index, text)"
                >
                  {{ text }}
                </button>
              </div>
              <template #reference>
                <IconStepBadge
                  :icon="item.icon"
                  :size="32"
                  :index="item.workInstruction ? null : item.indexNumber"
                  :work-instruction="item.workInstruction"
                  :editable="false"
                />
              </template>
            </el-popover>
            <IconStepBadge
              v-else-if="item.icon"
              :icon="item.icon"
              :size="32"
              :index="item.indexNumber"
              :work-instruction="item.workInstruction"
              :editable="editable"
              @update:index="(value: number) => emit('update:index', index, value)"
            />
            <span v-else class="icon-placeholder">工</span>
          </div>
          <button
            v-if="editable"
            class="remove-item"
            type="button"
            title="删除子工序"
            @click.stop="emit('remove', index)"
          >
            ×
          </button>
        </div>
      </template>
      <span
        v-if="dropIndex === items.length"
        class="drop-insert-line"
        aria-hidden="true"
      ></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import IconStepBadge from '@/components/IconStepBadge/index.vue'
import type { ProcessOperationItem } from './types'

const commonWorkInstructions = [
  '线路外形',
  '冲窗口灯孔',
  '一车一模',
  '一车二模',
  '撕保护膜',
  '贴保护膜',
]
const props = withDefaults(
  defineProps<{
    items: ProcessOperationItem[]
    remark?: string
    draggable?: boolean
    editable?: boolean
    dropIndex?: number | null
  }>(),
  { remark: '', draggable: false, editable: false, dropIndex: null }
)
const emit = defineEmits<{
  (event: 'item-dragstart', sourceEvent: DragEvent, itemIndex: number): void
  (event: 'item-dragend'): void
  (event: 'item-dragover', sourceEvent: DragEvent, itemIndex: number): void
  (event: 'item-drop', sourceEvent: DragEvent, itemIndex: number): void
  (event: 'update:index', itemIndex: number, value: number): void
  (event: 'update:work-instruction', itemIndex: number, value: string): void
  (event: 'remove', itemIndex: number): void
}>()
function selectWorkInstruction(itemIndex: number, value: string) {
  emit('update:work-instruction', itemIndex, value)
}
function onDragOver(event: DragEvent, itemIndex: number) {
  if (!props.draggable) return
  event.preventDefault()
  emit('item-dragover', event, itemIndex)
}
function onDrop(event: DragEvent, itemIndex: number) {
  if (!props.draggable) return
  event.preventDefault()
  event.stopPropagation()
  emit('item-drop', event, itemIndex)
}
</script>

<style scoped>
.process-operation {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: flex-start;
  box-sizing: border-box;
  overflow: visible;
}
.operation-children,
.operation-child {
  display: flex;
  min-width: 0;
  align-items: center;
}
.operation-children {
  gap: 8px;
}
.drop-insert-line {
  width: 2px;
  height: 38px;
  flex: 0 0 2px;
  border-radius: 1px;
  background: var(--el-color-primary);
}
.operation-child {
  position: relative;
  align-items: flex-end;
  gap: 0;
}
.operation-child.is-draggable {
  padding: 4px;
  border-radius: 6px;
  transition: background-color 0.15s ease;
}
.operation-child.is-draggable:hover {
  background: var(--el-fill-color-light);
}
.drag-handle {
  flex: 0 0 auto;
  color: var(--el-text-color-placeholder);
  font-size: 15px;
  line-height: 32px;
  cursor: grab;
  user-select: none;
}
.drag-handle:hover {
  color: var(--el-color-primary);
}
.drag-handle:active {
  cursor: grabbing;
}
.icon-cell {
  position: relative;
  display: flex;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  align-items: flex-end;
  justify-content: flex-start;
  color: var(--el-color-primary);
}
.icon-placeholder {
  display: flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  border: 1px dashed currentColor;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1;
}
.common-work-instructions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.common-work-instructions button {
  padding: 2px 6px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 3px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
}
.common-work-instructions button:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.remove-item {
  position: absolute;
  top: -10px;
  right: -9px;
  display: none;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--el-color-danger);
  color: #fff;
  font-size: 15px;
  line-height: 17px;
  cursor: pointer;
}
.operation-child:hover .remove-item {
  display: block;
}
.plus {
  flex: 0 0 auto;
  color: var(--el-text-color-placeholder);
  font-size: 14px;
}
</style>

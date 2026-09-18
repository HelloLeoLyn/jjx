<template>
  <div class="process-operation" :class="{ 'is-composite': items.length > 1 }">
    <draggable
      v-if="draggable"
      v-model="localItems"
      class="operation-children"
      item-key="key"
      :animation="150"
      ghost-class="operation-ghost"
      :group="{ name: 'routing-ops', pull: true, put: true }"
      :data-group-key="JSON.stringify(groupKey)"
      @end="onEnd"
      @add="onAdd"
      @remove="onRemove"
    >
      <template #item="{ element: item, index }">
        <div
          class="operation-child is-draggable"
          :data-item-index="index"
          :data-item-key="JSON.stringify(item.key)"
        >
          <span v-if="index > 0" class="plus">+</span>
          <span class="drag-handle" title="拖动调整工序顺序或移动到其他组合">⋮⋮</span>
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
                  @click="emit('update:work-instruction', index, text)"
                >
                  {{ text }}
                </button>
              </div>
              <template #reference
                ><IconStepBadge
                  :icon="item.icon"
                  :size="32"
                  :index="item.workInstruction ? null : item.indexNumber"
                  :work-instruction="item.workInstruction"
                  :editable="false"
              /></template>
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
    </draggable>
    <div v-else class="operation-children">
      <template v-for="(item, index) in items" :key="item.key ?? index">
        <span v-if="index > 0" class="plus">+</span>
        <div class="operation-child" :data-item-index="index">
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
                  @click="emit('update:work-instruction', index, text)"
                >
                  {{ text }}
                </button>
              </div>
              <template #reference
                ><IconStepBadge
                  :icon="item.icon"
                  :size="32"
                  :index="item.workInstruction ? null : item.indexNumber"
                  :work-instruction="item.workInstruction"
                  :editable="false"
              /></template>
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import draggable from 'vuedraggable'
import type { SortableEvent } from 'sortablejs'
import IconStepBadge from '@/components/IconStepBadge/index.vue'
import type { ProcessOperationItem } from './types'

type GroupKey = number | string
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
    groupKey?: GroupKey
    syncToken?: number
  }>(),
  { remark: '', draggable: false, editable: false, groupKey: '', syncToken: 0 }
)
const emit = defineEmits<{
  (
    event: 'items-reordered',
    payload: { groupKey: GroupKey; oldIndex: number; newIndex: number }
  ): void
  (
    event: 'item-added',
    payload: {
      groupKey: GroupKey
      fromGroupKey: GroupKey
      oldIndex: number
      newIndex: number
      key: ProcessOperationItem['key']
    }
  ): void
  (
    event: 'item-removed',
    payload: {
      groupKey: GroupKey
      toGroupKey: GroupKey
      oldIndex: number
      newIndex: number
      key: ProcessOperationItem['key']
    }
  ): void
  (event: 'update:index', itemIndex: number, value: number): void
  (event: 'update:work-instruction', itemIndex: number, value: string): void
  (event: 'remove', itemIndex: number): void
}>()

const localItems = ref<ProcessOperationItem[]>(props.draggable ? [...props.items] : [])
watch(
  () => props.syncToken,
  () => {
    if (!props.draggable) return
    localItems.value = [...props.items]
  }
)

function readJsonData(element: HTMLElement, name: 'groupKey' | 'itemKey'): GroupKey | undefined {
  const value = element.dataset[name]
  if (value == null) return undefined
  try {
    return JSON.parse(value) as GroupKey
  } catch {
    return value
  }
}
function onEnd(event: SortableEvent) {
  if (event.from !== event.to || event.oldIndex == null || event.newIndex == null) return
  emit('items-reordered', {
    groupKey: props.groupKey,
    oldIndex: event.oldIndex,
    newIndex: event.newIndex,
  })
}
function onAdd(event: SortableEvent) {
  if (event.oldIndex == null || event.newIndex == null) return
  emit('item-added', {
    groupKey: props.groupKey,
    fromGroupKey: readJsonData(event.from, 'groupKey') ?? '',
    oldIndex: event.oldIndex,
    newIndex: event.newIndex,
    key: readJsonData(event.item, 'itemKey'),
  })
}
function onRemove(event: SortableEvent) {
  if (event.oldIndex == null || event.newIndex == null) return
  emit('item-removed', {
    groupKey: props.groupKey,
    toGroupKey: readJsonData(event.to, 'groupKey') ?? '',
    oldIndex: event.oldIndex,
    newIndex: event.newIndex,
    key: readJsonData(event.item, 'itemKey'),
  })
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
.operation-child {
  position: relative;
  align-items: flex-end;
  gap: 0;
}
.operation-children > .operation-child:first-child .plus {
  display: none;
}
.operation-child.is-draggable {
  padding: 4px;
  border-radius: 6px;
  cursor: grab;
  transition: background-color 0.15s ease;
}
.operation-child.is-draggable:hover {
  background: var(--el-fill-color-light);
}
.operation-child.is-draggable:active {
  cursor: grabbing;
}
.operation-ghost {
  background: var(--el-color-primary-light-8);
  opacity: 0.45;
}
.drag-handle {
  flex: 0 0 auto;
  color: var(--el-text-color-placeholder);
  font-size: 15px;
  line-height: 32px;
  user-select: none;
}
.drag-handle:hover {
  color: var(--el-color-primary);
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

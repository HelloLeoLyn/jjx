<template>
  <div class="operation-card" :class="[`mode-${mode}`, { 'is-composite': items.length > 1 }]">
    <div class="operation-children">
      <template v-for="(item, index) in items" :key="item.key ?? index">
        <span v-if="index > 0" class="plus">+</span>
        <div
          class="operation-child"
          :class="{ 'is-draggable': draggable }"
          @dragover="onDragOver($event, index)"
          @drop="onDrop($event, index)"
        >
          <span
            v-if="draggable"
            class="drag-handle"
            draggable="true"
            title="拖动调整工序顺序或移动到其他组合"
            @dragstart="emit('item-dragstart', $event, index)"
            >⋮⋮</span
          >
          <div class="icon-cell">
            <SvgIcon v-if="item.icon" :name="item.icon" :size="32" />
            <span v-else class="icon-placeholder">工</span>
            <el-popover
            v-if="editable && !item.workInstruction && item.indexNumber != null"
              placement="top"
              :width="210"
              trigger="click"
            >
              <el-input-number
                :model-value="item.indexNumber"
                :min="0"
                :max="999"
                controls-position="right"
                size="small"
                @change="
                  (value: number | undefined) => value != null && emit('update:index', index, value)
                "
              />
              <template #reference>
                <span class="index-number is-editable" title="点击修改数字下标">{{
                  item.indexNumber
                }}</span>
              </template>
            </el-popover>
            <span v-else-if="!item.workInstruction && item.indexNumber != null" class="index-number">{{
              item.indexNumber
            }}</span>
          </div>
          <el-popover
            v-if="mode === 'table' && editable && (item.workInstruction || (item.hasWorkInstruction === 1 && item.indexNumber == null))"
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
              >{{ text }}</button>
            </div>
            <template #reference>
              <span class="table-subscript is-editable">
                {{ item.workInstruction || '＋作业说明' }}
              </span>
            </template>
          </el-popover>
          <span
            v-else-if="mode === 'table' && item.workInstruction"
            class="table-subscript"
          >
            {{ item.workInstruction }}
          </span>
          <div v-else-if="mode !== 'table'" class="process-copy">
            <el-popover
              v-if="editable && (item.hasWorkInstruction === 1 || item.workInstruction)"
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
                >{{ text }}</button>
              </div>
              <template #reference>
                <div
                  class="work-instruction is-editable"
                  :class="{ 'is-empty': !item.workInstruction }"
                >
                  {{ item.workInstruction || '＋作业说明' }}
                </div>
              </template>
            </el-popover>
            <div
              v-else-if="item.workInstruction"
              class="work-instruction"
              :title="item.workInstruction"
            >
              {{ item.workInstruction }}
            </div>
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

    <div v-if="remark" class="operation-remark" :title="remark">{{ remark }}</div>
  </div>
</template>

<script setup lang="ts">
import SvgIcon from '@/components/SvgIcon/index.vue'
import type { ProcessOperationCardItem } from './types'

const commonWorkInstructions = ['线路外形', '冲窗口灯孔', '一车一模', '一车二模', '撕保护膜', '贴保护膜']

const props = withDefaults(
  defineProps<{
    items: ProcessOperationCardItem[]
    remark?: string
    mode?: 'default' | 'table'
    draggable?: boolean
    editable?: boolean
  }>(),
  {
    remark: '',
    mode: 'default',
    draggable: false,
    editable: false,
  }
)

const emit = defineEmits<{
  (event: 'item-dragstart', sourceEvent: DragEvent, itemIndex: number): void
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
.operation-card {
  display: flex;
  min-height: 84px;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  box-sizing: border-box;
  padding: 18px 20px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: 0 4px 14px rgb(0 0 0 / 5%);
}

.operation-card.mode-table {
  min-height: 34px;
  justify-content: flex-start;
  gap: 0;
  padding: 0;
  overflow: visible;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.mode-table .operation-children {
  gap: 8px;
}

.mode-table .operation-child {
  align-items: flex-end;
  /* 下标紧贴图标右侧，避免表格中图标与说明被拉开 */
  gap: 0;
}

.mode-table .icon-cell {
  width: 30px;
  height: 30px;
  flex-basis: 30px;
  align-items: flex-end;
}

.mode-table .icon-placeholder {
  width: 24px;
  height: 24px;
  font-size: 11px;
}

.mode-table .table-subscript {
  max-width: 84px;
  margin-bottom: -5px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 10px;
  line-height: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mode-table .table-subscript.is-editable {
  cursor: pointer;
}

.mode-table .table-subscript.is-editable:hover {
  color: var(--el-color-primary);
}

.mode-table .operation-remark {
  display: none;
}

.mode-table .plus {
  font-size: 14px;
}

.operation-children,
.operation-child {
  display: flex;
  min-width: 0;
  align-items: center;
}

.operation-children {
  gap: 16px;
}

.operation-child {
  position: relative;
  gap: 8px;
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
  width: 44px;
  height: 40px;
  flex: 0 0 44px;
  align-items: flex-end;
  justify-content: flex-start;
  color: var(--el-color-primary);
}

.icon-placeholder {
  display: flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  border: 1px dashed currentColor;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1;
}

.index-number {
  position: absolute;
  right: 0;
  bottom: -1px;
  min-width: 17px;
  height: 17px;
  box-sizing: border-box;
  padding: 0 3px;
  border: 1px solid currentColor;
  border-radius: 9px;
  background: var(--el-bg-color);
  color: inherit;
  font-size: 10px;
  font-weight: 700;
  line-height: 15px;
  text-align: center;
}

.index-number.is-editable {
  cursor: pointer;
}

.process-copy {
  display: flex;
  min-width: 96px;
  height: 40px;
  flex-direction: column;
  justify-content: flex-end;
}

.process-name {
  max-width: 150px;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-instruction {
  height: 16px;
  max-width: 150px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-instruction.is-editable {
  border-radius: 3px;
  cursor: pointer;
}

.work-instruction.is-editable:hover {
  background: var(--el-fill-color);
  color: var(--el-color-primary);
}

.work-instruction.is-empty {
  color: var(--el-text-color-placeholder);
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
  font-size: 18px;
}

.operation-remark {
  max-width: 160px;
  flex: 0 0 auto;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.is-composite .operation-remark {
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color-lighter);
}
</style>

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
      <div class="pc-head-right">
        <el-tag v-if="pc.status === SampleProcessStatusEnum.DONE.value" size="small" type="success"
          >✓ 已完成</el-tag
        >
        <el-tag
          v-else-if="pc.status === SampleProcessStatusEnum.DOING.value"
          size="small"
          type="warning"
          >⏳ 进行中</el-tag
        >
        <el-tag v-else size="small" type="info">待做</el-tag>
        <span
          v-if="pc.status === SampleProcessStatusEnum.DONE.value && pc.durationMinutes"
          style="color: #909399; font-size: 12px"
          >⏱ {{ pc.durationMinutes }}分钟</span
        >
      </div>
    </div>
    <ProcessContentEditor
      :pc="pc" :readonly="readonly"
      @remove-item="(index) => $emit('remove-item', index)"
      @update-index="(item, value) => $emit('update-index', item, value)"
      @open-picker="$emit('open-picker')"
    />
    <ProcessMaterialEditor
      :pc="pc" :readonly="readonly" :parse-materials="parseMaterials"
      @search-material="(q, row) => $emit('search-material', q, row)"
      @material-selected="(row, value) => $emit('material-selected', row, value)"
      @select-visible="(row, visible) => $emit('select-visible', row, visible)"
      @add-material-row="$emit('add-material-row')"
      @remove-material="(index) => pc.materialRows.splice(index, 1)"
      @create-material="(row) => $emit('create-material', row)"
    />
    <!-- 行4：整道工序备注；单工序属于自身，复合工序属于整个组合 -->
    <div class="pc-row">
      <el-input
        v-if="!readonly && pc.editing"
        v-model="pc.operationRemark"
        type="textarea"
        :rows="2"
        placeholder="可选，如：一车一模；复合工序时作用于整道工序"
      />
      <div v-else class="pc-desc-readonly">{{ pc.operationRemark || '—' }}</div>
    </div>
    <!-- 右下角：删除/保存/编辑 -->
    <div class="pc-footer">
      <el-button v-if="!readonly && !pc.editing" size="small" @click="$emit('edit')"
        >✏️ 编辑</el-button
      >
      <template v-else-if="!readonly">
        <el-button size="small" @click="$emit('cancel-edit')">取消</el-button>
        <el-button size="small" type="danger" plain @click="$emit('delete')">🗑 删除</el-button>
        <el-button size="small" type="primary" :loading="pc.savingCard" @click="$emit('save')"
          >💾 保存</el-button
        >
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import ProcessContentEditor from './ProcessContentEditor.vue'
import ProcessMaterialEditor from './ProcessMaterialEditor.vue'
import { SampleProcessStatusEnum } from '@/enums/sales'

/**
 * 打样工序计划项（dev-20260811-008 组件化核心）
 * 自包含：状态/标准工序/材料/描述/编辑操作
 * 预留：卡片类型字段（标准/自定义），印刷工序后续扩展
 */
defineProps<{
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
  (e: 'search-material', q: string, row: any): void
  (e: 'material-selected', row: any, v: any): void
  (e: 'select-visible', row: any, v: boolean): void
  (e: 'add-material-row'): void
  (e: 'create-material', m: any): void
  (e: 'edit'): void
  (e: 'cancel-edit'): void
  (e: 'delete'): void
  (e: 'save'): void
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
  padding: 12px 14px;
  margin-bottom: 12px;
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
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px dashed #e8ecf1;
  padding-top: 8px;
}
</style>

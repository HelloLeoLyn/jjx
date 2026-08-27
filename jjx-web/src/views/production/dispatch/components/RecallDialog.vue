<template>
  <el-dialog v-model="visible" :title="target ? `收回 · ${orderProcessLabel(target)}` : '收回'" width="760px" append-to-body>
    <template v-if="target">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="工单/工序">{{
          orderProcessLabel(target)
        }}</el-descriptions-item>
        <el-descriptions-item label="当前执行人">{{
          target.assigneeName || '未分配'
        }}</el-descriptions-item>
      </el-descriptions>

      <div class="recall-title">可收回子任务（剩余 &gt; 0 的行可选，任意层级）</div>
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="rows"
        row-key="taskId"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :lazy="true"
        :load="loadChildren"
        max-height="360"
        class="recall-table"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="40" :selectable="selectable" />
        <el-table-column label="执行人" min-width="130">
          <template #default="{ row }">{{ row.assigneeName || '未分配' }}</template>
        </el-table-column>
        <el-table-column label="已分配" width="90" align="right">
          <template #default="{ row }">{{ fmtQty(row.assignedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="可收回" width="90" align="right">
          <template #default="{ row }">{{ fmtQty(row.remainingQuantity) }}</template>
        </el-table-column>
        <el-table-column label="收回数量" width="150">
          <template #default="{ row }">
            <el-input-number
              v-if="selectable(row)"
              v-model="qtyMap[row.taskId]"
              :min="0.01"
              :max="Math.max(Number(row.remainingQuantity || 0), 0.01)"
              :precision="2"
              :step="1"
              size="small"
              style="width: 130px"
            />
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ row.statusLabel || row.status || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button
              v-if="selectable(row)"
              type="primary"
              link
              size="small"
              :loading="submitLoading"
              @click="handleRecall(row)"
              >收回</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <div class="recall-summary">
        已选 {{ selectedRows.length }} 项 · 本次收回合计 {{ fmtQty(summary) }}
      </div>
      <el-input
        v-model="remark"
        type="textarea"
        :rows="2"
        maxlength="200"
        placeholder="备注（可选）"
      />
    </template>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="submitLoading"
        :disabled="!selectedRows.length"
        @click="handleBatchRecall"
        >批量收回({{ selectedRows.length }})</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TreeRow } from '../types'
import { useRecall } from '../composables/useRecall'
import { fmtQty, orderProcessLabel } from '../utils/taskFormatters'

const props = defineProps<{
  modelValue: boolean
  target: TreeRow | null
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success', taskId: number): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const {
  loading,
  rows,
  tableRef,
  selectedRows,
  qtyMap,
  summary,
  remark,
  loadChildren,
  onSelectionChange,
  selectable,
  handleRecall,
  handleBatchRecall,
  submitLoading,
} = useRecall({
  target: computed(() => props.target),
  visible,
  onSuccess: async (taskId) => {
    emit('success', taskId)
  },
})
</script>

<style scoped>
.recall-title {
  margin: 12px 0 8px;
  font-weight: 500;
}
.recall-table {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}
.recall-summary {
  margin: 10px 0 8px;
  font-size: 13px;
  color: #606266;
}
.text-muted {
  color: #909399;
}
</style>

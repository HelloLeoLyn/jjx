<template>
  <el-drawer
    :model-value="visible"
    title="操作流水"
    size="680px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading">
      <!-- Execution 上下文 -->
      <template v-if="execution">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="工单">{{ execution.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ execution.processName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工序总量">{{ fmt(execution.inputQuantity) }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <div class="flow-hint">任务之前发生过什么：分配 / 收回 / 退回 / 报工 / 撤销报工（按工序聚合，时间升序）</div>

      <el-table :data="eventList" size="small">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.time) }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="110">
          <template #default="{ row }">{{ row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="动作" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="actionTag(row.action)">{{ row.actionLabel || row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="涉及人员" width="110">
          <template #default="{ row }">{{ row.targetName || '-' }}</template>
        </el-table-column>
        <el-table-column label="数量" width="80" align="right">
          <template #default="{ row }">{{ row.quantity != null ? fmt(row.quantity) : '-' }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="110">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !eventList.length" description="暂无操作记录" :image-size="50" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { taskNodeApi } from '@/api/production/taskNode'
import type { TaskTreeEventVO } from '@/types/production/taskNode'
import type { OperationExecutionVO } from '@/types/production/operationExecution'

const props = defineProps<{
  visible: boolean
  executionId: number
  execution?: OperationExecutionVO | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
}>()

const loading = ref(false)
const eventList = ref<TaskTreeEventVO[]>([])

const actionTag = (action?: string): any => {
  return { ASSIGN: 'primary', RECALL: 'warning', RETURN: 'warning', WORK_REPORT: 'success', WORK_REPORT_CANCEL: 'danger' }[action || ''] || 'info'
}
const fmtTime = (t?: string) => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')
const fmt = (v?: number | null): string => String(Number(v || 0))

const load = async () => {
  loading.value = true
  try {
    const res: any = await taskNodeApi.events(props.executionId)
    eventList.value = res?.data || []
  } catch {
    eventList.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) load()
  },
)
</script>

<style scoped>
.flow-hint {
  color: #909399;
  font-size: 12px;
  margin: 10px 0;
}
</style>

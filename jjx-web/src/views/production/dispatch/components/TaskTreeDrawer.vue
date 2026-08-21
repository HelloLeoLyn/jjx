<template>
  <el-drawer :model-value="visible" title="任务树" size="860px" append-to-body @update:model-value="emit('update:visible', $event)">
    <div v-loading="loading">
      <!-- Execution 顶部信息 -->
      <template v-if="execution">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="工单">{{ execution.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ execution.processName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(execution.executionStatus) }}</el-descriptions-item>
          <el-descriptions-item label="计划数量">{{ fmt(execution.inputQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="已完成">{{ fmt(execution.outputQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="待完成">{{ fmt(execution.remainingQuantity) }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <!-- 任务树（后端 TaskNode 投影，整棵可看；操作仅限自己的节点） -->
      <div class="tree-title">任务树</div>
      <el-table
        v-if="tree"
        :data="[tree]"
        row-key="taskNodeId"
        :tree-props="{ children: 'children' }"
        default-expand-all
        size="small"
      >
        <el-table-column label="人员" min-width="130">
          <template #default="{ row }">{{ row.assigneeName || `用户${row.assigneeId}` }}</template>
        </el-table-column>
        <el-table-column label="任务数量" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.taskQuantity) }}</template>
        </el-table-column>
        <el-table-column label="已完成" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.selfReported) }}</template>
        </el-table-column>
        <el-table-column label="已下分" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.childOccupied) }}</template>
        </el-table-column>
        <el-table-column label="自己持有" width="90" align="right">
          <template #default="{ row }">{{ fmt(ownHeld(row)) }}</template>
        </el-table-column>
        <el-table-column label="待完成" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.remainingQuantity) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ row.statusLabel || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canAssign(row)" link type="primary" size="small" @click="openAssign(row)">分配任务</el-button>
            <!-- 退回剩余：自己的非 root 节点 + production:task:return + selfRemaining>0（更多菜单，非主按钮） -->
            <el-dropdown v-if="canReturn(row)" trigger="click" size="small" @command="(cmd: string) => onMoreCommand(cmd, row)">
              <span class="more-btn">···</span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="return">退回剩余</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="!loading" description="暂无任务树" :image-size="50" />
    </div>

    <!-- 分配任务弹窗（自己的节点） -->
    <AssignTaskDialog
      v-model:visible="assignVisible"
      :execution-id="execution?.executionId || 0"
      :parent-node-id="assignNodeId"
      :title="assignTitle"
      @changed="load"
    />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AssignTaskDialog from './AssignTaskDialog.vue'
import { taskNodeApi } from '@/api/production/taskNode'
import { hasPermi } from '@/directives'
import { useUserStore } from '@/store/modules/user'
import type { TaskNodeVO } from '@/types/production/taskNode'
import type { OperationExecutionVO } from '@/types/production/operationExecution'

const props = defineProps<{
  visible: boolean
  executionId: number
  execution?: OperationExecutionVO | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'changed'): void
}>()

const userStore = useUserStore()

const loading = ref(false)
const tree = ref<TaskNodeVO | null>(null)
const assignVisible = ref(false)
const assignNodeId = ref(0)
const assignTitle = ref('分配任务')

const STATUS_LABELS: Record<number, string> = {
  0: '待执行', 1: '准备中', 2: '执行中', 3: '已暂停', 4: '已完成',
  5: '已跳过', 6: '已取消', 7: '已超期', 8: '异常中', 9: '待确认',
}
function statusLabel(s?: number): string {
  return STATUS_LABELS[s ?? 0] || String(s ?? 0)
}
function fmt(v?: number | null): string {
  return String(Number(v || 0))
}
function ownHeld(row: TaskNodeVO): number {
  return Number(row.taskQuantity || 0) - Number(row.recalledQuantity || 0) - Number(row.childOccupied || 0)
}
function statusTag(status?: string): any {
  return { ACTIVE: 'success', COMPLETED: 'info', CANCELLED: 'danger' }[status || ''] || 'info'
}

const isAdminOrTaskAdmin = () => hasPermi('*:*:*') || hasPermi('production:task:admin')

/** 分配任务按钮：自己的节点（或超管/task:admin）+ task:assign + availableToAssign>0 */
const canAssign = (row: TaskNodeVO) => {
  return Number(row.availableToAssign || 0) > 0
    && hasPermi('production:task:assign')
    && (isAdminOrTaskAdmin() || userStore.userId === row.assigneeId)
}

const openAssign = (row: TaskNodeVO) => {
  assignNodeId.value = row.taskNodeId
  assignTitle.value = `${row.assigneeName || '节点'} · 分配任务`
  assignVisible.value = true
}

/** 退回剩余：自己的非 root 节点 + production:task:return + selfRemaining>0；退回后父节点容量自动恢复 */
const canReturn = (row: TaskNodeVO) => {
  return !!row.parentNodeId
    && Number(row.remainingQuantity || 0) > 0
    && hasPermi('production:task:return')
    && (isAdminOrTaskAdmin() || userStore.userId === row.assigneeId)
}

const handleReturn = async (row: TaskNodeVO) => {
  const maxQty = Number(row.remainingQuantity || 0)
  if (maxQty <= 0) return
  try {
    const { value } = await ElMessageBox.prompt(
      `退回「${row.assigneeName || row.assigneeId}」持有的剩余任务，可退回数量 ${fmt(maxQty)}；退回后父节点可分配容量自动恢复`,
      '退回剩余',
      { inputPattern: /^\d+(\.\d+)?$/, inputErrorMessage: '请输入数字' },
    )
    const qty = Number(value)
    if (qty <= 0 || qty > maxQty) {
      ElMessage.warning(`退回数量必须在 0 < x <= ${fmt(maxQty)} 之间`)
      return
    }
    await taskNodeApi.returnNode(row.taskNodeId, qty)
    ElMessage.success('已退回')
    await load()
    emit('changed')
  } catch (e: any) {
    if (e === 'cancel' || e?.toString().includes('cancel')) return
    ElMessage.error(e?.message || '退回失败')
  }
}

const onMoreCommand = (cmd: string, row: TaskNodeVO) => {
  if (cmd === 'return') handleReturn(row)
}

const load = async () => {
  loading.value = true
  try {
    const res: any = await taskNodeApi.getTree(props.executionId)
    tree.value = res?.data || null
  } catch (e: any) {
    ElMessage.error(e?.message || '加载任务树失败')
    tree.value = null
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
.tree-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin: 14px 0 8px;
}
.more-btn {
  cursor: pointer;
  color: #909399;
  font-weight: 600;
  letter-spacing: 2px;
  padding: 0 4px;
}
.more-btn:hover {
  color: #409eff;
}
</style>

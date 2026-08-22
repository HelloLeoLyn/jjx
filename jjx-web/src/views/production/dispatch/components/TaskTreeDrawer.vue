<template>
  <el-drawer :model-value="visible" title="任务树" size="860px" append-to-body @update:model-value="emit('update:visible', $event)">
    <div v-loading="loading">
      <!-- Execution 顶部信息 -->
      <template v-if="execution">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="工单">{{ execution.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ execution.processName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(execution.executionStatus) }}</el-descriptions-item>
          <el-descriptions-item label="工序总量">{{ fmt(execution.inputQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="已完成">{{ fmt(execution.outputQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="待完成">{{ fmt(execution.remainingQuantity) }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <el-tabs v-model="activeTab" style="margin-top: 8px">
        <!-- ============ 任务树 ============ -->
        <el-tab-pane label="任务树" name="tree">
          <div class="tree-legend">
            <span><i class="dot dot-mine"></i>我的节点</span>
            <span><i class="dot dot-my-child"></i>我分配的下级</span>
            <span><i class="dot dot-other"></i>其他节点</span>
          </div>
          <el-table
            v-if="displayTree.length"
            :data="displayTree"
            row-key="taskNodeId"
            :tree-props="{ children: 'children' }"
            :row-class-name="rowClassName"
            default-expand-all
            size="small"
          >
            <el-table-column label="人员" min-width="130">
              <template #default="{ row }">{{ row.assigneeName || '未知人员' }}</template>
            </el-table-column>
            <el-table-column label="任务数量" width="90" align="right">
              <template #default="{ row }">{{ fmt(row.taskQuantity) }}</template>
            </el-table-column>
            <el-table-column label="已完成" width="90" align="right">
              <template #default="{ row }">{{ fmt(row.selfReported) }}</template>
            </el-table-column>
            <el-table-column label="待完成" width="90" align="right">
              <template #default="{ row }">{{ fmt(row.remainingQuantity) }}</template>
            </el-table-column>
            <el-table-column label="已分给下级" width="100" align="right">
              <template #default="{ row }">{{ fmt(row.childOccupied) }}</template>
            </el-table-column>
            <el-table-column label="自己持有" width="90" align="right">
              <template #default="{ row }">{{ fmt(ownHeld(row)) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTag(row.status)">{{ row.statusLabel || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
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
          <el-empty v-else-if="!loading" description="未分配" :image-size="50" />
        </el-tab-pane>

        <!-- ============ 流水 ============ -->
        <el-tab-pane label="流水" name="events">
          <el-table v-loading="eventsLoading" :data="eventList" size="small">
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
            <el-table-column label="备注" min-width="120">
              <template #default="{ row }">{{ row.remark || '-' }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!eventsLoading && !eventList.length" description="暂无操作记录" :image-size="50" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 节点详情 -->
    <NodeDetailDialog
      v-model:visible="detailVisible"
      :node="detailNode"
      :execution="execution"
      :root="tree"
    />

    <!-- 分配任务弹窗（自己的节点） -->
    <AssignTaskDialog
      v-model:visible="assignVisible"
      :execution-id="execution?.executionId || 0"
      :parent-node-id="assignNodeId"
      :title="assignTitle"
      :order-no="execution?.orderNo"
      :process-name="execution?.processName"
      @changed="load"
    />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AssignTaskDialog from './AssignTaskDialog.vue'
import NodeDetailDialog from './NodeDetailDialog.vue'
import { taskNodeApi } from '@/api/production/taskNode'
import { hasPermi } from '@/directives'
import { useUserStore } from '@/store/modules/user'
import type { TaskNodeVO, TaskTreeEventVO } from '@/types/production/taskNode'
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
const activeTab = ref('tree')

/**
 * 系统根（assigneeId 为空）不展示为任务行：
 * - 有真实人员子节点 → 直接展示一级子节点（含其子树）
 * - 无子节点 → 空数组 → 显示"未分配"
 * 历史人员根（脏数据）仍按原样展示。
 */
const displayTree = computed<TaskNodeVO[]>(() => {
  if (!tree.value) return []
  if (!tree.value.assigneeId && (tree.value.children || []).length) return tree.value.children || []
  return [tree.value]
})

// ============ 节点三色区分（TT-FINAL-05：我的=绿 / 我直接分配的下级=蓝 / 其他=灰） ============
/** nodeId → 父节点 assigneeId（用于识别“我直接分配的下级”） */
const parentAssigneeMap = computed<Map<number, number | null | undefined>>(() => {
  const m = new Map<number, number | null | undefined>()
  const walk = (n: TaskNodeVO | null) => {
    if (!n) return
    for (const c of n.children || []) {
      m.set(c.taskNodeId, n.assigneeId)
      walk(c)
    }
  }
  walk(tree.value)
  return m
})

const rowClassName = ({ row }: { row: TaskNodeVO }) => {
  if (row.assigneeId != null && row.assigneeId === userStore.userId) return 'task-node-mine'
  const parentAssignee = parentAssigneeMap.value.get(row.taskNodeId)
  if (parentAssignee != null && parentAssignee === userStore.userId) return 'task-node-my-child'
  return ''
}

// ============ 节点详情 ============
const detailVisible = ref(false)
const detailNode = ref<TaskNodeVO | null>(null)
const openDetail = (row: TaskNodeVO) => {
  detailNode.value = row
  detailVisible.value = true
}

// ============ 分配 / 退回 ============
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
      `退回「${row.assigneeName || '未知人员'}」持有的剩余任务，可退回数量 ${fmt(maxQty)}；退回后上级可分配容量自动恢复`,
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

// ============ 流水（TT-FINAL-06） ============
const eventsLoading = ref(false)
const eventList = ref<TaskTreeEventVO[]>([])
const actionTag = (action?: string): any => {
  return { ASSIGN: 'primary', RECALL: 'warning', RETURN: 'warning', WORK_REPORT: 'success', WORK_REPORT_CANCEL: 'danger' }[action || ''] || 'info'
}
const fmtTime = (t?: string) => (t ? String(t).replace('T', ' ').slice(0, 19) : '-')

const loadEvents = async () => {
  eventsLoading.value = true
  try {
    const res: any = await taskNodeApi.events(props.executionId)
    eventList.value = res?.data || []
  } catch {
    eventList.value = []
  } finally {
    eventsLoading.value = false
  }
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
    if (v) {
      activeTab.value = 'tree'
      load()
    }
  },
)

watch(activeTab, (v) => {
  if (v === 'events' && props.visible) loadEvents()
})
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
.tree-legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #606266;
  margin: 6px 0 10px;
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-right: 4px;
  vertical-align: middle;
}
.dot-mine { background: #67c23a; }
.dot-my-child { background: #409eff; }
.dot-other { background: #c0c4cc; }
</style>

<style>
/* 节点三色区分（行级，Element Plus 动态行 class 作用在 tr 上，需全局样式） */
tr.task-node-mine td.el-table__cell { background: rgba(103, 194, 58, 0.08); }
tr.task-node-my-child td.el-table__cell { background: rgba(64, 158, 255, 0.08); }
</style>

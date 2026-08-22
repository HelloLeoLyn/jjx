<template>
  <div class="dispatch-page">
    <div class="page-header">
      <h1 class="page-title">派工管理</h1>
      <span class="page-sub">任务分配 · 收回 · 退回 · 报工（树 = 当前任务链，流水 = 历史操作）</span>
    </div>

    <!-- 筛选 -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-input v-model="queryParams.orderNo" placeholder="工单编号" clearable style="width: 160px" @keyup.enter="handleQuery" @clear="handleQuery" />
        <el-input v-model="queryParams.processName" placeholder="工序" clearable style="width: 130px" @keyup.enter="handleQuery" @clear="handleQuery" />
        <el-select v-model="queryParams.executionStatus" placeholder="状态" clearable style="width: 120px" @change="handleQuery">
          <el-option v-for="s in STATUS_ITEMS" :key="s.value" :label="s.label" :value="String(s.value)" />
        </el-select>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <!-- 树形懒加载主列表：Execution 第一层 → TaskNode 人员任务树 -->
    <el-card class="list-card" shadow="never">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="executionList"
        row-key="rowKey"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        lazy
        :load="loadChildren"
        :indent="0"
        :row-class-name="rowClassName"
        @expand-change="onExpandChange"
        style="width: 100%"
      >
        <!-- 工单号 / 人员（树形缩进列：Execution=主对象，TaskNode=层级节点） -->
        <el-table-column label="工单号" min-width="260">
          <template #default="{ row }" >
           <el-text  type="primary">{{ row.orderNo || '-' }} {{ row.processName }} {{ row.processOrder }}</el-text> 
          </template>
        </el-table-column>
        <el-table-column label="任务数量" width="90" align="right">
          <template #default="{ row }">
            <span :title="isNodeRow(row) ? '本节点任务数量' : '工序总量'">{{ fmt(isNodeRow(row) ? row.taskQuantity : row.inputQuantity) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="已完成" width="90" align="right">
          <template #default="{ row }">{{ fmt(isNodeRow(row) ? row.selfReported : row.outputQuantity) }}</template>
        </el-table-column>

        <el-table-column label="待完成" width="90" align="right">
          <template #default="{ row }">{{ isNodeRow(row) ? '-' : fmt(row.remainingQuantity) }}</template>
        </el-table-column>

        <el-table-column label="已下发" width="100" align="right">
          <template #default="{ row }">
            <span :title="isNodeRow(row) ? '该人员节点已分给直接下级' : '系统 Root 已下发给第一层人员'">{{ fmt(isNodeRow(row) ? row.childOccupied : row.rootChildOccupied) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="当前剩余" width="100" align="right">
          <template #default="{ row }">
            <span :title="isNodeRow(row) ? '该人员节点自己剩余（selfRemaining）' : '系统 Root 当前可继续分配'">{{ fmt(isNodeRow(row) ? row.remainingQuantity : row.rootAvailableToAssign) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="isNodeRow(row)" size="small" :type="nodeStatusTag(row.status)">{{ row.statusLabel || row.status || '-' }}</el-tag>
            <el-tag v-else size="small" :type="statusTag(row.executionStatus)">{{ statusLabel(row.executionStatus) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="250" fixed="right">
          <template #default="{ row }">
            <template v-if="isNodeRow(row)">
              <el-button link type="info" size="small" @click="openNodeDetail(row)">查看</el-button>
              <el-button v-if="canAssignNode(row)" link type="primary" size="small" @click="handleAssignNode(row)">分配任务</el-button>
              <el-button v-if="canReportNode(row)" link type="success" size="small" @click="goExecution(nodeExecRow(row))">报工</el-button>
              <el-dropdown trigger="click" size="small" @command="(cmd: string) => onNodeCommand(cmd, row)">
                <span class="more-btn">···</span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="canReturnNode(row)" command="return">退回剩余</el-dropdown-item>
                    <el-dropdown-item command="events">流水</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <template v-else>
              <el-button
                v-if="canDispatch(row)"
                type="primary"
                link
                icon="EditPen"
                @click="handleAssign(row)"
              >分配任务</el-button>
              <el-button type="info" link icon="Tickets" @click="openEvents(row)">流水</el-button>
              <el-button type="success" link icon="EditPen" v-hasPermi="['production:execution:view']" @click="goExecution(row)">报工管理</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </el-card>

    <!-- 分配任务弹窗（含当前分配/收回/本次分配） -->
    <AssignTaskDialog
      v-model:visible="assignVisible"
      :execution-id="assignExecutionId"
      :parent-node-id="assignParentNodeId"
      :title="assignTitle"
      :order-no="assignOrderNo"
      :process-name="assignProcessName"
      @changed="onAssignChanged"
    />

    <!-- 节点详情 -->
    <NodeDetailDialog
      v-model:visible="detailVisible"
      :node="detailNode"
      :execution="detailExecution"
      :root="null"
    />

    <!-- 操作流水（历史事件，按 executionId 聚合） -->
    <TaskEventsDrawer
      v-model:visible="eventsVisible"
      :execution-id="eventsExecutionId"
      :execution="eventsExecution"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { operationExecutionApi } from '@/api/production/operationExecution'
import { taskNodeApi } from '@/api/production/taskNode'
import { hasPermi } from '@/directives'
import { useUserStore } from '@/store/modules/user'
import AssignTaskDialog from './components/AssignTaskDialog.vue'
import NodeDetailDialog from './components/NodeDetailDialog.vue'
import TaskEventsDrawer from './components/TaskEventsDrawer.vue'
import type { OperationExecutionVO, OperationExecutionQuery } from '@/types/production/operationExecution'
import type { TaskNodeVO, TaskTreeEventVO } from '@/types/production/taskNode'

defineOptions({ name: 'ProductionDispatch' })

// ============ 行类型：Execution 行（第一层）与 TaskNode 行（任务树）共用一张树表 ============
type NodeRow = TaskNodeVO & {
  rowKey: string
  /** 树层级（Execution 行=0，第一层人员=1，逐层 +1；视觉缩进用） */
  level?: number
  /** 是否是其父行最后一个子节点（树连接线 └─/├─ 用） */
  isLast?: boolean
  /** 各祖先层是否仍有后续兄弟（true=该层显示 │） */
  branchPath?: boolean[]
}
type ExecutionRow = OperationExecutionVO & { rowKey: string; hasChildren?: boolean }

const isNodeRow = (row: any): boolean => !!row?.taskNodeId
const rowKeyOf = (row: any): string => (row?.taskNodeId ? `n${row.taskNodeId}` : `e${row.executionId}`)
const decorateNode = (n: TaskNodeVO, level: number, branchPath: boolean[], isLast: boolean): NodeRow => ({
  ...n,
  rowKey: `n${n.taskNodeId}`,
  level,
  isLast,
  branchPath,
})
/** 节点行层级引导段：祖先层 │ 或空白 + 自身连接符 ├─/└─ */
const branchSegments = (row: NodeRow): { text: string; type: 'line' | 'conn' }[] => {
  const out: { text: string; type: 'line' | 'conn' }[] = []
  const path = row.branchPath || []
  const level = row.level || 1
  for (let k = 0; k < level; k++) {
    out.push({ text: path[k] ? '│' : ' ', type: 'line' })
  }
  out.push({ text: row.isLast ? '└─' : '├─', type: 'conn' })
  return out
}
/** 行级视觉：Execution=主对象（浅 Primary 底）；本人 TaskNode=轻微绿色强调；其余为普通节点 */
const rowClassName = ({ row }: { row: any }): string => {
  if (!isNodeRow(row)) return 'dispatch-exec-row'
  if (row.assigneeId != null && row.assigneeId === userStore.userId) return 'task-node-mine'
  return ''
}
/** 节点行回查所属 Execution 行（带真实工单号/工序名，跳转报工入口用） */
const nodeExecRow = (row: TaskNodeVO): OperationExecutionVO => {
  if (row.executionId != null) {
    const execRow = findExecutionRow(row.executionId)
    if (execRow) return execRow
  }
  return row as unknown as OperationExecutionVO
}

const STATUS_LABELS: Record<number, string> = {
  0: '待执行', 1: '准备中', 2: '执行中', 3: '已暂停', 4: '已完成',
  5: '已跳过', 6: '已取消', 7: '已超期', 8: '异常中', 9: '待确认',
}
const STATUS_ITEMS = Object.entries(STATUS_LABELS).map(([v, label]) => ({ value: Number(v), label }))

function statusLabel(s?: number): string {
  return STATUS_LABELS[s ?? 0] || String(s ?? 0)
}
function statusTag(s?: number): any {
  return { 0: 'info', 1: 'warning', 2: 'success', 3: 'warning', 4: 'success', 6: 'danger' }[s ?? 0] || 'info'
}
function nodeStatusTag(status?: string): any {
  return { ACTIVE: 'success', COMPLETED: 'info', CANCELLED: 'danger' }[status || ''] || 'info'
}
function fmt(v?: number | null): string {
  return String(Number(v || 0))
}

const router = useRouter()
const userStore = useUserStore()

// 报工管理：跳转工序执行页（派工管理负责任务怎么分，工序执行负责怎么做/怎么报工）
const goExecution = (row: OperationExecutionVO) => {
  router.push({ path: '/production/execution', query: row.orderNo ? { orderNo: row.orderNo } : {} })
}

// ============ 第一层：Execution 分页查询（服务端分页 + 既有查询范围） ============
const loading = ref(false)
const executionList = ref<ExecutionRow[]>([])
const total = ref(0)
const queryParams = reactive<OperationExecutionQuery>({
  orderNo: '', processName: '', executionStatus: '',
  pageNum: 1, pageSize: 10,
})

/** 展开箭头：全局视角按“已有真实人员节点”；个人视角只要有本人节点即可展开（第一层 = 本人顶层持有节点） */
const execHasChildren = (row: OperationExecutionVO): boolean => {
  if (row.viewScope === 'PERSONAL') return true
  return !!row.hasTaskRoot && (row.taskNodeCount ?? 0) > 1
}

const decorateExec = (row: OperationExecutionVO): ExecutionRow => ({
  ...row,
  rowKey: `e${row.executionId}`,
  hasChildren: execHasChildren(row),
})

const getList = async () => {
  loading.value = true
  try {
    const res: any = await operationExecutionApi.page(queryParams)
    const data = res?.data
    const rows: OperationExecutionVO[] = data?.records || []
    executionList.value = rows.map(decorateExec)
    total.value = data?.total || 0
    await restoreExecutionExpansion()
  } catch {
    executionList.value = []
  } finally {
    loading.value = false
  }
}
const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => {
  Object.assign(queryParams, { orderNo: '', processName: '', executionStatus: '', pageNum: 1 })
  getList()
}

// ============ 树形懒加载（真正按层加载，浏览不写库） ============
const tableRef = ref<any>(null)
/** 已展开行 key（保持展开上下文，不因局部刷新折叠） */
const expandedKeys = reactive(new Set<string>())
/** 已加载节点行（按 rowKey）：操作后局部刷新定位用 */
const loadedNodeRows = new Map<string, NodeRow>()
/** 各父行已加载的直接子节点（节点详情“直接下级”展示用） */
const childrenByParentKey = new Map<string, NodeRow[]>()

const isExpanded = (row: any): boolean => expandedKeys.has(rowKeyOf(row))
/** 自定义展开/收起入口（替代默认小箭头；与工单号/人员形成整体） */
const toggleRow = (row: any) => {
  tableRef.value?.toggleRowExpansion(row)
}

const onExpandChange = (row: any, expanded: boolean) => {
  const k = row?.rowKey
  if (!k) return
  if (expanded) expandedKeys.add(k)
  else expandedKeys.delete(k)
}

/** 子节点行注入树层级信息（level / 是否末位 / 祖先分支路径） */
const decorateChildren = (raw: TaskNodeVO[], parentRow: any): NodeRow[] => {
  const parentLevel = parentRow?.level || 0
  const parentPath = parentRow?.branchPath || []
  const parentIsLast = parentRow?.isLast ?? true
  return raw.map((n, i) => decorateNode(n, parentLevel + 1, [...parentPath, !parentIsLast], i === raw.length - 1))
}

const loadChildren = async (row: any, _treeNode: any, resolve: (data: any[]) => void) => {
  try {
    const res: any = await taskNodeApi.children(row.executionId, row.taskNodeId || null)
    const nodes: NodeRow[] = decorateChildren(res?.data || [], row)
    childrenByParentKey.set(rowKeyOf(row), nodes)
    for (const n of nodes) loadedNodeRows.set(n.rowKey, n)
    resolve(nodes)
    // 恢复该层之前已展开的子节点（保持深层展开上下文）
    await nextTick()
    for (const n of nodes) {
      if (expandedKeys.has(n.rowKey)) tableRef.value?.toggleRowExpansion(n, true)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载任务树失败')
    resolve([])
  }
}

const restoreExecutionExpansion = async () => {
  await nextTick()
  for (const row of executionList.value) {
    if (expandedKeys.has(row.rowKey)) tableRef.value?.toggleRowExpansion(row, true)
  }
}

// ============ 操作后局部刷新（不整页重载、不折叠已展开树） ============
const findExecutionRow = (executionId: number): ExecutionRow | undefined =>
  executionList.value.find((r) => r.executionId === executionId)

const refreshExecutionRow = async (executionId: number) => {
  try {
    const res: any = await operationExecutionApi.getInfo(executionId)
    const fresh: OperationExecutionVO = res?.data
    if (!fresh) return
    const row = findExecutionRow(executionId)
    if (row) {
      Object.assign(row, fresh)
      row.rowKey = `e${row.executionId}`
      row.hasChildren = execHasChildren(row)
    }
  } catch { /* 局部刷新失败保持当前展示 */ }
}

const refreshNodeRow = async (taskNodeId: number) => {
  try {
    const res: any = await taskNodeApi.detail(taskNodeId)
    const fresh: TaskNodeVO = res?.data
    if (!fresh) return
    const row = loadedNodeRows.get(`n${taskNodeId}`)
    if (row) {
      const keep = { rowKey: row.rowKey }
      Object.assign(row, fresh, keep)
    }
  } catch { /* 局部刷新失败保持当前展示 */ }
}

/** 刷新某父行（Execution 行 或 TaskNode 行）的直接子节点分支 */
const refreshBranch = async (executionId: number, parentNodeId?: number | null) => {
  try {
    const res: any = await taskNodeApi.children(executionId, parentNodeId || null)
    const key = parentNodeId ? `n${parentNodeId}` : `e${executionId}`
    const parentRow: any = parentNodeId ? loadedNodeRows.get(key) : findExecutionRow(executionId)
    const fresh: NodeRow[] = decorateChildren(res?.data || [], parentRow)
    childrenByParentKey.set(key, fresh)
    for (const n of fresh) loadedNodeRows.set(n.rowKey, n)
    if (tableRef.value && typeof tableRef.value.updateKeyChildren === 'function') {
      tableRef.value.updateKeyChildren(key, fresh)
    }
  } catch { /* 局部刷新失败保持当前展示 */ }
}

/** 分配/收回/退回成功后：只刷新受影响 Execution 行与分支，保持已展开上下文 */
const onAssignChanged = async () => {
  const execId = assignExecutionId.value
  if (!execId) return
  await refreshExecutionRow(execId)
  if (assignFromRootFirstTime.value) {
    // 首次分配（系统根下新增第一层人员）：刷新 Execution 行第一层分支
    await refreshBranch(execId, null)
    assignFromRootFirstTime.value = false
  } else if (assignParentNodeId.value) {
    // 本人节点继续分配 / 收回：刷新父节点自身 + 其直接子节点分支
    await refreshNodeRow(assignParentNodeId.value)
    await refreshBranch(execId, assignParentNodeId.value)
  }
}

// ============ 分配任务（Execution 行 / Node 行） ============
/**
 * Execution 行按钮规则：
 * 无任务树（或系统根尚无真实人员子节点 taskNodeCount<=1）：production:task:dispatch（点击先建立/复用系统根，再进入分配）
 * 已有人员子节点：后端投影 myAssignableNodeId（本人持有且 availableToAssign>0）+ production:task:assign
 */
const canDispatch = (row: OperationExecutionVO) => {
  if (!row.hasTaskRoot || (row.taskNodeCount ?? 0) <= 1) return hasPermi('production:task:dispatch')
  return !!row.myAssignableNodeId && hasPermi('production:task:assign')
}

/** Node 行分配按钮：自己的节点（或超管/task:admin）+ task:assign + availableToAssign>0 */
const canAssignNode = (row: TaskNodeVO) => {
  return Number(row.availableToAssign || 0) > 0
    && hasPermi('production:task:assign')
    && (isAdminOrTaskAdmin() || userStore.userId === row.assigneeId)
}

const isAdminOrTaskAdmin = () => hasPermi('*:*:*') || hasPermi('production:task:admin')

/** Node 行报工按钮：本人节点 + 剩余>0 + work-report 权限（报工入口跳工序执行页） */
const canReportNode = (row: TaskNodeVO) => {
  return Number(row.remainingQuantity || 0) > 0
    && userStore.userId === row.assigneeId
    && hasPermi('production:work-report:add')
}

/** Node 行退回：自己的非 root 节点 + task:return + selfRemaining>0 */
const canReturnNode = (row: TaskNodeVO) => {
  return !!row.parentNodeId
    && Number(row.remainingQuantity || 0) > 0
    && hasPermi('production:task:return')
    && (isAdminOrTaskAdmin() || userStore.userId === row.assigneeId)
}

const assignVisible = ref(false)
const assignExecutionId = ref(0)
const assignParentNodeId = ref(0)
const assignTitle = ref('分配任务')
const assignOrderNo = ref('')
const assignProcessName = ref('')
const assignFromRootFirstTime = ref(false)

const handleAssign = async (row: ExecutionRow) => {
  if (!row.executionId) return
  // 首次分配（无 root 或系统根尚无真实人员子节点）：通过任务树 API 建立/复用系统根（root.taskQuantity = 计划数量）
  if (!row.hasTaskRoot || (row.taskNodeCount ?? 0) <= 1) {
    try {
      const res: any = await taskNodeApi.getTree(row.executionId)
      const root = res?.data
      if (!root?.taskNodeId) {
        ElMessage.warning('任务树建立失败，请刷新后重试')
        return
      }
      Object.assign(row, {
        hasTaskRoot: true,
        taskRootAssigneeId: root.assigneeId ?? null,
        taskRootAssigneeName: root.assigneeName ?? null,
        taskNodeCount: 1,
        taskChainText: root.assigneeName || '未分配',
        hasChildren: false,
      })
      assignParentNodeId.value = root.taskNodeId
      assignFromRootFirstTime.value = true
    } catch (e: any) {
      ElMessage.error(e?.message || '建立任务树失败')
      return
    }
  } else if (row.myAssignableNodeId) {
    assignParentNodeId.value = row.myAssignableNodeId
    assignFromRootFirstTime.value = false
  } else {
    return
  }
  assignExecutionId.value = row.executionId
  assignOrderNo.value = row.orderNo || ''
  assignProcessName.value = row.processName || ''
  assignTitle.value = `${row.orderNo || ''} ${row.processName || ''} · 分配任务`
  assignVisible.value = true
}

const handleAssignNode = (row: NodeRow) => {
  assignExecutionId.value = row.executionId || 0
  assignParentNodeId.value = row.taskNodeId || 0
  assignOrderNo.value = ''
  assignProcessName.value = ''
  assignFromRootFirstTime.value = false
  assignTitle.value = `${row.assigneeName || '节点'} · 分配任务`
  assignVisible.value = true
}

// ============ 退回剩余（Node 行更多菜单；复用现有 return 逻辑） ============
const handleReturn = async (row: NodeRow) => {
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
    // 局部刷新：退回节点自身 + 其父分支（父行可见时）
    await refreshNodeRow(row.taskNodeId)
    if (row.parentNodeId) {
      await refreshNodeRow(row.parentNodeId)
      await refreshBranch(row.executionId || 0, row.parentNodeId)
    }
    await refreshExecutionRow(row.executionId || 0)
  } catch (e: any) {
    if (e === 'cancel' || e?.toString().includes('cancel')) return
    ElMessage.error(e?.message || '退回失败')
  }
}

const onNodeCommand = (cmd: string, row: NodeRow) => {
  if (cmd === 'return') handleReturn(row)
  else if (cmd === 'events') openEvents(row)
}

// ============ 节点详情 ============
const detailVisible = ref(false)
const detailNode = ref<TaskNodeVO | null>(null)
const detailExecution = ref<OperationExecutionVO | null>(null)

const openNodeDetail = (row: NodeRow) => {
  const execRow = row.executionId != null ? findExecutionRow(row.executionId) : undefined
  detailExecution.value = execRow || null
  detailNode.value = { ...row, children: childrenByParentKey.get(row.rowKey) || [] }
  detailVisible.value = true
}

// ============ 操作流水（轻量 Drawer，复用 executionEvents） ============
const eventsVisible = ref(false)
const eventsExecutionId = ref(0)
const eventsExecution = ref<OperationExecutionVO | null>(null)

const openEvents = (row: any) => {
  const execId = row?.executionId
  if (!execId) return
  eventsExecutionId.value = execId
  eventsExecution.value = isNodeRow(row) ? findExecutionRow(execId) || null : row
  eventsVisible.value = true
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.dispatch-page { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; }
.page-sub { font-size: 12px; color: #909399; }
.filter-card { margin-bottom: 16px; }
.filter-bar { display: flex; gap: 10px; align-items: center; padding-bottom: 8px; flex-wrap: wrap; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }

/* ============ 树形层级视觉：Execution=主对象 / TaskNode=层级节点 ============ */
.exec-cell {
  display: flex;
  align-items: center;
  min-width: 0;
}
.exec-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}
.exec-order-no {
  font-weight: 600;
  color: #409eff;
  white-space: nowrap;
}
.exec-process {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-cell {
  display: flex;
  align-items: center;
  min-width: 0;
}
.tree-seg {
  flex: none;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1;
  color: #b0b3ba;
  user-select: none;
}
.tree-seg-line {
  width: 18px;
  text-align: center;
}
.tree-seg-conn {
  width: 24px;
  text-align: left;
}

.tree-toggle {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-right: 8px;
  padding: 0;
  border: 1px solid #bcd7f5;
  border-radius: 4px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 10px;
  line-height: 1;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.tree-toggle:hover {
  background: #d9ecff;
}
.tree-toggle-placeholder {
  visibility: hidden;
  border: none;
  background: transparent;
}
.node-dot {
  flex: none;
  width: 7px;
  height: 7px;
  margin: 0 12px 0 8px;
  border-radius: 50%;
  background: #c0c4cc;
  display: inline-block;
}
.node-name {
  font-weight: 500;
  color: #303133;
}
.node-source {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.more-btn {
  cursor: pointer;
  color: #909399;
  font-weight: 600;
  letter-spacing: 2px;
  padding: 0 4px;
}
.more-btn:hover { color: #409eff; }
</style>

<style>
/* 行级视觉（Element Plus 行 class 作用在 tr 上，需全局样式） */
tr.dispatch-exec-row td.el-table__cell {
  background: #f0f7ff;
}
tr.dispatch-exec-row:hover > td.el-table__cell {
  background: #e4f0ff !important;
}
tr.task-node-mine td.el-table__cell {
  background: rgba(103, 194, 58, 0.08);
}
tr.task-node-mine:hover > td.el-table__cell {
  background: rgba(103, 194, 58, 0.13) !important;
}

/* 隐藏 el-table 默认弱展开箭头（使用自定义 ▶/▼ 入口） */
.dispatch-page .el-table__expand-icon {
  display: none;
}
</style>

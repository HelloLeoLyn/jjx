<template>
  <div class="dispatch-page">
    <div class="page-header">
      <h1 class="page-title">派工管理</h1>
      <span class="page-sub">任务模型：Execution → TaskNode → WorkReport</span>
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

    <!-- 主列表：一行一道 Execution -->
    <el-card class="list-card" shadow="never">
      <el-table v-loading="loading" :data="executionList" style="width: 100%">
        <el-table-column prop="orderNo" label="工单号" width="180" show-overflow-tooltip />
        <el-table-column label="工序" min-width="140">
          <template #default="{ row }">
            <span>{{ row.processName || '-' }}</span>
            <div v-if="row.processOrder" style="font-size: 12px; color: #909399">序 {{ row.processOrder }}</div>
          </template>
        </el-table-column>
        <el-table-column label="任务链" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openTree(row)">
              <span v-if="row.hasTaskRoot">{{ row.taskChainText }}</span>
              <span v-else style="color: #c0c4cc">未分配</span>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="任务数量" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.inputQuantity) }}</template>
        </el-table-column>
        <el-table-column label="已完成" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.outputQuantity) }}</template>
        </el-table-column>
        <el-table-column label="待完成" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.remainingQuantity) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.executionStatus)">{{ statusLabel(row.executionStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="info" link icon="View" @click="openTree(row)">任务链</el-button>
            <!-- 无 root：task:dispatch 创建/分配 root；有 root：本人持有可分配节点 + task:assign -->
            <el-button
              v-if="canDispatch(row)"
              type="primary"
              link
              icon="EditPen"
              @click="handleAssign(row)"
            >分配任务</el-button>
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

    <!-- 任务树 Drawer -->
    <TaskTreeDrawer
      v-model:visible="treeVisible"
      :execution-id="treeExecutionId"
      :execution="treeExecution"
      @changed="getList"
    />

    <!-- 分配任务弹窗 -->
    <AssignTaskDialog
      v-model:visible="assignVisible"
      :execution-id="assignExecutionId"
      :parent-node-id="assignParentNodeId"
      :title="assignTitle"
      @changed="getList"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { operationExecutionApi } from '@/api/production/operationExecution'
import { taskNodeApi } from '@/api/production/taskNode'
import { hasPermi } from '@/directives'
import TaskTreeDrawer from './components/TaskTreeDrawer.vue'
import AssignTaskDialog from './components/AssignTaskDialog.vue'
import type { OperationExecutionVO, OperationExecutionQuery } from '@/types/production/operationExecution'

defineOptions({ name: 'ProductionDispatch' })

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
function fmt(v?: number | null): string {
  return String(Number(v || 0))
}

const loading = ref(false)
const executionList = ref<OperationExecutionVO[]>([])
const total = ref(0)
const queryParams = reactive<OperationExecutionQuery>({
  orderNo: '', processName: '', executionStatus: '',
  pageNum: 1, pageSize: 10,
})

const getList = async () => {
  loading.value = true
  try {
    const res: any = await operationExecutionApi.list(queryParams)
    const data = res?.data
    executionList.value = Array.isArray(data) ? data : data?.records || []
    total.value = Array.isArray(data) ? data.length : data?.total || 0
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

// ============ 分配任务按钮规则（消费后端 TaskNode/Execution 投影） ============
/**
 * 无 root：有 production:task:dispatch → 显示（点击先建立 root，再进入分配弹窗）
 * 有 root：后端投影 myAssignableNodeId（本人持有且 availableToAssign>0）+ production:task:assign → 显示
 */
const canDispatch = (row: OperationExecutionVO) => {
  if (!row.hasTaskRoot) return hasPermi('production:task:dispatch')
  return !!row.myAssignableNodeId && hasPermi('production:task:assign')
}

// ============ 分配任务 ============
const assignVisible = ref(false)
const assignExecutionId = ref(0)
const assignParentNodeId = ref(0)
const assignTitle = ref('分配任务')

const handleAssign = async (row: OperationExecutionVO) => {
  if (!row.executionId) return
  // 无 root：先通过任务树 API 建立根节点（root.taskQuantity = 计划数量）
  if (!row.hasTaskRoot) {
    try {
      const res: any = await taskNodeApi.getTree(row.executionId)
      const root = res?.data
      if (!root?.taskNodeId) {
        ElMessage.warning('任务树建立失败，请刷新后重试')
        return
      }
      row.hasTaskRoot = true
      row.taskRootAssigneeId = root.assigneeId
      row.taskRootAssigneeName = root.assigneeName
      row.taskNodeCount = 1
      row.taskChainText = root.assigneeName || ''
      assignParentNodeId.value = root.taskNodeId
    } catch (e: any) {
      ElMessage.error(e?.message || '建立任务树失败')
      return
    }
  } else if (row.myAssignableNodeId) {
    assignParentNodeId.value = row.myAssignableNodeId
  } else {
    return
  }
  assignExecutionId.value = row.executionId
  assignTitle.value = `${row.orderNo || ''} ${row.processName || ''} · 分配任务`
  assignVisible.value = true
}

// ============ 任务树 Drawer ============
const treeVisible = ref(false)
const treeExecutionId = ref(0)
const treeExecution = ref<OperationExecutionVO | null>(null)

const openTree = (row: OperationExecutionVO) => {
  if (!row.executionId) return
  treeExecutionId.value = row.executionId
  treeExecution.value = row
  treeVisible.value = true
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
</style>

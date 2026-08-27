<template>
  <div class="dispatch-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">派工管理</h1>
      <span class="page-subtitle">生产任务责任树 · ProductionTask（第一层与各级完全同构）</span>
    </div>

    <!-- 顶部统计（当前页 First Task 数据） -->
    <el-card class="stats-card" shadow="never">
      <div class="stats-bar">
        <div class="stat-item">
          <div class="stat-num">{{ total }}</div>
          <div class="stat-label">总任务</div>
        </div>
        <div class="stat-item">
          <div class="stat-num stat-pending">{{ statUnassigned }}</div>
          <div class="stat-label">未分配</div>
        </div>
        <div class="stat-item">
          <div class="stat-num stat-active">{{ statActive }}</div>
          <div class="stat-label">进行中</div>
        </div>
        <div class="stat-item">
          <div class="stat-num stat-waiting">{{ statPendingQty }}</div>
          <div class="stat-label">有待审批</div>
        </div>
      </div>
    </el-card>

    <!-- 筛选区（keyword/status 后端过滤） -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-input
          v-model="filterForm.keyword"
          placeholder="工单号 / 工序"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
          @clear="handleQuery"
        />
        <el-select
          v-model="filterForm.status"
          placeholder="状态"
          clearable
          style="width: 120px"
          @change="handleQuery"
        >
          <el-option label="未分配" value="PENDING" />
          <el-option label="进行中" value="ACTIVE" />
          <el-option label="已完成" value="COMPLETED" />
        </el-select>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
        <span class="filter-tip">关键词匹配工单号/工序名；状态过滤由后端分页执行</span>
      </div>
    </el-card>

    <!-- 主表：统一 ProductionTask 树（第一层 = page，children = 展开时懒加载） -->
    <el-card class="list-card" shadow="never">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="firstLevelRows"
        row-key="taskId"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :lazy="true"
        :load="loadTreeChildren"
        style="width: 100%"
      >
        <el-table-column label="工序单号" min-width="200">
          <template #default="{ row }">
            <span class="task-sub">任务号：{{ row.taskNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工序" min-width="180">
          <template #default="{ row }">
            <span class="task-sub">{{ row.processName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="执行人" prop="assigneeName" width="120" align="right">
          <template #default="{ row }">
            <span v-if="row.assigneeName">{{ row.assigneeName }}</span>
            <el-text type="primary" v-else-if="row.hasChildren">-</el-text>
            <span v-else class="text-muted">未分配</span>
          </template>
        </el-table-column>

        <el-table-column label="任务数量" width="100" align="right">
          <template #default="{ row }">{{ fmtQty(row.taskQuantity) }}</template>
        </el-table-column>

        <el-table-column label="已完成" width="110" align="right">
          <template #default="{ row }">
            <el-link
              v-if="completed(row) > 0"
              type="primary"
              underline
              @click="openCompletionDetails(row)"
            >
              {{ fmtQty(completed(row)) }}
            </el-link>
            <span v-else class="text-muted">0</span>
          </template>
        </el-table-column>

        <el-table-column label="待审批" width="100" align="right">
          <template #default="{ row }">
            <el-tag v-if="pending(row) > 0" size="small" type="warning" effect="plain">{{
              fmtQty(pending(row))
            }}</el-tag>
            <span v-else class="text-muted">0</span>
          </template>
        </el-table-column>

        <el-table-column label="已分配" width="100" align="right">
          <template #default="{ row }">{{ fmtQty(row.assignedQuantity) }}</template>
        </el-table-column>

        <el-table-column label="剩余" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': remaining(row) === 0 }">{{
              fmtQty(remaining(row))
            }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="allowedActions(row, 'ASSIGN')"
              type="primary"
              link
              icon="User"
              @click="openAssignDialog(row)"
              >分配</el-button
            >
            <el-button
              v-if="allowedActions(row, 'RETURN')"
              type="warning"
              link
              icon="RefreshLeft"
              @click="openReturnDialog(row)"
              >退回</el-button
            >
            <el-button
              v-if="allowedActions(row, 'RECALL')"
              type="info"
              link
              icon="Back"
              @click="openRecallDialog(row)"
              >收回</el-button
            >
            <el-button
              v-if="allowedActions(row, 'COMPLETE')"
              type="success"
              link
              icon="CircleCheck"
              @click="handleComplete(row)"
              >完成</el-button
            >
            <el-button
              v-if="allowedActions(row, 'FLOW')"
              type="info"
              link
              icon="Tickets"
              @click="openFlowDrawer(row)"
              >流水</el-button
            >
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
      <div class="qty-invariant-tip">
        数量口径（P4）：任务数量 = 已完成 + 待审批 + 已分配 + 剩余，均为后端投影，前端不重算。
      </div>
    </el-card>

    <!-- ============ 完成明细 Drawer（仅 APPROVED） ============ -->
    <el-drawer v-model="detailOpen" :title="`完成明细${detailTitle}`" size="680px" append-to-body>
      <el-table v-loading="detailLoading" :data="detailList" size="small">
        <el-table-column label="报工人" prop="reporterName" width="90" show-overflow-tooltip />
        <el-table-column label="执行人" prop="taskAssigneeName" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taskAssigneeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="报工数量" width="90" align="right">
          <template #default="{ row }">{{ fmtQty(row.reportQuantity) }}</template>
        </el-table-column>
        <el-table-column label="合格" width="70" align="right">
          <template #default="{ row }">{{ fmtQty(row.qualifiedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="不良" width="70" align="right">
          <template #default="{ row }">{{ fmtQty(row.defectiveQuantity) }}</template>
        </el-table-column>
        <el-table-column label="报工时间" width="120">
          <template #default="{ row }">{{ fmtTime(row.reportTime) }}</template>
        </el-table-column>
        <el-table-column label="审批人" prop="reviewerName" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reviewerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="审批时间" width="120">
          <template #default="{ row }">{{ fmtTime(row.reviewTime) }}</template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
      </el-table>
      <div class="detail-total">
        有效完成合计：<span class="text-primary">{{ fmtQty(detailTotal) }}</span>
        <span v-if="detailTotal !== completed(detailSource)" class="text-danger"
          >（与已点击完成数不一致，请核对！）</span
        >
      </div>
      <div class="text-muted tip">
        仅展示 APPROVED 有效完成事实；REJECTED / CANCELLED 不参与完成明细。
      </div>
    </el-drawer>

    <!-- ============ 分配 Dialog（统一多选 + 拆量：每个层级同构） ============ -->
    <el-dialog v-model="assignOpen" :title="assignTitle" width="760px" append-to-body>
      <template v-if="assignTarget">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单/工序">{{
            orderProcessLabel(assignTarget)
          }}</el-descriptions-item>
          <el-descriptions-item label="当前执行人">{{
            assignTarget.assigneeName || '未分配'
          }}</el-descriptions-item>
          <el-descriptions-item label="任务号">{{
            assignTarget.taskNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="任务数量">{{
            fmtQty(assignTarget.taskQuantity)
          }}</el-descriptions-item>
          <el-descriptions-item label="剩余">{{
            fmtQty(assignTarget.remainingQuantity)
          }}</el-descriptions-item>
        </el-descriptions>

        <div class="candidate-title">已分配责任（{{ assignedList.length }} 人）</div>
        <el-table
          v-if="assignedList.length"
          v-loading="assignedListLoading"
          :data="assignedList"
          size="small"
          max-height="180"
          class="assigned-table"
        >
          <el-table-column label="任务号" min-width="245" show-overflow-tooltip>
            <template #default="{ row }">{{ row.taskNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="姓名" width="140">
            <template #default="{ row }">{{ row.assigneeName || '-' }}</template>
          </el-table-column>
          <el-table-column label="已分配数量" width="110" align="right">
            <template #default="{ row }">{{ fmtQty(row.taskQuantity) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ row.statusLabel || row.status || '-' }}</template>
          </el-table-column>
          <el-table-column label="剩余" min-width="100" align="right">
            <template #default="{ row }">{{ fmtQty(row.remainingQuantity) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else-if="!assignedListLoading" description="暂无已分配责任" :image-size="40" />

        <div class="candidate-title">候选责任树（{{ totalCandidateCount }} 人）</div>
        <div class="candidate-hint">可选择直属负责人逐级派工，也可展开班组直接选择工人</div>
        <el-tree
          ref="candidateTreeRef"
          v-loading="candidateLoading"
          :data="candidateList"
          node-key="userId"
          :props="{ label: 'nickName', children: 'children', disabled: 'disabled' }"
          show-checkbox
          :check-strictly="true"
          :expand-on-click-node="false"
          :default-expand-all="true"
          class="candidate-tree"
          @check="onTreeCheck"
        >
          <template #default="{ data }">
            <span class="cand-node">
              <span class="cand-name">{{ candidateName(data) }}</span>
              <span class="cand-meta">{{ data.deptName || '-' }} · {{ candidateRoles(data) }}</span>
              <el-tag v-if="data.root" size="small" type="warning" effect="plain">我</el-tag>
            </span>
          </template>
        </el-tree>

        <div class="candidate-title">本次分配（{{ selectedRows.length }} 人）</div>
        <el-table :data="selectedRows" size="small" max-height="180">
          <el-table-column label="姓名" width="120">
            <template #default="{ row }">{{ candidateName(row) }}</template>
          </el-table-column>
          <el-table-column label="部门" prop="deptName" min-width="110" show-overflow-tooltip />
          <el-table-column label="分配数量" width="150">
            <template #default="{ row }">
              <el-input-number
                v-model="qtyMap[row.userId]"
                :min="0.01"
                :max="assignQuantityMax"
                :precision="2"
                :step="1"
                size="small"
                style="width: 130px"
              />
            </template>
          </el-table-column>
          <el-table-column label="" width="55">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="removeAssignItem(row)"
                >移除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <div class="assign-summary">
          <span
            >可分配：<b class="text-primary">{{ fmtQty(assignTarget.remainingQuantity) }}</b></span
          >
          <span
            >本次分配：<b>{{ fmtQty(assignTotal) }}</b></span
          >
          <span
            >分配后剩余：<b :class="{ 'text-danger': afterAssign < 0 }">{{
              fmtQty(afterAssign)
            }}</b></span
          >
          <span v-if="assignTotal > assignQuantityMax" class="text-danger">超出剩余，不能提交</span>
        </div>
      </template>
      <template #footer>
        <el-button @click="assignOpen = false">取消</el-button>
        <el-button
          type="primary"
          :loading="assignLoading"
          :disabled="submitDisabled"
          @click="handleAssignSubmit"
          >确认分配</el-button
        >
      </template>
    </el-dialog>

    <!-- ============ 退回 Dialog（当前执行人把自身剩余退给父任务） ============ -->
    <el-dialog v-model="returnOpen" title="退回" width="800px" append-to-body>
      <template v-if="returnTarget">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单/工序">{{
            orderProcessLabel(returnTarget)
          }}</el-descriptions-item>
          <el-descriptions-item label="当前执行人">{{
            returnTarget.assigneeName || '未分配'
          }}</el-descriptions-item>
          <el-descriptions-item label="当前剩余">{{
            fmtQty(returnTarget.remainingQuantity)
          }}</el-descriptions-item>
          <el-descriptions-item label="上级执行人">{{
            returnTarget.parentAssigneeName || '-'
          }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px" style="margin-top: 12px">
          <el-form-item label="退回数量" required>
            <el-input-number
              v-model="returnQuantity"
              :min="0.01"
              :max="Math.max(returnMax, 0.01)"
              :precision="2"
              :step="1"
              style="width: 100%"
            />
            <div class="text-muted tip">最多可退回：{{ fmtQty(returnMax) }}</div>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="returnRemark" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="returnOpen = false">取消</el-button>
        <el-button
          type="warning"
          :loading="returnLoading"
          :disabled="!returnQuantity || returnQuantity <= 0"
          @click="handleReturnSubmit"
          >确认退回</el-button
        >
      </template>
    </el-dialog>

    <!-- ============ 收回 Dialog（组件化：树形可收回列表 + 多选批量收回） ============ -->
    <RecallDialog v-model="recallOpen" :target="recallTarget" @success="handleRecallSuccess" />

    <!-- ============ 任务流水 Drawer（P6：ProductionTaskEvent 业务流水） ============ -->
    <el-drawer v-model="flowOpen" :title="`任务流水${flowTitle}`" size="620px" append-to-body>
      <div v-loading="flowLoading" class="flow-body">
        <el-timeline v-if="flowEvents.length">
          <el-timeline-item
            v-for="ev in flowEvents"
            :key="ev.eventId"
            :timestamp="fmtTime(ev.createTime)"
            :type="flowTagType(ev.action)"
            placement="top"
          >
            <div class="flow-item">
              <div class="flow-item-head">
                <el-tag size="small" :type="flowTagType(ev.action)">{{
                  flowActionLabel(ev.action)
                }}</el-tag>
                <span class="flow-operator">{{ ev.operatorName || ev.operatorId }}</span>
                <span v-if="ev.quantity != null" class="flow-qty"
                  >数量 {{ fmtQty(ev.quantity) }}</span
                >
              </div>
              <div class="flow-meta">
                <span v-if="ev.fromAssigneeName || ev.toAssigneeName">
                  {{ ev.fromAssigneeName || '-' }} → {{ ev.toAssigneeName || '-' }}
                </span>
                <span v-if="ev.beforeTaskQuantity != null">
                  任务数量 {{ fmtQty(ev.beforeTaskQuantity) }} → {{ fmtQty(ev.afterTaskQuantity) }}
                </span>
                <span v-if="ev.relatedTaskId" class="text-muted"
                  >关联任务 #{{ ev.relatedTaskId }}</span
                >
              </div>
              <div v-if="ev.remark" class="text-muted">备注：{{ ev.remark }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else-if="!flowLoading" description="暂无流水" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  candidateName,
  candidateRoles,
  completed,
  flowActionLabel,
  flowTagType,
  fmtQty,
  fmtTime,
  orderProcessLabel,
  pending,
  remaining,
  statusLabel,
  statusTag,
} from './utils/taskFormatters'
import { allowedActions } from './utils/taskActions'
import type { TreeRow } from './types'
import { useDispatchList } from './composables/useDispatchList'
import { useAssign } from './composables/useAssign'
import { useReturn } from './composables/useReturn'
import RecallDialog from './components/RecallDialog.vue'
import { useFlow } from './composables/useFlow'
import { useCompletionDetail } from './composables/useCompletionDetail'

defineOptions({ name: 'ProductionDispatchList' })

// ============ 列表 + 树（第一层分页 / 懒加载 / 行刷新 / 完成） ============
const {
  loading,
  firstLevelRows,
  total,
  queryParams,
  filterForm,
  getList,
  statUnassigned,
  statActive,
  statPendingQty,
  handleQuery,
  handleReset,
  tableRef,
  loadTreeChildren,
  refreshRowChain,
  handleComplete,
} = useDispatchList()

// ============ 分配 Dialog（统一多选 + 拆量） ============
const {
  assignOpen,
  assignLoading,
  assignTarget,
  candidateList,
  candidateLoading,
  selectedRows,
  qtyMap,
  assignQuantityMax,
  assignTotal,
  afterAssign,
  submitDisabled,
  assignTitle,
  assignedList,
  assignedListLoading,
  candidateTreeRef,
  totalCandidateCount,
  onTreeCheck,
  removeAssignItem,
  openAssignDialog,
  handleAssignSubmit,
} = useAssign({ onSuccess: refreshRowChain })

// ============ 退回 Dialog ============
const {
  returnOpen,
  returnLoading,
  returnTarget,
  returnQuantity,
  returnRemark,
  returnMax,
  openReturnDialog,
  handleReturnSubmit,
} = useReturn({ onSuccess: refreshRowChain })

// ============ 收回 Dialog（组件化：树形可收回列表 + 多选批量收回） ============
const recallOpen = ref(false)
const recallTarget = ref<TreeRow | null>(null)
const openRecallDialog = (row: TreeRow) => {
  recallTarget.value = row
  recallOpen.value = true
}
const handleRecallSuccess = async (taskId: number) => {
  await refreshRowChain(taskId)
}

// ============ 任务流水 Drawer ============
const { flowOpen, flowLoading, flowEvents, flowTitle, openFlowDrawer } = useFlow()

// ============ 完成明细 Drawer ============
const {
  detailOpen,
  detailLoading,
  detailList,
  detailSource,
  detailTitle,
  detailTotal,
  openCompletionDetails,
} = useCompletionDetail()

onMounted(() => {
  getList()
})
</script>

<style scoped>
.dispatch-page {
  padding: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.page-subtitle {
  font-size: 13px;
  color: #909399;
}
.stats-card {
  margin-bottom: 12px;
}
.stats-bar {
  display: flex;
  gap: 48px;
  padding: 6px 4px;
}
.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat-num {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}
.stat-label {
  font-size: 12px;
  color: #909399;
}
.stat-pending {
  color: #909399;
}
.stat-active {
  color: #67c23a;
}
.stat-waiting {
  color: #e6a23c;
}
.filter-card {
  margin-bottom: 12px;
}
.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  padding-bottom: 8px;
  flex-wrap: wrap;
}
.filter-tip {
  font-size: 12px;
  color: #c0c4cc;
}
.list-card {
  margin-bottom: 12px;
}
.task-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.task-title {
  font-weight: 500;
}
.task-sub {
  font-size: 12px;
  color: #606266;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.qty-invariant-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #c0c4cc;
}
.detail-total {
  margin-top: 14px;
  font-size: 14px;
  font-weight: 600;
}
.tip {
  margin-top: 8px;
  font-size: 12px;
}
.candidate-title {
  margin: 14px 0 8px;
  font-weight: 500;
}

.candidate-hint {
  margin: -4px 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.candidate-tree {
  max-height: 300px;
  overflow: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 6px;
}
.cand-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.cand-name {
  font-weight: 500;
}
.cand-meta {
  color: #909399;
  font-size: 12px;
}
.qty-form {
  margin-top: 12px;
}
.assign-summary {
  display: flex;
  gap: 28px;
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
}
.flow-body {
  min-height: 120px;
}
.flow-item-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}
.flow-operator {
  font-weight: 500;
}
.flow-qty {
  color: #606266;
  font-size: 13px;
}
.flow-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: #606266;
}
.text-muted {
  color: #909399;
}
.text-danger {
  color: #f56c6c;
}
.text-primary {
  color: #409eff;
}
</style>

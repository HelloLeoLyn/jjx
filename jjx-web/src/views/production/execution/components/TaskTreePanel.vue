<template>
  <el-card class="filter-card" shadow="never">
    <div class="filter-bar">
      <el-input
        :model-value="query.keyword"
        placeholder="工单号 / 工序 / 任务号"
        clearable
        style="width: 210px"
        @update:model-value="updateQuery('keyword', $event)"
        @keyup.enter="handleQuery"
        @clear="handleQuery"
      />
      <el-select
        :model-value="query.status"
        placeholder="任务状态"
        clearable
        style="width: 120px"
        @update:model-value="updateQuery('status', $event)"
        @change="handleQuery"
      >
        <el-option label="未分配" value="PENDING" />
        <el-option label="进行中" value="ACTIVE" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
      <el-button icon="Refresh" @click="handleReset">重置</el-button>
    </div>
  </el-card>

  <el-card class="list-card" shadow="never">
    <el-table
      v-loading="loading"
      :data="rows"
      row-key="taskId"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      :lazy="true"
      :load="loadChildren"
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
          <el-text v-else-if="row.hasChildren" type="primary">-</el-text>
          <span v-else class="text-muted">未分配</span>
        </template>
      </el-table-column>
      <el-table-column label="任务数量" width="100" align="right">
        <template #default="{ row }">{{ fmtQty(row.taskQuantity) }}</template>
      </el-table-column>
      <el-table-column label="已完成" width="110" align="right">
        <template #default="{ row }">
          <el-link
            v-if="Number(row.completedQuantity || 0) > 0"
            type="primary"
            underline
            @click="emit('completion', row)"
            >{{ fmtQty(row.completedQuantity) }}</el-link
          >
          <span v-else class="text-muted">0</span>
        </template>
      </el-table-column>
      <el-table-column label="待审批" width="100" align="right">
        <template #default="{ row }">
          <el-tag
            v-if="Number(row.pendingQuantity || 0) > 0"
            size="small"
            type="warning"
            effect="plain"
            >{{ fmtQty(row.pendingQuantity) }}</el-tag
          >
          <span v-else class="text-muted">0</span>
        </template>
      </el-table-column>
      <el-table-column label="已分配" width="100" align="right">
        <template #default="{ row }">{{ fmtQty(row.assignedQuantity) }}</template>
      </el-table-column>
      <el-table-column label="剩余" width="100" align="right">
        <template #default="{ row }">{{ fmtQty(row.remainingQuantity) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="taskStatusTag(row.status)">{{ taskStatusLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="300" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="Number(row.pendingQuantity || 0) > 0"
            type="warning"
            link
            @click="emit('approval')"
            >去审批</el-button
          >
          <el-button
            v-if="canReport(row)"
            type="primary"
            link
            icon="EditPen"
            v-hasPermi="['production:work-report:add']"
            @click="emit('report', row)"
            >报工</el-button
          >
          <el-button type="primary" link icon="View" @click="emit('detail', row)">详情</el-button>
          <el-button
            v-if="Number(row.completedQuantity || 0) > 0"
            type="info"
            link
            @click="emit('completion', row)"
            >完成明细</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <div v-if="paginated" class="pagination-wrap">
      <el-pagination
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @update:current-page="emit('update:pageNum', $event)"
        @update:page-size="emit('update:pageSize', $event)"
        @size-change="loadRoot"
        @current-change="loadRoot"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { TaskTreeRow } from '@/types/production/task'
import { fmtQty } from '../utils'
import {
  statusLabel as taskStatusLabel,
  statusTag as taskStatusTag,
} from '@/views/production/dispatch/utils/taskFormatters'

type FilterQuery = { keyword: string; status: string }
type LoadChildren = (
  row: TaskTreeRow,
  treeNode: unknown,
  resolve: (children: TaskTreeRow[]) => void
) => void

const props = withDefaults(
  defineProps<{
    rows: TaskTreeRow[]
    loading: boolean
    query: FilterQuery
    loadRoot: () => void | Promise<void>
    loadChildren: LoadChildren
    canReport: (row: TaskTreeRow) => boolean
    paginated?: boolean
    pageNum?: number
    pageSize?: number
    total?: number
  }>(),
  { paginated: false, pageNum: 1, pageSize: 10, total: 0 }
)

const emit = defineEmits<{
  query: []
  reset: []
  approval: []
  report: [row: TaskTreeRow]
  detail: [row: TaskTreeRow]
  completion: [row: TaskTreeRow]
  'update:query': [query: FilterQuery]
  'update:pageNum': [pageNum: number]
  'update:pageSize': [pageSize: number]
}>()

const updateQuery = (key: keyof FilterQuery, value: string) => {
  emit('update:query', { ...props.query, [key]: value })
}
const handleQuery = () => {
  emit('query')
  props.loadRoot()
}
const handleReset = () => {
  emit('reset')
  props.loadRoot()
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}
.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  padding-bottom: 8px;
  flex-wrap: wrap;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.text-muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

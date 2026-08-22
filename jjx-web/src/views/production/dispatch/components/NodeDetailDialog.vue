<template>
  <el-dialog
    :model-value="visible"
    title="节点详情"
    width="560px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-if="node">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="人员">{{ node.assigneeName || '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="节点状态">
          <el-tag size="small" :type="statusTag(node.status)">{{ node.statusLabel || node.status || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="所属工单">{{ execution?.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属工序">{{ execution?.processName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属上级">{{ parentName }}</el-descriptions-item>
        <el-descriptions-item label="任务来源">{{ taskSource }}</el-descriptions-item>
        <el-descriptions-item label="上级节点ID">{{ node.parentNodeId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务数量">{{ fmt(node.taskQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="已完成">{{ fmt(node.selfReported) }}</el-descriptions-item>
        <el-descriptions-item label="待完成">{{ fmt(node.remainingQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="已分给下级">{{ fmt(node.childOccupied) }}</el-descriptions-item>
        <el-descriptions-item label="自己持有">{{ fmt(ownHeld) }}</el-descriptions-item>
      </el-descriptions>

      <div class="detail-title">直接下级</div>
      <el-table v-if="(node.children || []).length" :data="node.children || []" size="small">
        <el-table-column label="人员" min-width="120">
          <template #default="{ row }">{{ row.assigneeName || '未知人员' }}</template>
        </el-table-column>
        <el-table-column label="任务数量" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.taskQuantity) }}</template>
        </el-table-column>
        <el-table-column label="已完成" width="80" align="right">
          <template #default="{ row }">{{ fmt(row.selfReported) }}</template>
        </el-table-column>
        <el-table-column label="剩余未完成" width="100" align="right">
          <template #default="{ row }">{{ fmt(row.remainingQuantity) }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-tip">无直接下级</div>
    </div>
    <el-empty v-else description="节点不存在" :image-size="50" />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TaskNodeVO } from '@/types/production/taskNode'

const props = defineProps<{
  visible: boolean
  node?: TaskNodeVO | null
  execution?: { orderNo?: string; processName?: string } | null
  root?: TaskNodeVO | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
}>()

const ownHeld = computed(() => {
  const n = props.node
  if (!n) return 0
  return num(n.taskQuantity) - num(n.recalledQuantity) - num(n.childOccupied) - num(n.selfReported)
})

const parentName = computed(() => {
  const n = props.node
  if (!n || n.parentNodeId == null) return '系统根'
  if (n.parentAssigneeName) return n.parentAssigneeName
  // 兜底：从树中查找；找不到时说明上级是系统根
  return findNode(props.root || null, n.parentNodeId)?.assigneeName || '系统根'
})

/** 任务来源：普通用户子树视图下上级节点不在树内，使用服务端透出的 parentAssigneeName */
const taskSource = computed(() => {
  const n = props.node
  if (!n || n.parentNodeId == null) return '系统初始分配'
  if (n.parentAssigneeName) return `由 ${n.parentAssigneeName} 分配`
  const found = findNode(props.root || null, n.parentNodeId)
  if (found?.assigneeName) return `由 ${found.assigneeName} 分配`
  return '系统根初始分配'
})

function findNode(n: TaskNodeVO | null, id: number): TaskNodeVO | null {
  if (!n) return null
  if (n.taskNodeId === id) return n
  for (const c of n.children || []) {
    const hit = findNode(c, id)
    if (hit) return hit
  }
  return null
}

function statusTag(status?: string): any {
  return { ACTIVE: 'success', COMPLETED: 'info', CANCELLED: 'danger' }[status || ''] || 'info'
}
function num(v?: number | null): number {
  return Number(v || 0)
}
function fmt(v?: number | null): string {
  const n = num(v)
  return Number.isInteger(n) ? String(n) : String(n)
}
</script>

<style scoped>
.detail-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin: 14px 0 8px;
}
.empty-tip {
  color: #c0c4cc;
  font-size: 12px;
  padding: 8px 0;
}
</style>

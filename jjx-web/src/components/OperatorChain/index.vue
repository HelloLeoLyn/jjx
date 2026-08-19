<template>
  <span class="op-chain" :class="{ clickable }" @click="clickable && open()">
    <!-- P1-D 简洁列表模式：当前责任人 + 责任历史入口 -->
    <template v-if="displayNodes.length">
      <span class="op-name" :class="{ 'op-last': true }">{{ primaryName }}</span>
      <el-tooltip :content="`查看责任历史（共 ${nodeCount} 个责任实例）`" placement="top">
        <span class="op-level">{{ nodeCount > 1 ? `＋${nodeCount - 1}` : '' }}历史</span>
      </el-tooltip>
    </template>
    <span v-else class="op-empty">未派工</span>
  </span>

  <!-- 责任历史弹窗（Node Timeline；legacy fallback 时展示兼容 DTO） -->
  <el-dialog v-model="visible" :title="`责任链 - ${processName || ''}`" width="480px" append-to-body>
    <div v-if="orderNo || teamName || equipmentName" class="op-ctx">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item v-if="orderNo" label="工单">{{ orderNo }}</el-descriptions-item>
        <el-descriptions-item v-if="teamName" label="班组">
          <el-tag size="small" type="primary" effect="plain">{{ teamName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="设备">{{ equipmentName || '不限' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div v-if="displayNodes.length" class="op-full">
      <div v-for="(n, i) in displayNodes" :key="n.nodeId || `${n.assigneeId}-${n.assignedAt}-${i}`" class="op-node">
        <div class="op-card" :class="{ 'op-card-last': n.nodeStatus === 'ACTIVE' }">
          <span class="op-user">{{ n.assigneeName }}</span>
          <el-tag v-if="n.orgName" size="small" type="info" effect="plain" style="margin-left: 6px">{{ n.orgName }}</el-tag>
          <el-tag size="small" :type="statusTag(n.nodeStatus)" effect="plain" style="margin-left: auto">{{ statusLabel(n.nodeStatus) }}</el-tag>
        </div>
        <div v-if="i < displayNodes.length - 1" class="op-link">↓</div>
      </div>
    </div>
    <el-empty v-else description="未派工" :image-size="60" />

    <template #footer>
      <el-button v-if="dispatchId" type="primary" plain @click="goLogs">查看流水</el-button>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

/** Node 责任实例（来自 /nodes API；Node-first，legacy fallback 时后端返回兼容 DTO） */
interface NodeItem {
  nodeId?: number
  assigneeId?: number
  assigneeName?: string
  orgName?: string
  nodeStatus?: string
  assignedAt?: string
  closedAt?: string
  assignedByName?: string
  remark?: string
}

const props = withDefaults(
  defineProps<{
    /** P1-D 优先：Node 责任历史（后端 /nodes 已兼容 legacy-only → Node-like DTO） */
    nodes?: NodeItem[] | null
    /** Legacy fallback：operators JSON（仅当 nodes 为空时使用） */
    operators?: string | null
    processName?: string
    orderNo?: string
    teamName?: string
    equipmentName?: string
    dispatchId?: number | null
    clickable?: boolean
  }>(),
  { clickable: true },
)

const emit = defineEmits<{ (e: 'logs', dispatchId: number): void }>()

const visible = ref(false)

const NODE_LABELS: Record<string, string> = {
  ACTIVE: '当前负责', DELEGATED: '已下派', REASSIGNED: '已改派',
  RETURNED: '已退回', COMPLETED: '已完成', CANCELLED: '已取消',
}

function statusLabel(s?: string): string {
  return NODE_LABELS[s || ''] || s || '-'
}

function statusTag(s?: string): any {
  return { ACTIVE: 'success', DELEGATED: 'primary', REASSIGNED: 'warning', RETURNED: 'danger', COMPLETED: 'info', CANCELLED: 'info' }[s || ''] || 'info'
}

/** legacy operators 解析（仅 fallback） */
function parseOperators(json?: string | null): NodeItem[] {
  if (!json) return []
  try {
    const arr = JSON.parse(json) as any[]
    return arr.map((o, i) => ({
      assigneeId: o.userId,
      assigneeName: o.userName,
      nodeStatus: i === arr.length - 1 ? 'ACTIVE' : 'DELEGATED',
    }))
  } catch {
    return []
  }
}

const displayNodes = computed<NodeItem[]>(() => {
  if (props.nodes && props.nodes.length) return props.nodes
  return parseOperators(props.operators)
})

const nodeCount = computed(() => displayNodes.value.length)

/** 当前责任人：末位 ACTIVE 节点（Node-first；后端保证 ACTIVE 在末位或唯一） */
const primaryName = computed(() => {
  const nodes = displayNodes.value
  if (!nodes.length) return '未派工'
  const active = nodes.find((n) => n.nodeStatus === 'ACTIVE') || nodes[nodes.length - 1]
  return active?.assigneeName || '未派工'
})

const open = () => {
  visible.value = true
}

const goLogs = () => {
  if (!props.dispatchId) return
  visible.value = false
  emit('logs', props.dispatchId)
}
</script>

<style scoped>
.op-chain {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
}
.op-chain.clickable {
  cursor: pointer;
}
.op-name {
  font-size: 12px;
  color: #606266;
}
.op-name.op-last {
  font-weight: 600;
  color: #67c23a;
}
.op-level {
  font-size: 11px;
  color: #909399;
  margin-left: 2px;
}
.op-empty {
  color: #c0c4cc;
  font-size: 12px;
}
.op-ctx {
  margin-bottom: 12px;
}
.op-full {
  display: flex;
  flex-direction: column;
}
.op-node {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.op-card {
  width: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fafafa;
}
.op-card-last {
  border-color: #b3e19d;
  background: #f0f9eb;
}
.op-user {
  font-size: 13px;
  font-weight: 500;
}
.op-link {
  color: #c0c4cc;
  font-size: 14px;
  line-height: 20px;
  padding: 2px 0;
}
</style>

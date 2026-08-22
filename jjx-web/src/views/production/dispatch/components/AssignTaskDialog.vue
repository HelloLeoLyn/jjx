<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="760px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading">
      <!-- 当前任务信息（后端 TaskNode 投影，不猜权限） -->
      <div class="section-title">当前任务</div>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="任务节点">{{ parentNode?.assigneeName || '未分配' }}</el-descriptions-item>
        <el-descriptions-item label="任务数量">{{ fmt(parentNode?.taskQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="已完成">{{ fmt(parentNode?.selfReported) }}</el-descriptions-item>
        <el-descriptions-item label="已分给下级">{{ fmt(parentNode?.childOccupied) }}</el-descriptions-item>
        <el-descriptions-item label="我自己剩余">{{ fmt(ownHeld) }}</el-descriptions-item>
        <el-descriptions-item label="当前可分配">
          <span :style="{ color: Number(parentNode?.availableToAssign || 0) > 0 ? '#409eff' : '#909399' }">
            {{ fmt(parentNode?.availableToAssign) }}
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 当前已分配（直接子节点） -->
      <div class="section-title">当前分配</div>
      <el-table :data="children" size="small" max-height="240">
        <el-table-column label="人员" min-width="110">
          <template #default="{ row }">{{ row.assigneeName || '未知人员' }}</template>
        </el-table-column>
        <el-table-column label="任务数量" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.taskQuantity) }}</template>
        </el-table-column>
        <el-table-column label="已完成" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.selfReported) }}</template>
        </el-table-column>
        <el-table-column label="剩余未完成" width="100" align="right">
          <template #default="{ row }">{{ fmt(row.remainingQuantity) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button v-if="canRecall(row)" link type="warning" size="small" @click="openRecall(row)">收回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 本次分配 -->
      <div class="section-title">
        本次分配
        <el-button link type="primary" icon="Plus" :disabled="!canAdd" @click="pickerVisible = true">添加人员</el-button>
      </div>
      <el-table v-if="drafts.length" :data="drafts" size="small">
        <el-table-column label="人员" min-width="120">
          <template #default="{ row }">{{ row.name }}</template>
        </el-table-column>
        <el-table-column label="部门" min-width="110">
          <template #default="{ row }">{{ row.deptName || '-' }}</template>
        </el-table-column>
        <el-table-column label="任务数量" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" :precision="4" :step="1" style="width: 120px" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="drafts.splice($index, 1)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!drafts.length" class="empty-tip">未添加分配人员；允许部分分配，剩余继续自己持有。</div>
      <div class="draft-total">
        本次合计：<b>{{ fmt(draftTotal) }}</b> / 当前可分配 {{ fmt(parentNode?.availableToAssign) }}
        <span v-if="draftTotal > Number(parentNode?.availableToAssign || 0)" style="color: #f56c6c">超出可分配数量</span>
      </div>
      <div class="after-total">
        分配后我自己剩余：<b :style="{ color: afterOwnHeld > 0 ? '#67c23a' : '#909399' }">{{ fmt(afterOwnHeld) }}</b>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleAssign">确认分配</el-button>
    </template>

    <!-- 人员选择（组织范围内候选） -->
    <OperatorPicker
      v-model:visible="pickerVisible"
      :users="candidates"
      :dept-tree="deptTree"
      :model-value="[]"
      title="选择分配人员"
      @confirm="onPick"
    />

    <!-- 节点详情（当前分配“查看”） -->
    <NodeDetailDialog
      v-model:visible="detailVisible"
      :node="detailNode"
      :execution="{ orderNo: props.orderNo, processName: props.processName }"
      :root="tree"
    />

    <!-- 独立收回弹窗（TT-FINAL-05：可收回范围/数量/备注） -->
    <RecallDialog
      v-model:visible="recallVisible"
      :child="recallTarget"
      @confirm="onRecallConfirm"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import OperatorPicker from '@/components/OperatorPicker/index.vue'
import NodeDetailDialog from './NodeDetailDialog.vue'
import RecallDialog from './RecallDialog.vue'
import { taskNodeApi } from '@/api/production/taskNode'
import { deptApi } from '@/api/system/dept'
import { hasPermi } from '@/directives'
import { useUserStore } from '@/store/modules/user'
import type { TaskNodeVO, TaskCandidateVO } from '@/types/production/taskNode'

const props = defineProps<{
  visible: boolean
  executionId: number
  /** 分配目标父节点ID（根节点或当前用户持有的节点） */
  parentNodeId: number
  /** 弹窗标题（如：印刷 · SO-001 分配任务） */
  title?: string
  /** 工单号/工序名（节点详情展示用，可选） */
  orderNo?: string
  processName?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'changed'): void
}>()

const userStore = useUserStore()
const me = computed(() => userStore.userId)

const loading = ref(false)
const submitting = ref(false)
const tree = ref<TaskNodeVO | null>(null)
const candidates = ref<TaskCandidateVO[]>([])
/** 真实组织树（sys_dept treeselect）：部门节点名称必须是真实 deptName（TT-FINAL-02） */
const deptTree = ref<any[]>([])
const pickerVisible = ref(false)

interface DraftRow {
  userId: number
  name: string
  deptName?: string
  quantity: number
}
const drafts = ref<DraftRow[]>([])

// TT-FINAL-05：节点详情 / 独立收回弹窗
const detailVisible = ref(false)
const detailNode = ref<TaskNodeVO | null>(null)
const recallVisible = ref(false)
const recallTarget = ref<TaskNodeVO | null>(null)

const dialogTitle = computed(() => props.title || '分配任务')

function findNode(node: TaskNodeVO | null | undefined, id: number): TaskNodeVO | null {
  if (!node) return null
  if (node.taskNodeId === id) return node
  for (const c of node.children || []) {
    const hit = findNode(c, id)
    if (hit) return hit
  }
  return null
}

const parentNode = computed(() => findNode(tree.value, props.parentNodeId))

const children = computed<TaskNodeVO[]>(() => parentNode.value?.children || [])

const ownHeld = computed(() => {
  const p = parentNode.value
  if (!p) return 0
  return num(p.taskQuantity) - num(p.recalledQuantity) - num(p.childOccupied)
})

const draftTotal = computed(() => drafts.value.reduce((s, d) => s + num(d.quantity), 0))
/** 分配后我自己剩余 = 自己当前持有 - 本次分配合计（下限 0） */
const afterOwnHeld = computed(() => Math.max(0, ownHeld.value - draftTotal.value))

const canAdd = computed(() => hasPermi('production:task:assign') && draftTotal.value < num(parentNode.value?.availableToAssign))

const isAdminOrTaskAdmin = () => hasPermi('*:*:*') || hasPermi('production:task:admin')

const isParentHolder = computed(() => {
  const p = parentNode.value
  if (!p) return false
  // 系统根（assigneeId 为空）无持有人：首次分配放行（后端 assign 权限点/数量校验兜底）
  if (p.assigneeId == null) return true
  return isAdminOrTaskAdmin() || me.value === p.assigneeId
})

const canSubmit = computed(() => {
  const p = parentNode.value
  if (!p) return false
  if (!hasPermi('production:task:assign') || !isParentHolder.value) return false
  if (drafts.value.length === 0 || draftTotal.value <= 0) return false
  return draftTotal.value <= num(p.availableToAssign)
})

/** 收回按钮：父节点持有人 + task:recall + 子节点 selfRemaining>0 */
const canRecall = (child: TaskNodeVO) => {
  return num(child.remainingQuantity) > 0
    && (isAdminOrTaskAdmin() || me.value === parentNode.value?.assigneeId)
    && hasPermi('production:task:recall')
}

async function load() {
  loading.value = true
  try {
    const res: any = await taskNodeApi.getTree(props.executionId)
    tree.value = res?.data || null
  } catch (e: any) {
    ElMessage.error(e?.message || '加载任务树失败')
  } finally {
    loading.value = false
  }
  if (!candidates.value.length) {
    try {
      const res: any = await taskNodeApi.candidates()
      candidates.value = res?.data || []
    } catch {
      candidates.value = []
    }
  }
  if (!deptTree.value.length) {
    try {
      const res: any = await deptApi.treeselect({})
      deptTree.value = res?.data || []
    } catch {
      deptTree.value = []
    }
  }
}

watch(
  () => [props.visible, props.parentNodeId],
  () => {
    if (props.visible) {
      drafts.value = []
      load()
    }
  },
)

const onPick = (ids: number[]) => {
  const existing = new Set(drafts.value.map((d) => d.userId))
  for (const id of ids) {
    if (existing.has(id)) continue
    const c = candidates.value.find((x) => x.userId === id)
    if (c) {
      drafts.value.push({ userId: c.userId, name: c.nickName || c.userName || '未知人员', deptName: c.deptName, quantity: 0 })
      existing.add(id)
    }
  }
}

const openDetail = (child: TaskNodeVO) => {
  detailNode.value = child
  detailVisible.value = true
}

const openRecall = (child: TaskNodeVO) => {
  if (num(child.remainingQuantity) <= 0) return
  recallTarget.value = child
  recallVisible.value = true
}

const onRecallConfirm = async (payload: { quantity: number; remark: string }) => {
  const child = recallTarget.value
  if (!child) return
  const maxQty = num(child.remainingQuantity)
  const qty = payload.quantity
  if (qty <= 0 || qty > maxQty) {
    ElMessage.warning(`收回数量必须在 0 < x <= ${fmt(maxQty)} 之间`)
    recallVisible.value = false
    return
  }
  try {
    await taskNodeApi.recall(child.taskNodeId, qty, payload.remark)
    ElMessage.success('已收回')
    recallVisible.value = false
    // 收回后容量立即恢复，刷新弹窗可马上重新分配
    await load()
    emit('changed')
  } catch (e: any) {
    ElMessage.error(e?.message || '收回失败')
  }
}

const handleAssign = async () => {
  if (drafts.value.some((d) => num(d.quantity) <= 0)) {
    ElMessage.warning('每人任务数量必须大于 0')
    return
  }
  const p = parentNode.value
  if (!p) return
  if (draftTotal.value > num(p.availableToAssign)) {
    ElMessage.warning(`本次合计 ${fmt(draftTotal.value)} 超过可分配 ${fmt(p.availableToAssign)}`)
    return
  }
  submitting.value = true
  try {
    await taskNodeApi.assign(p.taskNodeId, drafts.value.map((d) => ({ userId: d.userId, quantity: d.quantity })))
    ElMessage.success('分配成功')
    drafts.value = []
    await load()
    emit('changed')
  } catch (e: any) {
    ElMessage.error(e?.message || '分配失败')
  } finally {
    submitting.value = false
  }
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
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin: 14px 0 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.empty-tip {
  color: #c0c4cc;
  font-size: 12px;
  padding: 8px 0;
}
.draft-total {
  margin-top: 10px;
  font-size: 13px;
  color: #606266;
}
.after-total {
  margin-top: 4px;
  font-size: 13px;
  color: #606266;
}
</style>

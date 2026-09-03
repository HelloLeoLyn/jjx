<template>
  <el-dialog v-model="dialogVisible" title="四部门会签" width="720px" append-to-body>
    <div v-if="req" class="req-head">
      <el-tag size="small" type="info">{{ req.requirementNo }}</el-tag>
      <span style="margin-left: 8px; font-weight: 600">{{ req.title }}</span>
      <el-tag v-if="req.requirementStatus !== 2" :type="req.requirementStatus === 3 ? 'success' : 'danger'" size="small" style="margin-left: 8px">
        {{ req.requirementStatus === 3 ? '已通过（全部同意）' : '已驳回' }}
      </el-tag>
      <div v-if="req.requirementStatus === 2" class="req-tip">等待四部门会签：全部同意后自动生效，任一不同意即驳回</div>
    </div>

    <div class="dept-grid">
      <div v-for="d in DEPTS" :key="d.role" class="dept-card" :class="{ mine: canSign(d) }">
        <div class="dept-head">
          <b>{{ d.label }}</b>
          <el-tag :type="statusOf(d)?.tag || 'info'" size="small">
            {{ statusOf(d)?.text || '未签' }}
          </el-tag>
        </div>
        <template v-if="recordOf(d)">
          <div class="dept-meta">
            会签人：{{ recordOf(d)!.approvalUserName || '-' }}
            <div style="color: #909399; font-size: 12px">{{ recordOf(d)!.approveTime || '' }}</div>
          </div>
          <div v-if="recordOf(d)!.comment" class="dept-comment">意见：{{ recordOf(d)!.comment }}</div>
        </template>
        <template v-if="req && req.requirementStatus === 2 && canSign(d)">
          <el-input
            v-model="commentMap[d.role]"
            type="textarea"
            :rows="2"
            maxlength="200"
            placeholder="会签意见（可空）"
            style="margin: 8px 0"
          />
          <div class="sign-btns">
            <el-button type="success" size="small" :loading="signing === d.role" @click="sign(d, true)">✓ 同意</el-button>
            <el-button type="danger" size="small" :loading="signing === d.role" @click="sign(d, false)">✕ 不同意</el-button>
          </div>
        </template>
        <div v-else-if="!canSign(d) && !recordOf(d)" class="dept-none">无会签权限</div>
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { signApproval, listApprovals } from '@/api/biz/requirement'
import { hasPermi } from '@/directives'

const props = defineProps<{ visible: boolean; requirement: any }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'signed'): void }>()

const DEPTS = [
  { role: 'ENGINEERING', label: '工程部', perms: ['engineering:ops'] },
  { role: 'MAKING', label: '制造部', perms: ['production:all'] },
  { role: 'PURCHASE', label: '采购/仓库', perms: ['purchase:ops', 'inventory:ops'] },
  { role: 'QUALITY', label: '品管部', perms: ['production:ops'] },
]

const dialogVisible = ref(false)
const req = ref<any>(null)
const approvals = ref<any[]>([])
const signing = ref('')
const commentMap = ref<Record<string, string>>({})

watch(
  () => props.visible,
  (v) => {
    dialogVisible.value = v
    if (v && props.requirement) {
      req.value = props.requirement
      commentMap.value = {}
      load()
    }
  },
)
watch(dialogVisible, (v) => emit('update:visible', v))

function canSign(d: any) {
  return hasPermi(d.perms)
}

function recordOf(d: any) {
  if (!req.value) return undefined
  // 只看当前轮次的记录
  return approvals.value.find(
    (a) => a.approvalRole === d.role && a.roundNo === (req.value.currentRound || 1),
  )
}

type DeptTag = 'info' | 'success' | 'danger' | 'warning' | 'primary'

function statusOf(d: any): { text: string; tag: DeptTag } {
  const r = recordOf(d)
  if (!r) return { text: '未签', tag: 'info' }
  return r.approveResult === 1
    ? { text: '同意', tag: 'success' }
    : { text: '不同意', tag: 'danger' }
}

async function load() {
  if (!req.value) return
  const res: any = await listApprovals(req.value.requirementId)
  approvals.value = res?.data || []
  // 若列表内已流转（可能其他人刚签完），同步最新状态
  if (res?.data) {
    const cur = approvals.value.filter((a) => a.roundNo === (req.value.currentRound || 1))
    const agreed = cur.filter((a) => a.approveResult === 1).map((a) => a.approvalRole)
    if (agreed.length === 4) req.value.requirementStatus = 3
  }
}

async function sign(d: any, approved: boolean) {
  if (!req.value) return
  signing.value = d.role
  try {
    const res: any = await signApproval(req.value.requirementId, d.role, approved, commentMap.value[d.role]?.trim() || '')
    if (res?.code === 200) {
      ElMessage.success(approved ? '已同意' : '已驳回（需求退回）')
      if (res.data?.requirementStatus === 3) {
        ElMessage.success('四部门会签全部通过，需求已生效')
      }
      commentMap.value[d.role] = ''
      await load()
      emit('signed')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '会签失败')
  } finally {
    signing.value = ''
  }
}
</script>

<style scoped>
.req-head {
  margin-bottom: 12px;
  line-height: 1.6;
}
.req-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.dept-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.dept-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 12px;
}
.dept-card.mine {
  border-color: #409eff;
  background: #f5f9ff;
}
.dept-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.dept-meta {
  margin-top: 6px;
  font-size: 13px;
}
.dept-comment {
  margin-top: 4px;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  padding: 4px 6px;
  border-radius: 4px;
}
.dept-none {
  color: #c0c4cc;
  font-size: 12px;
  margin-top: 6px;
}
.sign-btns {
  display: flex;
  gap: 8px;
}
</style>

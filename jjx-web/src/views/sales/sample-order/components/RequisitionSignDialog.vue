<template>
  <el-dialog
    v-model="dialogVisible"
    title="样品需求单会签"
    width="720px"
    append-to-body
    :close-on-click-modal="false"
  >
    <div v-if="orderNo" class="sign-head">
      <el-tag size="small" type="info">{{ orderNo }}</el-tag>
      <span class="sign-tip">三位签字位（业务 / 核准 / 部门主管）各由权限决定；不卡流程，纯留痕。</span>
    </div>

    <div class="sign-grid">
      <div v-for="s in signRoles" :key="s.role" class="sign-card" :class="{ mine: canSign(s) }">
        <div class="sign-card-head">
          <b>{{ s.label }}</b>
          <el-tag :type="statusOf(s.role).tag" size="small">{{ statusOf(s.role).text }}</el-tag>
        </div>

        <template v-if="recordOf(s.role)">
          <div class="sign-meta">
            会签人：{{ recordOf(s.role)?.signerName || '-' }}
            <div class="sign-time">{{ recordOf(s.role)?.signTime || '' }}</div>
          </div>
          <div v-if="recordOf(s.role)?.comment" class="sign-comment">
            意见：{{ recordOf(s.role)?.comment }}
          </div>
        </template>

        <template v-if="canSign(s)">
          <el-input
            v-model="commentMap[s.role]"
            type="textarea"
            :rows="2"
            maxlength="200"
            placeholder="签字意见（可空）"
            class="sign-input"
          />
          <div class="sign-btns">
            <el-button type="success" size="small" :loading="signing === s.role" @click="sign(s, true)">
              ✓ 同意
            </el-button>
            <el-button type="danger" size="small" :loading="signing === s.role" @click="sign(s, false)">
              ✕ 不同意
            </el-button>
          </div>
        </template>
        <div v-else-if="!recordOf(s.role)" class="sign-none">无签字权限</div>
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
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SAMPLE_REQUISITION_SIGN_ROLES } from '@/enums/sales/SampleRequisitionEnum'
import type { SampleRequisitionSign } from '@/types/sales/sampleOrder'
import { hasPermi } from '@/directives'

const props = defineProps<{ visible: boolean; orderId?: number; orderNo?: string }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'signed'): void }>()

const signRoles = SAMPLE_REQUISITION_SIGN_ROLES

const dialogVisible = ref(false)
const signs = ref<SampleRequisitionSign[]>([])
const signing = ref('')
const commentMap = ref<Record<string, string>>({})

watch(
  () => props.visible,
  (v) => {
    dialogVisible.value = v
    if (v) {
      commentMap.value = {}
      load()
    }
  }
)
watch(dialogVisible, (v) => emit('update:visible', v))

function canSign(s: { permission: string }) {
  return hasPermi(s.permission)
}

function recordOf(role: string) {
  return signs.value.find((x) => x.signRole === role)
}

function statusOf(role: string): { text: string; tag: 'info' | 'success' | 'danger' } {
  const r = recordOf(role)
  if (!r) return { text: '未签', tag: 'info' }
  return r.approveResult === 1 ? { text: '同意', tag: 'success' } : { text: '不同意', tag: 'danger' }
}

async function load() {
  if (!props.orderId) return
  try {
    const res: any = await sampleOrderApi.listRequisitionSigns(props.orderId)
    signs.value = res?.data || []
  } catch {
    signs.value = []
  }
}

async function sign(s: { role: string; label: string }, approved: boolean) {
  if (!props.orderId) return
  signing.value = s.role
  try {
    const res: any = await sampleOrderApi.signRequisition(
      props.orderId,
      s.role,
      approved,
      commentMap.value[s.role]?.trim() || ''
    )
    if (res?.code === 200) {
      ElMessage.success(`${s.label} 已${approved ? '同意' : '不同意'}`)
      commentMap.value[s.role] = ''
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
.sign-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.sign-tip {
  color: #909399;
  font-size: 12px;
}

.sign-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
}

.sign-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 12px;
  min-height: 150px;
}

.sign-card.mine {
  border-color: #409eff;
  background: #f5f9ff;
}

.sign-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sign-meta {
  margin-top: 6px;
  font-size: 13px;
}

.sign-time {
  color: #909399;
  font-size: 12px;
}

.sign-comment {
  margin-top: 4px;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  padding: 4px 6px;
  border-radius: 4px;
  white-space: pre-wrap;
}

.sign-input {
  margin: 8px 0;
}

.sign-btns {
  display: flex;
  gap: 8px;
}

.sign-none {
  color: #c0c4cc;
  font-size: 12px;
  margin-top: 6px;
}
</style>

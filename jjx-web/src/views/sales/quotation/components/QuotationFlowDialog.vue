<template>
  <el-dialog
    :title="`状态流转 - ${quotationNo || ''}`"
    v-model="visible"
    width="720px"
    append-to-body
    destroy-on-close
  >
    <div class="flow-container">
      <!-- 当前状态 -->
      <div class="current-status">
        <span class="label">当前状态：</span>
        <el-tag :type="statusTagType" size="large" effect="dark">{{ statusLabel }}</el-tag>
      </div>

      <!-- 流转时间线 -->
      <el-timeline class="flow-timeline">
        <el-timeline-item
          v-for="(flow, idx) in flowList"
          :key="flow.flowId"
          :timestamp="formatTime(flow.createTime)"
          :type="timelineType(flow, idx)"
          :hollow="idx !== 0"
        >
          <div class="flow-item">
            <div class="flow-title">
              <el-icon><component :is="actionIcon(flow.actionCode)" /></el-icon>
              <span class="action-name">{{ flow.actionName }}</span>
              <span class="status-change" v-if="flow.fromStatus !== null && flow.fromStatus !== flow.toStatus">
                {{ statusText(flow.fromStatus) }} → {{ statusText(flow.toStatus) }}
              </span>
            </div>
            <div class="flow-meta">
              <span>操作人：{{ flow.operatorName || '-' }}</span>
            </div>
            <div class="flow-remark" v-if="flow.remark">
              <span class="remark-label">说明：</span>{{ flow.remark }}
            </div>
            <!-- 附件 -->
            <div class="flow-attachments" v-if="getAttachments(flow).length">
              <div
                v-for="att in getAttachments(flow)"
                :key="att.id"
                class="att-item"
              >
                <el-icon><Document /></el-icon>
                <el-link type="primary" :href="downloadUrl(att.id)" underline="never" target="_blank">
                  {{ att.fileName }}
                </el-link>
                <span class="att-size">{{ formatSize(att.fileSize) }}</span>
              </div>
            </div>
          </div>
        </el-timeline-item>
        <el-timeline-item v-if="!flowList.length">
          <span class="empty-text">暂无流转记录</span>
        </el-timeline-item>
      </el-timeline>

      <!-- 操作区域 -->
      <el-divider content-position="left">流转操作</el-divider>
      <div class="flow-actions">
        <el-button
          v-for="action in availableActions"
          :key="action.code"
          :type="action.type"
          :plain="action.plain"
          :disabled="action.disabled"
          @click="openAction(action)"
        >
          <el-icon style="margin-right: 4px"><component :is="action.icon" /></el-icon>
          {{ action.label }}
        </el-button>
        <span v-if="!availableActions.length" class="empty-text">当前状态无可执行操作</span>
      </div>
    </div>

    <!-- 操作确认弹窗（含说明 + 附件） -->
    <el-dialog
      :title="`${activeAction?.label || ''}`"
      v-model="actionDialogVisible"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form label-width="70px">
        <el-form-item label="说明">
          <el-input
            v-model="actionRemark"
            type="textarea"
            :rows="3"
            :placeholder="activeAction?.code?.includes('REJECT') ? '请填写拒绝原因（必填）' : '流转说明（选填）'"
          />
        </el-form-item>
        <el-form-item label="附件">
          <el-upload
            :action="uploadUrl"
            :headers="uploadHeaders"
            :data="uploadData"
            multiple
            :limit="9"
            :on-success="onUploadSuccess"
            :on-remove="onUploadRemove"
            :on-error="onUploadError"
            :file-list="actionFileList"
            list-type="text"
          >
            <el-button type="primary" plain :icon="Upload">上传附件（截图/文档/补充资料）</el-button>
          </el-upload>
          <div class="upload-tip">支持图片、PDF、Word、Excel 等，用于作为本次流转的依据</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmAction">确定{{ activeAction?.label }}</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { TagType } from '@/types'
import { ElMessage } from 'element-plus'
import { Document, Upload, UploadFilled, Promotion, Check, Close, EditPen, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { quotationApi } from '@/api/sales/quotation'
import { attachmentApi } from '@/api/system/attachment'

const props = defineProps<{
  modelValue: boolean
  quotationId: number | null
  quotationNo?: string
  currentStatus?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val),
})

const flowList = ref<any[]>([])
const attachmentsMap = ref<Record<number, any[]>>({})
const loading = ref(false)

// 状态映射（与后端 QuotationStatus 一致）
const statusMap: Record<number, string> = {
  0: '草稿', 1: '已发送', 2: '已确认', 3: '已拒绝', 4: '已过期', 5: '待审核', 6: '已审核', 8: '改单', 9: '已完成',
}

const statusLabel = computed(() => statusMap[props.currentStatus ?? -1] ?? '-')
const statusTagType = computed<TagType>(() => {
  const map: Record<number, TagType> = { 0: 'info', 1: 'primary', 2: 'success', 3: 'danger', 4: 'info', 5: 'warning', 6: 'success', 8: 'warning', 9: 'success' }
  return map[props.currentStatus ?? -1] ?? 'info'
})

function statusText(status: number | null | undefined): string {
  return statusMap[status ?? -1] ?? '-'
}

// ===== 可执行操作（按状态） =====
interface FlowAction {
  code: string
  label: string
  type: 'primary' | 'success' | 'danger' | 'warning' | 'info'
  plain?: boolean
  icon: any
  disabled?: boolean
}

const availableActions = computed<FlowAction[]>(() => {
  const s = props.currentStatus
  const list: FlowAction[] = []
  if (s === 0) {
    list.push({ code: 'SUBMIT_REVIEW', label: '提交审核', type: 'primary', icon: EditPen })
    list.push({ code: 'SEND', label: '发送报价', type: 'info', plain: true, icon: Promotion })
  } else if (s === 5) {
    list.push({ code: 'APPROVE', label: '审核通过', type: 'success', icon: Check })
    list.push({ code: 'REJECT', label: '审核驳回', type: 'danger', plain: true, icon: Close })
  } else if (s === 6) {
    list.push({ code: 'SEND', label: '发送报价', type: 'primary', icon: Promotion })
  } else if (s === 1) {
    list.push({ code: 'CUSTOMER_CONFIRM', label: '客户确认报价', type: 'success', icon: CircleCheck })
    list.push({ code: 'CUSTOMER_REJECT', label: '客户拒绝报价', type: 'danger', plain: true, icon: CircleClose })
  } else if (s === 9) {
    list.push({ code: 'MODIFY', label: '改单', type: 'warning', icon: EditPen })
  }
  return list
})

// ===== 操作弹窗 =====
const actionDialogVisible = ref(false)
const activeAction = ref<FlowAction | null>(null)
const actionRemark = ref('')
const actionFileList = ref<any[]>([])
const uploadedIds: number[] = []
const submitting = ref(false)

const uploadUrl = computed(() => {
  const base = (import.meta.env.VITE_BASE_API || '/api') as string
  return `${base}/system/attachment/upload`
})

const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { token } : {}
})

const uploadData = computed(() => ({
  bizType: 'quotation_flow',
  bizId: props.quotationId ?? 0,
}))

function openAction(action: FlowAction) {
  activeAction.value = action
  actionRemark.value = ''
  actionFileList.value = []
  uploadedIds.length = 0
  actionDialogVisible.value = true
}

function onUploadSuccess(response: any) {
  const id = response?.data
  if (id) uploadedIds.push(Number(id))
  else ElMessage.warning('附件上传响应异常')
}

function onUploadError() {
  ElMessage.error('附件上传失败')
}

function onUploadRemove(file: any) {
  const idx = actionFileList.value.indexOf(file)
  if (idx >= 0 && uploadedIds[idx] !== undefined) {
    // 删除时从已上传列表移除
    uploadedIds.splice(idx, 1)
  }
}

async function confirmAction() {
  const action = activeAction.value
  if (!action || !props.quotationId) return

  // 拒绝类操作必须有说明
  if (action.code.includes('REJECT') && !actionRemark.value.trim()) {
    ElMessage.warning('请填写拒绝原因')
    return
  }

  submitting.value = true
  try {
    const attIds = uploadedIds.length ? JSON.stringify(uploadedIds) : undefined
    const qid = props.quotationId
    switch (action.code) {
      case 'SUBMIT_REVIEW':
        await quotationApi.submitReview(qid, attIds)
        break
      case 'SEND':
        await quotationApi.send(qid, attIds)
        break
      case 'APPROVE':
      case 'REJECT':
        await quotationApi.review(qid, action.code === 'APPROVE', actionRemark.value || undefined, attIds)
        break
      case 'CUSTOMER_CONFIRM':
        await quotationApi.confirm(qid, attIds)
        break
      case 'CUSTOMER_REJECT':
        await quotationApi.reject(qid, attIds)
        break
      case 'MODIFY':
        await quotationApi.modify(qid, attIds)
        break
    }
    ElMessage.success(`${action.label}成功`)
    actionDialogVisible.value = false
    emit('success')
    await loadFlow()
  } catch (e: any) {
    ElMessage.error(e?.message || `${action.label}失败`)
  } finally {
    submitting.value = false
  }
}

// ===== 流转记录加载 =====
async function loadFlow() {
  if (!props.quotationId) return
  loading.value = true
  try {
    const res = await quotationApi.getFlowRecords(props.quotationId)
    flowList.value = (res as any)?.data || []
    // 拉取所有附件详情
    await loadAttachments()
  } finally {
    loading.value = false
  }
}

async function loadAttachments() {
  if (!props.quotationId) return
  const res = await attachmentApi.list('quotation_flow', props.quotationId)
  const atts: any[] = (res as any)?.data || []
  const map: Record<number, any[]> = {}
  for (const att of atts) {
    for (const flow of flowList.value) {
      if (flow.attachmentIds) {
        try {
          const ids = JSON.parse(flow.attachmentIds)
          if (ids.includes(Number(att.id))) {
            if (!map[flow.flowId]) map[flow.flowId] = []
            map[flow.flowId].push(att)
          }
        } catch { /* ignore */ }
      }
    }
  }
  attachmentsMap.value = map
}

function getAttachments(flow: any): any[] {
  return attachmentsMap.value[flow.flowId] || []
}

function downloadUrl(id: number): string {
  const base = (import.meta.env.VITE_BASE_API || '/api') as string
  return `${base}/system/attachment/download/${id}`
}

function formatSize(size: number | null | undefined): string {
  if (!size) return ''
  if (size < 1024) return `${size}B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`
  return `${(size / 1024 / 1024).toFixed(1)}MB`
}

function formatTime(t: string | null | undefined): string {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

function timelineType(flow: any, idx: number): TagType | '' {
  if (idx !== 0) return ''
  const map: Record<string, string> = {
    APPROVE: 'success', CUSTOMER_CONFIRM: 'success',
    REJECT: 'danger', CUSTOMER_REJECT: 'danger', MODIFY: 'warning',
    SUBMIT_REVIEW: 'primary', SEND: 'primary',
  }
  return (map[flow.actionCode] || 'primary') as TagType
}

function actionIcon(code: string) {
  const map: Record<string, any> = {
    APPROVE: Check, CUSTOMER_CONFIRM: CircleCheck,
    REJECT: Close, CUSTOMER_REJECT: CircleClose,
    SUBMIT_REVIEW: EditPen, SEND: Promotion, MODIFY: EditPen,
  }
  return map[code] || UploadFilled
}

watch(() => props.modelValue, (val) => {
  if (val) loadFlow()
})

onMounted(() => {
  if (props.modelValue) loadFlow()
})
</script>

<style scoped>
.flow-container {
  min-height: 200px;
}

.current-status {
  margin-bottom: 16px;
  font-size: 14px;
}

.current-status .label {
  color: #606266;
}

.flow-timeline {
  padding-left: 4px;
}

.flow-item {
  font-size: 13px;
}

.flow-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #303133;
}

.action-name {
  font-size: 14px;
}

.status-change {
  color: #909399;
  font-size: 12px;
  font-weight: normal;
}

.flow-meta {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}

.flow-remark {
  color: #606266;
  margin-top: 4px;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 6px 8px;
  white-space: pre-wrap;
}

.remark-label {
  color: #909399;
}

.flow-attachments {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.att-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.att-size {
  color: #c0c4cc;
  font-size: 11px;
}

.flow-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.empty-text {
  color: #c0c4cc;
  font-size: 13px;
}

.upload-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
</style>

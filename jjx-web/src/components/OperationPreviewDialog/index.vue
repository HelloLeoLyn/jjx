<template>
  <el-dialog
    :title="`操作预览 - ${operation?.name || ''}`"
    v-model="visible"
    width="640px"
    append-to-body
    destroy-on-close
  >
    <div v-if="operation" class="op-preview">
      <!-- ① 操作头：单据号 + 状态跳转 -->
      <div class="op-header">
        <div class="op-biz">
          <el-icon><Document /></el-icon>
          <span class="op-biz-no">{{ bizNo || '-' }}</span>
          <el-tag v-if="operation.key" size="small" type="info">{{ operation.key }}</el-tag>
        </div>
        <div v-if="operation.toStatus !== undefined" class="op-status-flow">
          <template v-for="(from, idx) in operation.fromStatus" :key="from">
            <el-tag v-if="idx > 0" class="status-or" type="info" effect="plain">或</el-tag>
            <el-tag :type="statusTagType(from)" effect="plain">{{ statusText(from) }}</el-tag>
          </template>
          <el-icon class="status-arrow"><Right /></el-icon>
          <el-tag :type="statusTagType(operation.toStatus)" effect="dark">{{ statusText(operation.toStatus) }}</el-tag>
        </div>
      </div>

      <!-- ② 操作内容：动态表单 -->
      <div v-if="operation.fields?.length" class="op-section">
        <div class="op-section-title">操作内容</div>
        <el-form label-width="90px" label-position="left">
          <el-form-item
            v-for="field in operation.fields"
            :key="field.key"
            :label="field.label"
            :required="field.required"
          >
            <el-input
              v-if="field.type === 'input'"
              v-model="formValues[field.key]"
              :placeholder="field.placeholder || `请输入${field.label}`"
            />
            <el-input
              v-else-if="field.type === 'textarea'"
              v-model="formValues[field.key]"
              type="textarea"
              :rows="3"
              :placeholder="field.placeholder || `请输入${field.label}`"
            />
            <el-input-number
              v-else-if="field.type === 'number'"
              v-model="formValues[field.key]"
              :min="1"
              style="width: 100%"
            />
            <el-select
              v-else-if="field.type === 'select'"
              v-model="formValues[field.key]"
              :placeholder="field.placeholder || `请选择${field.label}`"
              style="width: 100%"
            >
              <el-option v-for="opt in field.options || []" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <!-- ③ 证据：截图/文件/文档 -->
      <div v-if="operation.evidence" class="op-section">
        <div class="op-section-title">
          证据
          <span class="op-section-tip">截图 / 文件 / 文档，作为本次操作的依据（选填）</span>
        </div>
        <el-upload
          :action="uploadUrl"
          :headers="uploadHeaders"
          :data="uploadData"
          multiple
          :limit="9"
          :on-success="onUploadSuccess"
          :on-remove="onUploadRemove"
          :on-error="onUploadError"
          :file-list="evidenceFileList"
          list-type="text"
          drag
          style="width: 100%"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持图片、PDF、Word、Excel 等，最多 9 个文件</div>
          </template>
        </el-upload>
      </div>

      <!-- ④ 事件预告：通知谁 / 派什么任务 -->
      <div v-if="previewEvents.length" class="op-section">
        <div class="op-section-title">本次操作将触发</div>
        <div class="op-events">
          <div v-for="evt in previewEvents" :key="evt.eventCode" class="op-event-item">
            <div class="op-event-head">
              <el-icon class="op-event-icon"><Bell /></el-icon>
              <span class="op-event-name">{{ evt.eventName }}</span>
              <el-tag size="small" :type="evt.kanbanModule === 'dev' ? 'warning' : 'info'" effect="plain">
                {{ kanbanLabel(evt.kanbanModule) }}
              </el-tag>
            </div>
            <div class="op-event-body">
              <div class="op-event-row">
                <span class="op-event-label">通知</span>
                <template v-if="evt.roleNames.length">
                  <el-tag v-for="rn in evt.roleNames" :key="rn" size="small" type="success" effect="light">{{ rn }}</el-tag>
                </template>
                <span v-else class="op-event-empty">无角色通知</span>
              </div>
              <div class="op-event-row">
                <span class="op-event-label">任务</span>
                <span class="op-event-task">{{ evt.title || evt.content || '-' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="confirm">
        {{ operation?.confirmText || `确认${operation?.name || ''}` }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Right, Bell, UploadFilled } from '@element-plus/icons-vue'
import { eventConfigApi } from '@/api/system/event-config'
import { roleApi } from '@/api/system/role'
import type { SysEventConfig } from '@/types/system'
import type { OperationDef } from './registry'

const props = defineProps<{
  modelValue: boolean
  operation?: OperationDef | null
  bizId?: number | null
  /** 单据号（展示用） */
  bizNo?: string
  /** 状态文本映射 { 状态码: '状态名' } */
  statusTextMap?: Record<number, string>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: [payload?: { values: Record<string, any>; attachmentIds: number[] }]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val),
})

const submitting = ref(false)
const formValues = ref<Record<string, any>>({})
const evidenceFileList = ref<any[]>([])
const uploadedIds: number[] = []

// ===== 状态文本 =====
function statusText(status: number): string {
  return props.statusTextMap?.[status] || String(status)
}
function statusTagType(status: number): any {
  // 粗略映射：0 草稿 info / 流转中 warning / 成功类 success / 拒绝失败 danger
  if ([2, 9].includes(status)) return 'success'
  if ([3].includes(status)) return 'danger'
  if ([0].includes(status)) return 'info'
  return 'warning'
}

// ===== 事件预告数据（缓存） =====
let eventCache: SysEventConfig[] | null = null
let roleMapCache: Record<number, string> | null = null

async function loadEventsAndRoles() {
  if (!eventCache) {
    try {
      const res: any = await eventConfigApi.list()
      eventCache = res?.data || []
    } catch {
      eventCache = []
    }
  }
  if (!roleMapCache) {
    try {
      const res: any = await roleApi.list({})
      const roles: any[] = res?.data || []
      roleMapCache = Object.fromEntries(roles.map((r) => [Number(r.roleId), r.roleName]))
    } catch {
      roleMapCache = {}
    }
  }
}

const previewEvents = computed(() => {
  const op = props.operation
  if (!op?.events?.length || !eventCache) return []
  return op.events
    .map((code) => eventCache?.find((e) => e.eventCode === code))
    .filter(Boolean)
    .map((evt: any) => {
      let roleIds: number[] = []
      try {
        roleIds = JSON.parse(evt.targetRole || '[]')
      } catch {
        roleIds = []
      }
      const roleNames = roleIds.map((id: number) => roleMapCache?.[id] || `角色${id}`)
      // 标题模板 {bizId} → 单据号
      const title = (evt.title || '').replace(/\{bizId\}/g, props.bizNo || '')
      return { ...evt, roleNames, title }
    })
})

function kanbanLabel(module?: string): string {
  const map: Record<string, string> = { office: '办公看板', dev: '开发看板', emergency: '紧急看板', production: '生产看板' }
  return module ? map[module] || module : '看板'
}

// ===== 证据上传 =====
const uploadUrl = computed(() => {
  const base = (import.meta.env.VITE_BASE_API || '/api') as string
  return `${base}/system/attachment/upload`
})
const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { token } : {}
})
const uploadData = computed(() => ({
  bizType: props.operation?.bizType || '',
  bizId: props.bizId ?? 0,
}))

function onUploadSuccess(response: any) {
  if (response?.code === 200 && response?.data) {
    uploadedIds.push(Number(response.data))
  } else {
    ElMessage.warning(response?.msg || '上传响应异常')
  }
}
function onUploadRemove(file: any) {
  const idx = uploadedIds.indexOf(Number(file.response?.data))
  if (idx > -1) uploadedIds.splice(idx, 1)
}
function onUploadError() {
  ElMessage.error('证据上传失败')
}

// ===== 确认执行 =====
async function confirm() {
  const op = props.operation
  if (!op || !props.bizId) return

  // 必填校验
  for (const field of op.fields || []) {
    const v = formValues.value[field.key]
    if (field.required && (v === undefined || v === null || v === '')) {
      ElMessage.warning(`请填写${field.label}`)
      return
    }
  }

  submitting.value = true
  try {
    await op.api({
      bizId: props.bizId,
      values: { ...formValues.value },
      attachmentIds: [...uploadedIds],
    })
    ElMessage.success(`${op.name}成功`)
    emit('success', { values: { ...formValues.value }, attachmentIds: [...uploadedIds] })
    visible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || `${op.name}失败`)
  } finally {
    submitting.value = false
  }
}

// 打开时初始化
watch(() => props.modelValue, async (val) => {
  if (val) {
    formValues.value = {}
    for (const field of props.operation?.fields || []) {
      formValues.value[field.key] = field.defaultValue ?? (field.type === 'number' ? undefined : '')
    }
    evidenceFileList.value = []
    uploadedIds.length = 0
    await loadEventsAndRoles()
  }
})
</script>

<style scoped>
.op-preview {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.op-header {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 14px;
}

.op-biz {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: #303133;
  font-weight: 600;
}

.op-status-flow {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.status-or {
  font-size: 12px;
}

.status-arrow {
  color: #909399;
}

.op-section {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
}

.op-section-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.op-section-tip {
  font-weight: 400;
  font-size: 12px;
  color: #909399;
}

.op-events {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.op-event-item {
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 10px 12px;
}

.op-event-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.op-event-icon {
  color: #e6a23c;
}

.op-event-name {
  font-weight: 600;
  font-size: 13px;
  flex: 1;
}

.op-event-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.op-event-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.op-event-label {
  color: #909399;
  width: 40px;
  flex-shrink: 0;
}

.op-event-task {
  color: #303133;
}

.op-event-empty {
  color: #c0c4cc;
  font-size: 12px;
}
</style>

<template>
  <div class="attachment-upload">
    <AttachmentTempUploader
      v-bind="uploaderProps"
      @files="onFiles"
      @error="onError"
    >
      <template v-if="$slots.trigger" #trigger="scope">
        <slot name="trigger" v-bind="scope" />
      </template>
    </AttachmentTempUploader>
    <el-progress
      v-if="progress !== null"
      class="au-progress"
      :percentage="progress"
      :status="progress >= 100 ? 'success' : undefined"
    />
    <span v-if="uploadingCount" class="au-sub">{{ uploadingCount }} 个文件上传中…</span>
  </div>
</template>

<script setup lang="ts">
/**
 * 独立附件上传入口（dev-20261016-048 第一阶段 A）。
 *
 * 定位：只做「选文件 + 前端校验（复用 AttachmentTempUploader） + 实际上传」。
 * 实际上传**写共享 Session**（`session.upload`），归属在发起时固定；
 * **不挂 Manager、不独立建队列、不自行绑定正式引用**（绑定/落定由单据保存负责）。
 * 业务锁定/权限/禁用原因由调用方传 `blockedReason`；组件不判断报价/订单业务。
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { AttachmentSession, AttachmentOwnership } from '@/composables/useAttachmentSession'
import { orderOwnership } from '@/composables/useAttachmentSession'
import AttachmentTempUploader from './AttachmentTempUploader.vue'

const props = withDefaults(
  defineProps<{
    session: AttachmentSession
    /** 上传归属：订单级（不传=orderOwnership()）或产品级（产品级须用稳定键，编码仅展示） */
    ownership?: AttachmentOwnership | null
    multiple?: boolean
    /** 调用方业务禁用（如单据锁定）；非空即禁用并显示 blockedReason */
    disabled?: boolean
    maxSizeMB?: number
    accept?: string[]
    buttonText?: string
    tip?: string
    /** 渲染拖拽落区替代按钮 */
    dropZone?: boolean
    /** 上传禁用原因（调用方按业务决定）；组件本身不做业务判断 */
    blockedReason?: string
  }>(),
  {
    ownership: null,
    multiple: true,
    disabled: false,
    maxSizeMB: 20,
    accept: () => [
      '.pdf',
      '.doc',
      '.docx',
      '.xls',
      '.xlsx',
      '.jpg',
      '.jpeg',
      '.png',
      '.gif',
      '.webp',
      '.zip',
    ],
    buttonText: '上传附件',
    tip: '',
    dropZone: false,
    blockedReason: '',
  },
)

const emit = defineEmits<{
  uploaded: [payload: { attachmentId: number; temp: boolean; name?: string; ownership: AttachmentOwnership; pending: import('@/composables/useAttachmentRefs').PendingEntry }]
  changed: [payload: { count: number }]
  error: [payload: { action: string; message: string }]
}>()

const session = props.session
const progress = ref<number | null>(null)
const ownershipValue = computed<AttachmentOwnership>(() => props.ownership ?? orderOwnership())
const uploadingCount = computed(() => session.uploadingCount.value)
const canUpload = computed(() => !props.disabled && session.canEdit.value)
/** 禁用说明：优先调用方给的业务原因，其次权限/锁定 */
const disabledTip = computed(() => {
  if (props.blockedReason) return props.blockedReason
  if (!canUpload.value) return session.canEdit.value ? '当前不可上传' : '无上传权限或已被锁定'
  return ''
})
const uploaderProps = computed(() => ({
  multiple: props.multiple,
  disabled: !canUpload.value,
  maxSizeMB: props.maxSizeMB,
  accept: props.accept,
  buttonText: props.buttonText,
  tip: disabledTip.value || props.tip,
  dropZone: props.dropZone,
}))

function onError(message: string) {
  emit('error', { action: 'validate', message })
}

/** 选中文件 → 通过共享会话上传（归属固定，写同一份 Session 状态） */
async function onFiles(files: File[]) {
  if (!canUpload.value) return
  // 一次选择的所有文件属于同一对象，异步上传期间切换产品不改变后续文件归属。
  const ownership = { ...ownershipValue.value }
  for (const file of files) {
    try {
      const uploaded = await session.uploadTemporary(file, ownership, (percent) => {
        progress.value = percent
      })
      emit('uploaded', { attachmentId: uploaded.attachmentId, temp: true, name: file.name, ownership, pending: uploaded.entry })
      emit('changed', { count: session.pendingCount.value + 1 })
    } catch (e: any) {
      const message = e?.message || '附件上传失败'
      ElMessage.error(message)
      emit('error', { action: 'upload', message })
    }
  }
  progress.value = null
}

defineExpose({ canUpload, disabledTip })
</script>

<style scoped>
.au-progress {
  margin-top: 6px;
  width: 100%;
}
.au-sub {
  font-size: 12px;
  color: #909399;
}
</style>

<template>
  <el-upload
    ref="uploadRef"
    :auto-upload="false"
    :multiple="multiple"
    :show-file-list="false"
    :disabled="disabled"
    :on-change="onChange"
  >
    <el-button type="primary" size="small" :disabled="disabled">
      <el-icon><Upload /></el-icon> {{ buttonText }}
    </el-button>
    <template #tip>
      <div class="el-upload__tip">{{ tip }}</div>
    </template>
  </el-upload>
</template>

<script setup lang="ts">
/**
 * 公共附件 TEMP 上传入口（V1-B WP2）
 * 职责边界：只做「选文件 + 前端校验 + 向上抛出 File」，**不直接上传**。
 * 上传与绑定统一由 AttachmentCenter 通过 useAttachmentRefs 执行（保证 requestKey、待绑定队列、
 * 重复检测都只有一条实现；旧 AttachmentUploader 的"浏览器暂存 File"做法不再沿用）。
 */
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import type { UploadFile, UploadInstance } from 'element-plus'

const props = withDefaults(
  defineProps<{
    multiple?: boolean
    disabled?: boolean
    /** 大小上限（MB） */
    maxSizeMB?: number
    /** 允许的扩展名（如 ['.pdf','.jpg']）；不传不校验 */
    accept?: string[]
    buttonText?: string
    tip?: string
  }>(),
  {
    multiple: true,
    disabled: false,
    maxSizeMB: 20,
    accept: () => [],
    buttonText: '上传附件',
    tip: '',
  },
)

const emit = defineEmits<{
  files: [files: File[]]
  error: [message: string]
}>()

const uploadRef = ref<UploadInstance>()

function validate(file: File): string {
  if (file.size > props.maxSizeMB * 1024 * 1024) {
    return `文件大小不能超过${props.maxSizeMB}MB：${file.name}`
  }
  if (props.accept.length) {
    const ext = file.name.includes('.') ? `.${file.name.split('.').pop()?.toLowerCase()}` : ''
    if (!props.accept.includes(ext)) {
      return `不支持的文件格式（${props.accept.join(' / ')}）：${file.name}`
    }
  }
  return ''
}

function onChange(uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  const message = validate(raw)
  if (message) {
    ElMessage.warning(message)
    emit('error', message)
  } else {
    emit('files', [raw])
  }
  // 组件不持有文件列表：交回父组件后立即清空，避免重复上交
  nextTick(() => uploadRef.value?.clearFiles())
}

defineExpose({ clearFiles: () => uploadRef.value?.clearFiles() })
</script>

<style scoped>
.el-upload__tip {
  font-size: 12px;
  color: #999;
  margin-top: 6px;
}
</style>

<template>
  <el-upload
    :http-request="doUpload"
    :on-remove="onRemove"
    :file-list="fileList"
    :before-upload="beforeUpload"
    :multiple="multiple"
    list-type="text"
  >
    <el-button type="primary" size="small">
      <el-icon><Upload /></el-icon> {{ buttonText }}
    </el-button>
    <template #tip>
      <div class="el-upload__tip">{{ tip }}</div>
    </template>
  </el-upload>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import type { UploadProps, UploadRequestOptions } from 'element-plus'
import { attachmentApi } from '@/api/system/attachment'
import request from '@/utils/request'

/**
 * 统一内嵌附件上传组件（DEV-733）
 * 模式：有 bizId → 立即上传；无 bizId（新建单据）→ 暂存，保存后调 flushPending() 批量上传
 * 职责只做「上传」，列表展示请配合 AttachmentPanel / AttachmentUploadDialog
 */
const props = withDefaults(defineProps<{
  bizType: string
  /** 有值时立即上传；新建单据传 null，保存后调 flushPending */
  bizId?: number | null
  traceId?: string
  category?: string
  version?: string
  maxSizeMB?: number
  multiple?: boolean
  buttonText?: string
  tip?: string
  /** 允许的扩展名列表（如 ['.pdf','.jpg']），不传不校验 */
  accept?: string[]
}>(), {
  bizId: null,
  traceId: '',
  category: '',
  version: '',
  maxSizeMB: 10,
  multiple: true,
  buttonText: '上传附件',
  tip: '',
  accept: () => [],
})

const emit = defineEmits<{
  /** 单个文件上传成功（立即上传模式），携带附件ID */
  success: [id: number]
}>()

const fileList = ref<any[]>([])
/** 新建单据暂存的文件（保存后 flushPending 上传） */
const pendingUploads = ref<File[]>([])

function beforeUpload(file: File) {
  const maxSize = props.maxSizeMB * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error(`文件大小不能超过${props.maxSizeMB}MB`)
    return false
  }
  if (props.accept && props.accept.length) {
    const ext = '.' + (file.name.split('.').pop()?.toLowerCase() || '')
    if (!props.accept.includes(ext)) {
      ElMessage.error(`不支持的文件格式，支持：${props.accept.join(' / ')}`)
      return false
    }
  }
  return true
}

async function doUpload(options: UploadRequestOptions) {
  if (props.bizId) {
    // 已有单据：立即上传
    const formData = new FormData()
    formData.append('file', options.file)
    formData.append('bizType', props.bizType)
    formData.append('bizId', String(props.bizId))
    if (props.traceId) formData.append('traceId', props.traceId)
    if (props.category) formData.append('category', props.category)
    if (props.version) formData.append('version', props.version)
    try {
      const res: any = await request({
        url: '/system/attachment/upload',
        method: 'post',
        data: formData,
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      if (res?.code === 200) {
        options.onSuccess(res.data)
        emit('success', Number(res.data))
        ElMessage.success('附件上传成功')
      } else {
        options.onError(new Error(res?.msg || '上传失败') as any)
      }
    } catch (e: any) {
      options.onError(e)
    }
  } else {
    // 新建单据：暂存，保存后由 flushPending 上传
    pendingUploads.value.push(options.file)
    options.onSuccess('pending')
  }
}

function onRemove(file: any) {
  const idx = pendingUploads.value.findIndex((f) => f === file.raw || f.name === file.name)
  if (idx >= 0) pendingUploads.value.splice(idx, 1)
}

/**
 * 保存成功后调用：把暂存文件批量上传并绑定 bizId
 */
async function flushPending(bizId: number, traceId?: string) {
  if (pendingUploads.value.length === 0) return
  const files = [...pendingUploads.value]
  pendingUploads.value = []
  for (const file of files) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('bizType', props.bizType)
    formData.append('bizId', String(bizId))
    const tid = traceId || props.traceId
    if (tid) formData.append('traceId', tid)
    if (props.category) formData.append('category', props.category)
    if (props.version) formData.append('version', props.version)
    try {
      const res: any = await request({
        url: '/system/attachment/upload',
        method: 'post',
        data: formData,
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      if (res?.code === 200) emit('success', Number(res.data))
    } catch (e) {
      console.error('上传附件失败:', file.name, e)
    }
  }
  fileList.value = []
}

/** 暂存数量（供父组件提示/校验） */
function pendingCount() {
  return pendingUploads.value.length
}

/** 清空暂存（取消/关闭弹窗时调用） */
function clearPending() {
  pendingUploads.value = []
  fileList.value = []
}

defineExpose({ flushPending, pendingCount, clearPending })
</script>

<style scoped>
.el-upload__tip {
  font-size: 12px;
  color: #999;
  margin-top: 6px;
}
</style>

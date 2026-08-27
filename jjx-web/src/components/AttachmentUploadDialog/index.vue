<template>
  <el-dialog
    :title="`附件管理 - ${dialogTitle || ''}`"
    v-model="visible"
    width="560px"
    append-to-body
    destroy-on-close
  >
    <!-- 上传区域 -->
    <el-upload
      :action="uploadUrl"
      :headers="uploadHeaders"
      :data="uploadData"
      multiple
      :limit="9"
      :on-success="onUploadSuccess"
      :on-error="onUploadError"
      :file-list="fileList"
      list-type="text"
      drag
      style="margin-bottom: 12px"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">支持图片、PDF、Word、Excel 等，最多 9 个文件</div>
      </template>
    </el-upload>

    <!-- 已上传列表 -->
    <div class="att-list" v-if="attachments.length">
      <div v-for="att in attachments" :key="att.id" class="att-item">
        <div class="att-info">
          <el-icon><Document /></el-icon>
          <el-link type="primary" :href="downloadUrl(att.id)" :underline="false" target="_blank">
            {{ att.fileName }}
          </el-link>
          <span class="att-size">{{ formatSize(att.fileSize) }}</span>
        </div>
        <el-button link type="danger" :icon="Delete" @click="onDelete(att)"></el-button>
      </div>
    </div>
    <el-empty v-else-if="!fileList.length" description="暂无附件" :image-size="60" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Document, Delete } from '@element-plus/icons-vue'
import { attachmentApi } from '@/api/system/attachment'

const props = defineProps<{
  modelValue: boolean
  bizType: string
  bizId: number | null | undefined
  /** 链路追踪ID：上传时写入，方便来源单据追溯 */
  traceId?: string
  /** 文件类别（产品文件库用，业务附件可空） */
  category?: string
  /** 版本号（产品文件库用） */
  version?: string
  dialogTitle?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit('update:modelValue', val),
})

const attachments = ref<any[]>([])
const fileList = ref<any[]>([])
const uploadedIds: number[] = []

const uploadUrl = computed(() => {
  const base = (import.meta.env.VITE_BASE_API || '/api') as string
  return `${base}/system/attachment/upload`
})

const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { token } : {}
})

const uploadData = computed(() => ({
  bizType: props.bizType,
  bizId: props.bizId ?? 0,
  ...(props.traceId ? { traceId: props.traceId } : {}),
  ...(props.category ? { category: props.category } : {}),
  ...(props.version ? { version: props.version } : {}),
}))

function downloadUrl(id: number): string {
  return attachmentApi.downloadUrl(id)
}

async function loadAttachments() {
  if (!props.bizType || !props.bizId) return
  const res = await attachmentApi.list(props.bizType, props.bizId)
  attachments.value = (res as any)?.data || []
}

function onUploadSuccess(response: any) {
  if (response?.code === 200 && response?.data) {
    ElMessage.success('上传成功')
    uploadedIds.push(Number(response.data))
    loadAttachments()
  } else {
    ElMessage.warning(response?.msg || '上传响应异常')
  }
}

function onUploadError() {
  ElMessage.error('上传失败')
}

async function onDelete(att: any) {
  try {
    await ElMessageBox.confirm(`确认删除附件「${att.fileName}」？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await attachmentApi.remove(att.id)
    ElMessage.success('删除成功')
    loadAttachments()
    emit('success')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}

function formatSize(size: number | null | undefined): string {
  if (!size) return ''
  if (size < 1024) return `${size}B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`
  return `${(size / 1024 / 1024).toFixed(1)}MB`
}

// 打开时加载
watch(() => props.modelValue, (val) => {
  if (val) {
    fileList.value = []
    uploadedIds.length = 0
    loadAttachments()
  }
})
</script>

<style scoped>
.att-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
}

.att-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 13px;
}

.att-info {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.att-size {
  color: #c0c4cc;
  font-size: 11px;
}
</style>

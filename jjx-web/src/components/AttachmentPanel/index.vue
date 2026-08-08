<template>
  <div class="attachment-panel">
    <div class="panel-header">
      <span class="panel-title">
        <el-icon><FolderOpened /></el-icon>
        相关文档
      </span>
      <el-tag v-if="attachments.length" size="small" type="info">{{ attachments.length }} 个</el-tag>
    </div>

    <!-- 空态 -->
    <el-empty v-if="!loading && !attachments.length" description="暂无相关文档" :image-size="60" />

    <!-- 附件列表 -->
    <div v-else-if="!loading" class="att-list">
      <div
        v-for="att in attachments"
        :key="att.id"
        class="att-item"
      >
        <div class="att-info">
          <el-icon class="att-icon"><Document /></el-icon>
          <div class="att-meta">
            <el-link
              type="primary"
              :href="downloadUrl(att.id)"
              :underline="false"
              target="_blank"
              class="att-name"
            >
              {{ att.fileName || '-' }}
            </el-link>
            <div class="att-sub">
              <span>{{ formatSize(att.fileSize) }}</span>
              <span v-if="att.category" class="att-cat">· {{ att.category }}</span>
              <span v-if="att.version" class="att-cat">· v{{ att.version }}</span>
              <span v-if="att.createBy" class="att-uploader">· {{ att.createBy }}</span>
              <span v-if="att.createTime" class="att-time">· {{ formatTime(att.createTime) }}</span>
            </div>
          </div>
        </div>
        <div class="att-actions">
          <el-tooltip content="下载" placement="top">
            <el-button
              link
              type="primary"
              :icon="Download"
              @click="onDownload(att)"
            ></el-button>
          </el-tooltip>
          <el-tooltip content="预览" placement="top">
            <el-button
              link
              type="primary"
              :icon="View"
              @click="onPreview(att)"
            ></el-button>
          </el-tooltip>
        </div>
      </div>
    </div>

    <!-- 图片预览（支持左右切换上一张/下一张） -->
    <el-image-viewer
      v-if="previewVisible"
      :url-list="previewImageList"
      :initial-index="previewIndex"
      @close="previewVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { FolderOpened, Document, Download, View } from '@element-plus/icons-vue'
import { attachmentApi } from '@/api/system/attachment'

const props = defineProps<{
  bizType: string
  bizId: number | null | undefined
  /** 链路追踪ID：同时展示同链路来源单据的文档 */
  traceId?: string
}>()

const attachments = ref<any[]>([])
const loading = ref(false)

// 加载附件列表（本类型 + 同traceId的来源文档）
async function loadAttachments() {
  attachments.value = []
  if (!props.bizType || !props.bizId) {
    if (props.traceId) {
      await loadByTrace()
    }
    return
  }
  loading.value = true
  try {
    const res = await attachmentApi.list(props.bizType, props.bizId)
    attachments.value = (res as any)?.data || []
    // 追加同链路来源单据的文档（去重）
    if (props.traceId) {
      await loadByTrace()
    }
  } catch {
    attachments.value = []
  } finally {
    loading.value = false
  }
}

// 按traceId加载关联附件（去重合并）
async function loadByTrace() {
  try {
    const res = await attachmentApi.listByTrace(props.traceId as string)
    const traceAtts: any[] = (res as any)?.data || []
    const existing = new Set(attachments.value.map((a) => a.id))
    for (const att of traceAtts) {
      if (!existing.has(att.id)) {
        attachments.value.push(att)
        existing.add(att.id)
      }
    }
  } catch {
    // 忽略：trace查询失败不影响本类型附件
  }
}

watch(() => [props.bizType, props.bizId, props.traceId], loadAttachments, { immediate: true })

function downloadUrl(id: number): string {
  return attachmentApi.downloadUrl(id)
}

function onDownload(att: any) {
  window.open(downloadUrl(att.id), '_blank')
}

// ==================== 图片预览（左右切换） ====================
const previewVisible = ref(false)
const previewImageList = ref<string[]>([])
const previewIndex = ref(0)

const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp', 'image/bmp']

function isImage(att: any): boolean {
  return IMAGE_TYPES.includes(att.fileType)
}

function onPreview(att: any) {
  // 图片：打开全屏预览，支持左右切换本组所有图片
  if (isImage(att)) {
    const images = attachments.value.filter((a) => isImage(a))
    if (images.length === 0) return
    previewImageList.value = images.map((a) => downloadUrl(a.id))
    const idx = images.findIndex((a) => a.id === att.id)
    previewIndex.value = idx >= 0 ? idx : 0
    previewVisible.value = true
    return
  }
  // PDF/其他：新窗口预览/下载
  window.open(downloadUrl(att.id), '_blank')
}

function formatSize(size: number | null | undefined): string {
  if (!size) return ''
  if (size < 1024) return `${size}B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`
  return `${(size / 1024 / 1024).toFixed(1)}MB`
}

function formatTime(t: string | null | undefined): string {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

defineExpose({ loadAttachments })
</script>

<style scoped>
.attachment-panel {
  padding: 4px 0;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}

.att-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.att-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
  transition: background 0.2s;
}

.att-item:hover {
  background: #f0f7ff;
  border-color: #b3d8ff;
}

.att-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.att-icon {
  font-size: 20px;
  color: #409eff;
  flex-shrink: 0;
}

.att-meta {
  min-width: 0;
}

.att-name {
  font-size: 13px;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}

.att-sub {
  display: flex;
  gap: 6px;
  color: #909399;
  font-size: 11px;
  margin-top: 2px;
}

.att-cat {
  color: #409eff;
}

.att-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
</style>

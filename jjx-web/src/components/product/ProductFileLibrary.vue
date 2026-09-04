<template>
  <div class="product-file-library" v-loading="loading">
    <!-- 上传区 -->
    <div class="upload-area">
      <el-form inline>
        <el-form-item label="类别" style="margin-bottom: 0">
          <el-select v-model="category" placeholder="选择类别" style="width: 130px" size="small">
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本" style="margin-bottom: 0">
          <el-input
            v-model="version"
            placeholder="如 Rev003"
            style="width: 120px"
            size="small"
            clearable
          />
        </el-form-item>
        <el-form-item style="margin-bottom: 0">
          <el-upload
            :show-file-list="false"
            :before-upload="beforeUpload"
            :http-request="doUpload"
            :disabled="!category || uploading"
          >
            <el-button type="primary" size="small" :loading="uploading" :disabled="!category">
              <el-icon><Upload /></el-icon> 上传文件
            </el-button>
          </el-upload>
        </el-form-item>
        <el-form-item style="margin-bottom: 0">
          <span class="upload-tip">先选类别再上传；支持 PDF/DWG/DXF/图片/CDR 等工程文件</span>
        </el-form-item>
      </el-form>
    </div>

    <!-- 文件库列表（按类别分组） -->
    <div v-if="!loading && groups.length" class="groups">
      <div v-for="g in groups" :key="g.category" class="group">
        <div class="group-title">
          <el-icon><FolderOpened /></el-icon>
          <span>{{ g.category }}</span>
          <el-tag size="small" type="info" style="margin-left: 6px">{{ g.files.length }}</el-tag>
        </div>
        <div class="group-files">
          <div v-for="att in g.files" :key="att.id" class="file-item">
            <div class="file-info">
              <el-icon class="file-icon"><Document /></el-icon>
              <div class="file-meta">
                <el-link
                  v-if="isImage(att)"
                  type="primary"
                  underline="never"
                  class="file-name"
                  @click="previewImage(att)"
                >
                  {{ att.fileName || '-' }}
                </el-link>
                <el-link
                  v-else
                  type="primary"
                  :href="downloadUrl(att.id)"
                  underline="never"
                  target="_blank"
                  class="file-name"
                >
                  {{ att.fileName || '-' }}
                </el-link>
                <div class="file-sub">
                  <span v-if="att.version" class="ver-tag">v{{ att.version }}</span>
                  <span class="type-tag">{{ fileTypeLabel(att.fileName) }}</span>
                  <span>{{ formatSize(att.fileSize) }}</span>
                  <span v-if="att.createBy">· {{ att.createBy }}</span>
                  <span v-if="att.createTime">· {{ formatTime(att.createTime) }}</span>
                </div>
              </div>
            </div>
            <div class="file-actions">
              <el-tooltip content="下载" placement="top">
                <el-button
                  link
                  type="primary"
                  :icon="Download"
                  @click="windowOpen(downloadUrl(att.id))"
                />
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button link type="danger" :icon="Delete" @click="onDelete(att)" />
              </el-tooltip>
            </div>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无产品文件，先选择类别上传" :image-size="60" />
    <!-- 图片预览 -->
    <el-image-viewer
      v-if="previewVisible"
      :url-list="previewImageList"
      :initial-index="previewIndex"
      @close="previewVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, FolderOpened, Document, Download, Delete } from '@element-plus/icons-vue'
import { attachmentApi } from '@/api/system/attachment'

/** 产品文件类别（对应工程部目录习惯，方案文档） */
const CATEGORIES = ['客供稿', '承认书', '模具', '确认图', '菲林', '规范']

const props = defineProps<{
  productCode: string
}>()

const emit = defineEmits<{
  success: [id: number]
}>()

const category = ref('客供稿')
const version = ref('')
const files = ref<any[]>([])
const loading = ref(false)
const uploading = ref(false)

// 图片预览
const previewVisible = ref(false)
const previewImageList = ref<string[]>([])
const previewIndex = ref(0)

const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp']

function isImage(att: any): boolean {
  const ext = extOf(att.fileName)
  return IMAGE_EXT.includes(ext)
}

function extOf(name: string | null | undefined): string {
  if (!name || !name.includes('.')) return ''
  return name.split('.').pop()!.toLowerCase()
}

function fileTypeLabel(name: string | null | undefined): string {
  const ext = extOf(name)
  if (!ext) return ''
  return ext.toUpperCase()
}

function previewImage(att: any) {
  const images = files.value.filter((f) => isImage(f))
  if (!images.length) return
  previewImageList.value = images.map((f) => downloadUrl(f.id))
  const idx = images.findIndex((f) => f.id === att.id)
  previewIndex.value = idx >= 0 ? idx : 0
  previewVisible.value = true
}

const groups = computed(() => {
  const map = new Map<string, any[]>()
  for (const f of files.value) {
    const key = f.category || '未分类'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(f)
  }
  return Array.from(map.entries()).map(([c, list]) => ({ category: c, files: list }))
})

async function loadFiles() {
  if (!props.productCode) return
  loading.value = true
  try {
    const res: any = await attachmentApi.productFiles(props.productCode)
    files.value = (res as any)?.data || []
  } catch {
    files.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.productCode,
  () => {
    if (props.productCode) loadFiles()
  },
  { immediate: true }
)

function beforeUpload(file: File) {
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过50MB')
    return false
  }
  return true
}

async function doUpload(options: any) {
  if (!category.value) {
    ElMessage.warning('请先选择文件类别')
    options.onError(new Error('no category'))
    return
  }
  uploading.value = true
  try {
    const res: any = await attachmentApi.uploadProductFile(
      options.file,
      props.productCode,
      category.value,
      version.value || undefined
    )
    if (res?.code === 200) {
      ElMessage.success('上传成功')
      emit('success', Number(res.data))
      version.value = ''
      options.onSuccess(res.data)
      loadFiles()
    } else {
      ElMessage.error(res?.msg || '上传失败')
      options.onError(new Error(res?.msg || '上传失败'))
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
    options.onError(e)
  } finally {
    uploading.value = false
  }
}

async function onDelete(att: any) {
  try {
    await ElMessageBox.confirm(`确认删除文件「${att.fileName || '-'}」？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await attachmentApi.remove(att.id)
    ElMessage.success('删除成功')
    loadFiles()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}

function downloadUrl(id: number): string {
  return attachmentApi.downloadUrl(id)
}

function windowOpen(url: string) {
  window.open(url, '_blank')
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
</script>

<style scoped>
.product-file-library {
  padding: 4px 0;
}

.upload-area {
  background: #fafafa;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 14px;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
}

.group {
  margin-bottom: 14px;
}

.group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #303133;
  font-size: 13px;
  margin-bottom: 6px;
}

.group-files {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
}

.file-item:last-child {
  border-bottom: none;
}

.file-item:hover {
  background: #f5f9ff;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.file-icon {
  color: #409eff;
  flex-shrink: 0;
}

.file-meta {
  min-width: 0;
}

.file-name {
  display: block;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}

.ver-tag {
  color: #409eff;
}

.type-tag {
  background: #f0f2f5;
  color: #606266;
  border-radius: 2px;
  padding: 0 4px;
  font-size: 11px;
}
</style>

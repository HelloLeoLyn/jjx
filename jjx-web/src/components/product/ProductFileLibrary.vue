<template>
  <div class="product-file-library" v-loading="loading">
    <!-- 上传区 -->
    <div v-if="canUpload" class="upload-area">
      <el-form inline>
        <el-form-item label="类别" style="margin-bottom: 0">
          <el-select v-model="category" placeholder="选择类别" style="width: 150px" size="small">
            <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
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
          <el-checkbox
            v-if="selectable"
            class="group-check"
            :model-value="isGroupAllSelected(g)"
            :indeterminate="isGroupIndeterminate(g)"
            @change="(v: any) => toggleGroup(g, !!v)"
            >全选</el-checkbox
          >
        </div>
        <div class="group-files">
          <div v-for="att in g.files" :key="att.id" class="file-item">
            <div class="file-info">
              <el-checkbox
                v-if="selectable"
                class="file-check"
                :model-value="selectedIds.includes(att.id)"
                @change="(v: any) => toggleOne(att, !!v)"
              />
              <!-- 图片显示缩略图，点击预览 -->
              <img
                v-if="isImage(att)"
                class="thumb"
                :src="downloadUrl(att.id)"
                @click="previewImage(att)"
              />
              <el-icon v-else class="file-icon"><Document /></el-icon>
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
                  v-else-if="isPdf(att)"
                  type="primary"
                  underline="never"
                  class="file-name"
                  @click="windowOpen(downloadUrl(att.id))"
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
                  <el-tag size="small" :type="sourceTagType(att)" effect="plain" class="src-tag">{{
                    sourceLabel(att)
                  }}</el-tag>
                  <el-tag v-if="att.isCurrent === 1" size="small" type="success" effect="dark" class="cur-tag"
                    >现行</el-tag
                  >
                  <span v-if="att.version" class="ver-tag">v{{ att.version }}</span>
                  <span class="type-tag">{{ fileTypeLabel(att.fileName) }}</span>
                  <span>{{ formatSize(att.fileSize) }}</span>
                  <span v-if="att.createBy">· {{ att.createBy }}</span>
                  <span v-if="att.createTime">· {{ formatTime(att.createTime) }}</span>
                </div>
              </div>
            </div>
            <div class="file-actions">
              <el-tooltip v-if="canUpload && att.isCurrent !== 1" content="设为现行版" placement="top">
                <el-button link type="warning" @click="onSetCurrent(att)">设为现行</el-button>
              </el-tooltip>
              <el-tooltip content="下载" placement="top">
                <el-button
                  link
                  type="primary"
                  :icon="Download"
                  @click="windowOpen(downloadUrl(att.id))"
                />
              </el-tooltip>
              <el-tooltip v-if="canDelete" content="删除" placement="top">
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
import { useDict } from '@/composables/useDict'
import { useUserStore } from '@/store/modules/user'

/** 兜底类别（字典 product_file_category 未配置时使用） */
const FALLBACK_CATEGORIES = [
  '客供稿',
  '承认书',
  '模具',
  '确认图',
  '菲林',
  '规范',
  '结构图',
  '印刷指导图',
  '产品图集',
  '样品照片',
  '客户确认样品',
  '分色检查表',
]

/** 来源：客供类类别 */
const CUSTOMER_CATEGORIES = ['客供稿', '客户确认样品']

const props = defineProps<{
  productCode: string
  /** 只显示/只允许这些类别（不传=全部） */
  categories?: string[]
  /** 选择模式：显示勾选框，勾选通过 selection-change 抛出 */
  selectable?: boolean
  /** 上传权限点（可选，提供则按权限控制） */
  uploadPerm?: string
  /** 删除权限点（可选，提供则按权限控制） */
  deletePerm?: string
}>()

const emit = defineEmits<{
  success: [id: number]
  'selection-change': [ids: number[]]
}>()

const userStore = useUserStore()
const { options: dictCategories } = useDict('product_file_category')

/** 类别选项：优先字典，兜底内置列表；再用 categories 收窄 */
const categoryOptions = computed<{ label: string; value: string }[]>(() => {
  const list = (dictCategories.value || [])
    .map((d: any) => ({ label: d.label || d.itemValue || d.item_value, value: d.itemValue || d.item_value }))
    .filter((o: any) => o.value)
  const all = list.length ? list : FALLBACK_CATEGORIES.map((c) => ({ label: c, value: c }))
  if (props.categories && props.categories.length) {
    return all.filter((o) => props.categories!.includes(o.value))
  }
  return all
})

const canUpload = computed(() =>
  props.uploadPerm ? userStore.hasPermission(props.uploadPerm) : true
)
const canDelete = computed(() =>
  props.deletePerm ? userStore.hasPermission(props.deletePerm) : true
)

const category = ref('客供稿')
const version = ref('')
const files = ref<any[]>([])
const loading = ref(false)
const uploading = ref(false)

// 选择模式
const selectedIds = ref<number[]>([])

// 图片预览
const previewVisible = ref(false)
const previewImageList = ref<string[]>([])
const previewIndex = ref(0)

const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']

function isImage(att: any): boolean {
  const ext = extOf(att.fileName)
  if (IMAGE_EXT.includes(ext)) return true
  return typeof att.fileType === 'string' && att.fileType.startsWith('image/')
}

function isPdf(att: any): boolean {
  if (extOf(att.fileName) === 'pdf') return true
  return typeof att.fileType === 'string' && att.fileType.startsWith('application/pdf')
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

/** 来源：客供（客户给/客户确认）/ 工程 */
function sourceLabel(att: any): string {
  return CUSTOMER_CATEGORIES.includes(att.category) ? '客供' : '工程'
}
function sourceTagType(att: any): 'warning' | 'primary' {
  return CUSTOMER_CATEGORIES.includes(att.category) ? 'warning' : 'primary'
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
    if (props.categories && props.categories.length && !props.categories.includes(f.category)) {
      continue
    }
    const key = f.category || '未分类'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(f)
  }
  return Array.from(map.entries()).map(([c, list]) => ({ category: c, files: list }))
})

function isGroupAllSelected(g: { files: any[] }): boolean {
  return g.files.length > 0 && g.files.every((f) => selectedIds.value.includes(f.id))
}
function isGroupIndeterminate(g: { files: any[] }): boolean {
  const hit = g.files.filter((f) => selectedIds.value.includes(f.id)).length
  return hit > 0 && hit < g.files.length
}
function toggleGroup(g: { files: any[] }, checked: boolean) {
  const ids = g.files.map((f) => f.id)
  if (checked) {
    selectedIds.value = Array.from(new Set([...selectedIds.value, ...ids]))
  } else {
    selectedIds.value = selectedIds.value.filter((id) => !ids.includes(id))
  }
  emitSelection()
}
function toggleOne(att: any, checked: boolean) {
  if (checked) {
    if (!selectedIds.value.includes(att.id)) selectedIds.value.push(att.id)
  } else {
    selectedIds.value = selectedIds.value.filter((id) => id !== att.id)
  }
  emitSelection()
}
function emitSelection() {
  emit('selection-change', [...selectedIds.value])
}

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
    if (props.productCode) {
      selectedIds.value = []
      loadFiles()
    }
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
    selectedIds.value = selectedIds.value.filter((id) => id !== att.id)
    emitSelection()
    loadFiles()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}

function downloadUrl(id: number): string {
  return attachmentApi.downloadUrl(id)
}

async function onSetCurrent(att: any) {
  try {
    await attachmentApi.setCurrent(att.id)
    ElMessage.success('已设为现行版')
    loadFiles()
  } catch (e: any) {
    ElMessage.error(e?.message || '设置失败')
  }
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

.group-check {
  margin-left: auto;
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

.file-check {
  margin-right: 2px;
}

.thumb {
  width: 38px;
  height: 38px;
  object-fit: cover;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  cursor: pointer;
  flex-shrink: 0;
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

.src-tag {
  height: 18px;
  padding: 0 4px;
}

.cur-tag {
  height: 18px;
  padding: 0 4px;
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

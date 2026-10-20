<template>
  <div class="product-doc-panel" v-loading="loading">
    <!-- 上传：复用公共上传组件（只上传模式，走 refs） -->
    <div v-if="!readonly && canEdit" class="upload-area">
      <AttachmentCenter
        source-type="product"
        :source-id="productId"
        variant="upload-only"
        :edit-perm="editPerm"
        :view-perm="viewPerm"
        :title="title"
        @changed="onUploaded"
        @error="(p: any) => emit('error', p)"
      />
    </div>

    <!-- 列表：读 refs（产品维度），按一级类型分组 -->
    <div v-if="!loading && groups.length" class="groups">
      <div v-for="g in groups" :key="g.key" class="group">
        <div class="group-title">
          <el-icon><FolderOpened /></el-icon>
          <span>{{ g.label }}</span>
          <el-tag size="small" type="info" style="margin-left: 6px">{{ g.rows.length }}</el-tag>
        </div>
        <div class="group-files">
          <div v-for="row in g.rows" :key="row.id" class="file-item">
            <div class="file-info">
              <img v-if="isImage(row)" class="thumb" :src="downloadUrl(row.id)" @click="preview(row)" />
              <el-icon v-else class="file-icon"><Document /></el-icon>
              <div class="file-meta">
                <span class="file-name" :title="row.fileName || ''">{{ row.displayName || row.fileName || `附件 #${row.attachmentId}` }}</span>
                <div class="file-sub">
                  <el-tag size="small" type="primary" effect="plain">{{ secondLabelOf(row.documentType) }}</el-tag>
                  <span class="type-tag">{{ fileTypeLabel(row.fileName) }}</span>
                  <span>{{ formatSize(row.fileSize) }}</span>
                  <span v-if="row.createBy">· {{ row.createBy }}</span>
                  <span v-if="row.createTime">· {{ formatTime(row.createTime) }}</span>
                </div>
              </div>
            </div>
            <div class="file-actions">
              <el-button link type="primary" @click="preview(row)">预览</el-button>
              <el-button link type="primary" @click="windowOpen(downloadUrl(row.id))">下载</el-button>
              <el-button v-if="canEdit" link type="danger" @click="onUnbind(row)">移除</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="暂无产品资料" :image-size="60" />

    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerList"
      :initial-index="viewerIndex"
      @close="viewerVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 产品资料视图（dev-20261013-041 / M4）
 *
 * 与旧的「产品文件库」（老通道 biz_type=product + 19 类别，已退役）的差别：
 *   - 上传：复用公共上传组件（AttachmentCenter，只上传模式），落 sys_attachment_ref（source_type='product'）
 *   - 列表：读 refs 的产品维度（refsByProduct），按**一级资料类型**分组（不再用 19 类别）
 *   - 删除：改「解除引用」（不删物理文件）
 * 不含：受控图档（版本/设为现行/下发撤回）——那是工程图集页的事，不走本视图。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderOpened, Document } from '@element-plus/icons-vue'
import { attachmentRefApi, type AttachmentDocumentTypeItem, type AttachmentRefItem } from '@/api/system/attachmentRef'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'
import { useAttachmentDocTypes } from '@/components/attachment/useAttachmentDocTypes'

const props = withDefaults(
  defineProps<{
    productId: number
    title?: string
    /** 只读：不显示上传入口、不显示移除 */
    readonly?: boolean
    viewPerm?: string
    editPerm?: string
  }>(),
  { title: '产品资料', readonly: false, viewPerm: 'product:view', editPerm: 'product:edit' },
)

const emit = defineEmits<{
  changed: []
  error: [payload: { action: string; message: string }]
}>()

const documentTypes = ref<AttachmentDocumentTypeItem[]>([])
const { categoryLabelOf, categoryKeyOfType, secondLabelOf } = useAttachmentDocTypes(documentTypes, () => 'product')

const canEdit = computed(() => !props.readonly)
const loading = ref(false)
const rows = ref<AttachmentRefItem[]>([])

async function loadDict() {
  if (documentTypes.value.length) return
  try {
    const res: any = await attachmentRefApi.documentTypes()
    documentTypes.value = (res?.data || []) as AttachmentDocumentTypeItem[]
  } catch {
    documentTypes.value = []
  }
}

async function loadRows() {
  if (!props.productId) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const res: any = await attachmentRefApi.refsByProduct(props.productId)
    const list = ((res?.data || []) as AttachmentRefItem[]).filter((r) => ['product', 'work_spec'].includes(r.sourceType) && !r.deleted)
    rows.value = list
  } catch (e: any) {
    rows.value = []
    emit('error', { action: 'load', message: e?.message || '产品资料加载失败' })
  } finally {
    loading.value = false
  }
}

/** 按一级类型分组（无二级/未知归「未分类」） */
const groups = computed(() => {
  const map = new Map<string, { key: string; label: string; rows: AttachmentRefItem[] }>()
  for (const row of rows.value) {
    const key = categoryKeyOfType(row.documentType) || '__none__'
    const label = row.documentType ? categoryLabelOf(row.documentType) : '未分类'
    let g = map.get(key)
    if (!g) {
      g = { key, label, rows: [] }
      map.set(key, g)
    }
    g.rows.push(row)
  }
  return Array.from(map.values())
})

async function onUploaded() {
  await loadRows()
  emit('changed')
}

watch(
  () => props.productId,
  async (id) => {
    if (!id) {
      rows.value = []
      return
    }
    await loadDict()
    await loadRows()
  },
  { immediate: true },
)

const IMAGE_EXT = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
function extOf(name?: string | null): string {
  if (!name || !name.includes('.')) return ''
  return name.split('.').pop()!.toLowerCase()
}
function isImage(row: AttachmentRefItem): boolean {
  if (typeof row.fileType === 'string' && row.fileType.startsWith('image/')) return true
  return IMAGE_EXT.includes(extOf(row.fileName))
}
function fileTypeLabel(name?: string | null): string {
  const ext = extOf(name)
  return ext ? ext.toUpperCase() : ''
}

const viewerVisible = ref(false)
const viewerList = ref<string[]>([])
const viewerIndex = ref(0)
function downloadUrl(refId: number): string {
  return attachmentRefApi.downloadUrlByRef(refId)
}
function preview(row: AttachmentRefItem) {
  if (isImage(row)) {
    const images = rows.value.filter((r) => isImage(r))
    viewerList.value = images.map((r) => attachmentRefApi.previewUrlByRef(r.id))
    viewerIndex.value = Math.max(0, images.findIndex((r) => r.id === row.id))
    viewerVisible.value = true
    return
  }
  windowOpen(attachmentRefApi.previewUrlByRef(row.id))
}
function windowOpen(url: string) {
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function onUnbind(row: AttachmentRefItem) {
  try {
    await ElMessageBox.confirm(
      `从本产品移除「${row.displayName || row.fileName || '附件'}」的引用？仅解除引用，物理文件保留。`,
      '移除引用',
      { confirmButtonText: '确定移除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await attachmentRefApi.unbind(row.id)
    ElMessage.success('已移除引用')
    await loadRows()
    emit('changed')
  } catch (e: any) {
    ElMessage.error(e?.message || '移除失败')
  }
}

function formatSize(size?: number | null): string {
  if (!size) return ''
  if (size < 1024) return `${size}B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`
  return `${(size / 1024 / 1024).toFixed(1)}MB`
}
function formatTime(t?: string | null): string {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

defineExpose({ refresh: loadRows })
</script>

<style scoped>
.product-doc-panel {
  padding: 4px 0;
}
.upload-area {
  background: #fafafa;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 14px;
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
.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.thumb {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
}
.file-icon {
  font-size: 20px;
  color: #909399;
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
  color: #303133;
}
.file-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.file-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
</style>

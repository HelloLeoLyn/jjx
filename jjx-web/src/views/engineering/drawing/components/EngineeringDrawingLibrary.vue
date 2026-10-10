<template>
  <div class="drawing-library">
    <div class="library-toolbar">
      <div class="library-heading"><strong>图纸文件</strong><span>{{ files.length }} 个文件 · {{ rows.length }} 个版本</span></div>
      <div><el-button @click="loadFiles">刷新</el-button><el-button v-if="canUpload" type="primary" icon="Upload" @click="openUpload()">上传图纸</el-button></div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="library-alert" />
    <div class="filters">
      <el-input v-model="keyword" placeholder="图纸编号、名称或文件名" clearable style="width:240px" />
      <el-select v-model="view" style="width:160px"><el-option v-for="item in DrawingViewEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-select v-model="releaseFilter" style="width:145px"><el-option label="全部下发状态" value="" /><el-option v-for="item in DrawingReleaseFlagEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select>
    </div>
    <el-tabs v-model="categoryFilter" class="category-tabs">
      <el-tab-pane label="全部图种" name="" />
      <el-tab-pane v-for="item in visibleCategories" :key="item" :label="`${productFileCategoryLabel(item)} (${files.filter(f => f.category === item).length})`" :name="item" />
    </el-tabs>
    <el-table v-loading="loading" :data="filteredRows" row-key="key" class="drawing-table" empty-text="暂无符合条件的图纸，可切换筛选或上传文件">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="revision-files">
            <div v-for="file in row.files" :key="file.id" class="revision-file">
              <el-tag size="small" effect="plain">{{ file.fileRole ? DrawingFileRoleEnum.getLabel(file.fileRole) : '未归集' }}</el-tag>
              <el-link type="primary" @click="preview(file)">{{ file.fileName }}</el-link>
              <span class="file-info">{{ sizeLabel(file.fileSize) }} · {{ file.createBy || '—' }} · {{ timeLabel(file.createTime) }}</span>
              <el-button link type="primary" @click="download(file)">下载</el-button>
              <el-button v-if="canUpload && file.released !== DrawingReleaseFlagEnum.RELEASED.value" link type="primary" @click="openMetadata(file)">{{ file.drawingNo ? '编辑关联' : '补录图纸关联' }}</el-button>
              <el-button v-if="canDelete && file.isCurrent !== DrawingCurrentFlagEnum.CURRENT.value && file.released !== DrawingReleaseFlagEnum.RELEASED.value" link type="danger" @click="remove(file)">删除</el-button>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="图纸" min-width="210">
        <template #default="{ row }"><div class="drawing-title">{{ row.name }}</div><div class="drawing-sub">{{ row.drawingNo || '待归集：请补录图纸编号' }}</div></template>
      </el-table-column>
      <el-table-column label="图种" width="125"><template #default="{ row }">{{ productFileCategoryLabel(row.category) }}</template></el-table-column>
      <el-table-column label="版本" width="80"><template #default="{ row }">{{ row.version || '未标注' }}</template></el-table-column>
      <el-table-column label="文件 / 预览" min-width="170">
        <template #default="{ row }"><div v-for="file in row.files" :key="file.id" class="file-link"><el-link type="primary" @click="preview(file)">{{ file.fileRole ? DrawingFileRoleEnum.getLabel(file.fileRole) : file.fileName }}</el-link><span>{{ extension(file.fileName) }}</span></div></template>
      </el-table-column>
      <el-table-column label="状态" width="135">
        <template #default="{ row }"><div class="status-tags"><el-tag size="small" :type="row.current ? 'success' : 'info'">{{ DrawingCurrentFlagEnum.getLabel(row.current ? DrawingCurrentFlagEnum.CURRENT.value : DrawingCurrentFlagEnum.NOT_CURRENT.value) }}</el-tag><el-tag size="small" :type="row.released ? 'success' : 'info'">{{ DrawingReleaseFlagEnum.getLabel(row.released ? DrawingReleaseFlagEnum.RELEASED.value : DrawingReleaseFlagEnum.NOT_RELEASED.value) }}</el-tag></div></template>
      </el-table-column>
      <el-table-column label="最新上传" width="150"><template #default="{ row }"><div>{{ row.latest.createBy || '—' }}</div><div class="drawing-sub">{{ timeLabel(row.latest.createTime) }}</div></template></el-table-column>
      <el-table-column label="操作" min-width="200" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-if="canUpload && row.drawingNo" link type="primary" @click="openUpload(row, true)">新版本</el-button>
            <el-button v-if="canUpload && row.drawingNo && !row.hasReleased && !row.files.some((f: ProductDrawingFile) => f.fileRole === DrawingFileRoleEnum.PRINT.value)" link type="primary" @click="openUpload(row, false)">补打印件</el-button>
            <el-button v-if="canUpload && !row.drawingNo" link type="primary" @click="openMetadata(row.files[0])">补录关联</el-button>
            <el-button v-if="canUpload && row.drawingNo && !row.current" link type="warning" :disabled="!!mutationId" @click="setCurrent(row)">设为现行</el-button>
            <el-button v-if="canRelease && ((row.drawingNo && row.current) || row.hasReleased)" link :type="row.hasReleased ? 'info' : 'success'" :disabled="!!mutationId" @click="release(row)">{{ row.hasReleased ? '撤回' : '下发' }}</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <p class="library-note">同一图纸编号、同一版本的原稿与打印件一起管理；历史文件补录关联后，再设现行、下发。</p>

    <el-dialog v-model="uploadVisible" :title="uploadNewRevision ? '上传图纸新版本' : '上传工程图纸'" width="1100px" append-to-body destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!uploading" :show-close="!uploading" :before-close="closeUpload">
      <div class="upload-product"><strong>{{ productName }}</strong><span>{{ productCode }}</span></div>
      <div class="upload-defaults">
        <span>批量默认值</span><el-select v-model="defaults.category" :disabled="!!uploadDrawingNo || uploading" style="width:160px"><el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        <el-input v-model="defaults.version" placeholder="版本，如 A / B" maxlength="50" :disabled="uploading" style="width:160px" />
        <el-button :disabled="uploading" @click="applyDefaults">应用到待上传文件</el-button>
      </div>
      <el-upload drag multiple :auto-upload="false" :show-file-list="false" :disabled="uploading" :on-change="addFile">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon><div class="el-upload__text">拖入文件，或 <em>选择多个文件</em></div><template #tip><div class="el-upload__tip">每个文件不超过10MB。同一图纸的CAD原稿和PDF使用相同图纸编号、版本；文件用途不同。</div></template>
      </el-upload>
      <el-table :data="queue" max-height="340" class="upload-queue">
        <el-table-column label="预览" width="72" align="center"><template #default="{ row }"><el-image v-if="row.thumb" :src="row.thumb" :preview-src-list="[row.thumb]" :initial-index="0" fit="contain" preview-teleported class="queue-thumb" /><el-tag v-else size="small" effect="plain" type="info">{{ queueKindTag(row) }}</el-tag></template></el-table-column>
        <el-table-column label="文件" min-width="160"><template #default="{ row }"><div>{{ row.file.name }}</div><span class="drawing-sub">{{ sizeLabel(row.file.size) }}</span></template></el-table-column>
        <el-table-column label="图种" width="145"><template #default="{ row }"><el-select v-model="row.category" :disabled="locked(row) || !!uploadDrawingNo"><el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></template></el-table-column>
        <el-table-column label="图纸编号" min-width="170"><template #default="{ row }"><el-input v-model="row.drawingNo" maxlength="80" placeholder="必填" :disabled="locked(row) || !!uploadDrawingNo" /></template></el-table-column>
        <el-table-column label="图纸名称" min-width="155"><template #default="{ row }"><el-input v-model="row.drawingName" maxlength="120" :disabled="locked(row)" /></template></el-table-column>
        <el-table-column label="版本" width="100"><template #default="{ row }"><el-input v-model="row.version" maxlength="50" :disabled="locked(row) || !!uploadFixedVersion" /></template></el-table-column>
        <el-table-column label="用途" width="145"><template #default="{ row }"><el-select v-model="row.fileRole" :disabled="locked(row)"><el-option v-for="item in DrawingFileRoleEnum.items" :key="item.value" :label="item.label" :value="item.value" :disabled="item.value === DrawingFileRoleEnum.PRINT.value && fileKind({ fileName: row.file.name, fileType: row.file.type }) === 'other'" /></el-select></template></el-table-column>
        <el-table-column label="进度" width="145"><template #default="{ row }"><el-progress v-if="row.status === DrawingUploadStatusEnum.UPLOADING.value" :percentage="row.progress" /><el-tag v-else size="small" :type="DrawingUploadStatusEnum.getTagProps(row.status).type">{{ DrawingUploadStatusEnum.getLabel(row.status) }}</el-tag><div v-if="row.error" class="queue-error">{{ row.error }}</div></template></el-table-column>
        <el-table-column width="60"><template #default="{ row }"><el-button v-if="!locked(row)" link type="danger" @click="removeQueueRow(row)">移除</el-button></template></el-table-column>
      </el-table>
      <p class="library-note">新图纸编号默认取文件名（去扩展名），请核对；不同版本必须使用同一个图纸编号。</p>
      <template #footer><span class="upload-summary">{{ queue.filter(item => item.status === DrawingUploadStatusEnum.SUCCESS.value).length }} / {{ queue.length }} 个已上传</span><el-button :disabled="uploading" @click="closeUpload()">关闭</el-button><el-button type="primary" :loading="uploading" :disabled="!pendingUploads.length" @click="submitUpload">上传待处理文件 / 重试失败项</el-button></template>
    </el-dialog>

    <el-dialog v-model="metadataVisible" title="图纸文件关联" width="560px" append-to-body :close-on-click-modal="false" :show-close="!metadataSaving" :close-on-press-escape="!metadataSaving">
      <p class="metadata-file">{{ metadataFile?.fileName }}</p>
      <el-form label-width="95px"><el-form-item label="图种" required><el-select v-model="metadata.category"><el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="图纸编号" required><el-input v-model="metadata.drawingNo" maxlength="80" placeholder="同一张图纸的各版本使用相同编号" /></el-form-item><el-form-item label="图纸名称"><el-input v-model="metadata.drawingName" maxlength="120" /></el-form-item><el-form-item label="版本" required><el-input v-model="metadata.version" maxlength="50" /></el-form-item><el-form-item label="文件用途" required><el-select v-model="metadata.fileRole"><el-option v-for="item in DrawingFileRoleEnum.items" :key="item.value" :label="item.label" :value="item.value" :disabled="item.value === DrawingFileRoleEnum.PRINT.value && metadataFile && fileKind(metadataFile) === 'other'" /></el-select></el-form-item></el-form>
      <template #footer><el-button :disabled="metadataSaving" @click="metadataVisible = false">取消</el-button><el-button type="primary" :loading="metadataSaving" @click="saveMetadata">保存关联</el-button></template>
    </el-dialog>
    <ProductFilePreview v-model="previewVisible" :file="previewFile" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { attachmentApi, type DrawingMetadata, type ProductDrawingFile } from '@/api/system/attachment'
import { useUserStore } from '@/store/modules/user'
import { useDict } from '@/composables/useDict'
import { DrawingCurrentFlagEnum, DrawingReleaseFlagEnum, DrawingFileRoleEnum, DrawingViewEnum, DrawingUploadStatusEnum } from '@/enums/product/drawing'
import { ENGINEERING_DRAWING_VISIBLE_CATEGORIES, LEGACY_PRODUCT_ATLAS_CATEGORY, PRODUCT_FILE_FALLBACK_CATEGORIES, productFileCategoryLabel } from '@/components/product/productFileCategories'
import { downloadFile, fileKind } from '@/components/product/productFilePreview'
import ProductFilePreview from '@/components/product/ProductFilePreview.vue'

const props = defineProps<{ productCode: string; productName: string; categories?: string[]; active?: boolean }>()
const emit = defineEmits<{ busy: [value: boolean]; loaded: [files: ProductDrawingFile[]] }>()
const user = useUserStore()
const canUpload = computed(() => user.hasPermission('product:edit'))
const canDelete = computed(() => user.hasPermission('product:delete'))
const canRelease = computed(() => user.hasPermission('engineering:drawing:release'))
const allowedCategories = props.categories || [...ENGINEERING_DRAWING_VISIBLE_CATEGORIES, '客供稿']
const { options: dict } = useDict('product_file_category')
const categoryOptions = computed(() => {
  const options = dict.value.map((item: any) => ({ label: item.label || item.itemValue || item.item_value, value: item.itemValue || item.item_value }))
  return (options.length ? options : PRODUCT_FILE_FALLBACK_CATEGORIES.map(value => ({ label: value, value })))
    .filter(item => allowedCategories.includes(item.value) && item.value !== LEGACY_PRODUCT_ATLAS_CATEGORY)
    .sort((a, b) => allowedCategories.indexOf(a.value) - allowedCategories.indexOf(b.value))
})
const files = ref<ProductDrawingFile[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const categoryFilter = ref('')
const view = ref<string>(DrawingViewEnum.ALL.value)
const releaseFilter = ref<number | string>('')
const mutationId = ref<number>()
let generation = 0
let disposed = false
interface RevisionRow { key: string; drawingNo: string; name: string; category: string; version: string; files: ProductDrawingFile[]; current: boolean; released: boolean; hasReleased: boolean; latest: ProductDrawingFile }
const rows = computed<RevisionRow[]>(() => {
  const groups = new Map<string, ProductDrawingFile[]>()
  for (const file of files.value) {
    const key = file.drawingNo ? JSON.stringify([file.drawingNo, file.version]) : `legacy-${file.id}`
    groups.set(key, [...(groups.get(key) || []), file])
  }
  return [...groups.entries()].map(([key, list]) => {
    const latest = [...list].sort((a, b) => b.id - a.id)[0]
    return { key, drawingNo: latest.drawingNo || '', name: latest.drawingName || latest.fileName, category: latest.category, version: latest.version || '', files: list,
      current: list.every(file => file.isCurrent === DrawingCurrentFlagEnum.CURRENT.value), released: list.every(file => file.released === DrawingReleaseFlagEnum.RELEASED.value), hasReleased: list.some(file => file.released === DrawingReleaseFlagEnum.RELEASED.value), latest }
  }).sort((a, b) => b.latest.id - a.latest.id)
})
const visibleCategories = computed(() => allowedCategories.filter(category => files.value.some(file => file.category === category)))
const filteredRows = computed(() => rows.value.filter(row => {
  const term = keyword.value.trim().toLowerCase()
  return (!categoryFilter.value || row.category === categoryFilter.value)
    && (!term || [row.drawingNo, row.name, ...row.files.map(file => file.fileName)].some(value => value.toLowerCase().includes(term)))
    && (view.value !== DrawingViewEnum.CURRENT.value || row.current)
    && (view.value !== DrawingViewEnum.HISTORY.value || !row.current)
    && (view.value !== DrawingViewEnum.UNBOUND.value || !row.drawingNo)
    && (typeof releaseFilter.value !== 'number' || row.released === (releaseFilter.value === DrawingReleaseFlagEnum.RELEASED.value))
}))
async function loadFiles() {
  if (disposed || props.active === false) return
  const current = ++generation
  loading.value = true; error.value = ''
  try {
    const res: any = await attachmentApi.productFiles(props.productCode)
    if (current === generation && !disposed) {
      files.value = (res.data || []).filter((file: ProductDrawingFile) => allowedCategories.includes(file.category))
      emit('loaded', files.value)
    }
  } catch (e) { if (current === generation && !disposed) { files.value = []; error.value = e instanceof Error ? e.message : '文件加载失败，请刷新重试' } }
  finally { if (current === generation && !disposed) loading.value = false }
}
watch(() => props.productCode, () => { files.value = []; categoryFilter.value = ''; keyword.value = ''; previewVisible.value = false; loadFiles() }, { immediate: false })
const previewVisible = ref(false)
const previewFile = ref<ProductDrawingFile>()
function preview(file: ProductDrawingFile) { previewFile.value = file; previewVisible.value = true }
async function download(file: ProductDrawingFile) { try { await downloadFile(file) } catch (e) { ElMessage.error(e instanceof Error ? e.message : '下载失败') } }
function timeLabel(value?: string) { return value?.replace('T', ' ').slice(0, 16) || '—' }
function extension(name: string) { return name.split('.').pop()?.toUpperCase() || '' }
function stem(name: string) { const i = name.lastIndexOf('.'); return i > 0 ? name.slice(0, i) : name }
function sizeLabel(size = 0) { return size < 1024 * 1024 ? `${(size / 1024).toFixed(0)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB` }
function cancelled(e: unknown) { return e === 'cancel' || e === 'close' }
async function setCurrent(row: RevisionRow) {
  try {
    await ElMessageBox.confirm(`将「${row.name}」${row.version}版设为现行？此图纸的其他版本转为非现行，新现行版需下发后才默认进入打印。`, '切换现行版本', { type: 'warning' })
    mutationId.value = row.files[0].id
    await attachmentApi.setCurrent(row.files[0].id); ElMessage.success('已切换现行版本'); await loadFiles()
  } catch (e) { if (!cancelled(e)) ElMessage.error(e instanceof Error ? e.message : '设置失败') }
  finally { mutationId.value = undefined }
}
async function release(row: RevisionRow) {
  try {
    await ElMessageBox.confirm(`${row.hasReleased ? '撤回' : '下发'}「${row.name}」${row.version}版？同版本原稿、打印件一起处理。`, '图纸下发', { type: 'warning' })
    mutationId.value = row.files[0].id
    await attachmentApi.release(row.files[0].id, !row.hasReleased); ElMessage.success(row.hasReleased ? '已撤回' : '已下发'); await loadFiles()
  } catch (e) { if (!cancelled(e)) ElMessage.error(e instanceof Error ? e.message : '操作失败') }
  finally { mutationId.value = undefined }
}
async function remove(file: ProductDrawingFile) {
  try { await ElMessageBox.confirm(`删除未下发文件「${file.fileName}」？`, '删除文件', { type: 'warning' }); await attachmentApi.remove(file.id); await loadFiles() }
  catch (e) { if (!cancelled(e)) ElMessage.error(e instanceof Error ? e.message : '删除失败') }
}

interface UploadRow extends DrawingMetadata { uid: number; file: File; category: string; status: string; progress: number; error: string; thumb?: string }
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadNewRevision = ref(false)
const uploadDrawingNo = ref('')
const uploadDrawingName = ref('')
const uploadFixedVersion = ref('')
const defaults = reactive({ category: '', version: 'A' })
const queue = ref<UploadRow[]>([])
/** 非图片行在预览列显示的标签（PDF / 扩展名） */
function queueKindTag(row: UploadRow) { return extension(row.file.name) || '文件' }
/** 释放单行缩略图 objectURL */
function revokeThumb(row: UploadRow) { if (row.thumb) { URL.revokeObjectURL(row.thumb); row.thumb = '' } }
/** 释放队列全部缩略图 objectURL */
function revokeQueue() { queue.value.forEach(revokeThumb) }
/** 移除待上传行（释放缩略图） */
function removeQueueRow(row: UploadRow) { revokeThumb(row); queue.value = queue.value.filter(item => item.uid !== row.uid) }
const pendingUploads = computed(() => queue.value.filter(row => row.status === DrawingUploadStatusEnum.QUEUED.value || row.status === DrawingUploadStatusEnum.FAILED.value))
watch(categoryOptions, (options) => { if (!options.some(item => item.value === defaults.category)) defaults.category = options[0]?.value || '' }, { immediate: true })
function openUpload(row?: RevisionRow, newRevision = false) {
  revokeQueue(); queue.value = []; uploadNewRevision.value = newRevision; uploadDrawingNo.value = row?.drawingNo || ''; uploadDrawingName.value = row?.name || ''
  uploadFixedVersion.value = row && !newRevision ? row.version : ''; defaults.category = row?.category || categoryOptions.value[0]?.value || ''; defaults.version = row ? (newRevision ? '' : row.version) : 'A'
  uploadVisible.value = true
}
function addFile(upload: UploadFile) {
  if (!upload.raw || uploading.value) return
  if (upload.raw.size > 10 * 1024 * 1024 || !upload.raw.size) { ElMessage.warning(`${upload.name}：文件须非空且不超过10MB`); return }
  if (queue.value.some(row => row.file.name === upload.name && row.file.size === upload.size && row.file.lastModified === upload.raw?.lastModified)) return
  const kind = fileKind({ fileName: upload.name, fileType: upload.raw.type })
  const thumb = kind === 'image' ? URL.createObjectURL(upload.raw) : ''
  queue.value.push({ uid: upload.uid, file: upload.raw, thumb, category: defaults.category, drawingNo: uploadDrawingNo.value || stem(upload.name).slice(0, 80), drawingName: uploadDrawingName.value || stem(upload.name).slice(0, 120), version: defaults.version,
    fileRole: kind === 'other' ? DrawingFileRoleEnum.ORIGINAL.value : DrawingFileRoleEnum.PRINT.value, status: DrawingUploadStatusEnum.QUEUED.value, progress: 0, error: '' })
}
function locked(row: UploadRow) { return uploading.value || row.status === DrawingUploadStatusEnum.SUCCESS.value }
function applyDefaults() { pendingUploads.value.forEach(row => { row.category = defaults.category; row.version = uploadFixedVersion.value || defaults.version }) }
async function submitUpload() {
  const pending = pendingUploads.value
  if (pending.some(row => !row.drawingNo.trim() || !row.version.trim() || !categoryOptions.value.some(item => item.value === row.category))) { ElMessage.warning('请补齐每个文件的图种、图纸编号和版本'); return }
  const keys = new Set<string>()
  for (const row of queue.value) {
    const key = JSON.stringify([row.drawingNo.trim().toUpperCase(), row.version.trim().toUpperCase(), row.fileRole])
    if (keys.has(key)) { ElMessage.warning('同一图纸、同一版本只能各有一个原稿和打印件，请核对上传清单'); return }
    keys.add(key)
  }
  uploading.value = true
  const code = props.productCode
  try {
    for (const row of pending) {
      if (disposed) break
      row.status = DrawingUploadStatusEnum.UPLOADING.value; row.error = ''; row.progress = 0
      try {
        await attachmentApi.uploadProductFile(row.file, code, row.category, row.version, { drawingNo: row.drawingNo.trim(), drawingName: row.drawingName?.trim(), version: row.version.trim(), fileRole: row.fileRole }, value => { row.progress = value })
        row.status = DrawingUploadStatusEnum.SUCCESS.value; row.progress = 100
      } catch (e) { row.status = DrawingUploadStatusEnum.FAILED.value; row.error = e instanceof Error ? e.message : '上传失败' }
    }
    await loadFiles()
  } finally { uploading.value = false }
}
async function closeUpload(done?: () => void) {
  if (uploading.value) return
  if (pendingUploads.value.length) {
    try { await ElMessageBox.confirm('还有未上传文件，关闭后会清空这些待处理项。', '关闭上传', { type: 'warning' }) }
    catch (e) { if (!cancelled(e)) ElMessage.error('关闭失败'); return }
  }
  revokeQueue(); uploadVisible.value = false; queue.value = []; done?.()
}
const metadataVisible = ref(false)
const metadataSaving = ref(false)
const metadataFile = ref<ProductDrawingFile>()
const metadata = reactive<DrawingMetadata>({ drawingNo: '', drawingName: '', version: '', category: '', fileRole: DrawingFileRoleEnum.PRINT.value })
function openMetadata(file: ProductDrawingFile) {
  metadataFile.value = file
  Object.assign(metadata, { category: categoryOptions.value.some(item => item.value === file.category) ? file.category : (categoryOptions.value.find(item => item.value === '其他工程图')?.value || categoryOptions.value[0]?.value || ''), drawingNo: file.drawingNo || '', drawingName: file.drawingName || stem(file.fileName), version: file.version || 'A', fileRole: file.fileRole || (fileKind(file) === 'other' ? DrawingFileRoleEnum.ORIGINAL.value : DrawingFileRoleEnum.PRINT.value) })
  metadataVisible.value = true
}
async function saveMetadata() {
  if (!metadataFile.value || !metadata.drawingNo.trim() || !metadata.version.trim() || !categoryOptions.value.some(item => item.value === metadata.category)) { ElMessage.warning('请选择图种，填写图纸编号和版本'); return }
  metadataSaving.value = true
  try { await attachmentApi.updateDrawingMetadata(metadataFile.value.id, { ...metadata, drawingNo: metadata.drawingNo.trim(), version: metadata.version.trim() }); metadataVisible.value = false; ElMessage.success('图纸关联已保存'); await loadFiles() }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '保存失败') }
  finally { metadataSaving.value = false }
}
watch(() => uploadVisible.value || metadataVisible.value || uploading.value || metadataSaving.value || !!mutationId.value, value => emit('busy', value))
watch(() => props.active, value => { if (value) loadFiles() })
loadFiles()
onBeforeUnmount(() => { disposed = true; generation++; revokeQueue() })
</script>

<style scoped>
.library-toolbar { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:20px; }.library-heading { display:flex; align-items:center; gap:12px; }.library-heading strong { font-size:15px; }.library-heading span,.library-note { font-size:12px; color:#909baa; }.library-alert { margin-bottom:15px; }
.filters { display:flex; flex-wrap:wrap; gap:10px; margin-bottom:8px; }.category-tabs :deep(.el-tabs__header) { margin-bottom:16px; }.category-tabs :deep(.el-tabs__item) { font-size:13px; }.drawing-title { font-size:13px; font-weight:500; color:#35445a; }.drawing-sub { margin-top:4px; font-size:11px; color:#98a2af; }.status-tags { display:flex; flex-wrap:wrap; gap:5px; }.file-link { display:flex; align-items:center; gap:8px; margin:4px 0; }.file-link span { color:#99a2ae; font-size:10px; }.row-actions { display:flex; flex-wrap:wrap; gap:8px; }.row-actions :deep(.el-button+.el-button) { margin-left:0; }.revision-files { padding:12px 26px; background:#f8fafc; }.revision-file { display:flex; align-items:center; gap:12px; padding:9px 0; }.revision-file :deep(.el-link) { flex:1; justify-content:flex-start; }.file-info { color:#8a96a6; font-size:11px; }
.upload-product { display:flex; gap:14px; align-items:center; background:#f2f6fc; border-radius:6px; padding:14px 18px; margin-bottom:18px; }.upload-product span { color:#77869b; font-size:13px; }.upload-defaults { display:flex; align-items:center; gap:12px; margin-bottom:14px; }.upload-defaults > span { color:#8895a5; font-size:12px; }.upload-queue { margin-top:18px; }.queue-thumb { width:42px; height:42px; border-radius:4px; border:1px solid #e3e8ee; background:#fff; cursor:zoom-in; }.queue-error { color:#d95757; font-size:11px; margin-top:5px; }.upload-summary { float:left; color:#8896a8; font-size:12px; padding-top:10px; }.metadata-file { margin:0 0 20px; padding:12px; background:#f6f8fb; color:#69778a; }
</style>

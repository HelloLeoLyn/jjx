<template>
  <div class="drawing-library">
    <div class="library-toolbar">
      <div class="library-heading"><strong>工程图集</strong><span>{{ files.length }} 个文件 · {{ rows.length }} 张图纸</span></div>
      <div><el-button @click="loadFiles">刷新</el-button><el-button v-if="canUpload" type="primary" icon="Upload" :disabled="uploading" @click="openUpload()">上传图纸</el-button></div>
    </div>
    <p v-if="uploading" class="library-note" role="status">正在上传图纸…</p>
    <input ref="revisionInput" type="file" multiple hidden data-test="new-revision-file" :disabled="uploading" @change="uploadNewVersionFiles" />
    <el-alert v-if="syncError" :title="syncError" type="warning" :closable="false" class="library-alert" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="library-alert" />
    <div class="filters">
      <el-input v-model="keyword" placeholder="图纸编号、名称或文件名" clearable style="width:240px" />
      <el-select v-model="categoryFilter" aria-label="资料类型" style="width:190px">
        <el-option :label="`全部资料类型 (${keywordRows.length})`" value="" />
        <el-option v-for="item in documentTypeTabs" :key="item.value" :label="`${item.label} (${item.count})`" :value="item.value" />
      </el-select>
      <el-checkbox v-model="onlyPending">只看待处理 ({{ pendingCount }})</el-checkbox>
      <el-button link type="primary" @click="showMore = !showMore">{{ showMore ? '收起更多' : '更多' }}</el-button>
      <el-checkbox v-if="canRelease" v-model="printPublishing">打印发布</el-checkbox>
    </div>
    <div v-if="showMore" class="filters">
      <el-select v-model="view" style="width:160px"><el-option v-for="item in DrawingViewEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-select v-model="releaseFilter" style="width:145px"><el-option label="全部下发状态" value="" /><el-option v-for="item in DrawingReleaseFlagEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select>
    </div>
    <p class="library-note">图集打印需资料已分类、有图纸关联、为现行版且已下发。<span v-if="canRelease">需要下发或撤回时，打开「打印发布」并展开图纸版本。</span><span v-else>需由有下发权限的人员完成下发。</span></p>
    <el-table v-loading="loading" :data="filteredRows" row-key="key" :row-class-name="drawingRowClass" class="drawing-table">
      <template #empty>
        <el-empty :description="error ? '图纸加载失败，请刷新重试' : files.length ? '暂无符合条件的图纸，可切换筛选' : '暂无工程图纸'">
          <el-button v-if="error" @click="loadFiles">重试</el-button>
          <el-button v-else-if="canUpload && !files.length" type="primary" @click="openUpload(undefined, false, 'STRUCTURE_DRAWING')">上传第一张结构图</el-button>
        </el-empty>
      </template>
      <el-table-column type="expand">
        <template #default="{ row }">
          <el-timeline class="revision-timeline">
            <el-timeline-item v-for="revision in row.revisions" :key="revision.key" :timestamp="`${timeLabel(revision.latest.createTime)} · ${revision.latest.createBy || '—'}`" placement="top">
              <div class="revision-heading">
                <strong>{{ revision.version || '未标注' }} 版 · {{ revision.files.length }} 个文件</strong>
                <el-tag size="small" :type="revision.current ? 'success' : 'info'">{{ DrawingCurrentFlagEnum.getLabel(revision.current ? DrawingCurrentFlagEnum.CURRENT.value : DrawingCurrentFlagEnum.NOT_CURRENT.value) }}</el-tag>
                <el-tag size="small" :type="revision.released ? 'success' : 'info'">{{ revision.released ? '已下发' : revision.hasReleased ? '部分已下发' : '未下发' }}</el-tag>
                <el-button v-if="canUpload && revision.drawingNo && !revision.current" link type="warning" :disabled="!!mutationId" @click="setCurrent(revision)">设为现行</el-button>
                <el-button v-if="canRelease && printPublishing && ((revision.drawingNo && revision.current) || revision.hasReleased)" link :type="revision.hasReleased ? 'info' : 'success'" :disabled="!!mutationId" @click="release(revision)">{{ revision.hasReleased ? '撤回' : '下发' }}</el-button>
                <el-button v-if="pendingStructureSync?.drawingNo === revision.drawingNo && pendingStructureSync?.version === revision.version" link type="warning" :disabled="!!mutationId" @click="retryStructureSync">重试同步结构图</el-button>
              </div>
              <div class="revision-files">
                <div v-for="file in revision.files" :key="file.refId" class="revision-file">
                  <el-tag size="small" effect="plain">{{ documentTypeLabel(file.documentType) }}</el-tag>
                  <el-tag size="small" effect="plain">{{ file.fileRole ? DrawingFileRoleEnum.getLabel(file.fileRole) : '未归集' }}</el-tag>
                  <el-link type="primary" @click="preview(file)">{{ file.fileName }}</el-link>
                  <span class="file-info">{{ sizeLabel(file.fileSize) }} · {{ file.createBy || '—' }} · {{ timeLabel(file.createTime) }}</span>
                  <el-button link type="primary" @click="download(file)">下载</el-button>
                  <el-button v-if="canUpload && file.released !== DrawingReleaseFlagEnum.RELEASED.value" link type="primary" @click="openMetadata(file)">{{ !file.documentType ? '补资料类型' : file.drawingNo ? '编辑关联' : '补录图纸关联' }}</el-button>
                  <el-button v-if="canDelete && file.isCurrent !== DrawingCurrentFlagEnum.CURRENT.value && file.released !== DrawingReleaseFlagEnum.RELEASED.value" link type="danger" @click="remove(file)">删除</el-button>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </template>
      </el-table-column>
      <el-table-column label="图纸" min-width="210">
        <template #default="{ row }"><div class="drawing-title">{{ row.name }}</div><div class="drawing-sub">{{ row.drawingNo || '待归集：请补录关联' }}</div></template>
      </el-table-column>
      <el-table-column label="资料类型" width="140"><template #default="{ row }">{{ row.documentTypes.map(documentTypeLabel).join('、') }}</template></el-table-column>
      <el-table-column label="当前版本" width="110"><template #default="{ row }">{{ row.version || '未标注' }}{{ row.current ? '' : '（待设现行）' }}</template></el-table-column>
      <el-table-column label="文件数" width="85"><template #default="{ row }">{{ row.totalFiles.length }}</template></el-table-column>
      <el-table-column label="缺件" min-width="180"><template #default="{ row }">
        <el-tag v-if="row.missingPrint" size="small" type="warning">缺打印件</el-tag>
        <div v-if="row.unclassified" class="drawing-sub">补资料类型后才能进打印
          <span v-if="!row.unclassifiedFile">（已下发文件请先撤回）</span>
          <el-button v-if="canUpload && row.unclassifiedFile" link type="primary" @click="openMetadata(row.unclassifiedFile)">补资料类型</el-button>
        </div>
      </template></el-table-column>
      <el-table-column label="当前版文件 / 预览" min-width="170">
        <template #default="{ row }"><div v-for="file in row.files" :key="file.id" class="file-link"><el-link type="primary" @click="preview(file)">{{ file.fileRole ? DrawingFileRoleEnum.getLabel(file.fileRole) : file.fileName }}</el-link><span>{{ extension(file.fileName) }}</span></div></template>
      </el-table-column>
      <el-table-column label="状态" width="135">
        <template #default="{ row }"><div class="status-tags"><el-tag size="small" :type="row.current ? 'success' : 'info'">{{ DrawingCurrentFlagEnum.getLabel(row.current ? DrawingCurrentFlagEnum.CURRENT.value : DrawingCurrentFlagEnum.NOT_CURRENT.value) }}</el-tag><el-tag size="small" :type="row.released ? 'success' : 'info'">{{ DrawingReleaseFlagEnum.getLabel(row.released ? DrawingReleaseFlagEnum.RELEASED.value : DrawingReleaseFlagEnum.NOT_RELEASED.value) }}</el-tag></div></template>
      </el-table-column>
      <el-table-column label="最新上传" width="150"><template #default="{ row }"><div>{{ row.revisions[0].latest.createBy || '—' }}</div><div class="drawing-sub">{{ timeLabel(row.revisions[0].latest.createTime) }}</div></template></el-table-column>
      <el-table-column label="操作" min-width="200" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button v-if="canUpload && row.drawingNo" link type="primary" :disabled="uploading" @click="chooseNewVersion(row)">新版本</el-button>
            <el-button v-if="canUpload && !row.drawingNo" link type="primary" @click="openMetadata(row.files[0])">补录关联</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <p class="library-note">同一图纸编号、同一版本可放多个文件（原稿/打印件）；按图纸编号合并，展开查看版本时间线；行内操作作用于当前版本，无现行时作用于最新上传版本。</p>

    <el-dialog v-model="uploadVisible" :title="uploadNewRevision ? '上传图纸新版本' : '上传工程图纸'" width="1100px" append-to-body destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!uploading" :show-close="!uploading" :before-close="closeUpload">
      <div class="upload-product"><strong>{{ productName }}</strong><span>{{ productCode }}</span></div>
      <div class="upload-defaults">
        <span>批量默认值</span><el-tag size="small" effect="plain">工程技术资料</el-tag><el-select v-model="defaults.documentType" clearable placeholder="未分类（可稍后补录）" :disabled="!!uploadDrawingNo || uploading" style="width:170px"><el-option v-for="item in documentTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        <el-input v-model="defaults.version" placeholder="版本，如 A / B" maxlength="50" :disabled="uploading" style="width:160px" />
        <el-button :disabled="uploading" @click="applyDefaults">应用到待上传文件</el-button>
      </div>
      <el-upload drag multiple :auto-upload="false" :show-file-list="false" :disabled="uploading" :on-change="addFile">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon><div class="el-upload__text">拖入文件，或 <em>选择多个文件</em></div><template #tip><div class="el-upload__tip">每个文件不超过10MB。同一图纸的CAD原稿和PDF使用相同图纸编号、版本；文件用途不同。</div></template>
      </el-upload>
      <el-table :data="queue" max-height="340" class="upload-queue">
        <el-table-column label="预览" width="72" align="center"><template #default="{ row }"><el-image v-if="row.thumb" :src="row.thumb" :preview-src-list="[row.thumb]" :initial-index="0" fit="contain" preview-teleported class="queue-thumb" /><el-tag v-else size="small" effect="plain" type="info">{{ queueKindTag(row) }}</el-tag></template></el-table-column>
        <el-table-column label="文件" min-width="160"><template #default="{ row }"><div>{{ row.file.name }}</div><span class="drawing-sub">{{ sizeLabel(row.file.size) }}</span></template></el-table-column>
        <el-table-column label="资料类型" width="165"><template #default="{ row }"><el-select v-model="row.documentType" clearable placeholder="未分类" @change="regenerateIdentity(row)" :disabled="locked(row) || !!uploadDrawingNo"><el-option v-for="item in documentTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></template></el-table-column>
        <el-table-column label="版本" width="100"><template #default="{ row }"><el-input v-model="row.version" maxlength="50" @change="resetUploadAttempt(row)" :disabled="locked(row) || !!uploadFixedVersion" /></template></el-table-column>
        <el-table-column label="进度" width="145"><template #default="{ row }"><el-progress v-if="row.status === DrawingUploadStatusEnum.UPLOADING.value" :percentage="row.progress" /><el-tag v-else size="small" :type="DrawingUploadStatusEnum.getTagProps(row.status).type">{{ DrawingUploadStatusEnum.getLabel(row.status) }}</el-tag><div v-if="row.error" class="queue-error">{{ row.error }}</div></template></el-table-column>
        <el-table-column width="60"><template #default="{ row }"><el-button v-if="!locked(row)" link type="danger" @click="removeQueueRow(row)">移除</el-button></template></el-table-column>
      </el-table>
      <p class="library-note">图纸编号和名称自动生成，用途按文件类型识别；未分类文件上传后可补资料类型。版本不明确时请填写版本。</p>
      <template #footer><span class="upload-summary">{{ queue.filter(item => item.status === DrawingUploadStatusEnum.SUCCESS.value).length }} / {{ queue.length }} 个已上传</span><el-button :disabled="uploading" @click="closeUpload()">关闭</el-button><el-button type="primary" :loading="uploading" :disabled="!pendingUploads.length" @click="submitUpload">上传待处理文件 / 重试失败项</el-button></template>
    </el-dialog>

    <el-dialog v-model="metadataVisible" :title="metadataTypeOnly ? '补资料类型' : metadataFile?.drawingNo ? '编辑关联' : '补录关联'" width="560px" append-to-body :close-on-click-modal="false" :show-close="!metadataSaving" :close-on-press-escape="!metadataSaving">
      <p class="metadata-file">{{ metadataFile?.fileName }}</p>
      <el-alert v-if="metadataError" :title="metadataError" type="error" :closable="false" />
      <el-form label-width="110px"><el-form-item label="资料类型" required><div class="metadata-type"><el-tag size="small" effect="plain">工程技术资料</el-tag><el-select v-model="metadata.documentType" :disabled="metadataSaving" @change="regenerateMetadataIdentity"><el-option v-for="item in documentTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></div></el-form-item>
        <template v-if="!metadataTypeOnly && metadataFile?.drawingNo">
          <el-form-item label="版本" required><el-input v-model="metadata.version" maxlength="50" :disabled="metadataSaving" /></el-form-item>
          <el-form-item label="文件用途" required><el-select v-model="metadata.fileRole" :disabled="metadataSaving"><el-option v-for="item in DrawingFileRoleEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        </template>
      </el-form>
      <p v-if="!metadataFile?.drawingNo" class="library-note">图纸编号和名称自动生成；已有版本和文件用途保留。</p>
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
import { attachmentRefApi, type AttachmentDocumentTypeItem, type AttachmentDrawingMetadata, type AttachmentRefItem } from '@/api/system/attachmentRef'
import { useUserStore } from '@/store/modules/user'
import { DrawingCurrentFlagEnum, DrawingReleaseFlagEnum, DrawingFileRoleEnum, DrawingViewEnum, DrawingUploadStatusEnum } from '@/enums/product/drawing'
import { ENGINEERING_DOCUMENT_TYPES, ENGINEERING_DOCUMENT_TYPE_CODES, duplicateFingerprints, fileFingerprint } from '@/components/product/productFileCategories'
import { downloadFile, fileKind } from '@/components/product/productFilePreview'
import ProductFilePreview from '@/components/product/ProductFilePreview.vue'
import { newDrawingIdentity, nextDrawingVersion } from '@/components/product/drawingUploadRules'
import { normalizeWorkSpec, productWorkSpecApi } from '@/api/product/workSpec'

/** 图纸库行 = 附件（sys_attachment）+ 其产品引用（sys_attachment_ref）。
 *  dev-20261014-016 第3项：读走按产品查引用（refsByProduct），写走公共 temp 上传 + 建引用；refId 用于解除引用。 */
type DrawingFile = ProductDrawingFile & { refId: number; documentType?: string }

const props = defineProps<{ productId?: number; productCode: string; productName: string; categories?: string[]; active?: boolean }>()
const emit = defineEmits<{ busy: [value: boolean]; loaded: [files: ProductDrawingFile[]] }>()
const user = useUserStore()
const canUpload = computed(() => user.hasPermission('product:edit'))
const canDelete = computed(() => user.hasPermission('product:delete'))
const canRelease = computed(() => user.hasPermission('engineering:drawing:release'))
/** 允许的二级资料类型编码（props.categories 语义改为 documentType 编码范围）。 */
const allowedDocumentTypes = computed(() => {
  if (props.categories && props.categories.length) return props.categories
  // 图集页按接口返回的 ENGINEERING_DOC 二级类型动态展示；接口失败才用本地兜底。
  if (docTypes.value.length) return docTypes.value.map(item => item.value)
  return ENGINEERING_DOCUMENT_TYPE_CODES
})
interface DocTypeOption { value: string; label: string }
const docTypes = ref<DocTypeOption[]>([])
const labelOfDocumentType = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  for (const item of ENGINEERING_DOCUMENT_TYPES) map[item.value] = item.label
  for (const item of docTypes.value) map[item.value] = item.label
  return map
})
const documentTypeOptions = computed<DocTypeOption[]>(() =>
  allowedDocumentTypes.value.map(value => ({ value, label: labelOfDocumentType.value[value] || value })))
function documentTypeLabel(value?: string | null) {
  return value ? (labelOfDocumentType.value[value] || value) : '未分类'
}
const files = ref<DrawingFile[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const categoryFilter = ref('')
const view = ref<string>(DrawingViewEnum.ALL.value)
const releaseFilter = ref<number | string>('')
const mutationId = ref<number>()
const syncError = ref('')
const pendingStructureSync = ref<{ productId: number; drawingNo: string; version: string }>()
const onlyPending = ref(false)
const showMore = ref(false)
const printPublishing = ref(false)
let generation = 0
let disposed = false
interface RevisionRow { key: string; drawingNo: string; name: string; documentType: string; version: string; files: DrawingFile[]; current: boolean; released: boolean; hasReleased: boolean; latest: DrawingFile }
const revisionRows = computed<RevisionRow[]>(() => {
  const groups = new Map<string, DrawingFile[]>()
  for (const file of files.value) {
    const key = file.drawingNo ? JSON.stringify([file.drawingNo, file.version]) : `legacy-${file.id}`
    groups.set(key, [...(groups.get(key) || []), file])
  }
  return [...groups.entries()].map(([key, list]) => {
    const latest = [...list].sort((a, b) => b.id - a.id)[0]
    return { key, drawingNo: latest.drawingNo || '', name: latest.drawingName || latest.fileName, documentType: latest.documentType || '', version: latest.version || '', files: list,
      current: list.every(file => file.isCurrent === DrawingCurrentFlagEnum.CURRENT.value), released: list.every(file => file.released === DrawingReleaseFlagEnum.RELEASED.value), hasReleased: list.some(file => file.released === DrawingReleaseFlagEnum.RELEASED.value), latest }
  }).sort((a, b) => b.latest.id - a.latest.id)
})
interface DrawingRow extends RevisionRow {
  revisions: RevisionRow[]; totalFiles: DrawingFile[]; documentTypes: string[]
  missingPrint: boolean; unclassified: boolean; unclassifiedFile?: DrawingFile
}
function drawingKey(file: Pick<ProductDrawingFile, 'drawingNo' | 'id'>) {
  // 编号是跨版本身份；名称可能随修订更正，不据此拆成另一张图纸。
  return file.drawingNo ? JSON.stringify([file.drawingNo]) : `legacy-${file.id}`
}
const rows = computed<DrawingRow[]>(() => {
  const groups = new Map<string, RevisionRow[]>()
  for (const revision of revisionRows.value) {
    const key = drawingKey(revision.latest)
    groups.set(key, [...(groups.get(key) || []), revision])
  }
  return [...groups.entries()].map(([key, revisions]) => {
    // 已有现行优先；新上传历史版不能悄悄接管外层操作目标。
    const selected = revisions.find(revision => revision.current) || revisions[0]
    const totalFiles = revisions.flatMap(revision => revision.files)
    const unclassifiedFile = totalFiles.find(file => !file.documentType && file.released !== DrawingReleaseFlagEnum.RELEASED.value)
    return { ...selected, key, revisions, totalFiles, documentTypes: [...new Set(totalFiles.map(file => file.documentType || ''))],
      missingPrint: !selected.files.some(file => file.fileRole === DrawingFileRoleEnum.PRINT.value),
      unclassified: totalFiles.some(file => !file.documentType), unclassifiedFile }
  }).sort((a, b) => b.revisions[0].latest.id - a.revisions[0].latest.id)
})
/** 未分类筛选值（与“全部”的空串区分）。 */
const UNCATEGORIZED_FILTER = '__UNCATEGORIZED__'
const keywordRows = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  return rows.value.filter(row => !term || [row.drawingNo, row.name,
    ...row.totalFiles.flatMap(file => [file.drawingName || '', file.fileName])].some(value => value.toLowerCase().includes(term)))
})
const documentTypeTabs = computed(() => [
  ...allowedDocumentTypes.value.map(value => ({ value, label: documentTypeLabel(value),
    count: keywordRows.value.filter(row => row.documentTypes.includes(value)).length })),
  { value: UNCATEGORIZED_FILTER, label: '未分类', count: keywordRows.value.filter(row => row.unclassified).length },
])
const typeRows = computed(() => keywordRows.value.filter(row => !categoryFilter.value ||
  row.documentTypes.includes(categoryFilter.value === UNCATEGORIZED_FILTER ? '' : categoryFilter.value)))
function needsAttention(row: DrawingRow) { return row.missingPrint || row.unclassified || !row.drawingNo }
const pendingCount = computed(() => typeRows.value.filter(needsAttention).length)
const filteredRows = computed(() => typeRows.value.filter(row =>
  (!onlyPending.value || needsAttention(row))
  // 版本视图决定该图纸是否出现；完整时间线和当前版本操作目标始终保留。
  && row.revisions.some(revision =>
    (view.value !== DrawingViewEnum.CURRENT.value || revision.current)
    && (view.value !== DrawingViewEnum.HISTORY.value || !revision.current)
    && (view.value !== DrawingViewEnum.UNBOUND.value || !revision.drawingNo)
    && (typeof releaseFilter.value !== 'number' || revision.released === (releaseFilter.value === DrawingReleaseFlagEnum.RELEASED.value)))
))
const highlightedKeys = ref<string[]>([])
let highlightTimer: ReturnType<typeof setTimeout> | undefined
let hasLoaded = false
let lastLoadedIds = new Set<number>()
function drawingRowClass({ row }: { row: DrawingRow }) { return highlightedKeys.value.includes(row.key) ? 'uploaded-drawing' : '' }
function showUploaded(uploaded: DrawingFile[]) {
  if (!uploaded.length) return
  if (highlightTimer) clearTimeout(highlightTimer)
  highlightedKeys.value = [...new Set(uploaded.map(drawingKey))]
  ElMessage.success(`已上传：${[...new Set(uploaded.map(file => `${file.drawingNo || file.fileName}-${file.version || '未标注'}`))].join('、')}`)
  highlightTimer = setTimeout(() => { highlightedKeys.value = [] }, 2000)
}
async function loadFiles() {
  if (disposed || props.active === false) return
  if (!props.productId) { files.value = []; emit('loaded', []); return }
  const current = ++generation
  loading.value = true; error.value = ''
  try {
    // 读：按产品查引用（不再走已废弃的 biz_type/biz_id 通道）。refsByProduct 同时按 product_id 与 (source_type='product' AND source_id) 取；
    // 图纸库只管理 product 来源的附件，故按 sourceType 再过滤一层，避免其它来源（询价/报价等）带 product_id 的行串入。
    const res: any = await attachmentRefApi.refsByProduct(props.productId)
    if (current === generation && !disposed) {
      const previousIds = lastLoadedIds
      const nextFiles = ((res.data || []) as AttachmentRefItem[])
        .filter(ref => ref.sourceType === 'product' && Number(ref.attachmentId) > 0)
        .map(toDrawingFile)
        .filter(file => !file.documentType || allowedDocumentTypes.value.includes(file.documentType))
      files.value = nextFiles
      if (hasLoaded) showUploaded(nextFiles.filter(file => !previousIds.has(file.id)))
      lastLoadedIds = new Set(nextFiles.map(file => file.id))
      hasLoaded = true
      emit('loaded', files.value)
    }
  } catch (e) { if (current === generation && !disposed) { files.value = []; error.value = e instanceof Error ? e.message : '文件加载失败，请刷新重试' } }
  finally { if (current === generation && !disposed) loading.value = false }
}
/** 引用行 → 图纸库行：id 取附件 id（预览/下载/图纸操作均作用于附件行），图纸列由后端从 sys_attachment 回填。 */
function toDrawingFile(ref: AttachmentRefItem): DrawingFile {
  return {
    id: Number(ref.attachmentId),
    fileName: ref.fileName || '',
    fileSize: ref.fileSize ?? 0,
    fileType: ref.fileType ?? undefined,
    category: ref.category || '',
    documentType: ref.documentType ?? undefined,
    version: ref.version ?? undefined,
    drawingNo: ref.drawingNo ?? undefined,
    drawingName: ref.drawingName ?? undefined,
    fileRole: ref.fileRole ?? undefined,
    isCurrent: ref.isCurrent ?? undefined,
    isControlled: ref.isControlled ?? undefined,
    released: ref.released ?? undefined,
    createBy: ref.createBy ?? undefined,
    createTime: ref.createTime ?? undefined,
    refId: Number(ref.id),
  }
}
watch(() => props.productId, () => { pendingStructureSync.value = undefined; syncError.value = ''; hasLoaded = false; lastLoadedIds = new Set(); highlightedKeys.value = []; if (highlightTimer) clearTimeout(highlightTimer); onlyPending.value = false; showMore.value = false; printPublishing.value = false; view.value = DrawingViewEnum.ALL.value; releaseFilter.value = ''; files.value = []; categoryFilter.value = ''; keyword.value = ''; previewVisible.value = false; loadFiles() }, { immediate: false })
const previewVisible = ref(false)
const previewFile = ref<ProductDrawingFile>()
function preview(file: ProductDrawingFile) { previewFile.value = file; previewVisible.value = true }
async function download(file: ProductDrawingFile) { try { await downloadFile(file) } catch (e) { ElMessage.error(e instanceof Error ? e.message : '下载失败') } }
function timeLabel(value?: string) { return value?.replace('T', ' ').slice(0, 16) || '—' }
function extension(name: string) { return name.split('.').pop()?.toUpperCase() || '' }
function sizeLabel(size = 0) { return size < 1024 * 1024 ? `${(size / 1024).toFixed(0)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB` }
function cancelled(e: unknown) { return e === 'cancel' || e === 'close' }
function isStructure(file: DrawingFile) {
  return file.documentType === 'STRUCTURE_DRAWING' || file.category === '结构图'
}
async function syncStructure(row: RevisionRow, productId: number) {
  const candidates = row.files.filter(file => isStructure(file) && fileKind(file) !== 'other')
  const selected = candidates.find(file => file.fileRole === DrawingFileRoleEnum.PRINT.value) || candidates[0]
  if (!selected) throw new Error('此版本没有可预览的结构图，请补充图片或PDF后重试同步')
  const current = await productWorkSpecApi.get(productId)
  if (props.productId !== productId) return
  if (!current.data || typeof current.data !== 'object') throw new Error('未读取到最新作业规范')
  const spec = normalizeWorkSpec(current.data)
  spec.structureFileId = selected.id
  await productWorkSpecApi.save(productId, spec)
  if (props.productId !== productId) return
  pendingStructureSync.value = undefined
  syncError.value = ''
  ElMessage.success('已设为现行，作业规范结构图已同步')
}
async function retryStructureSync() {
  const pending = pendingStructureSync.value
  if (!pending || mutationId.value || pending.productId !== props.productId) return
  mutationId.value = -1
  try {
    await loadFiles()
    const row = revisionRows.value.find(row => row.drawingNo === pending.drawingNo && row.version === pending.version && row.current)
    if (!row) throw new Error('目标版本已不是现行版本，请重新选择')
    await syncStructure(row, pending.productId)
  } catch (e) {
    syncError.value = `图集现行状态保留，规范结构图同步失败：${e instanceof Error ? e.message : '请重试'}`
    ElMessage.error(syncError.value)
  } finally { mutationId.value = undefined }
}
async function setCurrent(row: RevisionRow) {
  if (mutationId.value || !props.productId) return
  const productId = props.productId
  let switched = false
  try {
    await ElMessageBox.confirm(`将「${row.name}」${row.version}版设为现行？此图纸的其他版本转为非现行，新现行版需重新下发。`, '切换现行版本', { type: 'warning' })
    if (props.productId !== productId) return
    mutationId.value = row.files[0].id
    const result = await attachmentApi.setCurrent(row.files[0].id)
    if (result?.data !== true) throw new Error('现行版本切换失败')
    switched = true
    if (props.productId !== productId) return
    if (row.files.some(isStructure)) {
      pendingStructureSync.value = { productId, drawingNo: row.drawingNo, version: row.version }
      await syncStructure(row, productId)
    } else ElMessage.success('已切换现行版本')
  } catch (e) {
    if (!cancelled(e) && props.productId === productId) {
      const message = e instanceof Error ? e.message : '设置失败'
      if (switched) syncError.value = `现行已切换，作业规范结构图同步失败：${message}。可在该版本行重试同步`
      ElMessage.error(switched ? syncError.value : message)
    }
  } finally {
    if (switched && props.productId === productId) await loadFiles()
    mutationId.value = undefined
  }
}
async function release(row: RevisionRow) {
  try {
    await ElMessageBox.confirm(`${row.hasReleased ? '撤回' : '下发'}「${row.name}」${row.version}版？同版本原稿、打印件一起处理。`, '图纸下发', { type: 'warning' })
    mutationId.value = row.files[0].id
    await attachmentApi.release(row.files[0].id, !row.hasReleased); ElMessage.success(row.hasReleased ? '已撤回' : '已下发'); await loadFiles()
  } catch (e) { if (!cancelled(e)) ElMessage.error(e instanceof Error ? e.message : '操作失败') }
  finally { mutationId.value = undefined }
}
async function remove(file: DrawingFile) {
  try { await ElMessageBox.confirm(`从图纸库移除「${file.fileName}」？仅解除引用，不删除文件。`, '移除文件', { type: 'warning' }); await attachmentRefApi.unbind(file.refId); await loadFiles() }
  catch (e) { if (!cancelled(e)) ElMessage.error(e instanceof Error ? e.message : '移除失败') }
}

type UploadRow = Omit<DrawingMetadata, 'category'> & { uid: number; file: File; documentType: string; status: string; progress: number; error: string; thumb?: string; requestKey?: string }
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadNewRevision = ref(false)
const uploadDrawingNo = ref('')
const uploadDrawingName = ref('')
const uploadLegacyCategory = ref<string>()
const uploadFixedVersion = ref('')
const defaults = reactive({ documentType: '', version: 'A' })
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
watch(documentTypeOptions, (options) => { if (defaults.documentType && !options.some(item => item.value === defaults.documentType)) defaults.documentType = '' }, { immediate: true })
const revisionInput = ref<HTMLInputElement>()
let revisionSelection: { row: DrawingRow; version: string; productId?: number } | undefined
function openUpload(row?: RevisionRow, newRevision = false, documentType?: string, show = true) {
  if (uploading.value) return
  revokeQueue(); queue.value = []; uploadNewRevision.value = newRevision; uploadDrawingNo.value = row?.drawingNo || ''; uploadDrawingName.value = row?.name || ''; uploadLegacyCategory.value = row?.files[0]?.category || undefined
  uploadFixedVersion.value = row && !newRevision ? row.version : ''
  defaults.documentType = row?.documentType || documentType || (categoryFilter.value !== UNCATEGORIZED_FILTER ? categoryFilter.value : '')
  defaults.version = row ? (newRevision ? '' : row.version) : 'A'
  uploadVisible.value = show
}
function chooseNewVersion(row: DrawingRow) {
  if (uploading.value) return
  const version = nextDrawingVersion(row.revisions.map(revision => revision.version))
  if (!version) { openUpload(row, true); return }
  revisionSelection = { row, version, productId: props.productId }
  revisionInput.value?.click()
}
async function uploadNewVersionFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = Array.from(input.files || [])
  input.value = ''
  const context = revisionSelection
  if (!context || context.productId !== props.productId || !selected.length || uploading.value) return
  openUpload(context.row, true, undefined, false)
  defaults.version = context.version; uploadFixedVersion.value = context.version
  selected.forEach((file, i) => addFile({ uid: Date.now() + i, name: file.name, raw: file } as UploadFile))
  await submitUpload()
  if (pendingUploads.value.length) uploadVisible.value = true
}
function resetUploadAttempt(row: UploadRow) {
  if (!uploading.value && row.status !== DrawingUploadStatusEnum.SUCCESS.value) row.requestKey = undefined
}
function regenerateIdentity(row: UploadRow) {
  resetUploadAttempt(row)
  if (uploadDrawingNo.value) return
  Object.assign(row, newDrawingIdentity(props.productCode, props.productName, row.documentType || '',
    [...files.value, ...queue.value.filter(other => other !== row)], documentTypeLabel(row.documentType)))
}
function addFile(upload: UploadFile) {
  if (!upload.raw || uploading.value) return
  if (upload.raw.size > 10 * 1024 * 1024 || !upload.raw.size) { ElMessage.warning(`${upload.name}：文件须非空且不超过10MB`); return }
  // 判重只看文件指纹（同名同大小同修改时间），图纸编号+版本不参与：同编号同版本允许多个文件。
  const fingerprint = fileFingerprint(upload.name, upload.raw.size, upload.raw.lastModified)
  if (queue.value.some(row => fileFingerprint(row.file.name, row.file.size, row.file.lastModified) === fingerprint)) {
    ElMessage.warning(`${upload.name}：与已选文件同名同大小，可能重复，请核对`)
  }
  const kind = fileKind({ fileName: upload.name, fileType: upload.raw.type })
  const thumb = kind === 'image' ? URL.createObjectURL(upload.raw) : ''
  const identity = uploadDrawingNo.value ? { drawingNo: uploadDrawingNo.value, drawingName: uploadDrawingName.value } : newDrawingIdentity(props.productCode, props.productName, defaults.documentType, [...files.value, ...queue.value], documentTypeLabel(defaults.documentType))
  queue.value.push({ uid: upload.uid, file: upload.raw, thumb, documentType: defaults.documentType, ...identity, version: defaults.version,
    fileRole: kind === 'other' ? DrawingFileRoleEnum.ORIGINAL.value : DrawingFileRoleEnum.PRINT.value, status: DrawingUploadStatusEnum.QUEUED.value, progress: 0, error: '' })
}
function locked(row: UploadRow) { return uploading.value || row.status === DrawingUploadStatusEnum.SUCCESS.value }
function applyDefaults() {
  const pending = pendingUploads.value
  const changedType = pending.filter(row => row.documentType !== defaults.documentType)
  const reserved = [...files.value, ...queue.value.filter(row => !changedType.includes(row))]
  for (const row of pending) {
    const version = uploadFixedVersion.value || defaults.version
    if (row.documentType !== defaults.documentType || row.version !== version) resetUploadAttempt(row)
    row.documentType = defaults.documentType; row.version = version
    if (!uploadDrawingNo.value && changedType.includes(row)) {
      Object.assign(row, newDrawingIdentity(props.productCode, props.productName, row.documentType, reserved, documentTypeLabel(row.documentType)))
      reserved.push(row)
    }
  }
}
async function submitUpload() {
  const pending = pendingUploads.value
  if (pending.some(row => !row.drawingNo.trim() || !row.version.trim() || (!!row.documentType && !documentTypeOptions.value.some(item => item.value === row.documentType)))) { ElMessage.warning('请确认资料类型有效并填写版本'); return }
  // 同一图纸编号+版本允许多文件；仅按文件指纹提示“可能重复”，不阻断上传。
  if (duplicateFingerprints(queue.value.map(row => ({ fileName: row.file.name, fileSize: row.file.size, lastModified: row.file.lastModified }))).length) {
    ElMessage.warning('上传清单中有同名同大小的文件，可能重复；仍会继续上传，请核对')
  }
  uploading.value = true
  try {
    for (const row of pending) {
      if (disposed) break
      row.status = DrawingUploadStatusEnum.UPLOADING.value; row.error = ''; row.progress = 0
      try {
        await uploadDrawingFile(row, value => { row.progress = value })
        row.status = DrawingUploadStatusEnum.SUCCESS.value; row.progress = 100
      } catch (e) { row.status = DrawingUploadStatusEnum.FAILED.value; row.error = e instanceof Error ? e.message : '上传失败' }
    }
    if (pending.some(row => row.status === DrawingUploadStatusEnum.SUCCESS.value)) {
      // 上传后回到可见范围，避免新图纸被先前筛选隐藏。
      categoryFilter.value = ''; keyword.value = ''; onlyPending.value = false
      view.value = DrawingViewEnum.ALL.value; releaseFilter.value = ''
      if (!pendingUploads.value.length) { revokeQueue(); uploadVisible.value = false; queue.value = [] }
    }
    await loadFiles()
  } finally { uploading.value = false }
}
/** 16~100 位 [A-Za-z0-9_-] 上传键；同一行重试复用同一键 → 服务端幂等返回原 attachmentId。 */
function requestKeyOf(row: UploadRow): string {
  if (row.requestKey) return row.requestKey
  const raw = `dwg${props.productId || 0}${row.uid}${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`
  row.requestKey = raw.replace(/[^A-Za-z0-9_-]/g, '').slice(0, 100).padEnd(16, '0')
  return row.requestKey
}
/** 二级资料类型下拉：只取 attachment_document_type 且 parent=ENGINEERING_DOC 的启用项。 */
async function loadDocTypes() {
  try {
    const res: any = await attachmentRefApi.documentTypes()
    const list = ((res?.data || []) as AttachmentDocumentTypeItem[])
      .filter(item => item.dict_code === 'attachment_document_type' && item.is_active !== 0 && item.item_key && item.label)
      .filter(item => {
        const ext = typeof item.ext_data === 'string' ? safeParseJson(item.ext_data) : item.ext_data
        const parent = (ext as Record<string, unknown> | null | undefined)?.parent
        return !parent || String(parent).toUpperCase() === 'ENGINEERING_DOC'
      })
      .map(item => ({ value: item.item_key, label: item.label }))
    docTypes.value = list
  } catch { docTypes.value = [] }
}
function safeParseJson(value: string): Record<string, unknown> | null {
  try { const parsed = JSON.parse(value); return parsed && typeof parsed === 'object' ? parsed as Record<string, unknown> : null } catch { return null }
}
/** 公共链路写入：TEMP 上传（带 drawingMeta 写 sys_attachment 图纸列）→ 建产品引用（必须带 product_id）。 */
async function uploadDrawingFile(row: UploadRow, onProgress: (percent: number) => void) {
  if (!props.productId) throw new Error('缺少产品标识，无法上传图纸')
  const productId = props.productId
  const productCode = props.productCode
  const drawingMeta: AttachmentDrawingMetadata = {
    // 仅旧编号的新版本继承服务器已有类别；新图纸不写旧图种、不提供旧图种输入。
    ...(uploadLegacyCategory.value ? { category: uploadLegacyCategory.value } : {}),
    drawingNo: row.drawingNo.trim(),
    drawingName: row.drawingName?.trim() || undefined,
    version: row.version.trim(),
    fileRole: row.fileRole,
  }
  const res: any = await attachmentRefApi.tempUpload(row.file, requestKeyOf(row), onProgress, drawingMeta)
  const attachmentId = Number(res?.data)
  if (!attachmentId) throw new Error(res?.msg || '临时上传失败')
  if (props.productId !== productId) throw new Error('产品已切换，请重新选择上传文件')
  await attachmentRefApi.createRef({
    attachmentId,
    sourceType: 'product',
    sourceId: productId,
    productId,
    productCode,
    documentType: row.documentType || null,
  })
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
const metadataFile = ref<DrawingFile>()
const metadataTypeOnly = ref(false)
const metadataError = ref('')
interface MetadataForm extends DrawingMetadata { documentType: string }
const metadata = reactive<MetadataForm>({ drawingNo: '', drawingName: '', version: '', documentType: '', fileRole: DrawingFileRoleEnum.PRINT.value })
function openMetadata(file: DrawingFile) {
  metadataFile.value = file
  metadataTypeOnly.value = !file.documentType
  metadataError.value = ''
  Object.assign(metadata, {
    documentType: file.documentType || '',
    drawingNo: file.drawingNo || newDrawingIdentity(props.productCode, props.productName, file.documentType || documentTypeOptions.value[0]?.value || '', files.value).drawingNo,
    drawingName: file.drawingName || newDrawingIdentity(props.productCode, props.productName, file.documentType || documentTypeOptions.value[0]?.value || '', files.value).drawingName,
    version: file.version || 'A',
    fileRole: file.fileRole || (fileKind(file) === 'other' ? DrawingFileRoleEnum.ORIGINAL.value : DrawingFileRoleEnum.PRINT.value),
  })
  metadataVisible.value = true
}
function regenerateMetadataIdentity() {
  if (!metadataFile.value || metadataFile.value.drawingNo) return
  const identity = newDrawingIdentity(props.productCode, props.productName, metadata.documentType, files.value, documentTypeLabel(metadata.documentType))
  metadata.drawingNo = identity.drawingNo
  if (!metadataFile.value.drawingName) metadata.drawingName = identity.drawingName
}
async function saveMetadata() {
  if (metadataSaving.value) return
  const file = metadataFile.value
  if (!file || !documentTypeOptions.value.some(item => item.value === metadata.documentType)) { ElMessage.warning('请选择资料类型'); return }
  if (!metadataTypeOnly.value && file.drawingNo && !metadata.version.trim()) { ElMessage.warning('请填写版本'); return }
  regenerateMetadataIdentity()
  metadataSaving.value = true
  metadataError.value = ''
  let drawingSaved = false
  try {
    // 只补类型不写已有图纸字段；待归集时才自动生成身份，保留已有版本与用途。
    if (!metadataTypeOnly.value || !file.drawingNo) {
      const result = await attachmentApi.updateDrawingMetadata(file.id, {
        drawingNo: file.drawingNo || metadata.drawingNo.trim(),
        drawingName: file.drawingNo ? file.drawingName : (file.drawingName || metadata.drawingName),
        version: metadataTypeOnly.value || !file.drawingNo ? (file.version || 'A') : metadata.version.trim(),
        fileRole: metadataTypeOnly.value || !file.drawingNo ? (file.fileRole || metadata.fileRole) : metadata.fileRole,
      })
      if (result.data === false) throw new Error('图纸关联保存失败')
      drawingSaved = true
    }
    if (metadata.documentType !== (file.documentType || '')) {
      const result = await attachmentRefApi.updateMeta(file.refId, { documentType: metadata.documentType })
      if (result.data === false) throw new Error('资料类型保存失败')
    }
    metadataVisible.value = false
    ElMessage.success('图纸关联已保存')
    await loadFiles()
  }
  catch (e) {
    const message = e instanceof Error ? e.message : '保存失败'
    metadataError.value = drawingSaved ? `图纸关联已保存，资料类型保存失败：${message}。请重试保存。` : message
    ElMessage.error(metadataError.value)
    await loadFiles()
    const latest = files.value.find(item => item.refId === file.refId)
    if (latest) metadataFile.value = latest
  }
  finally { metadataSaving.value = false }
}
watch(() => uploadVisible.value || metadataVisible.value || uploading.value || metadataSaving.value || !!mutationId.value, value => emit('busy', value))
watch(() => props.active, value => { if (value) loadFiles() })
loadFiles()
loadDocTypes()
onBeforeUnmount(() => { disposed = true; generation++; if (highlightTimer) clearTimeout(highlightTimer); revokeQueue() })
</script>

<style scoped>
.revision-timeline { padding:20px 24px; }.revision-heading { display:flex; flex-wrap:wrap; align-items:center; gap:10px; }
.drawing-table :deep(.uploaded-drawing > td.el-table__cell) { background:#e8f5e9; transition:background-color .3s; }

.library-toolbar { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:20px; }.library-heading { display:flex; align-items:center; gap:12px; }.library-heading strong { font-size:15px; }.library-heading span,.library-note { font-size:12px; color:#909baa; }.library-alert { margin-bottom:15px; }
.filters { display:flex; flex-wrap:wrap; gap:10px; margin-bottom:8px; }.drawing-title { font-size:13px; font-weight:500; color:#35445a; }.drawing-sub { margin-top:4px; font-size:11px; color:#98a2af; }.status-tags { display:flex; flex-wrap:wrap; gap:5px; }.file-link { display:flex; align-items:center; gap:8px; margin:4px 0; }.file-link span { color:#99a2ae; font-size:10px; }.row-actions { display:flex; flex-wrap:wrap; gap:8px; }.row-actions :deep(.el-button+.el-button) { margin-left:0; }.revision-files { padding:12px 26px; background:#f8fafc; }.revision-file { display:flex; align-items:center; gap:12px; padding:9px 0; }.revision-file :deep(.el-link) { flex:1; justify-content:flex-start; }.file-info { color:#8a96a6; font-size:11px; }
.upload-product { display:flex; gap:14px; align-items:center; background:#f2f6fc; border-radius:6px; padding:14px 18px; margin-bottom:18px; }.upload-product span { color:#77869b; font-size:13px; }.upload-defaults { display:flex; align-items:center; gap:12px; margin-bottom:14px; }.upload-defaults > span { color:#8895a5; font-size:12px; }.upload-queue { margin-top:18px; }.queue-thumb { width:42px; height:42px; border-radius:4px; border:1px solid #e3e8ee; background:#fff; cursor:zoom-in; }.queue-error { color:#d95757; font-size:11px; margin-top:5px; }.upload-summary { float:left; color:#8896a8; font-size:12px; padding-top:10px; }.metadata-file { margin:0 0 20px; padding:12px; background:#f6f8fb; color:#69778a; }.metadata-type { display:flex; align-items:center; gap:10px; width:100%; }
</style>

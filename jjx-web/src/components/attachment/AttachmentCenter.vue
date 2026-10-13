<template>
  <div class="attachment-center">
    <div class="ac-header">
      <span class="ac-title">
        <el-icon><FolderOpened /></el-icon>
        {{ title }}
      </span>
      <el-tag v-if="refRows.length" size="small" type="info">{{ refRows.length }} 条引用</el-tag>
      <el-tag v-if="pendingCount" size="small" type="warning">待绑定 {{ pendingCount }}</el-tag>
      <span class="ac-spacer" />
      <AttachmentTempUploader
        v-if="canUpload && !dropZone"
        v-bind="uploaderProps"
        @files="onFiles"
        @error="onUploaderError"
      />
    </div>

    <el-alert
      v-if="!draftReady"
      title="当前没有草稿标识（draftId）：新建单据场景必须传入唯一 draftId 才能上传附件，避免多个新建单据共用同一个待绑定队列。"
      type="warning"
      :closable="false"
      class="ac-alert"
    />

    <el-alert v-if="error" :title="error" type="warning" :closable="false" class="ac-alert" />
    <el-alert
      v-if="duplicateTip"
      :title="duplicateTip"
      type="info"
      :closable="true"
      class="ac-alert"
      @close="duplicateTip = ''"
    />

    <!-- 拖拽落区（dropZone 启用时替代 header 按钮；落区本身可点击选文件） -->
    <AttachmentTempUploader
      v-if="canUpload && dropZone"
      v-bind="uploaderProps"
      class="ac-dropzone"
      @files="onFiles"
      @error="onUploaderError"
    />

    <!-- 待绑定（TEMP）队列：仅当前单据草稿可见，刷新后可恢复 id 清单 -->
    <div v-if="pending.length" class="ac-pending">
      <div v-for="entry in pending" :key="entry.attachmentId" class="ac-row">
        <el-icon><Clock /></el-icon>
        <span v-if="pendingHas('fileName')" class="ac-name">{{ renderPending(entry, 'fileName') }}</span>
        <span v-if="pendingHas('fileSize')" class="ac-sub">{{ renderPending(entry, 'fileSize') }}</span>
        <el-tag size="small" :type="entry.state === 'failed' ? 'danger' : 'warning'">
          {{ entry.state === 'failed' ? '绑定失败' : '待绑定' }}
        </el-tag>
        <span v-if="entry.error" class="ac-error">{{ entry.error }}</span>
        <span class="ac-spacer" />
        <AttachmentTypeSelect
          v-if="canEdit && pendingHas('documentType')"
          :model-value="entry.documentType"
          :category="entryCategoryKey(entry)"
          :document-types="documentTypes"
          :source-type="sourceType"
          show-category
          @update:category="(v) => setEntryCategory(entry, v)"
          @update:model-value="
            (v) => {
              entry.documentType = v
              persist()
            }
          "
        />
        <el-button v-if="canEdit" link type="primary" @click="retryBind(entry)">重试绑定</el-button>
        <el-button v-if="canEdit" link type="danger" @click="removePending(entry.attachmentId)"
          >移除</el-button
        >
      </div>
      <div class="ac-hint">待绑定附件已上传到服务器（7 天有效），保存单据后自动建立引用。</div>
    </div>

    <!-- A 段：引用（主键 refId，不同 refId 全部保留）。列表抽到 AttachmentRefList，variant 切样式（table 新 / list 旧） -->
    <el-empty
      v-if="
        showList &&
        !loading &&
        !traceLoading &&
        !refRows.length &&
        !legacyOnlyRows.length &&
        !traceOnlyRows.length
      "
      description="暂无附件"
      :image-size="60"
    />
    <AttachmentRefList
      v-else-if="showList && refRows.length"
      :rows="refRows"
      :can-edit="canEdit"
      :variant="variant"
      :document-types="documentTypes"
      :source-type="sourceType"
      @preview="previewRef"
      @download="onRowDownload"
      @edit="openMeta"
      @unbind="onUnbind"
      @changed="onRefListChanged"
      @error="onRefListError"
    />

    <!-- B 段：历史附件 / 同链路来源单据（只读）→ 统一由只读列表组件渲染，字段来自 attachmentFieldSets -->
    <AttachmentReadonlyList
      v-if="showList && legacyOnlyRows.length"
      title="历史附件（未建立引用，只读）"
      key-prefix="att"
      :rows="legacyOnlyRows"
      :field-keys="attachmentFieldSets.legacy"
      :tag-of="() => '历史附件'"
      @download="(row) => openUrl(attachmentApi.downloadUrl(row.id))"
      @preview="(row) => previewReadOnly(row, legacyOnlyRows)"
    />
    <AttachmentReadonlyList
      v-if="showList && (traceLoading || traceError || traceOnlyRows.length)"
      title="同链路来源单据（只读）"
      key-prefix="trace"
      :rows="traceOnlyRows"
      :loading="traceLoading"
      loading-text="正在加载同链路文档…"
      :error="traceError"
      :field-keys="attachmentFieldSets.trace"
      :tag-of="(r) => r.bizTypeName || r.bizType || '来源单据'"
      :sub-of="(r) => r.sourceNo || (r.bizId ? `#${r.bizId}` : '')"
      @download="(row) => openUrl(attachmentApi.downloadUrl(row.id))"
      @preview="(row) => previewReadOnly(row, traceOnlyRows)"
    />

    <!-- 更多信息（类型/展示名已改成列表行内编辑，这里只留备注/排序） -->
    <el-dialog v-model="metaVisible" title="更多信息" width="460px" append-to-body destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="备注">
          <el-input v-model="metaForm.remark" maxlength="500" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="metaForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metaVisible = false">取消</el-button>
        <el-button type="primary" :loading="metaSaving" @click="saveMeta">保存</el-button>
      </template>
    </el-dialog>

    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerList"
      :initial-index="viewerIndex"
      @close="closeViewer()"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 公共附件中心（V1-B WP2）
 * - 唯一入口：上传（TEMP）→ 绑定（refs）→ 列表/预览/下载/解绑/元数据/重复提示
 * - A 段按 refId；B 段历史只读；traceId 存在时补同链路只读段，按 attachmentId 排除 A/B 已有附件
 * - readonly / locked 只是能力位，不定义业务规则；后端始终是权限最终裁决方
 * - 已知限制：GET /refs 不返回文件元数据，A 段文件名/大小靠本单据的 legacy 列表回填，
 *   取不到时展示「附件 #id」（建议后端后续在 refs 列表补 file_name/file_size）
 */
import { computed, onBeforeUnmount, reactive, ref, toRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderOpened, Document, Download, View, Clock } from '@element-plus/icons-vue'
import { attachmentApi } from '@/api/system/attachment'
import {
  attachmentRefApi,
  type AttachmentDocumentTypeItem,
  type AttachmentRefItem,
} from '@/api/system/attachmentRef'
import AttachmentTempUploader from './AttachmentTempUploader.vue'
import AttachmentRefList from './AttachmentRefList.vue'
import AttachmentReadonlyList from './AttachmentReadonlyList.vue'
import { useAttachmentDocTypes } from './useAttachmentDocTypes'
import AttachmentTypeSelect from './AttachmentTypeSelect.vue'
import { attachmentFieldSets, renderAttachmentField, type AttachmentFieldKey } from './attachmentFields'
import { useAttachmentPreview } from './useAttachmentPreview'
import { useAttachmentRefs, type PendingEntry } from '@/composables/useAttachmentRefs'
const props = withDefaults(
  defineProps<{
    sourceType: string
    sourceId?: number | null
    sourceNo?: string
    /** 新建单据草稿标识：同一张未保存单据复用同一值（隔离待绑定队列） */
    draftId?: string
    title?: string
    mode?: 'panel' | 'dialog' | 'upload-only' | 'readonly'
    readonly?: boolean
    locked?: boolean
    viewPerm?: string
    editPerm?: string
    traceId?: string
    maxSizeMB?: number
    accept?: string[]
    /** sourceId 有值时是否上传后立即绑定 */
    autoBind?: boolean
    /** 上传入口改为拖拽落区（替代 header 的小按钮）；默认 false 保持按钮形态 */
    dropZone?: boolean
    /** 引用列表样式：'table'（默认，标题行+勾选+批量）| 'list'（旧：一行行+行内按钮）；两版都在 AttachmentRefList 里 */
    variant?: 'table' | 'list'
    /** 单据产品（单产品单据传值，多产品单据不传）；写引用时随 product_id 落库 */
    productId?: number | null
    /** 上传禁用原因（调用方按业务决定，非空即禁用并显示这句话）；组件本身不做业务判断 */
    uploadBlocked?: string
  }>(),
  {
    sourceId: null,
    sourceNo: '',
    draftId: '',
    title: '附件',
    mode: 'panel',
    readonly: false,
    locked: false,
    viewPerm: '',
    editPerm: '',
    traceId: '',
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
    autoBind: true,
    dropZone: false,
    variant: 'table',
    productId: null,
    uploadBlocked: '',
  }
)

const emit = defineEmits<{
  uploaded: [payload: { attachmentId: number; temp: boolean }]
  bound: [payload: { refId: number; attachmentId: number }]
  unbound: [payload: { refId: number; attachmentId: number }]
  changed: [payload: { count: number }]
  duplicate: [payload: { attachmentId: number; sha256: string; matches: number }]
  error: [payload: { action: string; message: string }]
}>()

const refs = useAttachmentRefs({
  sourceType: toRef(props, 'sourceType'),
  sourceId: toRef(props, 'sourceId'),
  draftId: toRef(props, 'draftId'),
  traceId: toRef(props, 'traceId'),
  readonly: toRef(props, 'readonly'),
  locked: toRef(props, 'locked'),
  viewPerm: toRef(props, 'viewPerm'),
  editPerm: toRef(props, 'editPerm'),
  productId: toRef(props, 'productId'),
})

const {
  refRows,
  legacyOnlyRows,
  documentTypes,
  pending,
  pendingCount,
  draftReady,
  loading,
  error,
  canView,
  canEdit,
  load,
  loadDocumentTypes,
  uploadTemp,
  bindPending,
  bindPendingOne,
  unbind,
  updateMeta,
  checkDuplicate,
  removePending,
  persist,
} = refs

// by-trace 返回 AttachmentSourceVO[]：id 为附件 ID（不是 refId）。
interface TraceAttachment {
  id: number
  fileName?: string
  fileSize?: number
  fileType?: string
  createTime?: string
  bizType?: string
  bizTypeName?: string
  bizId?: number
  sourceNo?: string
}
const traceRows = ref<TraceAttachment[]>([])
const traceLoading = ref(false)
const traceError = ref('')
let traceRequest = 0

const traceOnlyRows = computed(() => {
  const seen = new Set([
    ...refRows.value.map((row) => Number(row.attachmentId)),
    ...legacyOnlyRows.value.map((row) => Number(row.id)),
  ])
  return traceRows.value.filter((row) => {
    const id = Number(row.id)
    if (seen.has(id)) return false
    seen.add(id)
    return true
  })
})

async function loadTrace() {
  const request = ++traceRequest
  traceRows.value = []
  traceError.value = ''
  traceLoading.value = false
  if (!canView.value || !props.traceId) return
  traceLoading.value = true
  try {
    const res: any = await attachmentApi.listByTrace(props.traceId)
    if (request !== traceRequest) return // 切换单据/链路后，旧请求不能回填。
    if (!Array.isArray(res?.data)) throw new Error('同链路文档返回格式异常')
    traceRows.value = res.data
  } catch (e: any) {
    if (request !== traceRequest) return
    traceError.value = e?.message || '同链路文档加载失败'
    emit('error', { action: 'trace', message: traceError.value })
  } finally {
    if (request === traceRequest) traceLoading.value = false
  }
}

watch(
  () => [props.sourceType, props.sourceId, props.traceId, canView.value],
  () => loadTrace(),
  { immediate: true }
)
watch(
  canView,
  (allowed) => {
    if (allowed) loadDocumentTypes()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  traceRequest++
})

const duplicateTip = ref('')
const uploadTip = computed(
  () =>
    `单个文件不超过 ${props.maxSizeMB}MB${props.accept.length ? '；支持 ' + props.accept.join(' ') : ''}`
)

// ==================== 上传入口（按钮 / 拖拽落区共用同一组 props 与回调） ====================
/** readonly 或无权编辑时不渲染上传入口 */
const canUpload = computed(() => props.mode !== 'readonly' && canEdit.value)
/**
 * 是否渲染列表段（A 段引用 / B 段历史 / 同链路段）。
 * `mode='upload-only'` 语义是"只给上传入口"（报审核、生成计划、产品资料视图都只是要个上传按钮），
 * 所以该模式下只保留上传入口与待绑定队列，不渲染任何列表，避免外面再套一层视图时出现"两个列表"。
 */
const showList = computed(() => props.mode !== 'upload-only')
/**
 * 上传禁用原因（唯一一条提示路径）：先看草稿标识是否就绪，再看调用方按业务传的 uploadBlocked。
 * 两者取第一个非空；组件不解释业务原因，只负责"禁用 + 显示这句话"。
 */
const uploadBlockedReason = computed(() => {
  if (!draftReady.value) return '请先保存单据或提供 draftId 后再上传（避免多个单据共用待绑定队列）'
  return props.uploadBlocked || ''
})
const uploaderProps = computed(() => ({
  multiple: true,
  disabled: !!uploadBlockedReason.value,
  maxSizeMB: props.maxSizeMB,
  accept: props.accept,
  buttonText: props.mode === 'upload-only' ? '上传附件' : '上传（通用附件）',
  tip: uploadBlockedReason.value || uploadTip.value,
  dropZone: props.dropZone,
}))
function onUploaderError(message: string) {
  emit('error', { action: 'validate', message })
}

// ==================== 资料类型字典派生（与 AttachmentRefList 共用 composable，避免两处各写一份）====================
const { categoryKeyOfType } = useAttachmentDocTypes(documentTypes, () => props.sourceType)

/** 待绑定行的一级选择（仅前端筛选，不落库）；未手动选时由已选二级的 parent 反推，刷新后仍一致 */
const entryCategoryMap = ref<Record<number, string>>({})
function entryCategoryKey(entry: PendingEntry): string {
  return entryCategoryMap.value[entry.attachmentId] || categoryKeyOfType(entry.documentType)
}
/** 切一级：当已选二级不属于新一级时清掉二级，避免一级/二级自相矛盾 */
function setEntryCategory(entry: PendingEntry, categoryKey: string | null) {
  if (categoryKey) entryCategoryMap.value[entry.attachmentId] = categoryKey
  else delete entryCategoryMap.value[entry.attachmentId]
  if (entry.documentType && categoryKeyOfType(entry.documentType) !== (categoryKey || '')) {
    entry.documentType = null
  }
  persist()
}

// ==================== 上传 / 绑定 ====================
async function onFiles(files: File[]) {
  for (const file of files) {
    try {
      const attachmentId = await uploadTemp(file)
      emit('uploaded', { attachmentId, temp: true })
      if (props.autoBind && props.sourceId) {
        const entry = pending.value.find((e) => e.attachmentId === attachmentId) as PendingEntry
        const refId = await bindPendingOne(entry)
        emit('bound', { refId, attachmentId })
        await afterBound(entry)
      } else {
        ElMessage.success('已上传，保存单据后自动建立引用')
      }
      emit('changed', { count: pendingCount.value })
    } catch (e: any) {
      const message = e?.message || '附件上传失败'
      ElMessage.error(message)
      emit('error', { action: 'upload', message })
    }
  }
}

async function retryBind(entry: PendingEntry) {
  try {
    const refId = await bindPendingOne(entry, {
      documentType: entry.documentType ?? null,
      displayName: entry.displayName ?? null,
    })
    emit('bound', { refId, attachmentId: entry.attachmentId })
    await afterBound(entry)
  } catch (e: any) {
    const message = e?.message || '绑定失败'
    ElMessage.error(message)
    emit('error', { action: 'bind', message })
  }
}

async function afterBound(entry: PendingEntry) {
  await load()
  emit('changed', { count: refRows.value.length })
  await loadDocumentTypes()
  const matches = await checkDuplicate(entry.sha256)
  const others = matches.filter((m) => Number(m.attachmentId) !== entry.attachmentId)
  if (others.length) {
    duplicateTip.value = `本单据内已有 ${others.length} 份相同内容的附件（仅提示，不合并文件）`
    emit('duplicate', {
      attachmentId: entry.attachmentId,
      sha256: entry.sha256 || '',
      matches: others.length,
    })
  }
}

// ==================== 列表组件回调（批量动作与勾选已抽到 AttachmentRefList）====================
/** 批量动作已落库 → 重新拉列表并通知外层 */
async function onRefListChanged() {
  await load()
  emit('changed', { count: refRows.value.length })
}
function onRowDownload(row: AttachmentRefItem) {
  openUrl(downloadUrlByRef(row.id))
}
function onRefListError(payload: { action: string; message: string }) {
  emit('error', payload)
}

// ==================== 解绑 / 元数据 ====================
async function onUnbind(row: AttachmentRefItem) {
  try {
    await ElMessageBox.confirm(
      '解除本单据对该附件的引用？仅解除引用，物理文件与其他单据的引用保留。',
      '解除引用',
      {
        confirmButtonText: '确定解除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    await unbind(row.id)
    ElMessage.success('已解除引用')
    emit('unbound', { refId: row.id, attachmentId: row.attachmentId })
    emit('changed', { count: refRows.value.length })
  } catch (e: any) {
    if (e === 'cancel') return
    const message = e?.message || '解除引用失败'
    ElMessage.error(message)
    emit('error', { action: 'unbind', message })
  }
}

const metaVisible = ref(false)
const metaSaving = ref(false)
const metaForm = reactive({
  refId: 0,
  documentType: '' as string | null,
  displayName: '',
  remark: '',
  sortOrder: 0,
})

function openMeta(row: AttachmentRefItem) {
  metaForm.refId = row.id
  // 类型/展示名已改为列表行内编辑；这里仍带原值，避免单条 PUT（整条覆盖）把这两项清空
  metaForm.documentType = row.documentType || ''
  metaForm.displayName = row.displayName || ''
  metaForm.remark = row.remark || ''
  metaForm.sortOrder = row.sortOrder || 0
  metaVisible.value = true
  loadDocumentTypes()
}

async function saveMeta() {
  metaSaving.value = true
  try {
    await updateMeta(metaForm.refId, {
      documentType: metaForm.documentType || null,
      displayName: metaForm.displayName || null,
      remark: metaForm.remark || null,
      sortOrder: metaForm.sortOrder || 0,
    })
    ElMessage.success('已保存')
    metaVisible.value = false
  } catch (e: any) {
    const message = e?.message || '保存失败'
    ElMessage.error(message)
    emit('error', { action: 'meta', message })
  } finally {
    metaSaving.value = false
  }
}

// ==================== 预览 / 下载（唯一实现：useAttachmentPreview） ====================
const {
  viewerVisible,
  viewerList,
  viewerIndex,
  preview: previewViewer,
  openUrl,
  close: closeViewer,
} = useAttachmentPreview()
function previewRef(row: AttachmentRefItem) {
  previewViewer(refRows.value, row, (r) => attachmentRefApi.previewUrlByRef(r.id))
}
function downloadUrlByRef(refId: number) {
  return attachmentRefApi.downloadUrlByRef(refId)
}
function previewReadOnly(row: TraceAttachment, rows: TraceAttachment[]) {
  previewViewer(rows, row, (r) => attachmentApi.downloadUrl(r.id))
}

// ==================== 待绑定段消费字段清单（dev-20261013-035） ====================
const pendingFields: readonly AttachmentFieldKey[] = attachmentFieldSets.pending
function pendingHas(key: AttachmentFieldKey): boolean {
  return pendingFields.includes(key)
}
function renderPending(entry: PendingEntry, key: AttachmentFieldKey): string {
  return renderAttachmentField(key, {
    id: entry.attachmentId,
    fileName: entry.fileName,
    fileSize: entry.fileSize,
    createTime: entry.createdAt,
  })
}

defineExpose({
  refresh: () => Promise.all([load(), loadTrace()]),
  bindPending,
  pendingCount,
  pendingEntries: pending,
})
</script>

<style scoped>
.attachment-center {
  padding: 4px 0;
}
.ac-header,
.ac-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ac-header {
  margin-bottom: 8px;
}
.ac-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
.ac-spacer {
  flex: 1;
}
.ac-alert {
  margin-bottom: 8px;
}
.ac-dropzone {
  display: block;
  margin-bottom: 8px;
}
.ac-pending,
.ac-legacy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ac-row {
  padding: 6px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
  font-size: 13px;
}
.ac-name {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ac-sub {
  color: #909399;
  font-size: 11px;
}
.ac-error {
  color: #f56c6c;
  font-size: 11px;
}
.ac-icon {
  color: #409eff;
  font-size: 18px;
}
.ac-legacy {
  margin-top: 12px;
}
.ac-legacy-title {
  font-size: 12px;
  color: #909399;
}
.ac-hint {
  font-size: 11px;
  color: #909399;
}
</style>

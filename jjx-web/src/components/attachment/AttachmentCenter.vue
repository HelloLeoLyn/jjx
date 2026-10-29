<template>
  <div class="attachment-center">
    <!-- 01. 标题与上传按钮 -->
    <div class="ac-header">
      <span v-if="!compactMode" class="ac-title">
        <el-icon><FolderOpened /></el-icon>
        {{ title }}
      </span>
      <el-tag v-if="displayRefRows.length" size="small" type="info"
        >{{ displayRefRows.length }} 条引用</el-tag
      >
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
    <!-- 待绑定、引用和历史只读附件共用一张表 -->
    <!-- 04. 附件列表 -->
    <el-empty
      v-if="
        showList &&
        !loading &&
        !traceLoading &&
        !refRows.length &&
        !legacyTraceDocRows.length &&
        !pending.length
      "
      description="暂无附件"
      :image-size="60"
    />
    <AttachmentRefList
      v-if="pending.length || (showRefs && displayRefRows.length)"
      :pending="pending"
      :rows="showRefs ? displayRefRows : []"
      :can-edit="canEdit"
      :readonly-type="compactMode && !editableDocumentType"
      :document-types="selectableTypes"
      :source-type="sourceType"
      :source-no="sourceNo"
      :lock-reason="lockReason"
      :actions="compactMode ? COMPACT_ACTIONS : actions"
      @save="emit('save')"
      @retry-bind="retryBind"
      @remove-pending="removePending"
      @pending-changed="persist"
      @preview="previewRef"
      @download="onRowDownload"
      @edit="openMeta"
      @unbind="onUnbind"
      @changed="onRefListChanged"
      @error="onRefListError"
    />
    <!-- 历史 / 同链路只读段：由只读件承接（不并进可写表） -->
    <AttachmentDocList
      v-if="showList && legacyTraceDocRows.length"
      :rows="legacyTraceDocRows"
      title="历史与同链路附件"
    />
    <div v-if="pending.length" class="ac-hint">
      待绑定附件已上传到服务器（7 天有效），保存单据后自动建立引用。
    </div>
    <div v-if="showList && traceLoading" class="ac-hint">正在加载同链路文档…</div>
    <el-alert
      v-if="showList && traceError"
      :title="traceError"
      type="warning"
      :closable="false"
      class="ac-alert"
    />
    <!-- 附件详情（类型/展示名已改成列表行内编辑，这里给只读归属信息 + 备注/排序） -->
    <!-- 05. 附件详情弹窗 -->
    <el-dialog v-model="metaVisible" title="附件详情" width="520px" append-to-body destroy-on-close>
      <AttachmentDetailInfo
        :row="metaRow"
        :ref-id="metaRow?.id"
        :visible="metaVisible"
        :document-types="selectableTypes"
        :source-type="props.sourceType"
      />
      <el-form label-width="80px" :disabled="!canEdit">
        <el-form-item label="备注">
          <el-input v-model="metaForm.remark" maxlength="500" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="metaForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metaVisible = false">取消</el-button>
        <el-button v-if="canEdit" type="primary" :loading="metaSaving" @click="saveMeta"
          >保存</el-button
        >
      </template>
    </el-dialog>
    <!-- 06. 图片预览 -->
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
import { FolderOpened } from '@element-plus/icons-vue'
import { attachmentApi, type AttachmentSourceItem } from '@/api/system/attachment'
import {
  attachmentRefApi,
  type AttachmentDocumentTypeItem,
  type AttachmentDrawingMetadata,
  type AttachmentRefItem,
} from '@/api/system/attachmentRef'
import AttachmentTempUploader from './AttachmentTempUploader.vue'
import AttachmentRefList from './AttachmentRefList.vue'
import type { AttachmentAction } from './attachmentActions'
import AttachmentDocList from './AttachmentDocList.vue'
import AttachmentDetailInfo from './AttachmentDetailInfo.vue'
import { attachmentDocFromFile, type AttachmentDocRow } from './useAttachmentDocList'
import { AttachmentDocumentStatusEnum } from '@/enums/system/AttachmentEnum'
import { useAttachmentPreview } from './useAttachmentPreview'
import { useAttachmentRefs, type PendingEntry } from '@/composables/useAttachmentRefs'
// ============ 01. 组件入参 Props ============
const props = withDefaults(
  defineProps<{
    sourceType: string
    sourceId?: number | null
    sourceNo?: string
    /** 新建单据草稿标识：同一张未保存单据复用同一值（隔离待绑定队列） */
    draftId?: string
    title?: string
    /** 形态（第一阶段⑥，dev-20261016-007）：panel / dialog / upload-only。
     *  与「动作清单」(actions) 分离——variant 定形态、actions 定可做动作。 */
    variant?: 'panel' | 'dialog' | 'upload-only' | 'upload-compact'
    /** @deprecated 兼容别名，请改用 variant（两者同传时 variant 优先） */
    mode?: 'panel' | 'dialog' | 'upload-only' | 'upload-compact'
    readonly?: boolean
    locked?: boolean
    viewPerm?: string
    editPerm?: string
    traceId?: string
    /** 同次操作的证据上传、绑定与业务确认共用，未传时保持逐请求分组。 */
    operationGroupId?: string
    maxSizeMB?: number
    accept?: string[]
    /** sourceId 有值时是否上传后立即绑定 */
    autoBind?: boolean
    /** 新上传待绑定项的默认二级类型；调用方仍可编辑。 */
    defaultDocumentType?: string
    /** 可选：本入口只允许这些二级资料类型（不传=按字典与来源过滤后的全部）。 */
    allowedDocumentTypes?: string[]
    /** 上传入口改为拖拽落区（替代 header 的小按钮）；默认 false 保持按钮形态 */
    dropZone?: boolean
    /** 单据产品（单产品单据传值，多产品单据不传）；写引用时随 product_id 落库 */
    productId?: number | null
    /** 行级附件归属：产品编码（内部字段，S5 dev-20261013-043）；为空=不归属产品 */
    productCode?: string | null
    /** 可选图纸元数据（dev-20261014-029，019 方案B）：非空时随上传写入 sys_attachment 图纸列；
     *  不传=不写这些列，行为不变。仅上传用，组件不渲染任何额外 UI（消费者接入属下游任务）。 */
    drawingMeta?: AttachmentDrawingMetadata | null
    /** 上传禁用原因（调用方按业务决定，非空即禁用并显示这句话）；组件本身不做业务判断 */
    uploadBlocked?: string
    /** upload-compact 专用：只展示这些引用（本次会话上传的证据）；不传=展示全部 */
    refIdsFilter?: number[]
    /** 动作清单（第一阶段①）：透传给可写表，决定操作列显示哪些动作；**不传 = 现状**（dev-20261016-002） */
    actions?: AttachmentAction[]
    /** 业务锁定原因（如"已审核，不可再改"）：有值时写操作按钮置灰并显示原因（dev-20261015-028） */
    lockReason?: string
    /** 证据区（upload-compact）是否允许改资料类型；默认 false 保持只读类型（dev-20261020-003） */
    editableDocumentType?: boolean
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
    defaultDocumentType: '',
    allowedDocumentTypes: undefined,
    dropZone: false,
    productId: null,
    productCode: null,
    drawingMeta: null,
    uploadBlocked: '',
    refIdsFilter: () => [],
    editableDocumentType: false,
  }
)
// ============ 02. 对外事件 Emits ============
const emit = defineEmits<{
  uploaded: [payload: { attachmentId: number; temp: boolean; name?: string }]
  bound: [payload: { refId: number; attachmentId: number }]
  unbound: [payload: { refId: number; attachmentId: number }]
  changed: [payload: { count: number }]
  duplicate: [payload: { attachmentId: number; sha256: string; matches: number }]
  /** 「保存」动作透传（含义由调用方定，dev-20261016-005） */
  save: []
  error: [payload: { action: string; message: string }]
}>()
/** 形态取值：variant 优先，mode 为兼容别名（dev-20261016-007） */
// ============ 03. 组件形态与组合式逻辑 ============
const centerVariant = computed(() => props.variant ?? props.mode ?? 'panel')
// ============ 04. 附件引用状态与操作 ============
const refs = useAttachmentRefs({
  sourceType: toRef(props, 'sourceType'),
  sourceId: toRef(props, 'sourceId'),
  draftId: toRef(props, 'draftId'),
  traceId: toRef(props, 'traceId'),
  operationGroupId: toRef(props, 'operationGroupId'),
  readonly: toRef(props, 'readonly'),
  locked: toRef(props, 'locked'),
  viewPerm: toRef(props, 'viewPerm'),
  editPerm: toRef(props, 'editPerm'),
  productId: toRef(props, 'productId'),
  productCode: toRef(props, 'productCode'),
  drawingMeta: toRef(props, 'drawingMeta'),
})
/** 本入口可选的二级资料类型：调用方白名单收敛；不传=字典+来源过滤后的全部（dev-20261029-005）
 *  注意：一级大类（attachment_category）必须保留，否则前端按 parent 分组会退化成扁平列表。 */
const selectableTypes = computed(() => {
  const allowed = props.allowedDocumentTypes
  if (!allowed?.length) return documentTypes.value
  return documentTypes.value.filter(
    (type) => type.dict_code === 'attachment_category' || allowed.includes(type.item_key)
  )
})

const {
  refRows,
  legacyOnlyRows,
  documentTypes,
  pending,
  pendingCount,
  pendingMissingTypeCount,
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
// ============ 05. 同链路历史附件状态 ============
const traceRows = ref<AttachmentSourceItem[]>([])
const traceLoading = ref(false)
const traceError = ref('')
let traceRequest = 0
// ============ 06. 同链路附件去重与展示 ============
const traceOnlyRows = computed(() => {
  // 同链路文件没有产品行标识；行级资料视图只展示本行有效引用。
  if (props.productCode) return []
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
const legacyTraceDocRows = computed<AttachmentDocRow[]>(() => [
  ...legacyOnlyRows.value.map((row) =>
    attachmentDocFromFile(row, AttachmentDocumentStatusEnum.LEGACY.value)
  ),
  ...traceOnlyRows.value.map((row) =>
    attachmentDocFromFile(row, AttachmentDocumentStatusEnum.TRACE.value)
  ),
])
// ============ 07. 同链路附件加载 ============
async function loadTrace() {
  const request = ++traceRequest
  traceRows.value = []
  traceError.value = ''
  traceLoading.value = false
  if (!canView.value || !props.traceId || props.productCode) return
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
// ============ 08. 数据监听与生命周期 ============
watch(
  () => [props.sourceType, props.sourceId, props.traceId, props.productCode, canView.value],
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
// ============ 09. 上传配置与权限 ============
const duplicateTip = ref('')
const uploadTip = computed(
  () =>
    `单个文件不超过 ${props.maxSizeMB}MB${props.accept.length ? '；支持 ' + props.accept.join(' ') : ''}`
)
// ==================== 上传入口（按钮 / 拖拽落区共用同一组 props 与回调） ====================
/** 无权编辑时不渲染上传入口（readonly / locked 均已在 canEdit 内为 false） */
const canUpload = computed(() => canEdit.value)
/**
 * 是否渲染列表段（A 段引用 / B 段历史 / 同链路段）。
 * `mode='upload-only'` 语义是"只给上传入口"（报审核、生成计划、产品资料视图都只是要个上传按钮），
 * 所以该模式下只保留上传入口与待绑定队列，不渲染任何列表，避免外面再套一层视图时出现"两个列表"。
 */
const showList = computed(() => centerVariant.value !== 'upload-only')
/** 证据上传形态：上传入口 + 本次上传的只读引用列表（不展示批量/类型编辑） */
const compactMode = computed(() => centerVariant.value === 'upload-compact')
const showRefs = computed(() => showList.value || compactMode.value)
const COMPACT_ACTIONS: AttachmentAction[] = ['download', 'remove']
/** compact 模式只展示本次会话产生的引用，避免把单据已有附件误当成本次证据 */
const displayRefRows = computed(() => {
  if (!compactMode.value) return refRows.value
  const ids = props.refIdsFilter || []
  return refRows.value.filter((row) => ids.includes(Number(row.id)))
})
/**
 * 上传禁用原因（唯一一条提示路径）：先看草稿标识是否就绪，再看调用方按业务传的 uploadBlocked。
 * 两者取第一个非空；组件不解释业务原因，只负责"禁用 + 显示这句话"。
 */
const uploadBlockedReason = computed(() => {
  // 业务锁定优先（dev-20261015-028）：有权限但单据状态不允许时，给出原因而不是让入口悄悄消失
  if (props.lockReason && !canEdit.value) return props.lockReason
  if (!draftReady.value) return '请先保存单据或提供 draftId 后再上传（避免多个单据共用待绑定队列）'
  return props.uploadBlocked || ''
})
const uploaderProps = computed(() => ({
  multiple: true,
  disabled: !!uploadBlockedReason.value,
  maxSizeMB: props.maxSizeMB,
  accept: props.accept,
  buttonText:
    centerVariant.value === 'upload-compact'
      ? '上传证据'
      : centerVariant.value === 'upload-only'
        ? '上传附件'
        : '上传（通用附件）',
  tip: uploadBlockedReason.value || uploadTip.value,
  dropZone: props.dropZone,
}))
function onUploaderError(message: string) {
  emit('error', { action: 'validate', message })
}
// ==================== 上传 / 绑定 ====================
// ============ 10. 上传与自动绑定 ============
const uploading = ref(false)
let activeUploads = 0
async function onFiles(files: File[]) {
  uploading.value = ++activeUploads > 0
  try {
    for (const file of files) {
      try {
        const attachmentId = await uploadTemp(file)
        const uploadedEntry = pending.value.find((entry) => entry.attachmentId === attachmentId)
        if (uploadedEntry && props.defaultDocumentType) {
          uploadedEntry.documentType = props.defaultDocumentType
          persist()
        }
        emit('uploaded', { attachmentId, temp: true, name: file.name })
        if (props.autoBind && props.sourceId) {
          const entry = uploadedEntry as PendingEntry
          try {
            const refId = await bindPendingOne(entry)
            emit('bound', { refId, attachmentId })
            await afterBound(entry)
          } catch (e: any) {
            // 可自行修正的失败（如未选资料类型）：不报错，落到待绑定区提示，补选类型后点「重试」
            const message = e?.message || '绑定失败'
            entry.state = 'failed'
            entry.error = message
            persist()
            ElMessage.warning(`已上传，但未绑定：${message}`)
          }
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
  } finally {
    uploading.value = --activeUploads > 0
  }
}
// ============ 11. 手动重试绑定 ============
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
// ============ 12. 绑定成功后处理 ============
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
// ============ 13. 列表事件与下载 ============
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
// ============ 14. 解除附件引用 ============
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
// ============ 15. 附件详情与元数据 ============
const metaVisible = ref(false)
const metaSaving = ref(false)
/** 详情弹窗展示的那一行（只读核对用） */
const metaRow = ref<AttachmentRefItem | null>(null)
const metaForm = reactive({
  refId: 0,
  documentType: '' as string | null,
  displayName: '',
  remark: '',
  sortOrder: 0,
})
function openMeta(row: AttachmentRefItem) {
  metaRow.value = row
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
  if (!canEdit.value) return
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
// ==================== 16. 预览 / 下载（useAttachmentPreview） ====================
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
// ============ 17. 对外暴露方法 ============
defineExpose({
  refresh: () => Promise.all([load(), loadTrace()]),
  bindPending,
  pendingCount,
  /** 资料类型字典（一级+二级），供调用方在延后绑定前选择类型 */
  documentTypes,
  /** 按 allowedDocumentTypes 收敛后的可选二级类型 */
  selectableTypes,
  /** 保存前校验用：待绑定队列中尚未选择二级资料类型的条数（dev-20261015-032） */
  getMissingTypeCount: () => pendingMissingTypeCount.value,
  uploading,
  pendingEntries: pending,
  getPendingEntries: () => pending.value,
  /** 业务服务已在事务内绑定成功，或用户放弃草稿时，仅清理本地队列。 */
  clearPending: () => {
    pending.value = []
    persist()
  },
})
</script>
<style scoped>
/* 01. 根容器 */
.attachment-center {
  padding: 4px 0;
}
/* 02. 标题栏 */
.ac-header {
  display: flex;
  align-items: center;
  gap: 8px;
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
/* 03. 提示与上传 */
.ac-alert {
  margin-bottom: 8px;
}
.ac-dropzone {
  display: block;
  margin-bottom: 8px;
}
.ac-hint {
  font-size: 11px;
  color: #909399;
}
</style>

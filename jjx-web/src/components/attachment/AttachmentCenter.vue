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
        <span class="ac-name">{{ entry.fileName || `附件 #${entry.attachmentId}` }}</span>
        <span class="ac-sub">{{ formatSize(entry.fileSize) }}</span>
        <el-tag size="small" :type="entry.state === 'failed' ? 'danger' : 'warning'">
          {{ entry.state === 'failed' ? '绑定失败' : '待绑定' }}
        </el-tag>
        <span v-if="entry.error" class="ac-error">{{ entry.error }}</span>
        <span class="ac-spacer" />
        <el-select
          v-if="canEdit"
          :model-value="entryCategoryKey(entry)"
          size="small"
          clearable
          placeholder="一级类型"
          style="width: 132px"
          @update:model-value="(v: string | null) => setEntryCategory(entry, v)"
        >
          <el-option v-for="c in selectableCategories" :key="c.key" :label="c.label" :value="c.key" />
        </el-select>
        <el-select
          v-if="canEdit"
          v-model="entry.documentType"
          size="small"
          clearable
          placeholder="二级类型"
          style="width: 168px"
          @change="() => persist()"
        >
          <el-option-group v-for="g in secondaryGroupsFor(entryCategoryKey(entry))" :key="g.key" :label="g.label">
            <el-option v-for="t in g.options" :key="t.item_key" :label="t.label" :value="t.item_key" />
          </el-option-group>
        </el-select>
        <el-button v-if="canEdit" link type="primary" @click="retryBind(entry)">重试绑定</el-button>
        <el-button v-if="canEdit" link type="danger" @click="removePending(entry.attachmentId)">移除</el-button>
      </div>
      <div class="ac-hint">待绑定附件已上传到服务器（7 天有效），保存单据后自动建立引用。</div>
    </div>

    <!-- A 段：引用（主键 refId，不同 refId 全部保留）。2026-10-13 dev-20261013-013：表格化 + 勾选 + 批量 -->
    <el-empty v-if="!loading && !traceLoading && !refRows.length && !legacyOnlyRows.length && !traceOnlyRows.length" description="暂无附件" :image-size="60" />
    <div v-else class="ac-ref">
      <div v-if="canEdit && refRows.length" class="ac-batch">
        <el-button size="small" :disabled="!selectedRefIds.length" @click="openBatchType">批量改类型</el-button>
        <el-button size="small" :disabled="!selectedRefIds.length" @click="openBatchName">批量改名</el-button>
        <el-button size="small" type="danger" plain :disabled="!selectedRefIds.length" @click="onBatchUnlink">批量移除</el-button>
        <span v-if="selectedRefIds.length" class="ac-sub">已选 {{ selectedRefIds.length }} 条</span>
      </div>

      <el-table
        v-if="refRows.length"
        :data="refRows"
        size="small"
        row-key="id"
        style="width: 100%"
        @selection-change="onSelectionChange"
      >
        <el-table-column v-if="canEdit" type="selection" width="42" />
        <el-table-column label="原文件名" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fileName || `附件 #${row.attachmentId}` }}</template>
        </el-table-column>
        <el-table-column label="展示文件名" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.displayName || row.fileName || `附件 #${row.attachmentId}` }}</template>
        </el-table-column>
        <el-table-column label="一级类型" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ categoryLabelOf(row.documentType) }}</template>
        </el-table-column>
        <el-table-column label="二级类型" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ secondLabelOf(row.documentType) }}</template>
        </el-table-column>
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="上传人" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createBy || '-' }}</template>
        </el-table-column>
        <el-table-column label="上传时间" width="140">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="previewRef(row)">预览</el-button>
            <el-button link type="primary" @click="openUrl(downloadUrlByRef(row.id))">下载</el-button>
            <el-button v-if="canEdit" link type="primary" @click="openMeta(row)">分类/命名</el-button>
            <el-button v-if="canEdit" link type="danger" @click="onUnbind(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- B 段：历史附件（主键 attachmentId，只读；已建立引用的不再重复展示） -->
    <div v-if="legacyOnlyRows.length" class="ac-legacy">
      <div class="ac-legacy-title">历史附件（未建立引用，只读）</div>
      <div v-for="row in legacyOnlyRows" :key="`att-${row.id}`" class="ac-row">
        <el-icon class="ac-icon"><Document /></el-icon>
        <span class="ac-name" :title="row.fileName">{{ row.fileName || `附件 #${row.id}` }}</span>
        <el-tag size="small" type="info">历史附件</el-tag>
        <span class="ac-sub">{{ formatSize(row.fileSize) }}</span>
        <span class="ac-sub">· {{ formatTime(row.createTime) }}</span>
        <span class="ac-spacer" />
        <el-button link type="primary" :icon="Download" @click="openUrl(attachmentApi.downloadUrl(row.id))" />
        <el-button link type="primary" :icon="View" @click="previewReadOnly(row, legacyOnlyRows)" />
      </div>
    </div>

    <!-- 同链路段按 attachmentId 去重，只有预览/下载，不进入 refs 写入链路。 -->
    <div v-if="traceLoading || traceError || traceOnlyRows.length" class="ac-legacy">
      <div class="ac-legacy-title">同链路来源单据（只读）</div>
      <div v-if="traceLoading" class="ac-hint">正在加载同链路文档…</div>
      <el-alert v-if="traceError" :title="traceError" type="warning" :closable="false" />
      <div v-for="row in traceOnlyRows" :key="`trace-${row.id}`" class="ac-row">
        <el-icon class="ac-icon"><Document /></el-icon>
        <span class="ac-name" :title="row.fileName">{{ row.fileName || `附件 #${row.id}` }}</span>
        <el-tag size="small" type="info">{{ row.bizTypeName || row.bizType || '来源单据' }}</el-tag>
        <span v-if="row.sourceNo || row.bizId" class="ac-sub">{{ row.sourceNo || `#${row.bizId}` }}</span>
        <span class="ac-sub">{{ formatSize(row.fileSize) }}</span>
        <span class="ac-sub">· {{ formatTime(row.createTime) }}</span>
        <span class="ac-spacer" />
        <el-button link type="primary" :icon="Download" @click="openUrl(attachmentApi.downloadUrl(row.id))" />
        <el-button link type="primary" :icon="View" @click="previewReadOnly(row, traceOnlyRows)" />
      </div>
    </div>

    <!-- 批量改类型 -->
    <el-dialog v-model="batchTypeVisible" title="批量改类型" width="440px" append-to-body>
      <div class="ac-meta-type">
        <el-select v-model="batchCategory" clearable placeholder="一级类型" @change="onBatchCategoryChange">
          <el-option v-for="c in selectableCategories" :key="c.key" :label="c.label" :value="c.key" />
        </el-select>
        <el-select v-model="batchDocumentType" clearable placeholder="二级类型">
          <el-option-group v-for="g in secondaryGroupsFor(batchCategory)" :key="g.key" :label="g.label">
            <el-option v-for="t in g.options" :key="t.item_key" :label="t.label" :value="t.item_key" />
          </el-option-group>
        </el-select>
      </div>
      <div class="ac-hint" style="margin-top: 8px">
        对选中的 {{ selectedRefIds.length }} 条统一设置同一个二级类型（一级仅用于筛选）。
      </div>
      <template #footer>
        <el-button @click="batchTypeVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" :disabled="!batchDocumentType" @click="applyBatchType">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量改名 -->
    <el-dialog v-model="batchNameVisible" title="批量改名" width="560px" append-to-body>
      <div class="ac-hint">
        命名规则：来源类型-来源Id-一级编码-二级编码-二级名称（产品段系统暂未提供，未纳入）。
      </div>
      <div class="ac-hint" style="margin-top: 6px">共 {{ batchNameRows.length }} 条；示例（第 1 条）：</div>
      <div class="ac-preview">{{ batchNamePreview }}</div>
      <el-alert
        v-if="batchNameHasOverflow"
        type="warning"
        :closable="false"
        title="有名称超过 255 字符，后端会拒绝，请先调整类型后再改。"
      />
      <template #footer>
        <el-button @click="batchNameVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="batchNameSaving"
          :disabled="!batchNameRows.length || batchNameHasOverflow"
          @click="applyBatchName"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 元数据编辑 -->
    <el-dialog v-model="metaVisible" title="附件信息" width="460px" append-to-body destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="分类">
          <div class="ac-meta-type">
            <el-select
              v-model="metaCategory"
              clearable
              placeholder="一级类型"
              @change="onMetaCategoryChange"
            >
              <el-option v-for="c in selectableCategories" :key="c.key" :label="c.label" :value="c.key" />
            </el-select>
            <el-select v-model="metaForm.documentType" clearable placeholder="二级类型">
              <el-option-group v-for="g in secondaryGroupsFor(metaCategory)" :key="g.key" :label="g.label">
                <el-option v-for="t in g.options" :key="t.item_key" :label="t.label" :value="t.item_key" />
              </el-option-group>
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="展示名">
          <el-input v-model="metaForm.displayName" maxlength="255" placeholder="留空使用默认展示" />
        </el-form-item>
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
      @close="viewerVisible = false"
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
import { attachmentRefApi, type AttachmentDocumentTypeItem, type AttachmentRefItem } from '@/api/system/attachmentRef'
import AttachmentTempUploader from './AttachmentTempUploader.vue'
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
    accept: () => ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.jpg', '.jpeg', '.png', '.gif', '.webp', '.zip'],
    autoBind: true,
    dropZone: false,
  },
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

watch(() => [props.sourceType, props.sourceId, props.traceId, canView.value], () => loadTrace(), { immediate: true })
watch(canView, (allowed) => { if (allowed) loadDocumentTypes() }, { immediate: true })
onBeforeUnmount(() => { traceRequest++ })

const duplicateTip = ref('')
const uploadTip = computed(() =>
  `单个文件不超过 ${props.maxSizeMB}MB${props.accept.length ? '；支持 ' + props.accept.join(' ') : ''}`,
)

// ==================== 上传入口（按钮 / 拖拽落区共用同一组 props 与回调） ====================
/** readonly 或无权编辑时不渲染上传入口 */
const canUpload = computed(() => props.mode !== 'readonly' && canEdit.value)
const uploaderProps = computed(() => ({
  multiple: true,
  disabled: !draftReady.value,
  maxSizeMB: props.maxSizeMB,
  accept: props.accept,
  buttonText: props.mode === 'upload-only' ? '上传附件' : '上传（通用附件）',
  tip: draftReady.value
    ? uploadTip.value
    : '请先保存单据或提供 draftId 后再上传（避免多个单据共用待绑定队列）',
  dropZone: props.dropZone,
}))
function onUploaderError(message: string) {
  emit('error', { action: 'validate', message })
}

/** 二级编码 → 一级名（无/未知返回 '—'，不硬编码中文） */
function categoryLabelOf(code?: string | null) {
  const key = categoryKeyOfType(code)
  if (!key) return '—'
  const c = documentTypes.value.find((x) => x.item_key === key && x.dict_code === 'attachment_category')
  return c ? c.label : key
}
/** 二级编码 → 二级名（空='未分类'；未知回退原编码） */
function secondLabelOf(code?: string | null) {
  if (!code) return '未分类'
  const t = documentTypes.value.find(
    (x) => x.item_key === code && x.dict_code === 'attachment_document_type',
  )
  return t ? t.label : code
}
/** 二级 → 一级（attachment_category）解析：用真实字典的 ext_data.parent，不硬编码中文；未知/悬空返回 null */
function parentCategoryOf(t: AttachmentDocumentTypeItem): AttachmentDocumentTypeItem | null {
  const ext = typeof t.ext_data === 'string' ? safeJson(t.ext_data) : t.ext_data
  const parent = (ext as any)?.parent
  if (!parent) return null
  return (
    documentTypes.value.find((x) => x.item_key === parent && x.dict_code === 'attachment_category') || null
  )
}
const selectableTypes = computed(() =>
  documentTypes.value.filter((t) => {
    if (t.dict_code !== 'attachment_document_type' || t.is_active !== 1) return false
    const ext = typeof t.ext_data === 'string' ? safeJson(t.ext_data) : t.ext_data
    const sources = (ext as any)?.sources
    if (!Array.isArray(sources)) return true
    return sources.includes(props.sourceType)
  }),
)
/** 类型下拉的两级形态：按一级（attachment_category）分组，组内为可选二级（attachment_document_type） */
const groupedSelectableTypes = computed(() => {
  const groups = new Map<string, { key: string; label: string; options: AttachmentDocumentTypeItem[] }>()
  for (const t of selectableTypes.value) {
    const parent = parentCategoryOf(t)
    const key = parent?.item_key || '__unknown__'
    let g = groups.get(key)
    if (!g) {
      g = { key, label: parent?.label || '未分类', options: [] }
      groups.set(key, g)
    }
    g.options.push(t)
  }
  return Array.from(groups.values())
})
/** 一级选项（= 当前单据下可选二级所属的一级），用于两级级联的第一个下拉 */
const selectableCategories = computed(() =>
  groupedSelectableTypes.value
    .filter((g) => g.key !== '__unknown__')
    .map((g) => ({ key: g.key, label: g.label })),
)
/** 二级下拉的组选项：选了某一级则只给该组，否则给全部（保留「按一级分组」形态） */
function secondaryGroupsFor(categoryKey: string) {
  return categoryKey
    ? groupedSelectableTypes.value.filter((g) => g.key === categoryKey)
    : groupedSelectableTypes.value
}
/** 二级编码 → 所属一级编码（未知返回空串） */
function categoryKeyOfType(code?: string | null): string {
  if (!code) return ''
  const t = documentTypes.value.find(
    (x) => x.item_key === code && x.dict_code === 'attachment_document_type',
  )
  return t ? parentCategoryOf(t)?.item_key || '' : ''
}
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
function safeJson(raw: string): Record<string, unknown> {
  try {
    return JSON.parse(raw)
  } catch {
    return {}
  }
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
    emit('duplicate', { attachmentId: entry.attachmentId, sha256: entry.sha256 || '', matches: others.length })
  }
}

// ==================== 批量操作（表格勾选：改类型 / 改名 / 移除）====================
const selectedRefIds = ref<number[]>([])
const selectedRefRows = computed(() => refRows.value.filter((r) => selectedRefIds.value.includes(r.id)))
function onSelectionChange(rows: AttachmentRefItem[]) {
  selectedRefIds.value = rows.map((r) => r.id)
}
async function afterBatchChange() {
  selectedRefIds.value = []
  await load()
  emit('changed', { count: refRows.value.length })
}

// —— 批量改类型：对选中行统一设同一个二级（一级仅作筛选）——
const batchTypeVisible = ref(false)
const batchCategory = ref('')
const batchDocumentType = ref<string | null>(null)
const batchSaving = ref(false)
function openBatchType() {
  batchCategory.value = ''
  batchDocumentType.value = null
  batchTypeVisible.value = true
}
function onBatchCategoryChange() {
  if (batchDocumentType.value && categoryKeyOfType(batchDocumentType.value) !== batchCategory.value) {
    batchDocumentType.value = null
  }
}
async function applyBatchType() {
  if (!batchDocumentType.value || !selectedRefIds.value.length) return
  batchSaving.value = true
  try {
    await attachmentRefApi.batchUpdate(
      selectedRefIds.value.map((refId) => ({ refId, documentType: batchDocumentType.value })),
    )
    ElMessage.success(`已批量设置 ${selectedRefIds.value.length} 条类型`)
    batchTypeVisible.value = false
    await afterBatchChange()
  } catch (e: any) {
    const message = e?.message || '批量设置类型失败'
    ElMessage.error(message)
    emit('error', { action: 'batch-type', message })
  } finally {
    batchSaving.value = false
  }
}

// —— 批量改名：按规则逐行生成（每行名字不同，所以按行传）——
const NAME_SEP = '-'
const batchNameVisible = ref(false)
const batchNameSaving = ref(false)
const batchNameRows = ref<{ refId: number; name: string; overflow: boolean }[]>([])
const batchNamePreview = computed(() => batchNameRows.value[0]?.name || '')
const batchNameHasOverflow = computed(() => batchNameRows.value.some((r) => r.overflow))
/** 命名规则：来源类型-来源Id-一级编码-二级编码-二级名称（产品段系统暂未提供，未纳入） */
function buildRowDisplayName(row: AttachmentRefItem): string {
  return [
    row.sourceType || '',
    row.sourceId == null ? '' : String(row.sourceId),
    categoryKeyOfType(row.documentType) || '',
    row.documentType || '',
    row.documentType ? secondLabelOf(row.documentType) : '',
  ]
    .filter((v) => v !== '')
    .join(NAME_SEP)
}
function openBatchName() {
  batchNameRows.value = selectedRefRows.value.map((row) => {
    const name = buildRowDisplayName(row)
    return { refId: row.id, name, overflow: name.length > 255 }
  })
  batchNameVisible.value = true
}
async function applyBatchName() {
  if (!batchNameRows.value.length) return
  if (batchNameHasOverflow.value) {
    ElMessage.warning('有名称超过 255 字符，请先调整类型后再改')
    return
  }
  batchNameSaving.value = true
  try {
    await attachmentRefApi.batchUpdate(batchNameRows.value.map((r) => ({ refId: r.refId, displayName: r.name })))
    ElMessage.success(`已批量改名 ${batchNameRows.value.length} 条`)
    batchNameVisible.value = false
    await afterBatchChange()
  } catch (e: any) {
    const message = e?.message || '批量改名失败'
    ElMessage.error(message)
    emit('error', { action: 'batch-name', message })
  } finally {
    batchNameSaving.value = false
  }
}

// —— 批量移除：只解除本单据引用，不动物理文件 ——
async function onBatchUnlink() {
  const count = selectedRefIds.value.length
  if (!count) return
  try {
    await ElMessageBox.confirm(
      `从本单据移除选中的 ${count} 个附件？只解除本单据的引用，文件本身不删除；其他单据若引用不受影响。`,
      '批量移除',
      { confirmButtonText: '确定移除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await attachmentRefApi.batchUnlink(selectedRefIds.value)
    ElMessage.success(`已移除 ${count} 条引用`)
    await afterBatchChange()
  } catch (e: any) {
    const message = e?.message || '批量移除失败'
    ElMessage.error(message)
    emit('error', { action: 'batch-unlink', message })
  }
}

// ==================== 解绑 / 元数据 ====================
async function onUnbind(row: AttachmentRefItem) {
  try {
    await ElMessageBox.confirm('解除本单据对该附件的引用？仅解除引用，物理文件与其他单据的引用保留。', '解除引用', {
      confirmButtonText: '确定解除',
      cancelButtonText: '取消',
      type: 'warning',
    })
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
const metaForm = reactive({ refId: 0, documentType: '' as string | null, displayName: '', remark: '', sortOrder: 0 })
/** 分类弹窗的一级筛选（仅前端；打开时由已存二级的 parent 反推） */
const metaCategory = ref('')

function onMetaCategoryChange() {
  if (metaForm.documentType && categoryKeyOfType(metaForm.documentType) !== metaCategory.value) {
    metaForm.documentType = ''
  }
}

function openMeta(row: AttachmentRefItem) {
  metaForm.refId = row.id
  metaForm.documentType = row.documentType || ''
  metaCategory.value = categoryKeyOfType(row.documentType)
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

// ==================== 预览 / 下载 ====================
const IMAGE_EXT = ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.bmp']
const viewerVisible = ref(false)
const viewerList = ref<string[]>([])
const viewerIndex = ref(0)

function isImageName(name?: string, fileType?: string) {
  if (fileType && fileType.startsWith('image/')) return true
  const lower = String(name || '').toLowerCase()
  return IMAGE_EXT.some((ext) => lower.endsWith(ext))
}
function previewRef(row: AttachmentRefItem) {
  if (isImageName(row.fileName || undefined, row.fileType || undefined)) {
    const images = refRows.value.filter((r) => isImageName(r.fileName || undefined, r.fileType || undefined))
    viewerList.value = images.map((r) => attachmentRefApi.previewUrlByRef(r.id))
    const idx = images.findIndex((r) => r.id === row.id)
    viewerIndex.value = idx >= 0 ? idx : 0
    viewerVisible.value = true
    return
  }
  openUrl(attachmentRefApi.previewUrlByRef(row.id))
}
function downloadUrlByRef(refId: number) {
  return attachmentRefApi.downloadUrlByRef(refId)
}
function previewReadOnly(row: TraceAttachment, rows: TraceAttachment[]) {
  if (isImageName(row.fileName, row.fileType)) {
    const images = rows.filter((item) => isImageName(item.fileName, item.fileType))
    viewerList.value = images.map((item) => attachmentApi.downloadUrl(item.id))
    viewerIndex.value = Math.max(0, images.findIndex((item) => item.id === row.id))
    viewerVisible.value = true
    return
  }
  openUrl(attachmentApi.downloadUrl(row.id))
}
function openUrl(url: string) {
  window.open(url, '_blank', 'noopener,noreferrer')
}
function formatSize(size?: number | null) {
  if (!size) return ''
  if (size < 1024) return `${size}B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)}KB`
  return `${(size / 1024 / 1024).toFixed(1)}MB`
}
function formatTime(t?: string | null) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
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
/* A 段引用列表：批量工具条 + 表格化（dev-20261013-013） */
.ac-ref {
  margin-bottom: 8px;
}
.ac-batch {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.ac-preview {
  margin: 6px 0;
  padding: 6px 8px;
  border-radius: 4px;
  background: #f5f7fa;
  font-size: 12px;
  color: #303133;
  word-break: break-all;
  white-space: pre-wrap;
}
.ac-dropzone {
  display: block;
  margin-bottom: 8px;
}
/* 分类弹窗两级下拉并排：外层 flex，选择器 flex:1 min-width:0（避免组件自带 width:100% 挤掉另一个） */
.ac-meta-type {
  display: flex;
  gap: 8px;
  width: 100%;
}
.ac-meta-type :deep(.el-select) {
  flex: 1;
  min-width: 0;
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

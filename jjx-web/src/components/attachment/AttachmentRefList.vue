<template>
  <div class="ar-list">
      <div v-if="canEdit && rows.length" class="ar-batch">
        <el-button size="small" :disabled="!selectedIds.length" @click="openBatchType"
          >批量改类型</el-button
        >
        <el-button size="small" :disabled="!selectedIds.length" @click="openBatchName"
          >批量改名</el-button
        >
        <el-button
          size="small"
          type="danger"
          plain
          :disabled="!selectedIds.length"
          @click="onBatchUnlink"
        >
          批量移除
        </el-button>
        <span v-if="selectedIds.length" class="ar-sub">已选 {{ selectedIds.length }} 条</span>
      </div>

      <el-table
        :data="tableRows"
        size="small"
        row-key="rowKey"
        style="width: 100%"
        @selection-change="onSelectionChange"
      >
        <el-table-column v-if="canEdit && rows.length" type="selection" width="42" :selectable="(row: TableRow) => row.kind === 'ref'" />
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag size="small" :title="row.kind === 'pending' ? pendingOf(row).error : undefined" :type="row.kind === 'pending' ? (pendingOf(row).state === 'failed' ? 'danger' : 'warning') : row.kind === 'ref' ? 'success' : 'info'">
              {{ row.kind === 'pending' ? (pendingOf(row).state === 'failed' ? '绑定失败' : '待绑定') : row.kind === 'ref' ? '已绑定' : row.kind === 'legacy' ? '历史' : '同链路' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-for="column in referenceColumns" :key="column.key"
          :label="column.label" :width="'width' in column ? column.width : undefined"
          :min-width="'minWidth' in column ? column.minWidth : undefined"
          :show-overflow-tooltip="column.render === 'preview' || column.render === 'category' || column.render === 'text'">
          <template #default="{ row }">
            <span v-if="column.render === 'preview'" :class="{ 'ar-file': row.kind !== 'pending' }" :title="row.fileName" @click="previewRow(row)">
              <el-icon v-if="row.kind !== 'pending'" class="ar-eye"><View /></el-icon>{{ row.fileName || `附件 #${row.attachmentId}` }}
            </span>
            <template v-else-if="column.render === 'displayName'">
              <el-input v-if="row.kind === 'ref' && canEdit" :model-value="nameDraft(refOf(row))" size="small" maxlength="255"
                placeholder="留空=用原文件名" @update:model-value="(v: string) => setNameDraft(refOf(row), v)"
                @change="() => saveDisplayName(refOf(row))" />
              <span v-else>{{ row.displayName || row.fileName || `附件 #${row.attachmentId}` }}</span>
            </template>
            <span v-else-if="column.render === 'category'">{{ row.documentType ? categoryLabelOf(row.documentType) : '-' }}</span>
            <template v-else-if="column.render === 'typeSelect'">
              <AttachmentTypeSelect v-if="row.kind === 'pending' && canEdit" :model-value="pendingOf(row).documentType"
                :category="pendingCategoryOf(pendingOf(row))" :document-types="documentTypes" :source-type="sourceType"
                show-category @update:category="(v) => setPendingCategory(pendingOf(row), v)"
                @update:model-value="(v) => setPendingType(pendingOf(row), v)" />
              <AttachmentTypeSelect v-else-if="row.kind === 'ref' && canEdit" :model-value="row.documentType"
                :document-types="documentTypes" :source-type="sourceType"
                :disabled="savingId === row.id" @update:model-value="(v) => saveDocType(refOf(row), v)" />
              <span v-else>{{ row.documentType ? secondLabelOf(row.documentType) : '-' }}</span>
            </template>
            <span v-else-if="column.render === 'size'">{{ formatSize(row.fileSize) }}</span>
            <span v-else-if="column.render === 'time'">{{ formatTime(row.createTime) }}</span>
            <span v-else-if="column.render === 'text'">{{ row.createBy || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="110">
          <template #default="{ row }">{{ row.kind === 'trace' ? (row.sourceNo || row.bizTypeName || row.bizType || '-') : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <template v-if="row.kind === 'pending'">
              <el-button v-if="canEdit" link type="primary" @click="emit('retryBind', pendingOf(row))">重试</el-button>
              <el-button v-if="canEdit" link type="danger" @click="emit('removePending', row.attachmentId)">移除</el-button>
            </template>
            <template v-else-if="row.kind === 'ref'">
              <el-button link type="primary" @click="emit('download', refOf(row))">下载</el-button>
              <el-button v-if="canEdit" link type="primary" @click="emit('edit', refOf(row))">更多</el-button>
              <el-button v-if="canEdit" link type="danger" @click="emit('unbind', refOf(row))">移除</el-button>
            </template>
            <template v-else>
              <el-button link type="primary" @click="emit('downloadReadonly', row.data)">下载</el-button>
              <el-button link type="primary" @click="emit('previewReadonly', row.data, readonlyGroup(row))">预览</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

    <!-- 批量改类型 -->
    <el-dialog v-model="batchTypeVisible" title="批量改类型" width="440px" append-to-body>
      <div class="ar-type-row">
        <AttachmentTypeSelect v-model="batchDocumentType" :category="batchCategory"
          :document-types="documentTypes" :source-type="sourceType" show-category
          @update:category="onBatchCategoryChange" />
      </div>
      <div class="ar-sub" style="margin-top: 8px">
        对选中的 {{ selectedIds.length }} 条统一设置同一个二级类型（一级仅用于筛选）。
      </div>
      <template #footer>
        <el-button @click="batchTypeVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="batchSaving"
          :disabled="!batchDocumentType"
          @click="applyBatchType"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量改名 -->
    <el-dialog v-model="batchNameVisible" title="批量改名" width="560px" append-to-body>
      <div class="ar-sub">
        命名规则：来源类型编码-来源Id-P产品id-一级码-二级码-二级名（编码取对照表：来源
        INQ/QT/SO…、一级 BIZ/CUS/ENG/GEN/QUA、二级如 CD/PI；<b>无产品时 P 段为 P0</b>；除产品id外各要素都必须有，缺任一不给生成）。
      </div>
      <div class="ar-sub" style="margin-top: 6px">
        共 {{ batchNameRows.length }} 条；示例（第 1 条）：
      </div>
      <div class="ar-preview">{{ batchNamePreview }}</div>
      <el-alert
        v-if="batchNameInvalidCount"
        type="warning"
        :closable="false"
        :title="`有 ${batchNameInvalidCount} 行要素不齐（缺 ${batchNameMissingLabels}），请先在列表里补齐类型后再改名。`"
      />
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
          :disabled="!batchNameRows.length || batchNameHasOverflow || batchNameInvalidCount > 0"
          @click="applyBatchName"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 公共附件统一表格：待绑定、引用及历史只读附件共用一套列与行渲染。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import type { PendingEntry } from '@/composables/useAttachmentRefs'
import {
  attachmentRefApi,
  type AttachmentDocumentTypeItem,
  type AttachmentRefItem,
} from '@/api/system/attachmentRef'
import { useAttachmentDocTypes } from './useAttachmentDocTypes'
import { attachmentSourceCode, attachmentCategoryCode, attachmentDocumentTypeCode } from '@/enums/system/AttachmentEnum'
import AttachmentTypeSelect from './AttachmentTypeSelect.vue'
import { referenceColumns } from './attachmentFields'
import { formatSize, formatTime } from './attachmentFormat'

const props = defineProps<{
    rows: AttachmentRefItem[]
    pending: PendingEntry[]
    legacyRows: ReadonlyAttachment[]
    traceRows: ReadonlyAttachment[]
    canEdit: boolean
    /** 资料类型字典（父组件已加载，避免重复请求） */
    documentTypes: AttachmentDocumentTypeItem[]
    sourceType: string
}>()

type ReadonlyAttachment = Record<string, any> & { id: number }
type TableRow = {
  rowKey: string
  kind: 'pending' | 'ref' | 'legacy' | 'trace'
  data: PendingEntry | AttachmentRefItem | ReadonlyAttachment
  id: number
  attachmentId: number
  fileName?: string | null
  displayName?: string | null
  documentType?: string | null
  fileSize?: number | null
  createTime?: string | null
  createBy?: string | null
  sourceNo?: string | null
  bizTypeName?: string | null
  bizType?: string | null
}

const tableRows = computed<TableRow[]>(() => [
  ...props.pending.map((entry) => ({
    rowKey: `pending:${entry.attachmentId}`, kind: 'pending' as const, data: entry,
    id: entry.attachmentId, attachmentId: entry.attachmentId,
    fileName: entry.fileName, displayName: entry.displayName, documentType: entry.documentType,
    fileSize: entry.fileSize, createTime: entry.createdAt,
  })),
  ...props.rows.map((row) => ({ ...row, rowKey: `ref:${row.id}`, kind: 'ref' as const, data: row })),
  ...props.legacyRows.map((row) => ({ ...row, rowKey: `legacy:${row.id}`, kind: 'legacy' as const, data: row, attachmentId: row.id })),
  ...props.traceRows.map((row) => ({ ...row, rowKey: `trace:${row.id}`, kind: 'trace' as const, data: row, attachmentId: row.id })),
])
const refOf = (row: TableRow) => row.data as AttachmentRefItem
const pendingOf = (row: TableRow) => row.data as PendingEntry
const readonlyGroup = (row: TableRow) => row.kind === 'legacy' ? props.legacyRows : props.traceRows
function previewRow(row: TableRow) {
  if (row.kind === 'ref') emit('preview', refOf(row))
  else if (row.kind !== 'pending') emit('previewReadonly', row.data as ReadonlyAttachment, readonlyGroup(row))
}

const pendingCategories = ref<Record<number, string>>({})
function pendingCategoryOf(entry: PendingEntry) {
  return pendingCategories.value[entry.attachmentId] || categoryKeyOfType(entry.documentType)
}
function setPendingCategory(entry: PendingEntry, category: string | null) {
  if (category) pendingCategories.value[entry.attachmentId] = category
  else delete pendingCategories.value[entry.attachmentId]
  if (entry.documentType && categoryKeyOfType(entry.documentType) !== (category || '')) entry.documentType = null
  emit('pendingChanged')
}
function setPendingType(entry: PendingEntry, value: string | null) {
  entry.documentType = value
  emit('pendingChanged')
}

const emit = defineEmits<{
  preview: [row: AttachmentRefItem]
  download: [row: AttachmentRefItem]
  edit: [row: AttachmentRefItem]
  unbind: [row: AttachmentRefItem]
  retryBind: [entry: PendingEntry]
  removePending: [attachmentId: number]
  pendingChanged: []
  previewReadonly: [row: ReadonlyAttachment, rows: ReadonlyAttachment[]]
  downloadReadonly: [row: ReadonlyAttachment]
  /** 批量动作已落库，父组件据此重新拉列表 */
  changed: []
  error: [payload: { action: string; message: string }]
}>()

const {
  categoryKeyOfType,
  categoryLabelOf,
  secondLabelOf,
} = useAttachmentDocTypes(
  computed(() => props.documentTypes),
  () => props.sourceType
)

// ==================== 勾选 ====================
const selectedIds = ref<number[]>([])
const selectedRows = computed(() => props.rows.filter((r) => selectedIds.value.includes(r.id)))
function onSelectionChange(rows: TableRow[]) {
  selectedIds.value = rows.filter((r) => r.kind === 'ref').map((r) => r.id)
}
// 父组件刷新列表后，旧勾选必须作废（el-table 自身会清空，这里同步状态）
watch(
  () => props.rows,
  () => {
    selectedIds.value = []
    nameDrafts.value = {} // 父组件刷新列表后丢弃草稿，避免旧值覆盖新值
  }
)

// ==================== 行内编辑（二级类型 / 展示文件名，改完即存）====================
const savingId = ref<number | null>(null)
/** 展示名草稿：key=refId；未编辑时回落到本行当前值 */
const nameDrafts = ref<Record<number, string>>({})
function nameDraft(row: AttachmentRefItem): string {
  return nameDrafts.value[row.id] ?? row.displayName ?? ''
}
function setNameDraft(row: AttachmentRefItem, value: string) {
  nameDrafts.value[row.id] = value
}
/**
 * 行内改二级类型：走 /refs/batch 的「按行部分更新」，只带 documentType，
 * 不会像单条 PUT（整条覆盖）那样把展示名/备注/排序清空；清空时传空串=清类型。
 */
async function saveDocType(row: AttachmentRefItem, value: string | null) {
  const next = value ?? ''
  if (next === (row.documentType ?? '')) return
  await inlineSave(row, { documentType: next })
}
/** 行内改展示名：空串=清空（回落到原文件名） */
async function saveDisplayName(row: AttachmentRefItem) {
  const next = (nameDrafts.value[row.id] ?? '').trim()
  if (next === (row.displayName ?? '')) return
  const ok = await inlineSave(row, { displayName: next })
  if (ok) delete nameDrafts.value[row.id]
}
async function inlineSave(
  row: AttachmentRefItem,
  patch: { documentType?: string; displayName?: string }
): Promise<boolean> {
  savingId.value = row.id
  try {
    await attachmentRefApi.batchUpdate([{ refId: row.id, ...patch }])
    ElMessage.success('已保存')
    emit('changed')
    return true
  } catch (e: any) {
    const message = e?.message || '保存失败'
    ElMessage.error(message)
    emit('error', { action: 'inline-save', message })
    return false
  } finally {
    savingId.value = null
  }
}

// ==================== 批量改类型 ====================
const batchTypeVisible = ref(false)
const batchCategory = ref('')
const batchDocumentType = ref<string | null>(null)
const batchSaving = ref(false)
function openBatchType() {
  batchCategory.value = ''
  batchDocumentType.value = null
  batchTypeVisible.value = true
}
function onBatchCategoryChange(category: string) {
  batchCategory.value = category
  if (batchDocumentType.value && categoryKeyOfType(batchDocumentType.value) !== category) {
    batchDocumentType.value = null
  }
}
async function applyBatchType() {
  const ids = [...selectedIds.value]
  if (!batchDocumentType.value || !ids.length) return
  batchSaving.value = true
  try {
    await attachmentRefApi.batchUpdate(
      ids.map((refId) => ({ refId, documentType: batchDocumentType.value }))
    )
    ElMessage.success(`已批量设置 ${ids.length} 条类型`)
    batchTypeVisible.value = false
    emit('changed')
  } catch (e: any) {
    const message = e?.message || '批量设置类型失败'
    ElMessage.error(message)
    emit('error', { action: 'batch-type', message })
  } finally {
    batchSaving.value = false
  }
}

// ==================== 批量改名（按行生成，每行名字不同）====================
const NAME_SEP = '-'
/** 产品id 缺省占位：保持 P 前缀；0 不是合法产品id，一眼看出"没有产品" */
const PRODUCT_PLACEHOLDER = 'P0'
interface BatchNameRow {
  refId: number
  name: string
  overflow: boolean
  /** 缺的要素名（除产品id外都必要）；非空 = 本行不能生成 */
  missing: string[]
}
const batchNameVisible = ref(false)
const batchNameSaving = ref(false)
const batchNameRows = ref<BatchNameRow[]>([])
const batchNamePreview = computed(() => batchNameRows.value[0]?.name || '')
const batchNameHasOverflow = computed(() => batchNameRows.value.some((r) => r.overflow))
/** 要素不齐的行数（>0 时整批不给生成） */
const batchNameInvalidCount = computed(() => batchNameRows.value.filter((r) => r.missing.length).length)
/** 缺了哪些要素（去重后拼成一句话） */
const batchNameMissingLabels = computed(() => {
  const set = new Set<string>()
  batchNameRows.value.forEach((r) => r.missing.forEach((m) => set.add(m)))
  return Array.from(set).join('、')
})
/**
 * 命名规则：来源类型编码-来源Id-P产品id-一级码-二级码-二级名
 * 例：INQ-3-P123-CUS-CD-客户图纸；无产品时 P 段为 P0。
 * 来源/一级/二级编码取前端常量对照表（AttachmentEnum）；除产品id外各要素都必要，缺任一则 missing 非空（不给生成）。
 */
function buildRowName(row: AttachmentRefItem): { name: string; missing: string[] } {
  const code = attachmentSourceCode(row.sourceType)
  const category = categoryKeyOfType(row.documentType) || ''
  const second = row.documentType ? secondLabelOf(row.documentType) : ''
  const missing: string[] = []
  if (!code) missing.push('来源类型')
  if (row.sourceId == null) missing.push('来源Id')
  if (!category) missing.push('一级类型')
  if (!row.documentType) missing.push('二级类型')
  if (!second) missing.push('二级名称')
  // 不丢空段（段位固定）：缺的要素位置留空，供预览时一眼看出
  const name = [
    code,
    row.sourceId == null ? '' : String(row.sourceId),
    row.productId == null ? PRODUCT_PLACEHOLDER : `P${row.productId}`,
    attachmentCategoryCode(category),
    attachmentDocumentTypeCode(row.documentType),
    second,
  ].join(NAME_SEP)
  return { name, missing }
}
function openBatchName() {
  batchNameRows.value = selectedRows.value.map((row) => {
    const { name, missing } = buildRowName(row)
    return { refId: row.id, name, overflow: name.length > 255, missing }
  })
  batchNameVisible.value = true
}
async function applyBatchName() {
  const items = batchNameRows.value.map((r) => ({ refId: r.refId, displayName: r.name }))
  if (!items.length) return
  if (batchNameInvalidCount.value) {
    ElMessage.warning(`有 ${batchNameInvalidCount.value} 行要素不齐（${batchNameMissingLabels.value}），请先补齐再改名`)
    return
  }
  if (batchNameHasOverflow.value) {
    ElMessage.warning('有名称超过 255 字符，请先调整类型后再改')
    return
  }
  batchNameSaving.value = true
  try {
    await attachmentRefApi.batchUpdate(items)
    ElMessage.success(`已批量改名 ${items.length} 条`)
    batchNameVisible.value = false
    emit('changed')
  } catch (e: any) {
    const message = e?.message || '批量改名失败'
    ElMessage.error(message)
    emit('error', { action: 'batch-name', message })
  } finally {
    batchNameSaving.value = false
  }
}

// ==================== 批量移除（只解除本单据引用，不动物理文件）====================
async function onBatchUnlink() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(
      `从本单据移除选中的 ${ids.length} 个附件？只解除本单据的引用，文件本身不删除；其他单据若引用不受影响。`,
      '批量移除',
      { confirmButtonText: '确定移除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await attachmentRefApi.batchUnlink(ids)
    ElMessage.success(`已移除 ${ids.length} 条引用`)
    emit('changed')
  } catch (e: any) {
    const message = e?.message || '批量移除失败'
    ElMessage.error(message)
    emit('error', { action: 'batch-unlink', message })
  }
}

</script>

<style scoped>
.ar-batch {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.ar-sub {
  font-size: 12px;
  color: #909399;
}
/* 原文件名：可点击预览 */
.ar-file {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #409eff;
  cursor: pointer;
}
.ar-file:hover {
  text-decoration: underline;
}
.ar-eye {
  font-size: 14px;
}
.ar-preview {
  margin: 6px 0;
  padding: 6px 8px;
  border-radius: 4px;
  background: #f5f7fa;
  font-size: 12px;
  color: #303133;
  word-break: break-all;
  white-space: pre-wrap;
}
.ar-type-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.ar-type-row :deep(.el-select) {
  flex: 1;
  min-width: 0;
}

</style>

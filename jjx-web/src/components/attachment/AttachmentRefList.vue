<template>
  <div class="ar-list">
      <div v-if="canEdit && !readonlyType && hasSaveAction && (rows.length || pending.length)" class="ar-batch">
        <el-button size="small" type="primary" :loading="saveSaving" @click="onSaveClick">保存</el-button>
        <span class="ar-sub">保存附件信息到本单</span>
      </div>
      <div v-if="canEdit && !readonlyType && rows.length" class="ar-batch">
        <el-button size="small" :disabled="!selectedIds.length" @click="openBatchType"
          >批量改类型</el-button
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

      <AttachmentTable :rows="tableRows" :document-types="documentTypes" :source-type="sourceType"
        row-key="rowKey" :selectable="canEdit && !readonlyType && rows.length ? (row: TableRow) => row.kind === 'ref' : undefined"
        @selection-change="onSelectionChange" @preview="previewRow"
        @download="(row) => emit('download', refOf(row))" @detail="(row) => emit('edit', refOf(row))">
        <template #status="{ row }">
          <el-tag size="small" :title="row.kind === 'pending' ? pendingOf(row).error : undefined" :type="row.kind === 'pending' ? (pendingOf(row).state === 'failed' ? 'danger' : 'warning') : 'success'">
            {{ row.kind === 'pending' ? (pendingOf(row).state === 'failed' ? '绑定失败' : '待绑定') : '已绑定' }}
          </el-tag>
        </template>
        <template #fileName="{ row }">
          <span v-if="row.kind === 'pending'">{{ textOf('fileName', row) }}</span>
          <el-button v-else link type="primary" @click="previewRow(row)">{{ textOf('fileName', row) }}</el-button>
        </template>
        <template #category="{ row }">
          <AttachmentTypeSelect v-if="canEdit && !readonlyType" category-only :category="categoryOfRow(row)"
            :document-types="documentTypes" :source-type="sourceType" :disabled="savingId === row.id"
            @update:category="(v) => onRowCategory(row, v)" />
          <span v-else>{{ textOf('category', row) }}</span>
        </template>
        <template #documentType="{ row }">
          <AttachmentTypeSelect v-if="row.kind === 'pending' && canEdit && !readonlyType" :model-value="pendingOf(row).documentType"
            :category="categoryOfRow(row)" :document-types="documentTypes" :source-type="sourceType"
            @update:model-value="(v) => setPendingType(pendingOf(row), v)" />
          <AttachmentTypeSelect v-else-if="canEdit && !readonlyType" :model-value="docTypeOfRow(row)"
            :category="categoryOfRow(row)" :document-types="documentTypes" :source-type="sourceType"
            :disabled="savingId === row.id" @update:model-value="(v) => saveDocType(refOf(row), v)" />
          <span v-else>{{ textOf('documentType', row) }}</span>
        </template>
        <template #actions="{ row }">
          <template v-if="row.kind === 'pending'">
            <el-button v-if="shows('retry', 'pending') && canEdit" link type="primary" @click="emit('retryBind', pendingOf(row))">重试</el-button>
            <el-button v-if="shows('remove', 'pending') && canEdit" link type="danger" @click="emit('removePending', row.attachmentId)">移除</el-button>
            <el-tooltip v-if="!canEdit && lockReason && (shows('retry', 'pending') || shows('remove', 'pending'))" :content="lockReason" placement="top">
              <span>
                <el-button v-if="shows('retry', 'pending')" link type="primary" disabled>重试</el-button>
                <el-button v-if="shows('remove', 'pending')" link type="danger" disabled>移除</el-button>
              </span>
            </el-tooltip>
          </template>
          <template v-else>
            <el-button v-if="shows('download', 'ref')" link type="primary" @click="emit('download', refOf(row))">下载</el-button>
            <el-button v-if="shows('detail', 'ref')" link type="primary" @click="emit('edit', refOf(row))">详情</el-button>
            <el-button v-if="canEdit && shows('remove', 'ref')" link type="danger" @click="emit('unbind', refOf(row))">移除</el-button>
            <el-tooltip v-else-if="shows('remove', 'ref') && lockReason" :content="lockReason" placement="top">
              <span><el-button link type="danger" disabled>移除</el-button></span>
            </el-tooltip>
          </template>
        </template>
      </AttachmentTable>

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

  </div>
</template>

<script setup lang="ts">
/**
 * 公共附件统一表格：待绑定、引用及历史只读附件共用一套列与行渲染。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { PendingEntry } from '@/composables/useAttachmentRefs'
import {
  attachmentRefApi,
  type AttachmentDocumentTypeItem,
  type AttachmentRefItem,
} from '@/api/system/attachmentRef'
import { useAttachmentDocTypes } from './useAttachmentDocTypes'
import AttachmentTypeSelect from './AttachmentTypeSelect.vue'
import AttachmentTable from './AttachmentTable.vue'
import { KIND_ACTIONS_DEFAULT, type AttachmentAction } from './attachmentActions'
import { useAttachmentRowText, type AttachmentRow } from './useAttachmentRowText'

const props = defineProps<{
    rows: AttachmentRefItem[]
    pending: PendingEntry[]
    canEdit: boolean
    /** 资料类型字典（父组件已加载，避免重复请求） */
    documentTypes: AttachmentDocumentTypeItem[]
  /** 改类型时不写变更日志（审核弹窗专用，dev-20261029-014） */
  suppressChangeLog?: boolean
    sourceType: string
    sourceNo?: string
    /** 业务锁定原因（如"已审核，不可再改"）：有值时写操作按钮置灰并显示原因（dev-20261015-028） */
    lockReason?: string
    /** 动作清单（第一阶段①）：由调用方决定操作列显示哪些动作；**不传 = 保持现状**（dev-20261016-002） */
    actions?: AttachmentAction[]
    /** 只读类型（证据区等场景）：隐藏类型选择与批量改类型，但仍保留动作列（需与 canEdit 配合） */
    readonlyType?: boolean
}>()

type TableRow = AttachmentRow & {
  rowKey: string
  kind: 'pending' | 'ref'
  data: PendingEntry | AttachmentRefItem
  id: number
}

const tableRows = computed<TableRow[]>(() => [
  ...props.pending.map((entry) => ({
    rowKey: `pending:${entry.attachmentId}`, kind: 'pending' as const, data: entry,
    id: entry.attachmentId, attachmentId: entry.attachmentId,
    fileName: entry.fileName, displayName: entry.displayName, documentType: entry.documentType,
    fileSize: entry.fileSize, createTime: entry.createdAt,
    sourceType: props.sourceType, sourceNo: props.sourceNo, productCode: entry.productCode,
  })),
  ...props.rows.map((row) => ({ ...row, rowKey: `ref:${row.id}`, kind: 'ref' as const, data: row })),
])
const refOf = (row: TableRow) => row.data as AttachmentRefItem
const pendingOf = (row: TableRow) => row.data as PendingEntry
const { textOf } = useAttachmentRowText(computed(() => props.documentTypes), () => props.sourceType)
/** 是否渲染某动作：调用方传了 actions 则取「清单 ∩ 该行默认可用的动作」，不传则用默认（行为与改前一致） */
function shows(action: AttachmentAction, kind: 'pending' | 'ref'): boolean {
  const base = KIND_ACTIONS_DEFAULT[kind]
  if (!base.includes(action)) return false
  return props.actions ? props.actions.includes(action) : true
}
/** 是否展示「保存」按钮（由调用方在 actions 里声明 `save`；与逐行无关，故不走 shows） */
const hasSaveAction = computed(() => !!props.actions?.includes('save'))
function previewRow(row: TableRow) {
  if (row.kind === 'ref') emit('preview', refOf(row))
}

/** 行内「一级」选择（待绑定与引用行共用）：键=rowKey；一级与二级拆到两列（dev-20261015-033） */
const rowCategories = ref<Record<string, string>>({})
function categoryOfRow(row: TableRow): string {
  const picked = rowCategories.value[row.rowKey]
  if (picked !== undefined) return picked
  return row.documentType ? categoryKeyOfType(row.documentType) : ''
}
/** 一级列独立选择：记录本行一级；若与当前二级所属一级不一致，则作废二级（待重新选） */
async function onRowCategory(row: TableRow, category: string | null) {
  const next = category || ''
  if (next) rowCategories.value[row.rowKey] = next
  else delete rowCategories.value[row.rowKey]
  if (!row.documentType || categoryKeyOfType(row.documentType) === next) return
  if (row.kind === 'pending') {
    pendingOf(row).documentType = null
    emit('pendingChanged')
  } else {
    await saveDocType(refOf(row), null)
  }
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
  /** 批量动作已落库，父组件据此重新拉列表 */
  changed: []
  /** 「保存」动作（调用方在 actions 里声明 'save' 后，表格上方出现该按钮；组件只透传，不定义含义） */
  save: []
  error: [payload: { action: string; message: string }]
}>()

const {
  categoryKeyOfType,
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
    rowCategories.value = {} // 丢弃未落库的一级选择（父组件刷新列表后）
    typeEdits.value = {} // 丢弃未落库的二级类型改动
  }
)

// ==================== 行内编辑（二级类型）====================
const savingId = ref<number | null>(null)
const saveSaving = ref(false)
/**
 * 行内改的二级类型草稿（dev-20261016-006）：当调用方声明了 `save` 动作时，
 * 行内改动**先攒着**、由表格上方「保存」统一提交；未声明 `save` 的页面保持「即改即存」（行为与改前一致）。
 */
const typeEdits = ref<Record<number, string>>({})
/** 二级列当前显示值：引用行优先显示未提交的草稿 */
function docTypeOfRow(row: TableRow): string {
  if (row.kind !== 'ref') return row.documentType ?? ''
  const draft = typeEdits.value[refOf(row).id]
  return draft !== undefined ? draft : (refOf(row).documentType ?? '')
}
/**
 * 行内改二级类型：走 /refs/batch 的「按行部分更新」，只带 documentType，
 * 不会像单条 PUT（整条覆盖）那样把展示名/备注/排序清空；清空时传空串=清类型。
 */
async function saveDocType(row: AttachmentRefItem, value: string | null) {
  const next = value ?? ''
  if (next === (row.documentType ?? '')) {
    delete typeEdits.value[row.id]
    return
  }
  if (!hasSaveAction.value) {
    await inlineSave(row, { documentType: next })
    return
  }
  typeEdits.value[row.id] = next
}
/** 提交攒着的二级类型改动；返回是否成功（无改动 = 成功） */
async function flushTypeEdits(): Promise<boolean> {
  const items = Object.entries(typeEdits.value).map(([refId, documentType]) => ({ refId: Number(refId), documentType }))
  if (!items.length) return true
  try {
    await attachmentRefApi.batchUpdate(items, !!props.suppressChangeLog)
    typeEdits.value = {}
    emit('changed')
    return true
  } catch (e: any) {
    const message = e?.message || '保存失败'
    ElMessage.error(message)
    emit('error', { action: 'flush-type-edits', message })
    return false
  }
}
/** 表格上方「保存」：先提交攒着的类型改动，再把「保存」事件交给调用方（含义由调用方定）。
 *  口径 014 / 001 §7-1（方案 A）：调用方**不应**在此落定附件——落定只由单据「保存」负责。 */
async function onSaveClick() {
  saveSaving.value = true
  try {
    if (!(await flushTypeEdits())) return
    emit('save')
  } finally {
    saveSaving.value = false
  }
}
async function inlineSave(
  row: AttachmentRefItem,
  patch: { documentType?: string }
): Promise<boolean> {
  savingId.value = row.id
  try {
    await attachmentRefApi.batchUpdate([{ refId: row.id, ...patch }], !!props.suppressChangeLog)
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
      ids.map((refId) => ({ refId, documentType: batchDocumentType.value })),
      !!props.suppressChangeLog
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

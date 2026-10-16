<template>
  <div class="attachment-manager">
    <div class="am-header">
      <span class="am-title">{{ title }}</span>
      <el-tag v-if="tableRows.length" size="small" type="info">{{ tableRows.length }} 条</el-tag>
      <el-tag v-if="pendingCount" size="small" type="warning">待保存 {{ pendingCount }}</el-tag>
      <el-tag v-if="session.dirtyCount.value" size="small" type="danger" effect="plain">未保存改动 {{ session.dirtyCount.value }}</el-tag>
      <span class="am-spacer" />
      <el-select
        v-if="showOwnershipFilter && ownershipOptions.length > 1"
        v-model="filterKey"
        size="small"
        class="am-filter"
      >
        <el-option label="全部归属" value="" />
        <el-option v-for="o in ownershipOptions" :key="o.key" :label="o.label || o.key" :value="o.key" />
      </el-select>
    </div>

    <el-alert v-if="session.error.value" :title="session.error.value" type="warning" :closable="false" class="am-alert" />

    <!-- 批量保存：默认隐藏，仅在调用方显式开启且会话已注入处理器时出现 -->
    <div v-if="canEdit && batchSaveEnabled" class="am-bar">
      <el-button
        size="small"
        type="primary"
        :loading="session.saving.value"
        :disabled="!selectedDirty"
        @click="onBatchSave"
      >
        保存选中
      </el-button>
      <span v-if="selectedIds.length" class="am-sub">已选 {{ selectedIds.length }} 条，可保存 {{ selectedDirtyCount }} 条改动</span>
    </div>

    <div v-if="canEdit && tableRows.length" class="am-bar">
      <el-button size="small" :disabled="!selectedIds.length" @click="openBatchType">批量改类型</el-button>
      <el-button size="small" type="danger" plain :disabled="!selectedIds.length" @click="onBatchRemove">批量移除</el-button>
      <span v-if="selectedIds.length" class="am-sub">已选 {{ selectedIds.length }} 条</span>
    </div>

    <el-empty v-if="!tableRows.length" description="暂无附件" :image-size="60" />

    <AttachmentTable
      v-else
      :rows="tableRows"
      :document-types="documentTypes"
      :source-type="sourceType"
      row-key="rowKey"
      :selectable="canEdit ? (row: TableRow) => row.kind === 'ref' : undefined"
      @selection-change="onSelectionChange"
      @preview="previewRow"
      @download="(row) => emit('download', refOf(row))"
      @detail="(row) => onDetail(row)"
    >
      <template #status="{ row }">
        <el-tag
          size="small"
          :title="row.kind === 'pending' ? pendingOf(row).error : undefined"
          :type="row.kind === 'pending' ? (pendingOf(row).state === 'failed' ? 'danger' : 'warning') : 'success'"
        >
          {{ row.kind === 'pending' ? (pendingOf(row).state === 'failed' ? '绑定失败' : '待保存') : '已绑定' }}
        </el-tag>
      </template>
      <template #fileName="{ row }">
        <span v-if="row.kind === 'pending'">{{ textOf('fileName', row) }}</span>
        <el-button v-else link type="primary" @click="previewRow(row)">{{ textOf('fileName', row) }}</el-button>
      </template>
      <template #category="{ row }">
        <AttachmentTypeSelect
          v-if="canEdit"
          category-only
          :category="categoryOfRow(row)"
          :document-types="documentTypes"
          :source-type="sourceType"
          @update:category="(v) => onRowCategory(row, v)"
        />
        <span v-else>{{ textOf('category', row) }}</span>
      </template>
      <template #documentType="{ row }">
        <AttachmentTypeSelect
          v-if="canEdit"
          :model-value="docTypeOfRow(row)"
          :category="categoryOfRow(row)"
          :document-types="documentTypes"
          :source-type="sourceType"
          @update:model-value="(v) => onRowType(row, v)"
        />
        <span v-else>{{ textOf('documentType', row) }}</span>
      </template>
      <template #actions="{ row }">
        <template v-if="row.kind === 'pending'">
          <el-button link type="danger" @click="onRemovePending(row.attachmentId)">移除</el-button>
        </template>
        <template v-else>
          <el-button link type="primary" @click="emit('download', refOf(row))">下载</el-button>
          <el-button link type="primary" @click="onDetail(row)">详情</el-button>
          <el-button v-if="rowSaveEnabled && session.hasEdit(refOf(row).id)" link type="primary" @click="onRowSave(refOf(row))">保存</el-button>
          <el-button v-if="canEdit" link type="danger" @click="onRemoveRef(refOf(row))">移除</el-button>
        </template>
      </template>
    </AttachmentTable>

    <div v-if="pendingCount" class="am-hint">待保存附件已上传到服务器，保存单据后自动建立引用。</div>

    <!-- 详情（备注/排序为「信息类」改动：先攒进会话，由保存入口统一提交，绝不即时提交） -->
    <el-dialog v-model="metaVisible" title="附件详情" width="800px" append-to-body destroy-on-close>
      <AttachmentDetailInfo :row="metaRow" :document-types="documentTypes" :source-type="sourceType" />
      <el-form label-width="80px" :disabled="!canEdit">
        <el-form-item label="备注">
          <el-input v-model="metaForm.remark" maxlength="500" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="metaForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <div class="am-hint">这些改动会先记录在会话里，随「保存」统一提交（不会即时入库）。</div>
      <template #footer>
        <el-button @click="metaVisible = false">取消</el-button>
        <el-button v-if="canEdit" type="primary" @click="applyMeta">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量改类型（同样先攒进会话） -->
    <el-dialog v-model="batchTypeVisible" title="批量改类型" width="440px" append-to-body>
      <AttachmentTypeSelect
        v-model="batchDocumentType"
        :category="batchCategory"
        :document-types="documentTypes"
        :source-type="sourceType"
        show-category
        @update:category="onBatchCategoryChange"
      />
      <div class="am-hint" style="margin-top: 8px">
        对选中的 {{ selectedIds.length }} 条统一设置二级类型（一级仅用于筛选）；随「保存」统一提交。
      </div>
      <template #footer>
        <el-button @click="batchTypeVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!batchDocumentType" @click="applyBatchType">确定</el-button>
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
 * 独立附件管理组件（dev-20261016-048 第一阶段 A）。
 *
 * 定位：只做「统一列表 + 归属筛选 + 信息编辑 + 详情 + 预览下载 + 即时移除」，
 * **不内置上传**（上传入口由调用方持会话自行发起）；不判断报价/订单业务状态。
 *
 * 状态边界：所有数据/编辑差异都在传入的共享会话（AttachmentSession）里，**不保留第二套可变状态**。
 * 信息编辑（二级类型/备注/排序）只写进会话的编辑差异，**绝不即时提交**；由会话的保存入口统一提交。
 * 行内/批量保存按钮**默认隐藏**，仅在调用方显式开启且会话已注入处理器时出现。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AttachmentDocumentTypeItem, AttachmentRefItem } from '@/api/system/attachmentRef'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import type { AttachmentSession, AttachmentOwnership } from '@/composables/useAttachmentSession'
import {
  ownershipKeyOfPending,
  ownershipKeyOfRef,
} from '@/composables/useAttachmentSession'
import type { PendingEntry } from '@/composables/useAttachmentRefs'
import { useAttachmentDocTypes } from './useAttachmentDocTypes'
import { useAttachmentRowText, type AttachmentRow } from './useAttachmentRowText'
import { useAttachmentPreview } from './useAttachmentPreview'
import AttachmentTable from './AttachmentTable.vue'
import AttachmentTypeSelect from './AttachmentTypeSelect.vue'
import AttachmentDetailInfo from './AttachmentDetailInfo.vue'

const props = withDefaults(
  defineProps<{
    session: AttachmentSession
    title?: string
    sourceType?: string
    sourceNo?: string
    /** 业务锁定原因（调用方按业务传）：只读/无权限不得产生写副作用 */
    lockReason?: string
    /** 行内保存：默认隐藏；启用需会话已注入保存处理器 */
    rowSave?: boolean
    /** 批量保存：默认隐藏；启用需会话已注入保存处理器 */
    batchSave?: boolean
    showOwnershipFilter?: boolean
  }>(),
  {
    title: '附件',
    sourceType: '',
    sourceNo: '',
    lockReason: '',
    rowSave: false,
    batchSave: false,
    showOwnershipFilter: true,
  },
)

const emit = defineEmits<{
  changed: [payload: { count: number }]
  error: [payload: { action: string; message: string }]
  download: [row: AttachmentRefItem]
  detail: [row: AttachmentRefItem]
}>()

const session = props.session
const canEdit = computed(() => session.canEdit.value)
const documentTypes = session.documentTypes

/** 保存入口只有在「调用方开启 + 会话有处理器」时才出现（无处理器不开放入口） */
const rowSaveEnabled = computed(() => !!(props.rowSave && session.canSave.value))
const batchSaveEnabled = computed(() => !!(props.batchSave && session.canSave.value))

const { categoryKeyOfType } = useAttachmentDocTypes(documentTypes, () => props.sourceType)

// ==================== 归属筛选（只改显示范围，不丢隐藏行改动） ====================
const ownershipOptions = computed<AttachmentOwnership[]>(() => session.ownerships.value)
const filterKey = ref('')
const matchesFilter = (key: string) => !filterKey.value || key === filterKey.value
const visibleRefs = computed(() => session.refRows.value.filter((r) => matchesFilter(ownershipKeyOfRef(r))))
const visiblePending = computed(() => session.pending.value.filter((e) => matchesFilter(ownershipKeyOfPending(e))))
const pendingCount = computed(() => session.pending.value.length)

// ==================== 表格行（镜像 RefList 的行结构；复用 AttachmentTable） ====================
type TableRow = AttachmentRow & {
  rowKey: string
  kind: 'pending' | 'ref'
  data: PendingEntry | AttachmentRefItem
  id: number
}
const tableRows = computed<TableRow[]>(() => [
  ...visiblePending.value.map((entry) => ({
    rowKey: `pending:${entry.attachmentId}`,
    kind: 'pending' as const,
    data: entry,
    id: entry.attachmentId,
    attachmentId: entry.attachmentId,
    fileName: entry.fileName,
    displayName: entry.displayName,
    documentType: entry.documentType,
    fileSize: entry.fileSize,
    createTime: entry.createdAt,
    sourceType: props.sourceType,
    sourceNo: props.sourceNo,
    productCode: entry.productCode,
  })),
  ...visibleRefs.value.map((row) => ({ ...row, rowKey: `ref:${row.id}`, kind: 'ref' as const, data: row })),
])
const refOf = (row: TableRow) => row.data as AttachmentRefItem
const pendingOf = (row: TableRow) => row.data as PendingEntry
const { textOf } = useAttachmentRowText(documentTypes, () => props.sourceType)

// ==================== 勾选 ====================
const selectedIds = ref<number[]>([])
function onSelectionChange(rows: TableRow[]) {
  selectedIds.value = rows.filter((r) => r.kind === 'ref').map((r) => r.id)
}
// 列表/筛选刷新后，旧勾选作废（隐藏行的改动仍在会话里，不受影响）
watch(() => [session.refRows.value, filterKey.value], () => { selectedIds.value = [] })
const selectedDirtyCount = computed(() => selectedIds.value.filter((id) => session.hasEdit(id)).length)
const selectedDirty = computed(() => selectedDirtyCount.value > 0)

// ==================== 行内编辑（写进会话编辑差异，不即时提交） ====================
const rowCategories = ref<Record<string, string>>({})
function categoryOfRow(row: TableRow): string {
  const picked = rowCategories.value[row.rowKey]
  if (picked !== undefined) return picked
  const code = docTypeOfRow(row)
  return code ? categoryKeyOfType(code) : ''
}
function docTypeOfRow(row: TableRow): string {
  if (row.kind === 'pending') return pendingOf(row).documentType ?? ''
  return session.effectiveDocumentType(refOf(row).id, refOf(row).documentType)
}
function onRowType(row: TableRow, value: string | null) {
  if (!canEdit.value) return
  if (row.kind === 'pending') {
    pendingOf(row).documentType = value
    session.persist()
    return
  }
  session.setEdit(refOf(row).id, { documentType: value })
}
function onRowCategory(row: TableRow, category: string | null) {
  if (!canEdit.value) return
  const next = category || ''
  if (next) rowCategories.value[row.rowKey] = next
  else delete rowCategories.value[row.rowKey]
  const current = docTypeOfRow(row)
  if (!current || categoryKeyOfType(current) === next) return
  onRowType(row, null)
}

// ==================== 详情 / 批量改类型（攒进会话） ====================
const metaVisible = ref(false)
const metaRow = ref<AttachmentRefItem | null>(null)
const metaForm = reactive({ remark: '', sortOrder: 0 })
function onDetail(row: TableRow) {
  if (row.kind !== 'ref') return
  const refRow = refOf(row)
  metaRow.value = refRow
  const edit = session.editOf(refRow.id)
  metaForm.remark = (edit && 'remark' in edit ? edit.remark : refRow.remark) ?? ''
  metaForm.sortOrder = (edit && 'sortOrder' in edit ? edit.sortOrder : refRow.sortOrder) ?? 0
  metaVisible.value = true
  emit('detail', refRow)
  void session.loadDocumentTypes()
}
function applyMeta() {
  if (!canEdit.value || !metaRow.value) return
  session.setEdit(metaRow.value.id, { remark: metaForm.remark || null, sortOrder: metaForm.sortOrder || 0 })
  metaVisible.value = false
}

const batchTypeVisible = ref(false)
const batchCategory = ref('')
const batchDocumentType = ref<string | null>(null)
function openBatchType() {
  batchCategory.value = ''
  batchDocumentType.value = null
  batchTypeVisible.value = true
}
function onBatchCategoryChange(category: string) {
  batchCategory.value = category
  if (batchDocumentType.value && categoryKeyOfType(batchDocumentType.value) !== category) batchDocumentType.value = null
}
function applyBatchType() {
  if (!batchDocumentType.value || !selectedIds.value.length) return
  for (const refId of selectedIds.value) session.setEdit(refId, { documentType: batchDocumentType.value })
  batchTypeVisible.value = false
}

// ==================== 保存（范围契约：行内=当前行；批量=勾选行） ====================
async function runSave(scope: { kind: 'row'; refId: number } | { kind: 'rows'; refIds: number[] }) {
  if (!session.canSave.value) return
  const changes = session.getChanges(scope)
  if (!changes.total) return
  const check = session.validate(scope)
  if (!check.ok) {
    ElMessage.warning(check.issues[0]?.message || '存在未完成的改动')
    return
  }
  try {
    await session.saveChanges(changes)
    ElMessage.success('已保存')
    emit('changed', { count: session.refRows.value.length })
  } catch (e: any) {
    const message = e?.message || '保存失败'
    ElMessage.error(message)
    emit('error', { action: 'save', message })
  }
}
function onRowSave(row: AttachmentRefItem) {
  return runSave({ kind: 'row', refId: row.id })
}
function onBatchSave() {
  return runSave({ kind: 'rows', refIds: [...selectedIds.value] })
}

// ==================== 即时移除（草稿移除队列项 / 已绑定解除引用；不删文件） ====================
function onRemovePending(attachmentId: number) {
  session.removePending(attachmentId)
  emit('changed', { count: session.refRows.value.length })
}
async function onRemoveRef(row: AttachmentRefItem) {
  try {
    await ElMessageBox.confirm(
      '解除本单据对该附件的引用？仅解除引用，物理文件与其他单据的引用保留。',
      '解除引用',
      { confirmButtonText: '确定解除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await session.unbind(row.id)
    selectedIds.value = selectedIds.value.filter((id) => id !== row.id)
    ElMessage.success('已解除引用')
    emit('changed', { count: session.refRows.value.length })
  } catch (e: any) {
    const message = e?.message || '解除引用失败'
    ElMessage.error(message)
    emit('error', { action: 'unbind', message })
  }
}
async function onBatchRemove() {
  const ids = [...selectedIds.value]
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(
      `从本单据移除选中的 ${ids.length} 个附件？只解除本单据的引用，文件本身不删除。`,
      '批量移除',
      { confirmButtonText: '确定移除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  let ok = 0
  let failed = 0
  for (const refId of ids) {
    try {
      await session.unbind(refId)
      ok++
      selectedIds.value = selectedIds.value.filter((id) => id !== refId)
    } catch (e: any) {
      failed++
      emit('error', { action: 'unbind', message: e?.message || '解除引用失败' })
    }
  }
  if (failed) ElMessage.warning(`已移除 ${ok} 条，${failed} 条失败（保留原状）`)
  else ElMessage.success(`已移除 ${ok} 条引用`)
  emit('changed', { count: session.refRows.value.length })
}

// ==================== 预览 / 下载（复用唯一实现） ====================
const { viewerVisible, viewerList, viewerIndex, preview: previewViewer, close: closeViewer } = useAttachmentPreview()
function previewRow(row: TableRow) {
  if (row.kind !== 'ref') return
  previewViewer(session.refRows.value, refOf(row), (r) => attachmentRefApi.previewUrlByRef(r.id))
}

defineExpose({
  refresh: () => session.refresh(),
  clearSelection: () => { selectedIds.value = [] },
})
</script>

<style scoped>
.attachment-manager {
  padding: 4px 0;
}
.am-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.am-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
.am-spacer {
  flex: 1;
}
.am-filter {
  width: 180px;
}
.am-alert {
  margin-bottom: 8px;
}
.am-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.am-sub,
.am-hint {
  font-size: 12px;
  color: #909399;
}
</style>

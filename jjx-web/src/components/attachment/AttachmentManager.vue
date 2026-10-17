<template>
  <div class="attachment-manager">
    <div class="am-header">
      <span class="am-title">{{ title }}</span>
      <el-tag v-if="totalCount" size="small" type="info">{{ totalCount }} 条</el-tag>
      <span v-if="filterKey" class="am-sub">当前显示 {{ tableRows.length }} 条</span>
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

    <el-empty v-if="!tableRows.length" :description="filterKey ? '当前筛选无附件' : '暂无附件'" :image-size="60" />

    <AttachmentTable
      v-else
      :rows="tableRows"
      :document-types="documentTypes"
      :source-type="sourceType"
      row-key="rowKey"
      :selectable="canEdit ? (row: TableRow) => capabilities(row).canSelect : undefined"
      @selection-change="onSelectionChange"
      @preview="previewRow"
      @download="downloadRow"
      @detail="(row) => onDetail(row)"
    >
      <template #status="{ row }">
        <el-tag
          size="small"
          :title="row.kind === 'pending' ? row.data.error : capabilities(row).readonlyReason"
          v-bind="AttachmentDocumentStatusEnum.getTagProps(row.status)"
        >
          {{ AttachmentDocumentStatusEnum.getLabel(row.status) }}{{ row.kind === 'readonly' ? '·只读' : '' }}
        </el-tag>
      </template>
      <template #fileName="{ row }">
        <span v-if="row.kind === 'pending'">{{ textOf('fileName', row) }}</span>
        <el-button v-else link type="primary" @click="previewRow(row)">{{ textOf('fileName', row) }}</el-button>
      </template>
      <template #category="{ row }">
        <AttachmentTypeSelect
          v-if="capabilities(row).editable"
          category-only
          :category="categoryOfRow(row)"
          :document-types="documentTypes"
          :source-type="sourceType"
          :disabled="capabilities(row).saving"
          @update:category="(v) => onRowCategory(row, v)"
        />
        <span v-else>{{ textOf('category', row) }}</span>
      </template>
      <template #documentType="{ row }">
        <AttachmentTypeSelect
          v-if="capabilities(row).editable"
          :model-value="docTypeOfRow(row)"
          :category="categoryOfRow(row)"
          :document-types="documentTypes"
          :source-type="sourceType"
          :disabled="capabilities(row).saving"
          @update:model-value="(v) => onRowType(row, v)"
        />
        <span v-else>{{ textOf('documentType', row) }}</span>
      </template>
      <template #actions="{ row }">
        <template v-if="row.kind === 'pending'">
          <el-button v-if="capabilities(row).canRemove" link type="danger" @click="onRemovePending(row.attachmentId)">移除</el-button>
        </template>
        <template v-else-if="row.kind === 'ref'">
          <el-button link type="primary" @click="onDownload(row.data)">下载</el-button>
          <el-button link type="primary" @click="onDetail(row)">详情</el-button>
          <el-button v-if="rowSaveEnabled && capabilities(row).canSave && session.hasEdit(row.refId)" link type="primary" @click="onRowSave(row)">保存</el-button>
          <el-button v-if="capabilities(row).canRemove" link type="danger" @click="onRemoveRef(row)">移除</el-button>
        </template>
        <template v-else>
          <el-button link type="primary" @click="downloadRow(row)">下载</el-button>
          <el-button link type="primary" @click="onDetail(row)">详情</el-button>
        </template>
      </template>
    </AttachmentTable>

    <div v-if="session.traceLoading.value" class="am-hint">正在加载同链路文档…</div>
    <el-alert
      v-if="session.traceError.value"
      :title="session.traceError.value"
      type="warning"
      :closable="false"
      class="am-alert"
    />

    <div v-if="pendingCount" class="am-hint">待保存附件已上传到服务器，保存单据后自动建立引用。</div>

    <AttachmentManagerDetailDialog
      v-model="metaVisible"
      :row="detailRow"
      :document-types="documentTypes"
      :source-type="sourceType"
      :editable="metaEditable"
      :readonly-reason="detailReadonlyReason"
      :remark="detailRemark"
      :sort-order="detailSortOrder"
      @confirm="applyMeta"
    />

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
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AttachmentRefItem } from '@/api/system/attachmentRef'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import { attachmentApi } from '@/api/system/attachment'
import { AttachmentDocumentStatusEnum } from '@/enums/system/AttachmentEnum'
import type { AttachmentSession } from '@/composables/useAttachmentSession'
import { useAttachmentDocTypes } from './useAttachmentDocTypes'
import { useAttachmentRowText } from './useAttachmentRowText'
import { useAttachmentPreview } from './useAttachmentPreview'
import AttachmentTable from './AttachmentTable.vue'
import AttachmentTypeSelect from './AttachmentTypeSelect.vue'
import AttachmentManagerDetailDialog from './AttachmentManagerDetailDialog.vue'
import { useAttachmentManagerRows, type AttachmentManagerRow as TableRow } from './useAttachmentManagerRows'

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
  changed: [payload: { count: number; dirtyCount: number }]
  error: [payload: { action: string; message: string }]
  download: [row: AttachmentRefItem]
  detail: [row: AttachmentRefItem]
}>()

const session = props.session
const canEdit = computed(() => session.canEdit.value)
const documentTypes = session.documentTypes
// 061：Manager 挂载即加载资料类型字典（原来只在「打开详情」时才加载，导致刚上传后一级/二级下拉为空）
onMounted(() => { void session.loadDocumentTypes() })
/** 列表变化 + 编辑差异实时通知调用方（仅条数不足以反映信息编辑） */
function notifyChanged() {
  emit('changed', { count: session.refRows.value.length, dirtyCount: session.dirtyCount.value })
}

/** 保存入口只有在「调用方开启 + 会话有处理器」时才出现（无处理器不开放入口） */
const rowSaveEnabled = computed(() => !!(props.rowSave && session.canSave.value))
const batchSaveEnabled = computed(() => !!(props.batchSave && session.canSave.value))

const { categoryKeyOfType } = useAttachmentDocTypes(documentTypes, () => props.sourceType)

// 行模型仅派生展示数据，编辑差异仍由共享会话持有。
const { tableRows, filterKey, ownershipOptions, pendingCount, totalCount, capabilities, refRow } =
  useAttachmentManagerRows(session, () => props)
const { textOf } = useAttachmentRowText(documentTypes, () => props.sourceType)

// ==================== 勾选 ====================
const selectedIds = ref<number[]>([])
function onSelectionChange(rows: TableRow[]) {
  selectedIds.value = rows.flatMap((row) => row.kind === 'ref' && capabilities(row).canSelect ? [row.refId] : [])
}
// 列表/筛选刷新后，旧勾选作废（隐藏行的改动仍在会话里，不受影响）
watch(() => [session.refRows.value, filterKey.value], () => { selectedIds.value = [] })
const selectedRefIds = computed(() => selectedIds.value.filter((id) => {
  const row = refRow(id)
  return row && capabilities(row).canSelect
}))
const selectedDirtyCount = computed(() => selectedRefIds.value.filter((id) => session.hasEdit(id)).length)
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
  if (row.kind === 'ref') return session.effectiveDocumentType(row.refId, row.data.documentType)
  return row.kind === 'pending' ? row.data.documentType ?? '' : row.documentType ?? ''
}
function onRowType(row: TableRow, value: string | null) {
  if (!capabilities(row).canEdit) return
  if (row.kind === 'pending') {
    row.data.documentType = value
    session.persist()
    notifyChanged()
    return
  }
  if (row.kind !== 'ref') return
  session.setEdit(row.refId, { documentType: value })
  notifyChanged()
}
function onRowCategory(row: TableRow, category: string | null) {
  if (!capabilities(row).canEdit) return
  const next = category || ''
  if (next) rowCategories.value[row.rowKey] = next
  else delete rowCategories.value[row.rowKey]
  const current = docTypeOfRow(row)
  if (!current || categoryKeyOfType(current) === next) return
  onRowType(row, null)
}

// ==================== 详情 / 批量改类型（攒进会话） ====================
const metaVisible = ref(false)
const detailRow = ref<TableRow | null>(null)
const metaEditable = computed(() => !!detailRow.value && capabilities(detailRow.value).canEdit)
const detailReadonlyReason = computed(() => detailRow.value ? capabilities(detailRow.value).readonlyReason : '')
const detailRemark = computed(() => {
  if (detailRow.value?.kind !== 'ref') return ''
  const edit = session.editOf(detailRow.value.refId)
  return (edit && 'remark' in edit ? edit.remark : detailRow.value.data.remark) ?? ''
})
const detailSortOrder = computed(() => {
  if (detailRow.value?.kind !== 'ref') return 0
  const edit = session.editOf(detailRow.value.refId)
  return (edit && 'sortOrder' in edit ? edit.sortOrder : detailRow.value.data.sortOrder) ?? 0
})
function onDetail(row: TableRow) {
  if (row.kind === 'pending') return
  detailRow.value = row
  metaVisible.value = true
  if (row.kind === 'readonly') return
  const refRow = row.data
  emit('detail', refRow)
  void session.loadDocumentTypes()
}
function applyMeta(value: { remark: string; sortOrder: number }) {
  if (detailRow.value?.kind !== 'ref') return
  const row = refRow(detailRow.value.refId)
  if (!row || !capabilities(row).canEdit) return
  session.setEdit(detailRow.value.refId, { remark: value.remark || null, sortOrder: value.sortOrder || 0 })
  metaVisible.value = false
  notifyChanged()
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
  if (!batchDocumentType.value || !selectedRefIds.value.length) return
  for (const refId of selectedRefIds.value) session.setEdit(refId, { documentType: batchDocumentType.value })
  batchTypeVisible.value = false
  notifyChanged()
}

// ==================== 保存（范围契约：行内=当前行；批量=勾选行） ====================
async function runSave(scope: { kind: 'row'; refId: number } | { kind: 'rows'; refIds: number[] }) {
  if (!canEdit.value || !session.canSave.value) return
  const ids = scope.kind === 'row' ? [scope.refId] : scope.refIds
  if (!ids.every((id) => {
    const row = refRow(id)
    return row && capabilities(row).canSave
  })) return
  const changes = session.getChanges(scope)
  if (!changes.total) return
  try {
    const committed = await session.commit(scope)
    if (!committed.validation.ok) {
      ElMessage.warning(committed.validation.issues[0]?.message || '存在未完成的改动')
      return
    }
    const failed = committed.result?.failed?.length ?? 0
    if (failed) ElMessage.warning(`已保存 ${changes.total - failed} 条，${failed} 条失败（保留待重试）`)
    else ElMessage.success('已保存')
    notifyChanged()
  } catch (e: any) {
    const message = e?.message || '保存失败'
    ElMessage.error(message)
    emit('error', { action: 'save', message })
  }
}
function onRowSave(row: TableRow) {
  if (row.kind !== 'ref' || !capabilities(row).canSave) return
  return runSave({ kind: 'row', refId: row.refId })
}
function onBatchSave() {
  return runSave({ kind: 'rows', refIds: [...selectedRefIds.value] })
}

// ==================== 即时移除（草稿移除队列项 / 已绑定解除引用；不删文件） ====================
async function onRemovePending(attachmentId: number) {
  try {
    if (!canEdit.value) {
      throw new Error(props.lockReason || '当前附件为只读、无编辑权限或已被业务锁定，不能移除待保存附件')
    }
    const row = tableRows.value.find(row => row.kind === 'pending' && row.attachmentId === attachmentId)
    if (!row || !capabilities(row).canRemove) return
    await session.removePending(attachmentId) // 先删草稿行，成功才清队列项
    notifyChanged()
  } catch (e: any) {
    const message = e?.message || '移除失败（已保留原行）'
    ElMessage.error(message)
    emit('error', { action: 'remove-pending', message })
  }
}
async function onRemoveRef(row: TableRow) {
  if (row.kind !== 'ref' || !capabilities(row).canRemove) return
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
    const current = refRow(row.refId)
    if (!current || !capabilities(current).canRemove) return
    await session.unbind(row.refId)
    selectedIds.value = selectedIds.value.filter((id) => id !== row.refId)
    ElMessage.success('已解除引用')
    notifyChanged()
  } catch (e: any) {
    const message = e?.message || '解除引用失败'
    ElMessage.error(message)
    emit('error', { action: 'unbind', message })
  }
}
async function onBatchRemove() {
  const ids = [...selectedRefIds.value]
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
      const row = refRow(refId)
      if (!row || !capabilities(row).canRemove) throw new Error('附件已不可移除，请刷新后重试')
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
  notifyChanged()
}

// ==================== 预览 / 下载（复用唯一实现） ====================
const { viewerVisible, viewerList, viewerIndex, preview: previewViewer, openUrl, close: closeViewer } = useAttachmentPreview()
function previewRow(row: TableRow) {
  if (row.kind === 'pending') return
  // 预览工具以数字id定位图片：使用当前展示行序号，避免文件ID和引用ID碰撞或多引用串页。
  const previews = tableRows.value.filter(item => item.kind !== 'pending').map((item, id) => ({
    id, fileName: item.fileName, fileType: item.fileType, url: previewUrl(item), rowKey: item.rowKey,
  }))
  const selected = previews.find(item => item.rowKey === row.rowKey)
  if (selected) previewViewer(previews, selected, item => previews[item.id].url)
}
function previewUrl(row: TableRow): string {
  return row.refId != null ? attachmentRefApi.previewUrlByRef(row.refId) : attachmentApi.downloadUrl(row.attachmentId)
}
function downloadRow(row: TableRow) {
  if (row.kind === 'pending') return
  if (row.kind === 'ref') return onDownload(row.data)
  openUrl(row.refId != null ? attachmentRefApi.downloadUrlByRef(row.refId) : attachmentApi.downloadUrl(row.attachmentId))
}
/** 下载：复用既有下载工具真正打开文件 URL（不是无效按钮），并向外 emit 供上层埋点/处理 */
function onDownload(row: AttachmentRefItem) {
  openUrl(attachmentRefApi.downloadUrlByRef(row.id))
  emit('download', row)
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

<template>
  <div class="ar-list">
    <!-- ===== 新：表格 + 勾选 + 批量（默认） ===== -->
    <template v-if="variant === 'table'">
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
        :data="rows"
        size="small"
        row-key="id"
        style="width: 100%"
        @selection-change="onSelectionChange"
      >
        <el-table-column v-if="canEdit" type="selection" width="42" />
        <el-table-column v-for="column in referenceColumns" :key="column.key"
          :label="column.label" :width="'width' in column ? column.width : undefined"
          :min-width="'minWidth' in column ? column.minWidth : undefined"
          :show-overflow-tooltip="column.render === 'preview' || column.render === 'category' || column.render === 'text'">
          <template #default="{ row }">
            <span v-if="column.render === 'preview'" class="ar-file" title="点击预览" @click="emit('preview', row)">
              <el-icon class="ar-eye"><View /></el-icon>{{ row.fileName || `附件 #${row.attachmentId}` }}
            </span>
            <template v-else-if="column.render === 'displayName'">
              <el-input v-if="canEdit" :model-value="nameDraft(row)" size="small" maxlength="255"
                placeholder="留空=用原文件名" @update:model-value="(v: string) => setNameDraft(row, v)"
                @change="() => saveDisplayName(row)" />
              <span v-else>{{ row.displayName || row.fileName || `附件 #${row.attachmentId}` }}</span>
            </template>
            <span v-else-if="column.render === 'category'">{{ categoryLabelOf(row.documentType) }}</span>
            <template v-else-if="column.render === 'typeSelect'">
              <AttachmentTypeSelect v-if="canEdit" :model-value="row.documentType"
                :document-types="documentTypes" :source-type="sourceType"
                :disabled="savingId === row.id" @update:model-value="(v) => saveDocType(row, v)" />
              <span v-else>{{ secondLabelOf(row.documentType) }}</span>
            </template>
            <span v-else-if="column.render === 'size'">{{ formatSize(row.fileSize) }}</span>
            <span v-else-if="column.render === 'time'">{{ formatTime(row.createTime) }}</span>
            <span v-else-if="column.render === 'text'">{{ row.createBy || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="emit('download', row)">下载</el-button>
            <el-button v-if="canEdit" link type="primary" @click="emit('edit', row)">更多</el-button>
            <el-button v-if="canEdit" link type="danger" @click="emit('unbind', row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- ===== 旧：一行行（保留，供切换/对比；无勾选、无批量） ===== -->
    <template v-else>
      <div class="ar-rows">
        <div v-for="row in rows" :key="`ref-${row.id}`" class="ar-row">
          <el-icon class="ar-icon"><Document /></el-icon>
          <span class="ar-name" :title="row.displayName || ''">{{ legacyDisplayName(row) }}</span>
          <span class="ar-sub">{{ formatSize(row.fileSize) }}</span>
          <span class="ar-sub">· {{ row.createBy }}</span>
          <span class="ar-sub">· {{ formatTime(row.createTime) }}</span>
          <span class="ar-spacer" />
          <el-button link type="primary" @click="emit('preview', row)">预览</el-button>
          <el-button link type="primary" @click="emit('download', row)">下载</el-button>
          <el-button v-if="canEdit" link type="primary" @click="emit('edit', row)"
            >分类/命名</el-button
          >
          <el-button v-if="canEdit" link type="danger" @click="emit('unbind', row)">移除</el-button>
        </div>
      </div>
    </template>

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
        命名规则：来源类型编码-来源Id-P产品id-一级码-二级码-二级名（来源类型编码取单号前缀，如
        INQ/QT/SO；产品id 为空则整段跳过）。
      </div>
      <div class="ar-sub" style="margin-top: 6px">
        共 {{ batchNameRows.length }} 条；示例（第 1 条）：
      </div>
      <div class="ar-preview">{{ batchNamePreview }}</div>
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
  </div>
</template>

<script setup lang="ts">
/**
 * 附件「引用」列表（A 段）——2026-10-13 dev-20261013-013 从 AttachmentCenter 抽出。
 *
 * 两种样子同时保留，`variant` 切换，默认 'table'（新）：
 *   - 'table'：标题行 + 勾选框 + 批量（改类型/改名/移除）
 *   - 'list' ：一行行 + 行内按钮（旧样子，无勾选/批量，供对比）
 *
 * 组件只负责「这一段长什么样 + 批量动作 + 勾选状态」；预览/下载/编辑/解绑由父组件处理
 * （父组件统一持有图片查看器与弹窗），避免同一能力两处实现。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, View } from '@element-plus/icons-vue'
import {
  attachmentRefApi,
  type AttachmentDocumentTypeItem,
  type AttachmentRefItem,
} from '@/api/system/attachmentRef'
import { useAttachmentDocTypes } from './useAttachmentDocTypes'
import { attachmentSourceCode } from '@/enums/system/AttachmentEnum'
import AttachmentTypeSelect from './AttachmentTypeSelect.vue'
import { referenceColumns } from './attachmentFields'
import { formatSize, formatTime } from './attachmentFormat'

const props = withDefaults(
  defineProps<{
    rows: AttachmentRefItem[]
    canEdit: boolean
    /** 'table'（默认，新）| 'list'（旧样子） */
    variant?: 'table' | 'list'
    /** 资料类型字典（父组件已加载，避免重复请求） */
    documentTypes: AttachmentDocumentTypeItem[]
    sourceType: string
  }>(),
  { variant: 'table' }
)

const emit = defineEmits<{
  preview: [row: AttachmentRefItem]
  download: [row: AttachmentRefItem]
  edit: [row: AttachmentRefItem]
  unbind: [row: AttachmentRefItem]
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
function onSelectionChange(rows: AttachmentRefItem[]) {
  selectedIds.value = rows.map((r) => r.id)
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
const batchNameVisible = ref(false)
const batchNameSaving = ref(false)
const batchNameRows = ref<{ refId: number; name: string; overflow: boolean }[]>([])
const batchNamePreview = computed(() => batchNameRows.value[0]?.name || '')
const batchNameHasOverflow = computed(() => batchNameRows.value.some((r) => r.overflow))
/**
 * 命名规则：来源类型编码-来源Id-P产品id-一级码-二级码-二级名
 * 例：INQ-2-P123-CUSTOMER_DOC-CUSTOMER_DRAWING-客户图纸
 * 来源类型编码取单号前缀登记表（AttachmentEnum.attachmentSourceCode）；产品id 为空时整段跳过。
 */
function buildRowDisplayName(row: AttachmentRefItem): string {
  return [
    attachmentSourceCode(row.sourceType),
    row.sourceId == null ? '' : String(row.sourceId),
    row.productId == null ? '' : `P${row.productId}`,
    categoryKeyOfType(row.documentType) || '',
    row.documentType || '',
    row.documentType ? secondLabelOf(row.documentType) : '',
  ]
    .filter((v) => v !== '')
    .join(NAME_SEP)
}
function openBatchName() {
  batchNameRows.value = selectedRows.value.map((row) => {
    const name = buildRowDisplayName(row)
    return { refId: row.id, name, overflow: name.length > 255 }
  })
  batchNameVisible.value = true
}
async function applyBatchName() {
  const items = batchNameRows.value.map((r) => ({ refId: r.refId, displayName: r.name }))
  if (!items.length) return
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

// ==================== 展示 ====================
/** 旧样式的名字：展示层拼「二级类型 · 展示名」（不写库） */
function legacyDisplayName(row: AttachmentRefItem): string {
  const base = row.displayName || row.fileName || `附件 #${row.attachmentId}`
  return row.documentType ? `${secondLabelOf(row.documentType)} · ${base}` : base
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

/* 旧样子（variant='list'） */
.ar-rows {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafafa;
  font-size: 13px;
}
.ar-icon {
  color: #409eff;
  font-size: 18px;
}
.ar-name {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ar-spacer {
  flex: 1;
}
</style>

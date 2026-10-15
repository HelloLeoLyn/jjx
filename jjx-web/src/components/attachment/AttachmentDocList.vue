<template>
  <section class="attachment-doc-list">
    <el-divider content-position="left">{{ title }}</el-divider>
    <el-alert
      v-for="message in messages"
      :key="message"
      :title="message"
      type="warning"
      :closable="false"
      show-icon
    />
    <AttachmentTable
      v-loading="loading" :rows="rows" :document-types="documentTypes"
      :source-type="sourceType" :columns="columns ?? readonlyDocColumns"
      :empty-text="hasQuery ? '暂无文档' : '未指定查询条件'"
      @preview="previewRow" @download="(row) => openUrl(downloadUrl(row))" @detail="openDetail"
    />
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerList"
      :initial-index="viewerIndex"
      @close="close"
    />
    <!-- 附件详情（只读；与可写表共用同一份信息块） -->
    <el-dialog v-model="detailVisible" title="附件详情" width="520px" append-to-body>
      <AttachmentDetailInfo :row="detailRow" :document-types="documentTypes" :source-type="sourceType" />
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { attachmentApi } from '@/api/system/attachment'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import { readonlyDocColumns, type AttachmentFieldKey } from './attachmentFields'
import AttachmentDetailInfo from './AttachmentDetailInfo.vue'
import AttachmentTable from './AttachmentTable.vue'
import { useAttachmentPreview, type PreviewRow } from './useAttachmentPreview'
import {
  useAttachmentDocList,
  docQueryOf,
  type AttachmentDocQuery,
  type AttachmentDocRow,
  type AttachmentDocScope,
} from './useAttachmentDocList'

const props = withDefaults(
  defineProps<
    AttachmentDocQuery & {
      columns?: readonly AttachmentFieldKey[]
      title?: string
      /** 受控行：调用方直接给行（已排序去重）；给了就只渲染，不按参数查询 */
      rows?: AttachmentDocRow[] | null
      /** 具名查询范围（推荐）：与散参数二选一，多个 = 并集；见 docQueryOf */
      scopes?: AttachmentDocScope[]
    }
  >(),
  { title: '文档', includeLegacy: undefined, rows: undefined, scopes: undefined }
)
/** 受控行模式：不发起文档查询，只加载资料类型字典（供分类/类型标签渲染） */
const isControlled = computed(() => props.rows != null)
const {
  rows: queriedRows,
  loading,
  messages,
  documentTypes,
  hasQuery: queriedHasQuery,
  reload,
} = useAttachmentDocList(
  () =>
    isControlled.value
      ? {}
      : props.scopes?.length
        ? { ...props, ...docQueryOf(...props.scopes) }
        : props,
  { typesOnly: () => isControlled.value }
)
const rows = computed(() => (isControlled.value ? (props.rows ?? []) : queriedRows.value))
const hasQuery = computed(() => isControlled.value || queriedHasQuery.value)
/** 详情弹窗（只读件与可写表用同一份信息块） */
const detailVisible = ref(false)
const detailRow = ref<AttachmentDocRow | null>(null)
function openDetail(row: AttachmentDocRow) {
  detailRow.value = row
  detailVisible.value = true
}
const { viewerVisible, viewerList, viewerIndex, preview, openUrl, close } = useAttachmentPreview()

function downloadUrl(row: AttachmentDocRow) {
  return row.refId != null
    ? attachmentRefApi.downloadUrlByRef(row.refId)
    : attachmentApi.downloadUrl(row.attachmentId)
}
function previewRow(row: AttachmentDocRow) {
  preview(rows.value, row, (item: PreviewRow) => {
    const document = item as AttachmentDocRow
    return document.refId != null
      ? attachmentRefApi.previewUrlByRef(document.refId)
      : attachmentApi.downloadUrl(document.attachmentId)
  })
}
defineExpose({ reload })
</script>

<style scoped>
.attachment-doc-list h4 {
  margin: 0 0 12px;
}
.attachment-doc-list :deep(.el-alert) {
  margin-bottom: 8px;
}
</style>

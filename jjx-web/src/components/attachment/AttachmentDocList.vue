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
    <el-table
      v-loading="loading"
      :data="rows"
      row-key="attachmentId"
      :empty-text="hasQuery ? '暂无文档' : '未指定查询条件'"
    >
      <el-table-column
        v-for="column in visibleColumns"
        :key="column.key"
        :label="column.label"
        :width="'width' in column ? column.width : undefined"
        :min-width="'minWidth' in column ? column.minWidth : undefined"
      >
        <template #default="{ row }">
          <el-button
            v-if="column.render === 'preview'"
            link
            type="primary"
            @click="previewRow(row)"
          >
            {{ row.fileName || '-' }}
          </el-button>
          <el-button
            v-else-if="column.render === 'download'"
            link
            type="primary"
            @click="openUrl(downloadUrl(row))"
            >下载</el-button
          >
          <el-tag
            v-else-if="column.render === 'status'"
            v-bind="AttachmentDocumentStatusEnum.getTagProps(row.status)"
          >
            {{ AttachmentDocumentStatusEnum.getLabel(row.status) }}
          </el-tag>
          <span v-else>{{ textOf(column.key, row) }}</span>
        </template>
      </el-table-column>
    </el-table>
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerList"
      :initial-index="viewerIndex"
      @close="close"
    />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { attachmentApi } from '@/api/system/attachment'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import {
  AttachmentDocumentStatusEnum,
  AttachmentSourceTypeEnum,
  canonicalAttachmentSource,
} from '@/enums/system/AttachmentEnum'
import { attachmentFields, documentColumnKeys, type AttachmentFieldKey } from './attachmentFields'
import { formatSize, formatTime } from './attachmentFormat'
import { useAttachmentPreview, type PreviewRow } from './useAttachmentPreview'
import { useAttachmentDocTypes } from './useAttachmentDocTypes'
import {
  useAttachmentDocList,
  type AttachmentDocQuery,
  type AttachmentDocRow,
} from './useAttachmentDocList'

const props = withDefaults(
  defineProps<
    AttachmentDocQuery & {
      columns?: readonly AttachmentFieldKey[]
      title?: string
    }
  >(),
  { title: '文档', includeLegacy: undefined }
)
const { rows, loading, messages, documentTypes, hasQuery, reload } = useAttachmentDocList(
  () => props
)
const visibleColumns = computed(() =>
  (props.columns ?? documentColumnKeys).map((key) => attachmentFields[key])
)
const { categoryLabelOf, secondLabelOf } = useAttachmentDocTypes(
  documentTypes,
  () => props.sourceType || ''
)
const { viewerVisible, viewerList, viewerIndex, preview, openUrl, close } = useAttachmentPreview()

function textOf(key: AttachmentFieldKey, row: AttachmentDocRow) {
  if (key === 'sourceType')
    return (
      row.sourceTypeName ||
      (row.sourceType
        ? AttachmentSourceTypeEnum.getLabel(canonicalAttachmentSource(row.sourceType))
        : '-')
    )
  if (key === 'category') return row.documentType ? categoryLabelOf(row.documentType) : '-'
  if (key === 'documentType') return row.documentType ? secondLabelOf(row.documentType) : '-'
  if (key === 'fileSize') return row.fileSize == null ? '-' : formatSize(row.fileSize)
  if (key === 'createTime') return row.createTime ? formatTime(row.createTime) : '-'
  return row[key as keyof AttachmentDocRow] ?? '-'
}
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

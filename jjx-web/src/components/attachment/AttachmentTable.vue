<template>
  <el-table
    :data="rows"
    :row-key="rowKey"
    size="small"
    :empty-text="emptyText"
    @selection-change="(selected) => emit('selectionChange', selected)"
  >
    <el-table-column v-if="selectable" type="selection" width="42" :selectable="selectable" />
    <el-table-column
      v-for="column in visibleColumns"
      :key="column.key"
      :label="column.label"
      :width="column.key === 'actions' ? 190 : 'width' in column ? column.width : undefined"
      :min-width="'minWidth' in column ? column.minWidth : undefined"
      :fixed="column.key === 'actions' ? 'right' : undefined"
    >
      <template #default="{ row }">
        <slot :name="column.key" :row="row">
          <el-button
            v-if="column.key === 'fileName'"
            link
            type="primary"
            @click="emit('preview', row)"
          >
            {{ textOf(column.key, row) }}
          </el-button>
          <template v-else-if="column.key === 'actions'">
            <el-button link type="primary" @click="emit('download', row)">下载</el-button>
            <el-button link type="primary" @click="emit('detail', row)">详情</el-button>
          </template>
          <el-tag
            v-else-if="column.key === 'status'"
            v-bind="AttachmentDocumentStatusEnum.getTagProps(row.status)"
          >
            {{ AttachmentDocumentStatusEnum.getLabel(row.status) }}
          </el-tag>
          <span v-else>{{ textOf(column.key, row) }}</span>
        </slot>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts" generic="T extends AttachmentRow">
import { computed } from 'vue'
import type { AttachmentDocumentTypeItem } from '@/api/system/attachmentRef'
import { AttachmentDocumentStatusEnum } from '@/enums/system/AttachmentEnum'
import { attachmentColumns, attachmentFields, type AttachmentFieldKey } from './attachmentFields'
import { useAttachmentRowText, type AttachmentRow } from './useAttachmentRowText'

const props = withDefaults(
  defineProps<{
    rows: T[]
    documentTypes: AttachmentDocumentTypeItem[]
    sourceType?: string
    columns?: readonly AttachmentFieldKey[]
    rowKey?: string
    emptyText?: string
    selectable?: (row: T) => boolean
  }>(),
  { sourceType: '', rowKey: 'attachmentId', emptyText: '暂无附件' }
)
const emit = defineEmits<{
  preview: [row: T]
  download: [row: T]
  detail: [row: T]
  selectionChange: [rows: T[]]
}>()
const visibleColumns = computed(() =>
  (props.columns ?? attachmentColumns).map((key) => attachmentFields[key])
)
const { textOf } = useAttachmentRowText(
  computed(() => props.documentTypes),
  () => props.sourceType
)
</script>

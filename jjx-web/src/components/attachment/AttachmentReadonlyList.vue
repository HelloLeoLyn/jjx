<template>
  <div class="ac-readonly">
    <div v-if="title" class="ac-legacy-title">{{ title }}</div>
    <div v-if="loading" class="ac-hint">{{ loadingText }}</div>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <div v-for="row in rows" :key="`${keyPrefix}-${row.id}`" class="ac-row">
      <el-icon class="ac-icon"><Document /></el-icon>
      <span v-if="has('fileName')" class="ac-name" :title="row.fileName">{{
        render('fileName', row)
      }}</span>
      <el-tag v-if="tagOf && tagOf(row)" size="small" type="info">{{ tagOf(row) }}</el-tag>
      <span v-if="subOf && subOf(row)" class="ac-sub">{{ subOf(row) }}</span>
      <span v-if="has('fileSize')" class="ac-sub">{{ render('fileSize', row) }}</span>
      <span v-if="has('createTime')" class="ac-sub">· {{ render('createTime', row) }}</span>
      <span class="ac-spacer" />
      <el-button link type="primary" :icon="Download" @click="emit('download', row)" />
      <el-button link type="primary" :icon="View" @click="emit('preview', row)" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 附件只读列表（dev-20261013-040 / 035）：历史段与同链路段共用；字段由 attachmentFieldSets 驱动
 * （只展示集合内的 key，值经 renderAttachmentField 唯一渲染），标签/来源单号为段内附加信息。
 */
import { Document, Download, View } from '@element-plus/icons-vue'
import { renderAttachmentField, type AttachmentFieldKey } from './attachmentFields'

const props = withDefaults(
  defineProps<{
    title?: string
    rows: Record<string, any>[]
    /** 本段消费的字段集合（来自 attachmentFieldSets.legacy / .trace 等） */
    fieldKeys?: readonly AttachmentFieldKey[]
    keyPrefix?: string
    loading?: boolean
    loadingText?: string
    error?: string
    tagOf?: (row: any) => string
    subOf?: (row: any) => string
  }>(),
  { title: '', keyPrefix: 'att', fieldKeys: () => [], loading: false, loadingText: '正在加载…' }
)

const emit = defineEmits<{ preview: [row: any]; download: [row: any] }>()

function has(key: AttachmentFieldKey): boolean {
  return props.fieldKeys.includes(key)
}
function render(key: AttachmentFieldKey, row: Record<string, any>): string {
  return renderAttachmentField(key, row)
}
</script>

<style scoped>
.ac-legacy-title {
  font-size: 12px;
  color: #909399;
  margin: 6px 0;
}
.ac-hint {
  font-size: 12px;
  color: #909399;
  padding: 4px 0;
}
.ac-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.ac-icon {
  color: #909399;
}
.ac-name {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ac-sub {
  color: #909399;
  font-size: 12px;
}
.ac-spacer {
  flex: 1;
}
</style>

<template>
  <el-dialog
    :model-value="modelValue"
    title="附件详情"
    width="800px"
    append-to-body
    destroy-on-close
    @update:model-value="onVisibleChange"
  >
    <AttachmentDetailInfo :row="row" :document-types="documentTypes" :source-type="sourceType" />
    <el-alert v-if="readonlyReason" :title="readonlyReason" type="info" :closable="false" />
    <el-form v-if="row?.kind === 'ref'" label-width="80px" :disabled="!editable">
      <el-form-item label="备注">
        <el-input v-model="form.remark" maxlength="500" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
      </el-form-item>
    </el-form>
    <div v-if="editable" class="am-hint">这些改动会先记录在会话里，随「保存」统一提交（不会即时入库）。</div>
    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button v-if="editable" type="primary" @click="onConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { AttachmentDocumentTypeItem } from '@/api/system/attachmentRef'
import AttachmentDetailInfo from './AttachmentDetailInfo.vue'
import type { AttachmentManagerRow } from './useAttachmentManagerRows'

const props = defineProps<{
  modelValue: boolean
  row: AttachmentManagerRow | null
  documentTypes: AttachmentDocumentTypeItem[]
  sourceType: string
  editable: boolean
  readonlyReason: string
  remark: string
  sortOrder: number
}>()

const emit = defineEmits<{
  'update:modelValue': [visible: boolean]
  confirm: [value: { remark: string; sortOrder: number }]
}>()

const form = reactive({ remark: '', sortOrder: 0 })

watch(
  () => [props.modelValue, props.row?.rowKey] as const,
  ([visible]) => {
    if (!visible) return
    form.remark = props.remark
    form.sortOrder = props.sortOrder
  },
  { immediate: true },
)

function onVisibleChange(visible: boolean) {
  if (!visible) onCancel()
}

function onCancel() {
  emit('update:modelValue', false)
}

function onConfirm() {
  if (!props.editable || props.row?.kind !== 'ref') return
  emit('confirm', { remark: form.remark, sortOrder: form.sortOrder })
}
</script>

<style scoped>
.am-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
</style>

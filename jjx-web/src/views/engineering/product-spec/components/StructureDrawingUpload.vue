<template>
  <div class="structure-upload">
    <component
      :is="editable ? 'button' : 'div'"
      class="structure-image-area"
      :class="{ editable, busy }"
      :type="editable ? 'button' : undefined"
      :disabled="editable ? disabled || busy : undefined"
      :aria-label="editable ? (failed ? '重试上传结构图' : '选择结构图') : undefined"
      :aria-busy="busy"
      data-test="open"
      @click="open"
    >
      <slot><span class="empty-hint">暂无结构图</span></slot>
      <span v-if="editable" class="upload-overlay">
        {{
          busy
            ? '正在上传并保存…'
            : failed
              ? '上传未完成，点击重试'
              : '点击选择图片或 PDF，上传后自动保存'
        }}
      </span>
    </component>
    <input
      ref="fileInput"
      data-test="file"
      type="file"
      accept="image/*,.pdf,application/pdf"
      hidden
      :disabled="!editable || disabled || busy"
      @change="chooseFile"
    />
    <p v-if="editable && duplicate" class="upload-message" role="status">
      文件可能重复，请核对已有工程附件。
    </p>
    <p
      v-if="message && (editable || completed)"
      class="upload-message"
      :role="failed ? 'alert' : 'status'"
    >
      {{ message }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue'
import { fileKind } from '@/components/product/productFilePreview'
import { normalizeWorkSpec } from '@/api/product/workSpec'
import { useWorkSpecAttachmentSession } from './useWorkSpecAttachmentSession'
import type { PendingEntry } from '@/composables/useAttachmentRefs'
const props = defineProps<{
  productId: number
  productCode: string
  productName?: string
  structureFileId?: number | null
  disabled?: boolean
  editable?: boolean
}>()
const emit = defineEmits<{ busy: [value: boolean]; updated: [] }>()
const draft = useWorkSpecAttachmentSession(toRef(props, 'productId'), toRef(props, 'productCode'))
const fileInput = ref<HTMLInputElement>()
const busy = ref(false)
const message = ref('')
const completed = ref(false)
const failed = ref(false)
const duplicate = ref(false)
const file = ref<File>()
const entry = ref<PendingEntry>()
let generation = 0
function open() {
  if (!props.editable || props.disabled || busy.value) return
  if (failed.value && file.value) {
    void submit()
    return
  }
  fileInput.value?.click()
}
async function chooseFile(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = input.files?.[0]
  input.value = ''
  if (!selected || !props.editable || props.disabled || busy.value) return
  if (fileKind({ fileName: selected.name, fileType: selected.type }) === 'other') {
    message.value = '请选择图片或 PDF'
    return
  }
  file.value = selected
  entry.value = undefined
  completed.value = false
  duplicate.value = draft.files.value.some(
    (row) => row.fileName === selected.name && row.fileSize === selected.size
  )
  await submit()
}
async function submit() {
  if (!file.value || busy.value || !props.editable || props.disabled) return
  busy.value = true
  failed.value = false
  message.value = ''
  const current = generation
  try {
    if (!draft.ready.value) await draft.load()
    if (!draft.ready.value) throw new Error('草稿未加载，请重试')
    if (!entry.value) {
      const uploaded = await draft.session.uploadTemporary(file.value, draft.ownership.value)
      if (current !== generation) return
      entry.value = uploaded.entry
      duplicate.value = draft.files.value.some((row) =>
        uploaded.entry.sha256 && row.sha256
          ? uploaded.entry.sha256 === row.sha256
          : row.fileName === uploaded.entry.fileName && row.fileSize === uploaded.entry.fileSize
      )
      draft.register(uploaded.entry, 'STRUCTURE_DRAWING')
    }
    const next = normalizeWorkSpec(draft.spec.value)
    next.structureFileId = entry.value.attachmentId
    await draft.save(next)
    if (current !== generation) return
    completed.value = true
    message.value = '结构图已保存到规范草稿'
    emit('updated')
  } catch (error) {
    if (current !== generation) return
    failed.value = true
    message.value =
      (entry.value ? '文件已上传，草稿保存失败，点击重试：' : '上传失败，点击重试：') +
      (error instanceof Error ? error.message : String(error))
  } finally {
    if (current === generation) busy.value = false
  }
}
watch(
  () => props.productId,
  () => {
    generation++
    file.value = undefined
    entry.value = undefined
    busy.value = completed.value = failed.value = duplicate.value = false
    message.value = ''
  }
)
watch(busy, (value) => emit('busy', value), { flush: 'sync' })
onBeforeUnmount(() => {
  generation++
  emit('busy', false)
})
</script>

<style scoped>
.structure-upload {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.structure-image-area {
  position: relative;
  flex: 1;
  width: 100%;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  box-sizing: border-box;
  overflow: hidden;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
}
.structure-image-area.editable {
  cursor: pointer;
  border-color: var(--el-color-primary);
}
.structure-image-area.editable:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
.structure-image-area:disabled {
  cursor: wait;
}
.upload-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 8px;
  font-size: 12px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}
.empty-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.upload-message {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 6px 0;
}
.upload-message[role='alert'] {
  color: var(--el-color-danger);
}
</style>

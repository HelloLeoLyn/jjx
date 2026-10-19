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
      可能重复：文件名称、大小与已有文件一致，继续上传。
    </p>
    <p v-if="message && (editable || completed)" class="upload-message" :role="failed ? 'alert' : 'status'">{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { attachmentRefApi, type AttachmentRefItem } from '@/api/system/attachmentRef'
import { fileKind } from '@/components/product/productFilePreview'
import { newDrawingIdentity, nextDrawingVersion } from '@/components/product/drawingUploadRules'
import { normalizeWorkSpec, productWorkSpecApi } from '@/api/product/workSpec'
import { DrawingFileRoleEnum } from '@/enums/product/drawing'
import { useUserStore } from '@/store/modules/user'

const props = defineProps<{
  productId: number
  productCode: string
  productName?: string
  structureFileId?: number | null
  disabled?: boolean
  editable?: boolean
}>()
const emit = defineEmits<{ busy: [value: boolean]; updated: [] }>()
const user = useUserStore()
const fileInput = ref<HTMLInputElement>()
const busy = ref(false)
const message = ref('')
const completed = ref(false)
const failed = ref(false)
let generation = 0
const file = ref<File>()
// 二级类型为引用真源；category 兼容尚未完成收敛的图集/打印读取。
const documentType = 'STRUCTURE_DRAWING'
const drawingNo = ref('')
const drawingName = ref('')
const legacyCategory = ref<string>()
const version = ref('A')
const fileRole = ref<string>(DrawingFileRoleEnum.PRINT.value)
const refs = ref<AttachmentRefItem[]>([])
let metadataReady = false
const attachmentId = ref<number>()
let requestKey = ''
let linked = false
const sameDrawing = computed(() =>
  refs.value.filter(
    (row) => (row.drawingNo || '').trim().toUpperCase() === drawingNo.value.trim().toUpperCase()
  )
)
// 轻量前端指纹只提示“可能重复”，不作为上传门禁。
const duplicate = ref(false)

function suggestVersion() {
  version.value = nextDrawingVersion(sameDrawing.value.map(row => row.version)) || 'A'
}
function open() {
  if (!props.editable || props.disabled || busy.value) return
  if (!completed.value && (attachmentId.value || file.value)) {
    void submit()
    return
  }
  // 在用户点击的同步调用栈中打开系统文件选择器。
  fileInput.value?.click()
}
async function prepareMetadata(productId: number, currentGeneration: number) {
  const response = await attachmentRefApi.listRefs('product', productId)
  if (currentGeneration !== generation) return
  refs.value = response.data || []
  const structure = refs.value
    .filter((row) => row.documentType === documentType || row.category === '结构图')
    .sort(
      (a, b) =>
        String(b.createTime || '').localeCompare(String(a.createTime || '')) ||
        (b.id || b.attachmentId) - (a.id || a.attachmentId)
    )
  const identity = newDrawingIdentity(props.productCode, props.productName || '', documentType, refs.value)
  drawingNo.value = structure[0]?.drawingNo?.trim() || identity.drawingNo
  drawingName.value = structure[0]?.drawingNo ? structure[0].drawingName?.trim() || '' : identity.drawingName
  legacyCategory.value = structure[0]?.drawingNo ? structure[0].category || undefined : '结构图'
  suggestVersion()
  duplicate.value = refs.value.some(
    (row) => row.fileName === file.value!.name && Number(row.fileSize) === file.value!.size
  )
  metadataReady = true
}
async function chooseFile(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = input.files?.[0]
  if (!selected || !props.editable || props.disabled || busy.value) return
  input.value = '' // 同一个文件也可再次选择；重复文件不阻断。
  if (fileKind({ fileName: selected.name, fileType: selected.type }) === 'other') {
    message.value = '结构图请选择可预览的图片或 PDF'
    return
  }
  attachmentId.value = undefined
  completed.value = false
  failed.value = false
  linked = metadataReady = false
  duplicate.value = false
  file.value = selected
  fileRole.value = DrawingFileRoleEnum.PRINT.value
  requestKey = newRequestKey()
  await submit()
}
function newRequestKey(): string {
  const uuid = globalThis.crypto?.randomUUID?.()
  if (uuid && /^[A-Za-z0-9_-]{16,100}$/.test(uuid)) return uuid
  // 兼容内网 HTTP 页面：requestKey 为上传幂等标识，不依赖安全上下文中的 randomUUID。
  return `structure-${Date.now().toString(36)}-${Math.random().toString(36).slice(2).padEnd(16, '0')}`
}
function errorText(error: unknown) {
  return error instanceof Error ? error.message : String(error)
}
async function submit() {
  if (
    !props.editable ||
    props.disabled ||
    busy.value ||
    !file.value ||
    !user.hasPermission('product:edit')
  )
    return
  busy.value = true
  message.value = ''
  failed.value = false
  const currentGeneration = generation
  const productId = props.productId
  const productCode = props.productCode
  try {
    if (!attachmentId.value) {
      if (!metadataReady) await prepareMetadata(productId, currentGeneration)
      if (currentGeneration !== generation) return
      if (!/^[A-Za-z0-9_-]{16,100}$/.test(requestKey)) requestKey = newRequestKey()
      const response = await attachmentRefApi.tempUpload(file.value!, requestKey, undefined, {
        ...(legacyCategory.value ? { category: legacyCategory.value } : {}),
        drawingNo: drawingNo.value.trim(),
        drawingName: drawingName.value,
        version: version.value.trim(),
        fileRole: fileRole.value,
      })
      if (currentGeneration !== generation) return
      const id = Number(response.data)
      if (!Number.isSafeInteger(id) || id <= 0) throw new Error('上传未返回有效附件编号')
      attachmentId.value = id
    }
    if (!linked) {
      const response = await attachmentRefApi.createRef({
        attachmentId: attachmentId.value,
        sourceType: 'product',
        sourceId: productId,
        productId,
        productCode,
        documentType,
      })
      if (currentGeneration !== generation) return
      if (!Number(response.data)) throw new Error('产品引用未建立')
      linked = true
    }
    // 每次重试读取最新 revision 和内容，避免覆盖其它规范字段。
    const current = await productWorkSpecApi.get(productId)
    if (currentGeneration !== generation) return
    if (!current.data || typeof current.data !== 'object')
      throw new Error('未读取到当前规范，请重试')
    const spec = normalizeWorkSpec(current.data)
    spec.structureFileId = attachmentId.value
    await productWorkSpecApi.save(productId, spec)
    if (currentGeneration !== generation) return
    completed.value = true
    message.value = '结构图已保存'
    emit('updated')
  } catch (error) {
    if (currentGeneration !== generation) return
    failed.value = true
    const prefix = attachmentId.value
      ? linked
        ? '文件已上传，规范保存失败，点击图片区重试'
        : '文件已上传，产品引用未建立，点击图片区重试'
      : '上传失败，点击图片区重试'
    message.value = prefix + '：' + errorText(error)
  } finally {
    if (currentGeneration === generation) busy.value = false
  }
}
watch(
  () => props.productId,
  () => {
    generation++
    file.value = undefined
    attachmentId.value = undefined
    requestKey = ''
    linked = metadataReady = false
    busy.value = completed.value = failed.value = duplicate.value = false
    message.value = ''
  }
)
watch(busy, (working) => emit('busy', working), { flush: 'sync' })
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

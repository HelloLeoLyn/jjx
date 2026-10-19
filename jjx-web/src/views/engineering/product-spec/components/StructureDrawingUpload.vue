<template>
  <div class="structure-upload">
    <el-button :disabled="disabled || busy" :loading="busy" data-test="open" @click="open">
      {{
        attachmentId && !completed
          ? '重试结构图处理'
          : file && !completed
            ? '重试上传结构图'
            : '上传结构图'
      }}
    </el-button>
    <input
      ref="fileInput"
      data-test="file"
      type="file"
      hidden
      :disabled="disabled || busy"
      @change="chooseFile"
    />
    <p v-if="duplicate" class="upload-message" role="status">
      可能重复：文件指纹（名称、大小）与已有文件一致，继续上传。
    </p>
    <p v-if="message" class="upload-message" role="status">{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { attachmentRefApi, type AttachmentRefItem } from '@/api/system/attachmentRef'
import { attachmentApi } from '@/api/system/attachment'
import { normalizeWorkSpec, productWorkSpecApi } from '@/api/product/workSpec'
import { DrawingFileRoleEnum } from '@/enums/product/drawing'
import { useUserStore } from '@/store/modules/user'

const props = defineProps<{
  productId: number
  productCode: string
  structureFileId?: number | null
  disabled?: boolean
}>()
const emit = defineEmits<{ busy: [value: boolean]; updated: [] }>()
const user = useUserStore()
const canRelease = computed(() => user.hasPermission('engineering:drawing:release'))
const fileInput = ref<HTMLInputElement>()
const busy = ref(false)
const message = ref('')
const completed = ref(false)
const file = ref<File>()
// 二级类型为引用真源；category 兼容尚未完成收敛的图集/打印读取。
const documentType = 'STRUCTURE_DRAWING'
const drawingNo = ref('')
const version = ref('A')
const fileRole = ref<string>(DrawingFileRoleEnum.PRINT.value)
const refs = ref<AttachmentRefItem[]>([])
let metadataReady = false
const attachmentId = ref<number>()
let requestKey = ''
let linked = false
let madeCurrent = false
let released = false
const sameDrawing = computed(() =>
  refs.value.filter(
    (row) => (row.drawingNo || '').trim().toUpperCase() === drawingNo.value.trim().toUpperCase()
  )
)
// 轻量前端指纹只提示“可能重复”，不作为上传门禁。
const duplicate = ref(false)

function suggestVersion() {
  const letters = sameDrawing.value
    .map((row) => (row.version || '').trim().toUpperCase())
    .filter((value) => /^[A-Z]+$/.test(value))
    .sort((a, b) => b.length - a.length || b.localeCompare(a))
  // 同编号所有引用参与版本计算，含历史和未下发版本；Z → AA。
  // 直接对字母进位，避免长版本转换为数字丢失精度。
  const next = [...(letters[0] || '')]
  let index = next.length - 1
  while (index >= 0 && next[index] === 'Z') {
    next[index--] = 'A'
  }
  if (index < 0) next.unshift('A')
  else next[index] = String.fromCharCode(next[index].charCodeAt(0) + 1)
  version.value = next.join('')
}
function open() {
  if (props.disabled || busy.value) return
  if (!completed.value && (attachmentId.value || file.value)) {
    void submit()
    return
  }
  // 在用户点击的同步调用栈中打开系统文件选择器。
  fileInput.value?.click()
}
async function prepareMetadata() {
  const response = await attachmentRefApi.listRefs('product', props.productId)
  refs.value = response.data || []
  const structure = refs.value
    .filter((row) => row.documentType === documentType || row.category === '结构图')
    .sort(
      (a, b) =>
        String(b.createTime || '').localeCompare(String(a.createTime || '')) ||
        (b.id || b.attachmentId) - (a.id || a.attachmentId)
    )
  drawingNo.value = structure[0]?.drawingNo?.trim() || `${props.productCode}-ST`
  suggestVersion()
  duplicate.value = refs.value.some(
    (row) => row.fileName === file.value!.name && Number(row.fileSize) === file.value!.size
  )
  metadataReady = true
}
async function chooseFile(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = input.files?.[0]
  if (!selected || props.disabled || busy.value) return
  input.value = '' // 同一个文件也可再次选择；重复文件不阻断。
  attachmentId.value = undefined
  completed.value = false
  linked = madeCurrent = released = metadataReady = false
  duplicate.value = false
  file.value = selected
  fileRole.value =
    selected.type.startsWith('image/') ||
    selected.type === 'application/pdf' ||
    /\.(png|jpe?g|gif|webp|bmp|svg|tiff?|avif|heic|pdf)$/i.test(selected.name)
      ? DrawingFileRoleEnum.PRINT.value
      : DrawingFileRoleEnum.ORIGINAL.value
  requestKey = crypto.randomUUID()
  await submit()
}
function errorText(error: unknown) {
  return error instanceof Error ? error.message : String(error)
}
function requireTrue(response: { data: boolean }, action: string) {
  if (response.data !== true) throw new Error(action + '失败')
}
async function submit() {
  if (props.disabled || busy.value || !file.value || !user.hasPermission('product:edit')) return
  busy.value = true
  message.value = ''
  try {
    if (!attachmentId.value) {
      if (!metadataReady) await prepareMetadata()
      const response = await attachmentRefApi.tempUpload(file.value!, requestKey, undefined, {
        category: '结构图',
        drawingNo: drawingNo.value.trim(),
        version: version.value.trim(),
        fileRole: fileRole.value,
      })
      const id = Number(response.data)
      if (!Number.isSafeInteger(id) || id <= 0) throw new Error('上传未返回有效附件编号')
      attachmentId.value = id
    }
    if (!linked) {
      const response = await attachmentRefApi.createRef({
        attachmentId: attachmentId.value,
        sourceType: 'product',
        sourceId: props.productId,
        productId: props.productId,
        productCode: props.productCode,
        documentType,
      })
      if (!Number(response.data)) throw new Error('产品引用未建立')
      linked = true
      emit('updated')
    }
    if (!canRelease.value) {
      message.value = '已上传，需有权限的人下发（未下发）'
      return
    }
    if (!madeCurrent) {
      requireTrue(await attachmentApi.setCurrent(attachmentId.value), '设为现行')
      madeCurrent = true
    }
    if (!released) {
      requireTrue(await attachmentApi.release(attachmentId.value, true), '下发')
      released = true
    }
    // 每次重试读取最新 revision 和内容，避免覆盖其它规范字段。
    const current = await productWorkSpecApi.get(props.productId)
    const spec = normalizeWorkSpec(current.data)
    spec.structureFileId = attachmentId.value
    await productWorkSpecApi.save(props.productId, spec)
    completed.value = true
    message.value = '已上传，已设为现行并下发，作业规范结构图已更新'
    emit('updated')
  } catch (error) {
    const prefix = released
      ? '已下发，作业规范引用未更新，可重试'
      : attachmentId.value
        ? linked
          ? '已上传，未下发，可重试'
          : '文件已上传，产品引用未建立，未下发，可重试'
        : '上传失败，可重试'
    message.value = prefix + '：' + errorText(error)
  } finally {
    busy.value = false
  }
}
watch(busy, (working) => emit('busy', working), { flush: 'sync' })
onBeforeUnmount(() => emit('busy', false))
</script>

<style scoped>
.upload-message {
  font-size: 12px;
  color: var(--el-color-warning);
  margin: 6px 0;
  overflow-wrap: anywhere;
}
</style>

<template>
  <div class="structure-upload">
    <el-button :disabled="disabled || busy" data-test="open" @click="open">
      {{ attachmentId && !completed ? '重试结构图处理' : '上传结构图' }}
    </el-button>
    <p v-if="message" class="upload-message" role="status">{{ message }}</p>
    <el-dialog
      v-model="visible"
      title="上传结构图"
      width="560px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      :close-on-press-escape="!busy"
      :show-close="!busy"
      :before-close="close"
    >
      <el-alert
        :title="
          canRelease
            ? '上传后自动设为现行并下发，再更新作业规范结构图；旧版本转为历史。'
            : '当前账号只上传文件，需有权限的人下发后再选用。'
        "
        type="info"
        :closable="false"
      />
      <el-form label-width="90px" :disabled="busy || !!attachmentId">
        <el-form-item label="图种" required
          ><el-select v-model="documentType"
            ><el-option label="结构图" value="STRUCTURE_DRAWING" /></el-select
        ></el-form-item>
        <el-form-item label="图纸编号" required
          ><el-input
            v-model="drawingNo"
            maxlength="80"
            data-test="drawing-no"
            @change="suggestVersion"
        /></el-form-item>
        <el-form-item label="版本" required
          ><el-input
            v-model="version"
            maxlength="50"
            data-test="version"
            @input="versionEdited = true"
        /></el-form-item>
        <el-form-item label="文件用途" required
          ><el-select v-model="fileRole"
            ><el-option
              v-for="role in DrawingFileRoleEnum.items"
              :key="role.value"
              :value="role.value"
              :label="role.label" /></el-select
        ></el-form-item>
        <el-form-item label="文件" required
          ><input
            data-test="file"
            type="file"
            accept="image/*,.pdf"
            :disabled="busy || !!attachmentId"
            @change="chooseFile"
        /></el-form-item>
      </el-form>
      <el-alert
        v-if="duplicate"
        title="可能重复：该图纸编号和版本已有文件，可继续上传。"
        type="warning"
        :closable="false"
      />
      <el-alert
        v-if="message"
        :title="message"
        :type="completed ? 'success' : 'warning'"
        :closable="false"
        data-test="result"
      />
      <template #footer>
        <el-button :disabled="busy" @click="close()">关闭</el-button>
        <el-button
          v-if="!completed"
          type="primary"
          :loading="busy"
          :disabled="busy || !ready"
          data-test="submit"
          @click="submit"
        >
          {{ attachmentId ? '重试后续处理' : canRelease ? '上传并下发' : '上传' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { attachmentRefApi, type AttachmentRefItem } from '@/api/system/attachmentRef'
import { attachmentApi } from '@/api/system/attachment'
import { normalizeWorkSpec, productWorkSpecApi } from '@/api/product/workSpec'
import { DrawingCurrentFlagEnum, DrawingFileRoleEnum } from '@/enums/product/drawing'
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
const visible = ref(false)
const busy = ref(false)
const message = ref('')
const completed = ref(false)
const file = ref<File>()
// 二级类型为引用真源；category 兼容尚未完成收敛的图集/打印读取。
const documentType = ref('STRUCTURE_DRAWING')
const drawingNo = ref('')
const version = ref('A')
const versionEdited = ref(false)
const fileRole = ref<string>(DrawingFileRoleEnum.PRINT.value)
const refs = ref<AttachmentRefItem[]>([])
const listLoaded = ref(false)
const attachmentId = ref<number>()
let requestKey = ''
let linked = false
let madeCurrent = false
let released = false
const ready = computed(
  () =>
    !!attachmentId.value ||
    (listLoaded.value && !!file.value && !!drawingNo.value.trim() && !!version.value.trim())
)
const sameDrawing = computed(() =>
  refs.value.filter(
    (row) => (row.drawingNo || '').trim().toUpperCase() === drawingNo.value.trim().toUpperCase()
  )
)
const duplicate = computed(
  () =>
    !!version.value.trim() &&
    sameDrawing.value.some(
      (row) => (row.version || '').trim().toUpperCase() === version.value.trim().toUpperCase()
    )
)

function suggestVersion() {
  if (versionEdited.value || attachmentId.value) return
  const letters = sameDrawing.value
    .map((row) => (row.version || '').trim().toUpperCase())
    .filter((value) => /^[A-Z]+$/.test(value))
  // 字母序版本支持 Z → AA；仅提供建议，版本输入始终可改且必填。
  const numberOf = (value: string) =>
    [...value].reduce((total, char) => total * 26 + char.charCodeAt(0) - 64, 0)
  let next = Math.max(0, ...letters.map(numberOf)) + 1
  let result = ''
  while (next > 0) {
    next--
    result = String.fromCharCode(65 + (next % 26)) + result
    next = Math.floor(next / 26)
  }
  version.value = result
}
async function open() {
  visible.value = true
  if (attachmentId.value && !completed.value) return
  attachmentId.value = undefined
  completed.value = false
  message.value = ''
  file.value = undefined
  linked = madeCurrent = released = false
  requestKey = crypto.randomUUID()
  versionEdited.value = false
  busy.value = true
  listLoaded.value = false
  try {
    const response = await attachmentRefApi.listRefs('product', props.productId)
    refs.value = response.data || []
    listLoaded.value = true
    const structure = refs.value.filter(
      (row) => row.documentType === 'STRUCTURE_DRAWING' || row.category === '结构图'
    )
    const selected =
      structure.find((row) => row.attachmentId === props.structureFileId) ||
      structure.find(
        (row) => row.isCurrent === DrawingCurrentFlagEnum.CURRENT.value && row.drawingNo
      ) ||
      structure.find((row) => row.drawingNo)
    drawingNo.value = selected?.drawingNo || `${props.productCode}-STRUCTURE`
    suggestVersion()
  } catch (error) {
    message.value = '结构图清单读取失败，请关闭后重新打开：' + errorText(error)
    refs.value = []
  } finally {
    busy.value = false
  }
}
function chooseFile(event: Event) {
  const selected = (event.target as HTMLInputElement).files?.[0]
  if (!selected) return
  if (
    !selected.type.startsWith('image/') &&
    !/\.(png|jpe?g|gif|webp|bmp|svg|pdf)$/i.test(selected.name)
  ) {
    message.value = '结构图请选择可预览的图片或PDF打印件'
    file.value = undefined
    return
  }
  file.value = selected
  requestKey = crypto.randomUUID()
  message.value = ''
}
function errorText(error: unknown) {
  return error instanceof Error ? error.message : String(error)
}
function requireTrue(response: { data: boolean }, action: string) {
  if (response.data !== true) throw new Error(action + '失败')
}
async function submit() {
  if (busy.value || !ready.value || !user.hasPermission('product:edit')) return
  busy.value = true
  message.value = ''
  try {
    if (!attachmentId.value) {
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
        documentType: documentType.value,
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
function close(done?: () => void) {
  if (busy.value) return
  visible.value = false
  done?.()
}
watch([visible, busy], ([open, working]) => emit('busy', open || working))
onBeforeUnmount(() => emit('busy', false))
</script>

<style scoped>
.upload-message {
  font-size: 12px;
  color: var(--el-color-warning);
  margin: 6px 0;
  overflow-wrap: anywhere;
}
.el-form {
  margin-top: 16px;
}
</style>

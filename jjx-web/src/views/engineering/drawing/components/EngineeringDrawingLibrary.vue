<template>
  <section class="draft-library" v-loading="draft.loading.value">
    <el-alert v-if="draft.error.value" :title="draft.error.value" type="error" :closable="false">
      <template v-if="draft.conflict.value">
        <el-button :disabled="draft.busy.value" @click="recover(false)">比较并重新应用</el-button>
        <el-button :disabled="draft.busy.value" @click="recover(true)">放弃并重读</el-button>
      </template>
      <el-button v-else link @click="draft.load()">重试</el-button>
    </el-alert>
    <div class="draft-actions">
      <el-tag
        :type="draft.spec.value.finalizedVersionId && !draft.dirty.value ? 'success' : 'warning'"
        >{{ draft.spec.value.finalizedVersionId && !draft.dirty.value ? '已定版' : '草稿' }}</el-tag
      >
      <span>制定负责人</span>
      <el-select
        v-if="canEdit"
        v-model="draft.owner.value"
        placeholder="默认当前用户"
        filterable
        :disabled="draft.busy.value"
        style="width: 160px"
      >
        <el-option
          v-for="person in owners"
          :key="person.id"
          :value="person.id"
          :label="person.name"
        />
      </el-select>
      <span v-else>{{ draft.spec.value.preparedByName || '未指定' }}</span>
      <el-button
        v-if="canEdit"
        type="primary"
        :loading="draft.saving.value"
        :disabled="!draft.ready.value || draft.busy.value"
        @click="save"
        >保存草稿</el-button
      >
      <el-button :disabled="draft.busy.value" @click="history">操作记录</el-button>
      <el-button v-if="draft.dirty.value" :disabled="draft.busy.value" @click="cancel"
        >取消未保存改动</el-button
      >
    </div>
    <p>附件随作业规范一起定版。新版移除附件不会删除旧版文件。</p>
    <div v-if="canEdit" class="draft-actions">
      <el-select
        v-model="uploadType"
        placeholder="请选择资料类型"
        clearable
        :disabled="draft.busy.value"
        style="width: 180px"
      >
        <el-option
          v-for="type in types"
          :key="type.value"
          :label="type.label"
          :value="type.value"
        />
      </el-select>
      <span v-if="!uploadTypeAvailable" class="upload-type-hint"
        >资料类型不可用，暂不能上传，请重试加载</span
      >
      <AttachmentUpload
        :session="draft.session"
        :ownership="draft.ownership.value"
        :disabled="draft.busy.value || !uploadTypeSelected"
        :blocked-reason="uploadBlockedReason"
        :accept="acceptedFiles"
        :max-size-m-b="100"
        @uploaded="uploaded"
      />
    </div>
    <AttachmentManager
      :session="draft.session"
      title="工程附件"
      source-type="product"
      :source-no="productCode"
      :show-ownership-filter="false"
    />
    <el-table v-if="draft.files.value.length" :data="draft.files.value" size="small" border>
      <el-table-column type="index" label="顺序" width="65" />
      <el-table-column label="文件" min-width="200"
        ><template #default="{ row }">{{
          row.displayName || row.fileName
        }}</template></el-table-column
      >
      <el-table-column label="打印" width="100"
        ><template #default="{ row }">
          <el-checkbox
            :model-value="row.print"
            :disabled="
              !canEdit || draft.busy.value || detectFileKind(row.fileName, row.fileType) === 'other'
            "
            @update:model-value="(value) => (draft.prints[row.attachmentId] = !!value)"
            >打印</el-checkbox
          >
        </template></el-table-column
      >
      <el-table-column v-if="canEdit" label="排序" width="130"
        ><template #default="{ row, $index }">
          <el-button
            link
            :disabled="draft.busy.value || !$index"
            @click="draft.move(row.attachmentId, -1)"
            >上移</el-button
          >
          <el-button
            link
            :disabled="draft.busy.value || $index === draft.files.value.length - 1"
            @click="draft.move(row.attachmentId, 1)"
            >下移</el-button
          >
        </template></el-table-column
      >
    </el-table>
    <ProductWorkSpecVersionPanel
      v-if="productId && showVersions"
      :product-id="productId"
      @updated="emit('updated')"
    />
    <el-dialog v-model="historyVisible" title="规范操作记录" width="760px" append-to-body>
      <el-table :data="historyRows" v-loading="historyLoading" size="small">
        <el-table-column prop="changedAt" label="时间" width="175" />
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="action" label="操作" min-width="180" />
      </el-table>
      <el-button v-if="historyBefore" :loading="historyLoading" @click="loadHistory"
        >更多</el-button
      >
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { productWorkSpecApi } from '@/api/product/workSpec'
import type { PendingEntry } from '@/composables/useAttachmentRefs'
import AttachmentUpload from '@/components/attachment/AttachmentUpload.vue'
import AttachmentManager from '@/components/attachment/AttachmentManager.vue'
import ProductWorkSpecVersionPanel from '../../product-spec/components/ProductWorkSpecVersionPanel.vue'
import { useWorkSpecAttachmentSession } from '../../product-spec/components/useWorkSpecAttachmentSession'
import { detectFileKind } from '../../product-spec/components/versionDocset'
const props = withDefaults(
  defineProps<{
    productId: number
    productCode: string
    productName: string
    categories?: string[]
    active?: boolean
    showVersions?: boolean
  }>(),
  { showVersions: true }
)
const emit = defineEmits<{ busy: [value: boolean]; loaded: [files: any[]]; updated: [] }>()
const draft = useWorkSpecAttachmentSession(toRef(props, 'productId'), toRef(props, 'productCode'))
const user = useUserStore()
const canEdit = computed(() => user.hasPermission('product:edit'))
const owners = ref<{ id: number; name: string }[]>([])
// 两个工程入口均承接后端ENGINEERING_DOC有效字典；旧categories固定列表不再截断。
const types = computed(() =>
  draft.engineeringTypes.value.map((type) => ({ value: type.item_key, label: type.label }))
)
// 上传不设默认资料类型：必须由用户明确选择，避免误登记为结构图。
const uploadType = ref('')
const uploadTypeAvailable = computed(() => types.value.length > 0)
const uploadTypeSelected = computed(() =>
  types.value.some((type) => type.value === uploadType.value)
)
const uploadBlockedReason = computed(() => {
  if (!uploadTypeAvailable.value) return '资料类型不可用，暂不能上传'
  if (!uploadTypeSelected.value) return '先选择资料类型再上传'
  return ''
})
// 只在当前选择失效（字典刷新/停用）时清空，不自动回退到第一项。
watch(types, (options) => {
  if (uploadType.value && !options.some((type) => type.value === uploadType.value))
    uploadType.value = ''
})
const acceptedFiles = [
  '.pdf',
  '.jpg',
  '.jpeg',
  '.png',
  '.gif',
  '.webp',
  '.bmp',
  '.svg',
  '.dwg',
  '.dxf',
  '.step',
  '.stp',
  '.igs',
  '.iges',
  '.ai',
  '.psd',
  '.cdr',
  '.zip',
  '.doc',
  '.docx',
  '.xls',
  '.xlsx',
]
function uploaded(payload: { pending: PendingEntry }) {
  if (!uploadTypeSelected.value) {
    ElMessage.warning('先选择资料类型再上传')
    return
  }
  const duplicate = draft.files.value.some((file) =>
    payload.pending.sha256 && file.sha256
      ? payload.pending.sha256 === file.sha256
      : file.fileName === payload.pending.fileName && file.fileSize === payload.pending.fileSize
  )
  draft.register(payload.pending, uploadType.value)
  if (duplicate)
    ElMessage.info(
      payload.pending.sha256
        ? '已有相同内容的文件，请检查后保存'
        : '可能重复：文件名和大小相同，请检查后保存'
    )
}
async function save() {
  try {
    await draft.save()
    ElMessage.success('草稿已保存')
    emit('updated')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败，改动已保留')
  }
}
async function recover(discard: boolean) {
  try {
    await draft.recover(discard)
    emit('updated')
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close')
      ElMessage.error(cause instanceof Error ? cause.message : '冲突恢复失败，改动已保留')
  }
}
async function cancel() {
  try {
    await ElMessageBox.confirm('放弃尚未保存的附件、顺序和负责人改动？', '取消改动')
    draft.session.pending.value = []
    draft.session.persist()
    await draft.load(true)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(String(error))
  }
}
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyRows = ref<any[]>([])
const historyBefore = ref<number>()
async function history() {
  historyRows.value = []
  historyBefore.value = undefined
  historyVisible.value = true
  await loadHistory()
}
async function loadHistory() {
  historyLoading.value = true
  try {
    const result = await productWorkSpecApi.history(props.productId, historyBefore.value)
    historyRows.value.push(...(result.data?.items || []))
    historyBefore.value = result.data?.nextBefore || undefined
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '记录加载失败')
  } finally {
    historyLoading.value = false
  }
}
watch(
  () => props.productId,
  async () => {
    // 切换产品不继承上一产品的资料类型选择。
    uploadType.value = ''
    if (!canEdit.value) return
    try {
      await draft.session.loadDocumentTypes()
      const result = await productWorkSpecApi.responsibleUsers(props.productId)
      owners.value = result.data || []
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '负责人加载失败')
    }
  },
  { immediate: true }
)
watch(
  () => draft.busy.value || draft.dirty.value,
  (value) => emit('busy', value),
  { immediate: true }
)
onBeforeUnmount(() => emit('busy', false))
defineExpose({ save, dirty: draft.dirty, busy: draft.busy })
</script>
<style scoped>
.draft-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin: 12px 0;
}
.upload-type-hint {
  color: var(--el-color-warning);
  font-size: 13px;
}
.draft-library > p {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.draft-library :deep(.wsv-card) {
  margin-top: 18px;
}
</style>

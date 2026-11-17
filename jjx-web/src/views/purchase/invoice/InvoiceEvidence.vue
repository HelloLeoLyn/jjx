<template>
  <div v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert
      v-else-if="legacy"
      title="这是历史暂存文件；请由原登记人补录并保存，建立正式附件关联。"
      type="warning"
      :closable="false"
    />
    <el-empty v-if="!loading && !files.length && !error" description="暂无发票原件" />
    <template v-if="files.length">
      <el-select
        v-model="selectedId"
        style="width: 100%; margin: 12px 0"
        placeholder="选择发票文件"
      >
        <el-option
          v-for="file in files"
          :key="file.attachmentId"
          :value="file.attachmentId"
          :label="file.fileName || '登记时上传的文件'"
        />
      </el-select>
      <el-image
        v-if="isImage"
        :src="url"
        :preview-src-list="[url]"
        fit="contain"
        style="width: 100%; height: 480px"
      />
      <iframe v-else-if="isPdf" :src="url" title="发票原件预览" class="pdf-preview" />
      <el-button v-else type="primary" link @click="open">打开文件查看</el-button>
      <div>
        <el-link :href="url" target="_blank" rel="noopener noreferrer">打开 / 下载原件</el-link>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getInvoiceDocRefs } from '@/api/purchase/invoice'
import { attachmentApi } from '@/api/system/attachment'
import { attachmentRefApi } from '@/api/system/attachmentRef'
const props = defineProps<{ invoice: Record<string, any> }>()
const files = ref<any[]>([])
const selectedId = ref<number>()
const loading = ref(false)
const error = ref('')
const legacy = ref(false)
let requestId = 0
const selected = computed(() => files.value.find((file) => file.attachmentId === selectedId.value))
const url = computed(() =>
  selected.value?.refId
    ? attachmentRefApi.previewUrlByRef(selected.value.refId)
    : selectedId.value
      ? attachmentApi.downloadUrl(selectedId.value)
      : ''
)
const isImage = computed(
  () =>
    selected.value?.fileType?.startsWith('image/') ||
    /\.(png|jpe?g|gif|webp|bmp)$/i.test(selected.value?.fileName || '')
)
const isPdf = computed(
  () =>
    selected.value?.fileType === 'application/pdf' || /\.pdf$/i.test(selected.value?.fileName || '')
)
function open() {
  window.open(url.value, '_blank', 'noopener,noreferrer')
}
watch(
  () => props.invoice,
  async (invoice) => {
    const currentRequest = ++requestId
    loading.value = true
    files.value = []
    selectedId.value = undefined
    error.value = ''
    legacy.value = false
    try {
      const response: any = await getInvoiceDocRefs(invoice.documentId)
      if (currentRequest !== requestId) return
      files.value = response.data || []
      const carried = /\/system\/attachment\/download\/(\d+)$/.exec(invoice.fileUrl || '')
      if (!files.value.length && carried) {
        files.value = [{ attachmentId: Number(carried[1]), fileName: invoice.fileName }]
        legacy.value = true
      }
      selectedId.value = files.value[0]?.attachmentId
    } catch (cause: any) {
      if (currentRequest === requestId) error.value = cause?.message || '附件加载失败，请重试'
    } finally {
      if (currentRequest === requestId) loading.value = false
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.pdf-preview {
  width: 100%;
  height: 560px;
  border: 1px solid var(--el-border-color);
}
</style>

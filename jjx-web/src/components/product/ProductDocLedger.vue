<template>
  <section ref="root" class="product-doc-ledger" aria-label="产品资料">
    <el-tabs v-model="category" aria-label="一级分类">
      <el-tab-pane label="全部" name="" />
      <el-tab-pane v-for="item in categories" :key="item.key" :label="item.label" :name="item.key" />
    </el-tabs>
    <div class="ledger-toolbar">
      <el-select v-model="documentType" placeholder="全部二级类型" clearable aria-label="二级类型">
        <el-option v-for="item in secondaryTypes" :key="item.key" :label="item.label" :value="item.key" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索文件名" clearable aria-label="搜索文件名" />
      <span class="ledger-count" aria-live="polite">{{ filteredRows.length }} / {{ rows.length }} 个文件</span>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-table v-else-if="filteredRows.length" ref="table" :data="filteredRows" row-key="id" highlight-current-row border size="small">
      <el-table-column label="一级类型" min-width="110">
        <template #default="{ row }">{{ categoryLabelOf(row.documentType) }}</template>
      </el-table-column>
      <el-table-column label="二级类型" min-width="130">
        <template #default="{ row }">{{ secondLabelOf(row.documentType) }}</template>
      </el-table-column>
      <el-table-column label="文件名" min-width="200">
        <template #default="{ row }">
          <el-button link type="primary" :data-attachment-id="row.attachmentId" @click="previewFile(row)">
            {{ row.fileName || row.displayName || `附件 #${row.attachmentId}` }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="90">
        <template #default="{ row }">{{ formatSize(row.fileSize) || '-' }}</template>
      </el-table-column>
      <el-table-column label="关联产品编码" prop="productCode" min-width="130">
        <template #default="{ row }">{{ row.productCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="上传人" min-width="90">
        <template #default="{ row }">{{ row.createBy || '-' }}</template>
      </el-table-column>
      <el-table-column label="上传时间" min-width="150">
        <template #default="{ row }">{{ formatTime(row.createTime) || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="70">
        <template #default="{ row }">
          <el-button link type="primary" @click="openUrl(attachmentRefApi.downloadUrlByRef(row.id))">下载</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else-if="!error" :description="rows.length ? '未找到符合条件的产品资料' : '暂无产品资料'" :image-size="60" />
    <el-image-viewer v-if="viewerVisible" :url-list="viewerList" :initial-index="viewerIndex" @close="close" />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onScopeDispose, ref, watch } from 'vue'
import type { TableInstance } from 'element-plus'
import { attachmentRefApi, type AttachmentRefItem, type AttachmentDocumentTypeItem } from '@/api/system/attachmentRef'
import { useAttachmentDocTypes } from '@/components/attachment/useAttachmentDocTypes'
import { useAttachmentPreview } from '@/components/attachment/useAttachmentPreview'
import { formatSize, formatTime } from '@/components/attachment/attachmentFormat'

const props = defineProps<{ productId: number }>()
const emit = defineEmits<{ loaded: [rows: AttachmentRefItem[]]; loading: [value: boolean] }>()
const root = ref<HTMLElement>()
const table = ref<TableInstance>()
const rows = ref<AttachmentRefItem[]>([])
const types = ref<AttachmentDocumentTypeItem[]>([])
const loading = ref(false)
const error = ref('')
const category = ref('')
const documentType = ref('')
const keyword = ref('')
const { categoryKeyOfType, categoryLabelOf, secondLabelOf } = useAttachmentDocTypes(types, () => 'product')
const { preview, openUrl, viewerVisible, viewerList, viewerIndex, close } = useAttachmentPreview()
let request = 0
const keyOf = (row: AttachmentRefItem) => categoryKeyOfType(row.documentType) || '__none__'
const categories = computed(() => Array.from(new Map(rows.value.map(row => [keyOf(row), {
  key: keyOf(row), label: keyOf(row) === '__none__' ? '未分类' : categoryLabelOf(row.documentType),
}])).values()))
const secondaryTypes = computed(() => Array.from(new Set(rows.value
  .filter(row => !category.value || keyOf(row) === category.value).map(row => row.documentType).filter(Boolean)))
  .map(code => ({ key: code!, label: secondLabelOf(code) })))
const filteredRows = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return rows.value.filter(row => (!category.value || keyOf(row) === category.value)
    && (!documentType.value || row.documentType === documentType.value)
    && (!search || [row.fileName, row.displayName].some(name => name?.toLowerCase().includes(search))))
})
watch(category, () => { documentType.value = '' })

async function load() {
  const current = ++request
  const id = props.productId
  rows.value = []; error.value = ''; emit('loaded', [])
  loading.value = !!id; emit('loading', loading.value)
  if (!id) return
  const [files, dictionary] = await Promise.allSettled([
    attachmentRefApi.refsByProduct(id), attachmentRefApi.documentTypes(),
  ])
  if (current !== request) return
  const failures: string[] = []
  if (files.status === 'fulfilled' && Array.isArray(files.value.data)) {
    rows.value = files.value.data.filter(row => row.sourceType === 'product' && !row.deleted)
  } else failures.push('产品资料加载失败')
  if (dictionary.status === 'fulfilled' && Array.isArray(dictionary.value.data)) types.value = dictionary.value.data
  else failures.push('资料类型加载失败')
  error.value = failures.join('；')
  loading.value = false; emit('loading', false); emit('loaded', rows.value)
}
function previewFile(row: AttachmentRefItem) {
  preview(rows.value, row, item => attachmentRefApi.previewUrlByRef(item.id))
}
async function focusAttachment(attachmentId: number) {
  category.value = ''; documentType.value = ''; keyword.value = ''
  await nextTick()
  const row = rows.value.find(item => item.attachmentId === attachmentId)
  if (!row) return
  table.value?.setCurrentRow(row)
  // 空筛选结果切回表格时，等待表格完成行渲染后再定位按钮。
  await nextTick()
  const button = root.value?.querySelector<HTMLElement>(`[data-attachment-id="${attachmentId}"]`)
  button?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  button?.focus()
}
watch(() => props.productId, () => {
  category.value = ''; documentType.value = ''; keyword.value = ''; types.value = []
  close(); void load()
}, { immediate: true })
onScopeDispose(() => { request++ })
defineExpose({ focusAttachment, refresh: load })
</script>

<style scoped>
.ledger-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.ledger-toolbar .el-select { width: 200px; }
.ledger-toolbar .el-input { width: 260px; }
.ledger-count { margin-left: auto; color: var(--el-text-color-secondary); font-size: 13px; }
</style>

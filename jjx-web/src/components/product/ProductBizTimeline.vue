<template>
  <section aria-label="业务流转">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <el-button link type="primary" @click="load">重试</el-button>
    </el-alert>
    <el-skeleton v-if="loading || productDocsLoading" :rows="5" animated />
    <el-timeline v-else-if="groups.length" class="biz-timeline">
      <el-timeline-item v-for="group in groups" :key="keyOf(group)" type="primary" hollow>
        <div class="document-summary">
          <el-button link type="primary" :aria-expanded="expanded.has(keyOf(group))" @click="toggle(group)">
            {{ group.sourceNo || `#${group.sourceId}` }}
          </el-button>
          <span>{{ AttachmentSourceTypeEnum.getLabel(group.sourceType) }}</span>
          <span>客户：—</span><span>状态：—</span><span>日期：—</span>
          <el-tag size="small" type="info">附件 {{ group.files.length }}</el-tag>
          <el-button link type="primary" @click="toggle(group)">{{ expanded.has(keyOf(group)) ? '收起' : '展开' }}</el-button>
        </div>
        <el-table v-if="expanded.has(keyOf(group))" :data="group.files" row-key="id" size="small" class="document-files">
          <el-table-column label="文件名" min-width="200">
            <template #default="{ row }">
              <span v-if="inLedger(row)">{{ row.fileName || row.displayName || `附件 #${row.attachmentId}` }}</span>
              <el-button v-else link type="primary" @click="previewFile(group, row)">
                {{ row.fileName || row.displayName || `附件 #${row.attachmentId}` }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="90">
            <template #default="{ row }">{{ formatSize(row.fileSize) || '-' }}</template>
          </el-table-column>
          <el-table-column label="上传人" min-width="90">
            <template #default="{ row }">{{ row.createBy || '-' }}</template>
          </el-table-column>
          <el-table-column label="附件上传时间" min-width="150">
            <template #default="{ row }">{{ formatTime(row.createTime) || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="160">
            <template #default="{ row }">
              <el-button v-if="inLedger(row)" link type="primary" @click="emit('openProductDoc', row.attachmentId!)">已归入产品资料</el-button>
              <el-button v-else link type="primary" @click="openUrl(attachmentRefApi.downloadUrlByRef(row.id))">下载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else-if="!error" description="暂无业务流转" :image-size="60" />
    <el-image-viewer v-if="viewerVisible" :url-list="viewerList" :initial-index="viewerIndex" @close="close" />
  </section>
</template>

<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue'
import { productApi, type ProductBizAttachment, type ProductBizAttachmentGroup } from '@/api/product'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import { AttachmentSourceTypeEnum } from '@/enums/system/AttachmentEnum'
import { useAttachmentPreview } from '@/components/attachment/useAttachmentPreview'
import { formatSize, formatTime } from '@/components/attachment/attachmentFormat'

const props = withDefaults(defineProps<{ productId: number; productAttachmentIds: number[]; productDocsLoading?: boolean }>(), { productDocsLoading: false })
const emit = defineEmits<{ openProductDoc: [attachmentId: number] }>()
const groups = ref<ProductBizAttachmentGroup[]>([])
const loading = ref(false)
const error = ref('')
const expanded = ref(new Set<string>())
const ledgerIds = computed(() => new Set(props.productAttachmentIds))
const keyOf = (group: ProductBizAttachmentGroup) => `${group.sourceType}:${group.sourceId}`
const inLedger = (file: ProductBizAttachment) => file.attachmentId != null && ledgerIds.value.has(file.attachmentId)
const { preview, openUrl, viewerVisible, viewerList, viewerIndex, close } = useAttachmentPreview()
let request = 0
async function load() {
  const current = ++request
  const id = props.productId
  groups.value = []; expanded.value = new Set(); error.value = ''; loading.value = !!id
  if (!id) return
  try {
    const response = await productApi.bizAttachments(id)
    if (current !== request) return
    if (!Array.isArray(response.data)) throw new Error('业务流转返回格式异常')
    // 接口已按单据时间排序；不以附件上传时间冒充单据日期重新排序。
    groups.value = response.data.map(group => {
      const seen = new Set<string>()
      return { ...group, files: group.files.filter(file => {
        const key = file.attachmentId != null ? `file:${file.attachmentId}` : `ref:${file.id}`
        if (seen.has(key)) return false
        seen.add(key); return true
      }) }
    })
    if (groups.value.length) expanded.value.add(keyOf(groups.value[0]))
  } catch (cause: unknown) {
    if (current !== request) return
    error.value = cause instanceof Error ? cause.message : '业务流转加载失败'
  } finally { if (current === request) loading.value = false }
}
function toggle(group: ProductBizAttachmentGroup) {
  const key = keyOf(group)
  if (expanded.value.has(key)) expanded.value.delete(key)
  else expanded.value.add(key)
}
function previewFile(group: ProductBizAttachmentGroup, file: ProductBizAttachment) {
  preview(group.files.filter(row => !inLedger(row)), file, row => attachmentRefApi.previewUrlByRef(row.id))
}
watch(() => props.productId, () => { close(); void load() }, { immediate: true })
onScopeDispose(() => { request++ })
</script>

<style scoped>
.biz-timeline { padding-top: 8px; }
.document-summary { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-size: 13px; }
.document-summary > span { color: var(--el-text-color-secondary); }
.document-files { margin-top: 8px; }
</style>

<template>
  <span v-if="!versionId">{{ emptyText }}</span>
  <el-button v-else link type="primary" :loading="loading" @click="openDetail">
    {{ version?.versionNo || (failed ? '版本读取失败，重试' : `版本 #${versionId}`) }}
  </el-button>
  <el-dialog v-model="visible" :title="`作业规范版本 ${version?.versionNo || ''}`" width="820px" append-to-body destroy-on-close>
    <el-descriptions v-if="version" :column="2" border>
      <el-descriptions-item label="版本号">{{ version.versionNo }}</el-descriptions-item>
      <el-descriptions-item label="产品ID">{{ version.productId }}</el-descriptions-item>
      <el-descriptions-item label="发布人">{{ version.publishedBy || '-' }}</el-descriptions-item>
      <el-descriptions-item label="发布时间">{{ version.publishedAt || '-' }}</el-descriptions-item>
      <el-descriptions-item label="修订说明" :span="2">{{ version.changeSummary || '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="items" border style="margin-top: 12px">
      <el-table-column prop="resourceType" label="条目类型" width="140" />
      <el-table-column label="发布内容 / 引用">
        <template #default="{ row }"><pre class="snapshot">{{ row.snapshotJson }}</pre></template>
      </el-table-column>
      <el-table-column prop="contentHash" label="哈希" width="140" show-overflow-tooltip />
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { productWorkSpecApi } from '@/api/product/workSpec'

const props = withDefaults(defineProps<{ versionId?: number | null; emptyText?: string }>(), {
  emptyText: '未指定 / 历史未知',
})
interface Version {
  versionNo: string
  productId: number
  publishedBy?: string
  publishedAt?: string
  changeSummary?: string
}
const version = ref<Version | null>(null)
const items = ref<{ resourceType: string; snapshotJson: string; contentHash?: string }[]>([])
const visible = ref(false)
const loading = ref(false)
const failed = ref(false)
let requestId = 0
async function load() {
  const current = ++requestId
  version.value = null
  items.value = []
  failed.value = false
  if (!props.versionId) { loading.value = false; return }
  loading.value = true
  try {
    // 历史只读取绑定ID，绝不按产品取最新版本。
    const response = await productWorkSpecApi.getVersion(props.versionId)
    if (current !== requestId) return
    version.value = response.data.version
    items.value = response.data.items || []
  } catch {
    if (current === requestId) failed.value = true
  } finally {
    if (current === requestId) loading.value = false
  }
}
async function openDetail() {
  if (!version.value) await load()
  if (version.value) visible.value = true
}
watch(() => props.versionId, () => { visible.value = false; load() }, { immediate: true })
</script>

<style scoped>
.snapshot { white-space: pre-wrap; overflow-wrap: anywhere; margin: 0; }
</style>

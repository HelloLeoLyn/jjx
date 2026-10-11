<template>
  <el-card shadow="never" class="wsv-card">
    <template #header>
      <div class="wsv-head">
        <span class="wsv-title">作业规范发布版本</span>
        <div>
          <el-button
            v-hasPermi="['product:work-spec:publish']"
            type="primary"
            size="small"
            :loading="publishing"
            @click="publish"
          >发布新版本</el-button>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>
    </template>

    <el-table :data="versions" size="small" border v-loading="loading">
      <el-table-column prop="versionNo" label="版本号" width="100" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'RETIRED' ? 'info' : 'success'">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="changeSummary" label="修订说明" min-width="180" show-overflow-tooltip />
      <el-table-column prop="publishedBy" label="发布人" width="100" align="center" />
      <el-table-column prop="publishedAt" label="发布时间" width="160" align="center" />
      <el-table-column label="操作" width="90" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">明细</el-button>
        </template>
      </el-table-column>
      <template #empty>尚未发布版本</template>
    </el-table>

    <el-dialog v-model="detailVisible" :title="`版本详情 ${detailVersion?.versionNo || ''}`" width="820px" append-to-body destroy-on-close>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="版本号">{{ detailVersion?.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detailVersion?.status) }}</el-descriptions-item>
        <el-descriptions-item label="发布人">{{ detailVersion?.publishedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ detailVersion?.publishedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="修订说明" :span="2">{{ detailVersion?.changeSummary || '-' }}</el-descriptions-item>
        <el-descriptions-item label="清单哈希" :span="2">{{ detailVersion?.manifestHash || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detailItems" size="small" border style="margin-top: 10px">
        <el-table-column label="条目" width="140">
          <template #default="{ row }">{{ resourceLabel(row.resourceType) }}</template>
        </el-table-column>
        <el-table-column label="内容 / 引用" min-width="320">
          <template #default="{ row }"><span class="wsv-snap">{{ row.snapshotJson }}</span></template>
        </el-table-column>
        <el-table-column label="哈希" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contentHash ? row.contentHash.slice(0, 12) + '…' : '-' }}</template>
        </el-table-column>
      </el-table>
      <div class="wsv-tip">发布后内容不可改；此处为只读快照。BOM/工艺路线为「引用已批准版本」，工程图为「引用文件版本 + 哈希」。</div>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productWorkSpecApi } from '@/api/product/workSpec'

const props = defineProps<{ productId: number }>()

const RESOURCE_LABELS: Record<string, string> = {
  SPEC_JSON: '规范正文快照',
  BOM: 'BOM',
  ROUTING: '工艺路线',
  DRAWING: '工程图/结构图',
}
const STATUS_LABELS: Record<string, string> = { PUBLISHED: '已发布', RETIRED: '已停用' }

const versions = ref<any[]>([])
const loading = ref(false)
const publishing = ref(false)
const detailVisible = ref(false)
const detailVersion = ref<any>(null)
const detailItems = ref<any[]>([])

function resourceLabel(code: string) {
  return RESOURCE_LABELS[code] || code
}
function statusLabel(code?: string) {
  return code ? STATUS_LABELS[code] || code : '-'
}

async function load() {
  if (!props.productId) return
  loading.value = true
  try {
    const res: any = await productWorkSpecApi.listVersions(props.productId)
    versions.value = res?.data || []
  } catch {
    versions.value = []
  } finally {
    loading.value = false
  }
}

async function publish() {
  try {
    const { value } = await ElMessageBox.prompt('填写本次修订说明（可空）', '发布新版本', {
      inputPlaceholder: '如：冲型压力 80→85',
      inputValue: '',
      confirmButtonText: '发布',
      cancelButtonText: '取消',
    })
    publishing.value = true
    const res: any = await productWorkSpecApi.publishVersion(props.productId, value || undefined)
    if (res?.code === 200) {
      ElMessage.success(`已发布 ${res.data?.versionNo || ''}`)
      await load()
    }
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

async function openDetail(row: any) {
  const res: any = await productWorkSpecApi.getVersion(row.id)
  if (res?.code === 200) {
    detailVersion.value = res.data?.version
    detailItems.value = res.data?.items || []
    detailVisible.value = true
  }
}

onMounted(load)
</script>

<style scoped>
.wsv-card { margin-bottom: 12px; }
.wsv-head { display: flex; align-items: center; justify-content: space-between; }
.wsv-title { font-weight: 600; }
.wsv-snap { white-space: pre-wrap; word-break: break-all; font-size: 12px; color: #555; }
.wsv-tip { margin-top: 10px; font-size: 12px; color: #909399; }
</style>

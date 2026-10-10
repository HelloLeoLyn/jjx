<template>
  <div class="customer-doc-panel" v-loading="loading">
    <div class="panel-heading">
      <div>
        <h3>客供资料</h3>
        <p>汇集当前产品的业务单据附件与工程上传的客供稿。</p>
      </div>
      <div class="panel-actions">
        <el-button @click="loadFiles">刷新</el-button>
        <el-button v-if="!readonly && canManage" @click="showFileManagement = !showFileManagement">管理工程客供稿</el-button>
        <el-upload
          v-if="!readonly && canUpload"
          :show-file-list="false"
          :before-upload="beforeUpload"
          :http-request="uploadFile"
          :disabled="uploading || !productCode"
        >
          <el-button type="primary" :loading="uploading" :disabled="!productCode">上传客供稿</el-button>
        </el-upload>
      </div>
    </div>

    <div class="summary">
      <div><strong>{{ files.length }}</strong><span>资料文件</span></div>
      <div><strong>{{ sourceCount }}</strong><span>单据附件</span></div>
      <div><strong>{{ productCount }}</strong><span>工程上传</span></div>
    </div>

    <el-alert
      title="当前展示来源附件；资料的受控入库、确认生效与修订历史将在后续接入。"
      type="info"
      :closable="false"
      class="panel-notice"
    />
    <el-alert v-if="error" :title="error" type="warning" :closable="false" class="panel-notice" />

    <div class="filters">
      <el-select v-model="sourceFilter" placeholder="全部来源" clearable style="width: 160px">
        <el-option label="询价单" value="询价单" />
        <el-option label="报价单" value="报价单" />
        <el-option label="工程上传" value="工程上传" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索文件名或单据号" clearable style="width: 250px" />
    </div>

    <el-table :data="visibleFiles" border size="small" row-key="key">
      <el-table-column label="文件名" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click="openFile(row.id)">{{ row.fileName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="110">
        <template #default="{ row }"><el-tag size="small" :type="row.source === '工程上传' ? 'primary' : 'info'">{{ row.source }}</el-tag></template>
      </el-table-column>
      <el-table-column label="来源单号" prop="sourceNo" min-width="150" show-overflow-tooltip />
      <el-table-column label="类别" prop="category" min-width="120" />
      <el-table-column label="附件版本" prop="version" width="110" />
      <el-table-column label="上传人" prop="createBy" width="110" />
      <el-table-column label="上传时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }"><el-button link type="primary" @click="openFile(row.id)">查看</el-button></template>
      </el-table-column>
      <template #empty><el-empty description="暂无客供资料文件" :image-size="60" /></template>
    </el-table>

    <div v-if="showFileManagement && !readonly && canManage" class="file-management">
      <ProductFileLibrary
        :product-code="productCode"
        :categories="['客供稿']"
        upload-perm="product:edit"
        delete-perm="product:delete"
        @success="loadFiles"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { attachmentApi } from '@/api/system/attachment'
import ProductFileLibrary from '@/components/product/ProductFileLibrary.vue'

interface CustomerFile {
  key: string
  id: number
  fileName: string
  source: string
  sourceNo: string
  category: string
  version: string
  createBy: string
  createTime: string
}

const props = withDefaults(defineProps<{ productId: number; productCode: string; readonly?: boolean }>(), {
  readonly: false,
})

const userStore = useUserStore()
const canUpload = computed(() => userStore.hasPermission('product:edit'))
const canManage = computed(() => canUpload.value || userStore.hasPermission('product:delete'))
const files = ref<CustomerFile[]>([])
const loading = ref(false)
const uploading = ref(false)
const error = ref('')
const sourceFilter = ref('')
const keyword = ref('')
const showFileManagement = ref(false)
let requestId = 0

const sourceCount = computed(() => files.value.filter(file => file.source !== '工程上传').length)
const productCount = computed(() => files.value.filter(file => file.source === '工程上传').length)
const visibleFiles = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  return files.value.filter(file =>
    (!sourceFilter.value || file.source === sourceFilter.value)
    && (!term || `${file.fileName} ${file.sourceNo}`.toLowerCase().includes(term))
  )
})

function text(value: unknown): string {
  return value == null || value === '' ? '-' : String(value)
}

async function loadFiles() {
  const current = ++requestId
  files.value = []
  error.value = ''
  if (!props.productId || !props.productCode) {
    loading.value = false
    return
  }
  loading.value = true
  const results = await Promise.allSettled([
    attachmentApi.customerDocs(props.productId),
    attachmentApi.productFiles(props.productCode),
  ])
  if (current !== requestId) return
  const rows: CustomerFile[] = []
  const seen = new Set<string>()
  if (results[0].status === 'fulfilled') {
    for (const item of results[0].value.data || []) {
      const id = Number(item.id)
      if (!id) continue
      const source = text(item.sourceType ?? item.sourcetype)
      const key = `${source}-${id}`
      if (seen.has(key)) continue
      seen.add(key)
      rows.push({
        key,
        id,
        fileName: text(item.file_name ?? item.fileName),
        source,
        sourceNo: text(item.sourceNo ?? item.sourceno),
        category: text(item.category),
        version: text(item.version),
        createBy: text(item.create_by ?? item.createBy),
        createTime: text(item.create_time ?? item.createTime),
      })
    }
  } else {
    error.value = '单据附件加载失败，请刷新重试。'
  }
  if (results[1].status === 'fulfilled') {
    for (const item of results[1].value.data || []) {
      if (item.category !== '客供稿') continue
      const id = Number(item.id)
      if (!id) continue
      rows.push({
        key: `product-${id}`,
        id,
        fileName: text(item.fileName),
        source: '工程上传',
        sourceNo: '-',
        category: text(item.category),
        version: text(item.version),
        createBy: text(item.createBy),
        createTime: text(item.createTime),
      })
    }
  } else {
    error.value += '工程客供稿加载失败，请刷新重试。'
  }
  files.value = rows.sort((a, b) => b.createTime.localeCompare(a.createTime))
  loading.value = false
}

function beforeUpload(file: File): boolean {
  if (file.size <= 50 * 1024 * 1024) return true
  ElMessage.error('文件大小不能超过50MB')
  return false
}

async function uploadFile(options: any) {
  uploading.value = true
  try {
    const response: any = await attachmentApi.uploadProductFile(options.file, props.productCode, '客供稿')
    if (response.code !== 200) throw new Error(response.msg || '上传失败')
    options.onSuccess(response.data)
    ElMessage.success('客供稿上传成功')
    await loadFiles()
  } catch (cause) {
    options.onError(cause)
    ElMessage.error(cause instanceof Error ? cause.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

function openFile(id: number) {
  window.open(attachmentApi.downloadUrl(id), '_blank', 'noopener,noreferrer')
}

watch(() => [props.productId, props.productCode], loadFiles, { immediate: true })
</script>

<style scoped>
.customer-doc-panel { padding: 8px 0 16px; }
.panel-heading, .panel-actions, .filters { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.panel-heading { justify-content: space-between; margin-bottom: 16px; }
.panel-heading h3 { margin: 0 0 4px; font-size: 17px; }
.panel-heading p { margin: 0; color: #909399; font-size: 12px; }
.summary { display: flex; gap: 12px; margin-bottom: 16px; }
.summary > div { display: flex; align-items: baseline; gap: 8px; min-width: 135px; padding: 12px 16px; border: 1px solid #ebeef5; border-radius: 6px; }
.summary strong { font-size: 22px; }
.summary span { color: #909399; font-size: 13px; }
.panel-notice, .filters { margin-bottom: 14px; }
.file-management { margin-top: 20px; padding: 16px; border: 1px solid #ebeef5; border-radius: 6px; }
</style>

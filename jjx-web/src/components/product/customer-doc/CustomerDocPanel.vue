<template>
  <div class="customer-doc-panel" v-loading="loading">
    <div class="panel-heading">
      <div>
        <h3>客供资料</h3>
        <p>本产品资料（上传/管理走公共附件组件）＋ 单据归集（只读）。</p>
      </div>
    </div>

    <!-- 本产品资料：公共附件组件（上传/列表/预览下载/解绑；写引用表 sys_attachment_ref）dev-20261014-020 -->
    <AttachmentCenter
      source-type="product"
      :source-id="productId"
      :product-id="productId"
      :product-code="productCode"
      :mode="readonly ? 'readonly' : 'panel'"
      view-perm="product:view"
      edit-perm="product:edit"
      title="本产品资料"
    />

    <el-divider content-position="left">单据归集（只读）</el-divider>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" class="panel-notice" />

    <div class="filters">
      <el-select v-model="sourceFilter" placeholder="全部来源" clearable style="width: 160px">
        <el-option label="询价单" value="询价单" />
        <el-option label="报价单" value="报价单" />
        <el-option label="销售订单" value="销售订单" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索文件名或单据号" clearable style="width: 250px" />
      <el-button @click="loadFiles">刷新</el-button>
    </div>

    <el-table :data="visibleFiles" border size="small" row-key="key">
      <el-table-column label="文件名" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link
            type="primary"
            :title="row.originalFileName !== row.fileName ? `原文件名：${row.originalFileName}` : row.fileName"
            @click="openFile(row.id)"
            >{{ row.fileName }}</el-link
          >
        </template>
      </el-table-column>
      <el-table-column label="来源" width="110">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.source }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源单号" prop="sourceNo" min-width="150" show-overflow-tooltip />
      <el-table-column label="类别" prop="category" min-width="120" />
      <el-table-column label="上传人" prop="createBy" width="110" />
      <el-table-column label="上传时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openFile(row.id)">查看</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无单据归集资料" :image-size="60" /></template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'
import { attachmentApi } from '@/api/system/attachment'

interface CustomerFile {
  key: string
  id: number
  fileName: string
  originalFileName: string
  source: string
  sourceNo: string
  category: string
  createBy: string
  createTime: string
}

const props = withDefaults(defineProps<{ productId: number; productCode: string; readonly?: boolean }>(), {
  readonly: false,
})

const files = ref<CustomerFile[]>([])
const loading = ref(false)
const error = ref('')
const sourceFilter = ref('')
const keyword = ref('')
let requestId = 0

const visibleFiles = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  return files.value.filter(
    (file) =>
      (!sourceFilter.value || file.source === sourceFilter.value) &&
      (!term || `${file.fileName} ${file.originalFileName} ${file.sourceNo}`.toLowerCase().includes(term)),
  )
})

function text(value: unknown): string {
  return value == null || value === '' ? '-' : String(value)
}

/** 单据归集（只读）：某产品关联的询价/报价/订单/样品单附件（跨单据并集，dev-20260929-023） */
async function loadFiles() {
  const current = ++requestId
  files.value = []
  error.value = ''
  if (!props.productId) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const res: any = await attachmentApi.customerDocs(props.productId)
    if (current !== requestId) return
    const rows: CustomerFile[] = []
    const seen = new Set<string>()
    for (const item of res?.data || []) {
      const id = Number(item.id)
      if (!id) continue
      const source = text(item.sourceType ?? item.sourcetype)
      const key = `${source}-${id}`
      if (seen.has(key)) continue
      seen.add(key)
      rows.push({
        key,
        id,
        fileName: text(item.display_name || item.displayName || item.file_name || item.fileName),
        originalFileName: text(item.file_name ?? item.fileName),
        source,
        sourceNo: text(item.sourceNo ?? item.sourceno),
        category: text(item.category),
        createBy: text(item.create_by ?? item.createBy),
        createTime: text(item.create_time ?? item.createTime),
      })
    }
    files.value = rows.sort((a, b) => b.createTime.localeCompare(a.createTime))
  } catch {
    error.value = '单据归集加载失败，请刷新重试。'
  } finally {
    if (current === requestId) loading.value = false
  }
}

function openFile(id: number) {
  window.open(attachmentApi.downloadUrl(id), '_blank', 'noopener,noreferrer')
}

watch(() => [props.productId, props.productCode], loadFiles, { immediate: true })
</script>

<style scoped>
.customer-doc-panel {
  padding: 8px 0 16px;
}
.panel-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: space-between;
  margin-bottom: 16px;
}
.panel-heading h3 {
  margin: 0 0 4px;
  font-size: 17px;
}
.panel-heading p {
  margin: 0;
  color: #909399;
  font-size: 12px;
}
.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.panel-notice {
  margin-bottom: 14px;
}
</style>

<template>
  <div class="app-container quality-archive-page">
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="模板">
          <el-select v-model="query.templateId" clearable filterable placeholder="全部模板" style="width: 220px">
            <el-option
              v-for="item in templates"
              :key="item.id"
              :label="`${item.recordNo} ${item.recordName}`"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="归属部门">
          <el-select v-model="query.ownerDept" clearable filterable placeholder="全部部门" style="width: 150px">
            <el-option v-for="dept in ownerDepts" :key="dept" :label="dept" :value="dept" />
          </el-select>
        </el-form-item>
        <el-form-item label="回传状态">
          <el-select v-model="query.archived" clearable placeholder="全部" style="width: 130px">
            <el-option label="已回传" :value="true" />
            <el-option label="未回传" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="到期状态">
          <el-select v-model="query.expiryState" clearable placeholder="全部" style="width: 140px">
            <el-option label="已到期" value="overdue" />
            <el-option label="临期30天" value="soon" />
          </el-select>
        </el-form-item>
        <el-form-item label="记录号">
          <el-input v-model.trim="query.recordNo" clearable placeholder="模糊搜索" @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button :icon="Refresh" @click="loadRows">刷新</el-button>
        </el-form-item>
      </el-form>
      <div class="expiry-tip">到期口径：到期日早于今天为已到期；今天起至未来 30 天（含）为临期。</div>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="recordNo" label="记录号" min-width="130" />
        <el-table-column prop="recordName" label="模板名" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ownerDept" label="归属部门" width="120" />
        <el-table-column prop="bizType" label="业务类型" min-width="150" />
        <el-table-column prop="bizId" label="bizId" width="100" />
        <el-table-column prop="operatorName" label="打印人" width="110" />
        <el-table-column prop="printTime" label="打印时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.printTime) }}</template>
        </el-table-column>
        <el-table-column prop="expiryDate" label="到期日" width="120">
          <template #default="{ row }">
            <span :class="{ overdue: isOverdue(row.expiryDate) }">{{ row.expiryDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="回传状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.archived ? 'success' : 'info'">{{ row.archived ? '已回传' : '未回传' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="130">
          <template #default="{ row }">
            <el-button
              v-if="!row.archived"
              link
              type="primary"
              :disabled="!row.bizType || !row.bizId"
              @click="openUpload(row)"
            >回传扫描件</el-button>
            <el-button v-else link type="primary" @click="openAttachments(row)">查看附件</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="loadRows"
        @current-change="loadRows"
      />
    </el-card>

    <el-dialog v-model="uploadVisible" title="回传扫描件" width="520px" destroy-on-close>
      <AttachmentUploader
        v-if="selectedRow?.bizType && selectedRow.bizId"
        :biz-type="selectedRow.bizType"
        :biz-id="selectedRow.bizId"
        category="quality_archive_scan"
        button-text="选择并上传扫描件"
        :accept="['.pdf', '.png', '.jpg', '.jpeg']"
        @success="uploaded"
      />
    </el-dialog>

    <el-dialog v-model="attachmentsVisible" title="归档附件" width="680px" destroy-on-close>
      <AttachmentPanel
        v-if="selectedRow?.bizType && selectedRow.bizId"
        :biz-type="selectedRow.bizType"
        :biz-id="selectedRow.bizId"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { Refresh } from '@element-plus/icons-vue'
import AttachmentPanel from '@/components/AttachmentPanel/index.vue'
import AttachmentUploader from '@/components/AttachmentUploader/index.vue'
import {
  getQualityArchivePage,
  type QualityArchiveQuery,
  type QualityArchiveRow,
} from '@/api/production/qualityArchive'
import {
  getQualityTemplateOwnerDepts,
  getQualityTemplatePage,
  type QualityTemplate,
} from '@/api/production/qualityTemplate'

const loading = ref(false)
const rows = ref<QualityArchiveRow[]>([])
const total = ref(0)
const templates = ref<QualityTemplate[]>([])
const ownerDepts = ref<string[]>([])
const selectedRow = ref<QualityArchiveRow>()
const uploadVisible = ref(false)
const attachmentsVisible = ref(false)
const query = reactive<QualityArchiveQuery>({ pageNum: 1, pageSize: 20 })

function responseRows(response: any) {
  return response?.data?.records || []
}

async function loadRows() {
  loading.value = true
  try {
    const response: any = await getQualityArchivePage(query)
    rows.value = responseRows(response)
    total.value = Number(response?.data?.total || 0)
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [templateResponse, deptResponse]: any[] = await Promise.all([
    getQualityTemplatePage({ pageNum: 1, pageSize: 100 }),
    getQualityTemplateOwnerDepts(),
  ])
  templates.value = responseRows(templateResponse)
  ownerDepts.value = deptResponse?.data || []
}

function search() {
  query.pageNum = 1
  loadRows()
}

function reset() {
  Object.assign(query, { pageNum: 1, pageSize: query.pageSize }, {
    templateId: undefined,
    ownerDept: undefined,
    archived: undefined,
    expiryState: undefined,
    recordNo: undefined,
  })
  loadRows()
}

function openUpload(row: QualityArchiveRow) {
  selectedRow.value = row
  uploadVisible.value = true
}

function openAttachments(row: QualityArchiveRow) {
  selectedRow.value = row
  attachmentsVisible.value = true
}

async function uploaded() {
  uploadVisible.value = false
  await loadRows()
}

function formatDateTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
}

function isOverdue(value?: string) {
  return Boolean(value && dayjs(value).isBefore(dayjs(), 'day'))
}

onMounted(() => {
  loadRows()
  loadOptions()
})
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.search-card :deep(.el-form-item) { margin-bottom: 10px; }
.expiry-tip { color: var(--el-text-color-secondary); font-size: 12px; }
.overdue { color: var(--el-color-danger); font-weight: 600; }
.pagination { justify-content: flex-end; margin-top: 16px; }
</style>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="变更日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item label="变更机种">
          <el-input v-model="queryParams.bizNo" placeholder="机种/单号关键字" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.requirementStatus" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="status in statusOptions" :key="status.value" :label="status.label" :value="status.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更类型">
          <el-select v-model="queryParams.changeType" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in changeTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" icon="Download" v-hasPermi="['biz:requirement:view']" :loading="exporting" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 12px">
      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="变更日期" width="120" align="center">
          <template #default="{ row }">{{ formatDate(row.applyTime) }}</template>
        </el-table-column>
        <el-table-column label="单号" prop="requirementNo" width="150" />
        <el-table-column label="变更机种" prop="bizNo" min-width="140" />
        <el-table-column label="变更内容" prop="description" min-width="240" show-overflow-tooltip />
        <el-table-column label="变更类型" width="110" align="center">
          <template #default="{ row }">{{ changeTypeLabel(row.changeType) }}</template>
        </el-table-column>
        <el-table-column label="版本" width="140" align="center">
          <template #default="{ row }">{{ versionLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.requirementStatus)">{{ statusLabel(row.requirementStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请人" prop="applicantName" width="100" align="center" />
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'BizChanges' })

import { onMounted, reactive, ref } from 'vue'
import type { TagProps } from 'element-plus'
import { ElMessage } from 'element-plus'
import { changesExport, pageRequirement } from '@/api/biz/requirement'
import { RequirementStatusEnum } from '@/enums/biz/RequirementEnum'
import { download } from '@/utils/format'

interface RequirementRow {
  applyTime?: string
  requirementNo?: string
  bizNo?: string
  description?: string
  changeType?: string
  versionBefore?: string
  versionAfter?: string
  requirementStatus?: number
  applicantName?: string
}

const changeTypeOptions = [
  { value: 'DESIGN', label: '设计改版' },
  { value: 'PROCESS', label: '工艺调整' },
  { value: 'MATERIAL', label: '材料变更' },
  { value: 'DRAWING', label: '图纸更新' },
  { value: 'OTHER', label: '其他' },
]
const visibleStatuses = new Set<number>([
  RequirementStatusEnum.DRAFT.value,
  RequirementStatusEnum.REVIEWING.value,
  RequirementStatusEnum.APPROVED.value,
  RequirementStatusEnum.EXECUTING.value,
])
const statusOptions = RequirementStatusEnum.items.filter(status => visibleStatuses.has(status.value))

const loading = ref(false)
const exporting = ref(false)
const list = ref<RequirementRow[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | []>([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  bizNo: '',
  requirementStatus: RequirementStatusEnum.APPROVED.value as number | undefined,
  changeType: '',
})

function requestParams(includePage = true): Record<string, unknown> {
  const params: Record<string, unknown> = {
    requirementType: 'CHANGE',
    bizNo: queryParams.bizNo || undefined,
    requirementStatus: queryParams.requirementStatus,
    changeType: queryParams.changeType || undefined,
    startDate: dateRange.value[0],
    endDate: dateRange.value[1],
  }
  if (includePage) {
    params.pageNum = queryParams.pageNum
    params.pageSize = queryParams.pageSize
  }
  return params
}

async function getList() {
  loading.value = true
  try {
    const response: any = await pageRequirement(requestParams())
    list.value = response.data?.records || []
    total.value = Number(response.data?.total || 0)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  getList()
}

function handleReset() {
  dateRange.value = []
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    bizNo: '',
    requirementStatus: RequirementStatusEnum.APPROVED.value,
    changeType: '',
  })
  getList()
}

async function handleExport() {
  exporting.value = true
  try {
    const response: any = await changesExport(requestParams(false))
    const now = new Date()
    const date = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
    download(response, `变更记录表-${date}.xlsx`)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

function statusLabel(value?: number) {
  return RequirementStatusEnum.items.find(status => status.value === value)?.label || '-'
}

function statusTag(value?: number): TagProps['type'] {
  return value == null ? 'info' : RequirementStatusEnum.getTagProps(value).type
}

function changeTypeLabel(value?: string) {
  return changeTypeOptions.find(item => item.value === value)?.label || value || '-'
}

function versionLabel(row: RequirementRow) {
  if (!row.versionBefore && !row.versionAfter) return '-'
  return `${row.versionBefore || ''}→${row.versionAfter || ''}`
}

function formatDate(value?: string) {
  return value ? value.slice(0, 10) : '-'
}

onMounted(getList)
</script>

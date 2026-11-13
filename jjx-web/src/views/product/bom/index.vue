<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :model="query" inline @submit.prevent="search">
        <el-form-item label="产品编码"
          ><el-input v-model="query.productCode" clearable @keyup.enter="search"
        /></el-form-item>
        <el-form-item label="产品名称"
          ><el-input v-model="query.productName" clearable @keyup.enter="search"
        /></el-form-item>
        <el-form-item label="BOM名称"
          ><el-input v-model="query.bomName" clearable @keyup.enter="search"
        /></el-form-item>
        <el-form-item label="审核状态"
          ><el-select v-model="query.approveStatus" clearable style="width: 140px"
            ><el-option
              v-for="s in BomStatusEnum.items"
              :key="s.value"
              :label="s.label"
              :value="s.value" /></el-select
        ></el-form-item>
        <el-button type="primary" @click="search">搜索</el-button
        ><el-button @click="reset">重置</el-button>
      </el-form>
      <el-button v-hasPermi="['engineering:bom:add']" type="primary" @click="add"
        >新增BOM</el-button
      >
      <el-button @click="exportList">导出全部版本</el-button>
      <el-table v-loading="loading" :data="rows" :row-key="rowKey" style="margin-top: 16px">
        <el-table-column prop="productCode" label="产品/对象编码" min-width="160" />
        <el-table-column prop="productName" label="产品/对象名称" min-width="160" />
        <el-table-column label="规格" min-width="140" show-overflow-tooltip
          ><template #default="{ row }">{{
            formatSpecification(row.specification)
          }}</template></el-table-column
        >
        <el-table-column label="当前版本（生效日）" min-width="190"
          ><template #default="{ row }"
            >{{ row.currentVersion || '—' }}（{{
              row.currentEffectiveDate ? parseTime(row.currentEffectiveDate, '{y}-{m}-{d}') : '—'
            }}）</template
          ></el-table-column
        >
        <el-table-column prop="latestVersion" label="最新版本" width="110" />
        <el-table-column label="版本数" width="90"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openVersions(row)">{{
              row.versionCount
            }}</el-button></template
          ></el-table-column
        >
        <el-table-column label="状态摘要" min-width="150"
          ><template #default="{ row }"
            ><el-tag v-if="row.hasDraft" type="info">有草稿</el-tag>
            <el-tag v-if="row.hasPending" type="warning">有待审</el-tag
            ><span v-if="!row.hasDraft && !row.hasPending">—</span></template
          ></el-table-column
        >
        <el-table-column label="最近修改时间" width="170"
          ><template #default="{ row }">{{
            parseTime(row.updateTime) || '—'
          }}</template></el-table-column
        >
        <el-table-column label="操作" width="280"
          ><template #default="{ row }">
            <el-button link type="primary" @click="openTrace(row)">流水</el-button>
            <el-button
              v-hasPermi="['engineering:bom:edit']"
              link
              type="primary"
              @click="openVersions(row)"
              >设置当前版本</el-button
            >
            <el-button link type="primary" @click="openVersions(row)">版本维护</el-button>
          </template></el-table-column
        >
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="query.pageNum"
        v-model:limit="query.pageSize"
        @pagination="loadData"
      />
    </el-card>
    <el-dialog
      v-model="versionsVisible"
      :title="`${selected?.productName || ''} · 版本维护`"
      width="850px"
    >
      <el-table v-loading="versionsLoading" :data="versions">
        <el-table-column prop="bomVersion" label="版本号" />
        <el-table-column label="状态"
          ><template #default="{ row }">{{
            BomStatusEnum.getLabel(row.approveStatus)
          }}</template></el-table-column
        >
        <el-table-column prop="createBy" label="创建人" /><el-table-column
          prop="createTime"
          label="创建时间"
          min-width="160"
        />
        <el-table-column label="操作" min-width="200"
          ><template #default="{ row }">
            <el-button link @click="view(row)">查看</el-button>
            <el-button
              v-if="BomStatusEnum.canDo(row.approveStatus, ProductActions.EDIT)"
              v-hasPermi="['engineering:bom:edit']"
              link
              type="primary"
              @click="edit(row)"
              >编辑</el-button
            >
            <el-button
              v-if="row.approveStatus === BomStatusEnum.APPROVED.value"
              v-hasPermi="['engineering:bom:edit']"
              link
              type="primary"
              @click="setCurrent(row)"
              >设为当前</el-button
            >
          </template></el-table-column
        >
      </el-table>
    </el-dialog>
    <BomFormDialog ref="formDialogRef" v-model="formVisible" :bom-id="formId" @success="refresh" />
    <BomDetail v-model="detailVisible" :bom-id="detailId" />
    <BomApproveDialog v-model="approveVisible" :bom-id="approveId" @success="refresh" />
    <TraceTimeline v-model="traceVisible" biz-type="bom" :biz-id="String(traceId || '')" />
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { parseTime } from '@/utils/format'
import { BomStatusEnum, ProductActions } from '@/enums/product'
import { productBomApi } from '@/api/product/bom'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import BomFormDialog from './components/BomFormDialog.vue'
import BomDetail from './components/BomDetail.vue'
import BomApproveDialog from './components/BomApproveDialog.vue'
import type { EngineeringBom } from '@/types/product/bom'
type Version = EngineeringBom
interface ProductRow {
  outputType: string
  outputId: number
  productCode?: string
  productName?: string
  specification?: string
  currentId?: number
  currentVersion?: string
  currentEffectiveDate?: string
  latestId: number
  latestVersion: string
  versionCount: number
  hasDraft: boolean
  hasPending: boolean
  updateTime?: string
}
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  productCode: '',
  productName: '',
  bomName: '',
  approveStatus: undefined as number | undefined,
})
const rows = ref<ProductRow[]>([])
const total = ref(0)
const loading = ref(false)
let listRequest = 0
const rowKey = (row: ProductRow) => `${row.outputType}:${row.outputId}`
const formatSpecification = (value?: string) => {
  if (!value) return '—'
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed))
      return (
        parsed
          .map((x) => `${x.name}: ${x.value}${x.unit === '-' ? '' : x.unit || ''}`)
          .join(' / ') || '—'
      )
    if (parsed && typeof parsed === 'object') {
      const dimensions = [parsed.length, parsed.width, parsed.height].filter((x) => Number(x) > 0)
      return dimensions.length ? `${dimensions.join(' × ')} ${parsed.unit || 'mm'}` : '—'
    }
    return String(parsed)
  } catch {
    return value
  }
}
const loadData = async () => {
  const requestId = ++listRequest
  loading.value = true
  try {
    const response = await request.get('/engineering/bom/product-page', { params: query })
    if (requestId !== listRequest) return
    rows.value = response.data?.records || []
    total.value = response.data?.total || 0
  } finally {
    if (requestId === listRequest) loading.value = false
  }
}
const search = () => {
  query.pageNum = 1
  void loadData()
}
const reset = () => {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 10,
    productCode: '',
    productName: '',
    bomName: '',
    approveStatus: undefined,
  })
  void loadData()
}
const exportList = async () => {
  const data = await productBomApi.exportEngineeringBom({
    ...query,
    approveStatus: query.approveStatus == null ? undefined : String(query.approveStatus),
    showAllVersions: true,
  })
  const url = URL.createObjectURL(new Blob([data as any]))
  const link = document.createElement('a')
  link.href = url
  link.download = 'BOM版本列表.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}
const selected = ref<ProductRow>()
const versions = ref<Version[]>([])
const versionsVisible = ref(false)
const versionsLoading = ref(false)
let versionRequest = 0
const loadVersions = async () => {
  if (!selected.value) return
  const requestId = ++versionRequest
  versionsLoading.value = true
  try {
    const response = await request.get('/engineering/bom/object-versions', {
      params: { outputType: selected.value.outputType, outputId: selected.value.outputId },
    })
    if (requestId === versionRequest) versions.value = response.data || []
  } finally {
    if (requestId === versionRequest) versionsLoading.value = false
  }
}
const openVersions = (row: ProductRow) => {
  selected.value = row
  versions.value = []
  versionsVisible.value = true
  void loadVersions()
}
const refresh = async () => {
  await loadData()
  if (versionsVisible.value) await loadVersions()
}
const formVisible = ref(false)
const formId = ref<number>()
const formDialogRef = ref<InstanceType<typeof BomFormDialog>>()
const detailVisible = ref(false)
const detailId = ref<number>()
const approveVisible = ref(false)
const approveId = ref<number>()
const add = () => {
  formId.value = undefined
  formVisible.value = true
}
const edit = (row: Version) => {
  formId.value = row.bomId
  formVisible.value = true
}
const view = (row: Version) => {
  detailId.value = row.bomId
  detailVisible.value = true
}
const setCurrent = async (row: Version) => {
  await ElMessageBox.confirm(`将 ${row.bomVersion} 设为当前版本？`, '设置当前版本', {
    type: 'warning',
  })
  await productBomApi.setCurrentEngineeringBom(row.bomId)
  ElMessage.success('已设置当前版本')
  await refresh()
}
const traceVisible = ref(false)
const traceId = ref<number>()
const openTrace = (row: ProductRow) => {
  traceId.value = row.currentId || row.latestId
  traceVisible.value = true
}
onMounted(loadData)
onBeforeRouteLeave(async () => {
  if (!formVisible.value || !formDialogRef.value?.formIsDirty?.()) return true
  return await formDialogRef.value.resolveLeave()
})
</script>

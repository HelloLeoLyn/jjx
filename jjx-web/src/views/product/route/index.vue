<template>
  <div class="app-container">
    <el-card shadow="never">
      <RouteSearch @search="handleSearch" @reset="reset" />
      <el-button v-hasPermi="['engineering:routing:add']" type="primary" @click="add"
        >新增工艺路线</el-button
      >
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
              v-hasPermi="['engineering:routing:edit']"
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
    <VersionMaintenanceDialog
      v-model="versionsVisible"
      :title="selected?.productName || selected?.productCode || ''"
      domain="routing"
      :rows="versionRows"
      :loading="versionsLoading"
      :busy="actionBusy"
      :supports-validity="false"
      :supports-review-audit="false"
      :fetch-detail="fetchVersionDetail"
      @action="handleVersionAction"
    />
    <RouteCopyDialog
      v-model="copyVisible"
      :current-version="copySourceVersion"
      :suggested-version="suggestedVersion"
      :loading="copyBusy"
      :error="copyError"
      @confirm="copyConfirm"
    />
    <RouteFormDialog
      ref="formDialogRef"
      v-model="formVisible"
      :routing-id="formId"
      @success="refresh"
    />
    <RouteDetailDialog v-model="detailVisible" :routing-id="detailId" />
    <RouteApproveDialog
      v-model="approveVisible"
      :routing-id="approveId"
      @approve="approvePass"
      @reject="approveReject"
    />
    <TraceTimeline v-model="traceVisible" biz-type="routing" :biz-id="String(traceId || '')" />
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import VersionMaintenanceDialog from '@/components/product/VersionMaintenanceDialog.vue'
import type { VersionMaintenanceRow } from '@/components/product/VersionMaintenanceDialog.vue'
import { parseTime } from '@/utils/format'
import { RouteStatusEnum, ProductActions } from '@/enums/product'
import { productRouteApi } from '@/api/product/routing'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import RouteCopyDialog from './components/RouteCopyDialog.vue'
import RouteApproveDialog from './components/RouteApproveDialog.vue'
import RouteSearch from './components/RouteSearch.vue'
import RouteFormDialog from './components/RouteFormDialog.vue'
import RouteDetailDialog from './components/RouteDetailDialog.vue'
import type { ProductQueryParams } from '@/types/product'
import type { EngineeringRoutingVO } from '@/types/product/routing'
type Version = EngineeringRoutingVO & { canSetCurrent: boolean }
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
  routingCode: '',
  routingName: '',
  productId: undefined as number | undefined,
  isCurrent: undefined as number | undefined,
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
    const response = await request.get('/engineering/routings/product-page', { params: query })
    if (requestId !== listRequest) return
    rows.value = response.data?.records || []
    total.value = response.data?.total || 0
  } finally {
    if (requestId === listRequest) loading.value = false
  }
}
const handleSearch = (params: ProductQueryParams) => {
  Object.assign(query, params)
  query.pageNum = 1
  void loadData()
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
    routingCode: '',
    routingName: '',
    productId: undefined as number | undefined,
    isCurrent: undefined as number | undefined,
    approveStatus: undefined,
  })
  void loadData()
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
    const response = await request.get('/engineering/routings/object-versions', {
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
const formDialogRef = ref<InstanceType<typeof RouteFormDialog>>()
const detailVisible = ref(false)
const detailId = ref<number>()
const approveVisible = ref(false)
const approveId = ref<number>()

const add = () => {
  formId.value = undefined
  formVisible.value = true
}
const edit = (row: Version) => {
  formId.value = row.routingId
  formVisible.value = true
}
const view = (row: Version) => {
  detailId.value = row.routingId
  detailVisible.value = true
}
const versionRows = computed<VersionMaintenanceRow[]>(() =>
  versions.value.map((row) => ({
    id: row.routingId,
    version: row.routingVersion,
    approveStatus: row.approveStatus,
    isCurrent: Boolean(row.isCurrent),
    canSetCurrent: row.canSetCurrent,
    createBy: row.createBy,
    createTime: row.createTime,
    remark: row.remark,
  }))
)
const fetchVersionDetail = async (id: number) => {
  const response = await productRouteApi.getProductRouteInfo(id)
  if (!response.data) throw new Error('版本已不存在，请刷新')
  return response.data
}
const actionBusy = ref(false)
const copyVisible = ref(false)
const copyId = ref<number>()
const copySourceVersion = ref('')
const suggestedVersion = ref('')
const copyBusy = ref(false)
const copyError = ref('')
const copyConfirm = async (newVersion: string) => {
  if (copyBusy.value || !copyId.value) return
  copyBusy.value = true
  copyError.value = ''
  try {
    await productRouteApi.copyProductRoute(copyId.value, newVersion.trim())
    copyVisible.value = false
    ElMessage.success('复制成功')
    await refresh()
  } catch (error: any) {
    copyError.value = error?.message || '复制失败，请检查版本号后重试'
  } finally {
    copyBusy.value = false
  }
}
const handleVersionAction = async (key: string, row: VersionMaintenanceRow) => {
  if (actionBusy.value) return
  const raw = versions.value.find((v) => v.routingId === row.id)
  if (!raw) return
  if (key === 'view') {
    view(raw)
    return
  }
  if (key === 'edit') {
    edit(raw)
    return
  }
  if (key === 'approve') {
    approveId.value = row.id
    approveVisible.value = true
    return
  }
  const expectedCurrentId = versions.value.find((v) => Boolean(v.isCurrent))?.routingId || 0
  actionBusy.value = true
  try {
    if (key === 'copy') {
      const response = await request.get(`/engineering/routings/${row.id}/next-version`)
      copyId.value = row.id
      copySourceVersion.value = row.version
      suggestedVersion.value = response.data
      copyError.value = ''
      copyVisible.value = true
      return
    }
    if (key === 'submit') {
      await ElMessageBox.confirm(`提交 ${row.version} 审核？`, '提交审核')
      await productRouteApi.submitProductRoute(row.id)
    } else if (key === 'setCurrent') {
      await ElMessageBox.confirm(`将 ${row.version} 设为当前版本？`, '设置当前版本', {
        type: 'warning',
      })
      await request.put(`/engineering/routings/${row.id}/set-current`, null, {
        params: { expectedCurrentId },
      })
    } else if (key === 'delete') {
      await ElMessageBox.confirm(`删除草稿版本 ${row.version}？`, '删除版本', { type: 'warning' })
      await productRouteApi.removeProductRoute(row.id)
    } else if (key === 'void') {
      await ElMessageBox.confirm(`作废已生效版本 ${row.version}？`, '作废版本', { type: 'warning' })
      await request.put(`/engineering/routings/${row.id}/void`)
    } else return
    ElMessage.success('操作成功')
    await refresh()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '操作失败，请刷新重试')
      await refresh()
    }
  } finally {
    actionBusy.value = false
  }
}
const approveRoute = async (pass: boolean, remark?: string) => {
  if (actionBusy.value || !approveId.value) return
  actionBusy.value = true
  try {
    if (pass) await productRouteApi.approveProductRoute(approveId.value, remark)
    else await productRouteApi.rejectProductRoute(approveId.value, remark || '')
    approveVisible.value = false
    ElMessage.success(pass ? '审核通过' : '已驳回')
    await refresh()
  } finally {
    actionBusy.value = false
  }
}
const approvePass = (remark?: string) => approveRoute(true, remark)
const approveReject = (remark: string) => approveRoute(false, remark)
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

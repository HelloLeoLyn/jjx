<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" class="bom-domain-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="产品BOM" name="PRODUCT" />
        <el-tab-pane label="材料BOM" name="MATERIAL" />
      </el-tabs>
      <el-form :model="query" inline @submit.prevent="search">
        <el-form-item :label="activeTab === 'PRODUCT' ? '产品编码' : '物料编码'">
          <el-input v-model="query.productCode" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item :label="activeTab === 'PRODUCT' ? '产品名称' : '物料名称'">
          <el-input v-model="query.productName" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="BOM名称">
          <el-input v-model="query.bomName" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="query.approveStatus" clearable style="width: 140px">
            <el-option
              v-for="s in BomStatusEnum.items"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-button type="primary" @click="search">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form>

      <el-button
        v-if="activeTab === 'PRODUCT'"
        v-hasPermi="['engineering:bom:add']"
        type="primary"
        @click="add"
        >新增BOM</el-button
      >
      <el-button @click="exportList">导出全部版本</el-button>

      <el-table v-loading="loading" :data="rows" :row-key="rowKey" style="margin-top: 16px">
        <el-table-column :label="activeTab === 'PRODUCT' ? '产品信息' : '物料信息'" min-width="220">
          <template #default="{ row }">
            <div>{{ row.productCode || '—' }}</div>
            <div
              v-if="row.productName && row.productName !== row.productCode"
              class="secondary-line"
            >
              {{ row.productName }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="规格" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ formatSpecification(row.specification) }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'PRODUCT'" label="当前生效版本" min-width="180">
          <template #default="{ row }">
            <el-link v-if="row.currentId" type="primary" @click="openVersionDetail(row.currentId)">
              {{ row.currentVersion || '查看' }}
            </el-link>
            <span v-else class="secondary-line">未生效</span>
            <el-tag
              v-if="row.currentId && row.currentStatus != null"
              size="small"
              :type="statusTagType(row.currentStatus)"
              class="inline-tag"
            >
              {{ statusLabel(row.currentStatus) }}
            </el-tag>
            <div v-if="row.currentId && row.currentEffectiveDate" class="secondary-line">
              生效：{{ parseTime(row.currentEffectiveDate, 'yyyy-MM-dd') }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最新维护版本" min-width="170">
          <template #default="{ row }">
            <el-link v-if="row.latestId" type="primary" @click="openVersionDetail(row.latestId)">
              {{ row.latestVersion || '查看' }}
            </el-link>
            <span v-else class="secondary-line">未建BOM</span>
            <el-tag
              v-if="row.latestId && row.latestStatus != null"
              size="small"
              :type="statusTagType(row.latestStatus)"
              class="inline-tag"
            >
              {{ statusLabel(row.latestStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="activeTab === 'PRODUCT'"
          label="待处理"
          min-width="130"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              v-if="row.issueCount == null || row.issueCount < 0 || row.issueCount > 0"
              link
              :type="row.issueCount > 0 ? 'danger' : 'warning'"
              :disabled="!row.latestId"
              @click="openPendingIssues(row)"
            >{{ row.issueCount > 0 ? row.issueCount + ' 项待处理' : '待核查' }}</el-button>
            <span v-else class="secondary-line">—</span>
          </template>
        </el-table-column>
        <el-table-column label="最近修改时间" width="170">
          <template #default="{ row }">{{ parseTime(row.updateTime) || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="openVersions(row)"
              >版本维护（{{ row.versionCount ?? 0 }}）</el-button
            >
            <el-button link type="primary" @click="openTrace(row)">流水</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="query.pageNum"
        v-model:limit="query.pageSize"
        @pagination="loadData"
      />
    </el-card>

    <el-dialog v-model="pendingVisible" :title="pendingTitle" width="min(1250px, calc(100vw - 32px))" append-to-body>
      <el-skeleton v-if="pendingLoading" :rows="5" animated />
      <el-alert v-else-if="pendingError" :title="pendingError" type="error" :closable="false" />
      <template v-else-if="pendingIssues.length">
        <p class="secondary-line">共 {{ pendingIssues.length }} 项。以下包含最新维护版本及其实际引用的下层BOM问题；请按处理建议修改对应BOM，保存后重新查看。</p>
        <el-table :data="pendingIssues" border size="small">
          <el-table-column label="所属BOM / 版本" min-width="180">
            <template #default="{ row }">
              <el-link v-if="row.bomId" type="primary" @click="openVersionDetail(row.bomId)">{{ row.bomCode || '查看BOM' }} {{ row.bomVersion }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="物料" min-width="180">
            <template #default="{ row }">
              <div>{{ row.materialName || (row.itemId ? '未填写物料名称' : '整单问题') }}</div>
              <div v-if="row.materialCode" class="secondary-line">{{ row.materialCode }}</div>
            </template>
          </el-table-column>
          <el-table-column label="规格" prop="specification" min-width="130" />
          <el-table-column label="问题原因" prop="message" min-width="240" />
          <el-table-column label="处理建议" min-width="260">
            <template #default="{ row }">{{ issueGuidance(row) }}</template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="当前已无待处理问题" />
      <template #footer>
        <el-button :loading="pendingLoading" @click="pendingTarget && openPendingIssues(pendingTarget)">重新查看</el-button>
        <el-button type="primary" @click="pendingVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <VersionMaintenanceDialog
      v-model="versionsVisible"
      :title="selected?.productName || selected?.productCode || ''"
      domain="bom"
      :rows="versionRows"
      :loading="versionsLoading"
      :busy="actionBusy"
      :supports-validity="true"
      :supports-review-audit="true"
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
    <BomFormDialog ref="formDialogRef" v-model="formVisible" :bom-id="formId" @success="refresh" />
    <BomDetail v-model="detailVisible" :bom-id="detailId" />
    <BomApproveDialog
      v-model="approveVisible"
      :bom-id="approveId"
      items-only
      title="处理BOM明细"
      @success="refresh"
    />
    <TraceTimeline v-model="traceVisible" biz-type="bom" :biz-id="String(traceId || '')" />

    <BomReviewScope
      ref="reviewScopeRef"
      v-model="scopeVisible"
      :action="scopeAction"
      :root-bom-ids="scopeRoots"
      @done="handlePackageDone"
      @close="returnToVersions"
      @open-bom="openBomFromScope"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import VersionMaintenanceDialog from '@/components/product/VersionMaintenanceDialog.vue'
import type { VersionMaintenanceRow } from '@/components/product/VersionMaintenanceDialog.vue'
import { parseTime } from '@/utils/format'
import { BomStatusEnum } from '@/enums/product'
import { productBomApi } from '@/api/product/bom'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import RouteCopyDialog from '../route/components/RouteCopyDialog.vue'
import BomFormDialog from './components/BomFormDialog.vue'
import BomDetail from './components/BomDetail.vue'
import BomApproveDialog from './components/BomApproveDialog.vue'
import BomReviewScope from './components/BomReviewScope.vue'
import type { ReviewAction } from './components/BomReviewScope.vue'
import type { EngineeringBom, ProductBomRow, BomReviewBatchResult, BomCheckIssue } from '@/types/product/bom'

type Version = EngineeringBom & { canSetCurrent: boolean; approveBy?: string; approveTime?: string }
type DomainTab = 'PRODUCT' | 'MATERIAL'

const activeTab = ref<DomainTab>('PRODUCT')
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  productCode: '',
  productName: '',
  bomName: '',
  approveStatus: undefined as number | undefined,
})
const rows = ref<ProductBomRow[]>([])
const total = ref(0)
const loading = ref(false)
let listRequest = 0
const rowKey = (row: ProductBomRow) => row.outputType + ':' + row.outputId
const formatSpecification = (value?: string) => {
  if (!value) return '—'
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed))
      return (
        parsed
          .map((x) => x.name + ': ' + x.value + (x.unit === '-' ? '' : x.unit || ''))
          .join(' / ') || '—'
      )
    if (parsed && typeof parsed === 'object') {
      const dimensions = [parsed.length, parsed.width, parsed.height].filter((x) => Number(x) > 0)
      return dimensions.length ? dimensions.join(' × ') + ' ' + (parsed.unit || 'mm') : '—'
    }
    return String(parsed)
  } catch {
    return value
  }
}
const statusLabel = (value: number) => BomStatusEnum.getLabel(value)
const statusTagType = (value: number) => {
  const item = BomStatusEnum.items.find((entry) => entry.value === value)
  return (item as any)?.tagType || 'info'
}
const loadData = async () => {
  const requestId = ++listRequest
  loading.value = true
  try {
    const response = await request.get('/engineering/bom/product-page', {
      params: { ...query, outputType: activeTab.value },
    })
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
const handleTabChange = () => {
  query.pageNum = 1
  void loadData()
}
const exportList = async () => {
  const data = await productBomApi.exportEngineeringBom({
    ...query,
    outputType: activeTab.value,
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
const pendingVisible = ref(false)
const pendingLoading = ref(false)
const pendingTitle = ref('待处理明细')
const pendingError = ref('')
const pendingIssues = ref<BomCheckIssue[]>([])
const pendingTarget = ref<ProductBomRow>()
let pendingRequest = 0
const openPendingIssues = async (row: ProductBomRow) => {
  if (!row.latestId) return
  const requestId = ++pendingRequest
  pendingTarget.value = row
  pendingTitle.value = `${row.productCode || row.productName || 'BOM'} ${row.latestVersion || ''} · 待处理明细`
  pendingIssues.value = []
  pendingError.value = ''
  pendingVisible.value = true
  pendingLoading.value = true
  try {
    const response = await productBomApi.getPackageIssues(row.latestId)
    if (requestId !== pendingRequest) return
    if (!Array.isArray(response.data)) throw new Error('问题明细返回异常，请重新查看')
    pendingIssues.value = response.data
    row.issueCount = response.data.length
  } catch (error: any) {
    if (requestId === pendingRequest) pendingError.value = error?.message || '问题明细读取失败，请重新查看'
  } finally {
    if (requestId === pendingRequest) pendingLoading.value = false
  }
}
const issueGuidance = (issue: BomCheckIssue) => {
  switch (issue.field) {
    case 'processId': return '在对应BOM中重新选择有效标准工序；若该行属于公共物料，清空项目。正/反面等说明可填写在备注中。'
    case 'materialId': return '在对应BOM中关联有效物料档案，需要时先新建物料。'
    case 'materialName': return '在对应BOM中补充材料名称。'
    case 'quantity':
    case 'moduleQty':
    case 'baseQty': return '核对该行用量、模数和基数，按实际用料修正。'
    case 'unit': return '为该行选择正确的计量单位。'
    case 'unitFactor': return '核对BOM与物料档案单位，跨单位时补充换算系数及依据。'
    case 'parentMaterialId':
    case 'subBomId': return '查看对应BOM，核对父子关系及引用的下层BOM版本，按问题原因修正。'
    default: return '查看对应BOM，按问题原因修正，保存后重新查看。'
  }
}
watch(pendingVisible, (visible) => {
  if (!visible) {
    pendingRequest++
    pendingLoading.value = false
  }
})
onBeforeUnmount(() => { pendingRequest++ })

const selected = ref<ProductBomRow>()
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
const openVersions = (row: ProductBomRow) => {
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
const openVersionDetail = (bomId?: number) => {
  if (!bomId) return
  detailId.value = bomId
  detailVisible.value = true
}
const toVersionRow = (row: Version): VersionMaintenanceRow => ({
  id: row.bomId,
  version: row.bomVersion,
  approveStatus: row.approveStatus,
  isCurrent: Boolean(row.isCurrent),
  canSetCurrent: row.canSetCurrent,
  createBy: row.createBy,
  createTime: row.createTime,
  remark: row.remark,
  effectiveDate: row.effectiveDate,
  expiryDate: row.expiryDate,
  approveBy: row.approveBy,
  approveTime: row.approveTime,
})
const versionRows = computed<VersionMaintenanceRow[]>(() => versions.value.map(toVersionRow))
const fetchVersionDetail = async (id: number) => {
  const response = await productBomApi.getEngineeringBomInfo(id)
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
    await productBomApi.copyEngineeringBom(copyId.value, newVersion.trim())
    copyVisible.value = false
    ElMessage.success('复制成功')
    await refresh()
  } catch (error: any) {
    copyError.value = error?.message || '复制失败，请检查版本号后重试'
  } finally {
    copyBusy.value = false
  }
}
const handleVersionAction = (key: string, row: VersionMaintenanceRow) =>
  handleVersionActionIn(versions.value, key, row)
const handleVersionActionIn = async (list: Version[], key: string, row: VersionMaintenanceRow) => {
  if (actionBusy.value) return
  const raw = list.find((v) => v.bomId === row.id)
  if (!raw) return
  if (key === 'view') {
    view(raw)
    return
  }
  if (key === 'edit') {
    edit(raw)
    return
  }
  if (key === 'submit' || key === 'approve') {
    openPackageReview(row.id, key === 'submit' ? 'SUBMIT' : 'APPROVE')
    return
  }
  const expectedCurrentId = list.find((v) => Boolean(v.isCurrent))?.bomId || 0
  actionBusy.value = true
  try {
    if (key === 'copy') {
      const response = await request.get('/engineering/bom/' + row.id + '/next-version')
      copyId.value = row.id
      copySourceVersion.value = row.version
      suggestedVersion.value = response.data
      copyError.value = ''
      copyVisible.value = true
      return
    }
    if (key === 'setCurrent') {
      await ElMessageBox.confirm('将 ' + row.version + ' 设为当前版本？', '设置当前版本', {
        type: 'warning',
      })
      await request.put('/engineering/bom/setDefault/' + row.id, null, {
        params: { expectedCurrentId },
      })
    } else if (key === 'delete') {
      await ElMessageBox.confirm('删除草稿版本 ' + row.version + '？', '删除版本', {
        type: 'warning',
      })
      await productBomApi.removeEngineeringBom(row.id)
    } else if (key === 'void') {
      await ElMessageBox.confirm('作废已生效版本 ' + row.version + '？', '作废版本', {
        type: 'warning',
      })
      await request.put('/engineering/bom/' + row.id + '/void')
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

// 版本维护只传用户选中的版本；整套范围、校验和状态变化复用服务端接口。
const scopeVisible = ref(false)
const scopeAction = ref<ReviewAction>('SUBMIT')
const scopeRoots = ref<number[]>([])
const reviewScopeRef = ref<InstanceType<typeof BomReviewScope>>()
const openPackageReview = (bomId: number, action: ReviewAction) => {
  scopeAction.value = action
  scopeRoots.value = [bomId]
  versionsVisible.value = false
  scopeVisible.value = true
}
const returnToVersions = () => {
  versionsVisible.value = true
}
const handlePackageDone = async (_result: BomReviewBatchResult) => {
  await Promise.all([loadData(), loadVersions()])
}
const openBomFromScope = async (bomId: number, process: boolean) => {
  if (!process) {
    openVersionDetail(bomId)
    return
  }
  try {
    const response = await fetchVersionDetail(bomId)
    if (response.approveStatus === BomStatusEnum.REVIEWING.value) {
      approveId.value = bomId
      approveVisible.value = true
    } else if (
      response.approveStatus === BomStatusEnum.DRAFT.value ||
      response.approveStatus === BomStatusEnum.REJECTED.value
    ) {
      formId.value = bomId
      formVisible.value = true
    } else {
      openVersionDetail(bomId)
    }
  } catch (error: any) {
    ElMessage.error(error?.message || 'BOM详情读取失败，请重新检查')
  }
}
// 辅助处理保留物料关联/待移除确认；关闭后重新取得整套范围与指纹。
watch([formVisible, approveVisible], (current, previous) => {
  if (scopeVisible.value && current.every((value) => !value) && previous.some(Boolean)) {
    void reviewScopeRef.value?.loadPlan()
  }
})

const traceVisible = ref(false)
const traceId = ref<number>()
const openTrace = (row: ProductBomRow) => {
  traceId.value = row.currentId || row.latestId
  traceVisible.value = true
}
onMounted(loadData)
onBeforeRouteLeave(async () => {
  if (!formVisible.value || !formDialogRef.value?.formIsDirty?.()) return true
  return await formDialogRef.value.resolveLeave()
})
</script>

<style scoped>
.bom-domain-tabs {
  margin-bottom: 4px;
}
.inline-tag {
  margin-left: 6px;
}
.secondary-line {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

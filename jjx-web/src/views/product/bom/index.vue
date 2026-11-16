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
        <el-table-column
          prop="productCode"
          :label="activeTab === 'PRODUCT' ? '产品编码' : '物料编码'"
          min-width="160"
        />
        <el-table-column
          prop="productName"
          :label="activeTab === 'PRODUCT' ? '产品名称' : '物料名称'"
          min-width="160"
        />
        <el-table-column label="规格" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ formatSpecification(row.specification) }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'PRODUCT'" label="当前版本（生效日）" min-width="210">
          <template #default="{ row }">
            <el-link v-if="row.currentId" type="primary" @click="openVersionDetail(row.currentId)">
              {{ row.currentVersion }}
            </el-link>
            <span v-else>{{ row.currentVersion || '—' }}</span>
            <el-tag
              v-if="row.currentStatus != null"
              size="small"
              :type="statusTagType(row.currentStatus)"
              class="inline-tag"
            >
              {{ statusLabel(row.currentStatus) }}
            </el-tag>
            （{{
              row.currentEffectiveDate ? parseTime(row.currentEffectiveDate, 'yyyy-MM-dd') : '—'
            }}）
          </template>
        </el-table-column>
        <el-table-column label="最新维护版本" min-width="170">
          <template #default="{ row }">
            <span v-if="row.latestVersion">{{ row.latestVersion }}</span>
            <span v-else>—</span>
            <el-tag
              v-if="row.latestStatus != null"
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
          label="实际引用方案"
          min-width="240"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span v-if="row.subBomSummary">{{ row.subBomSummary }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'PRODUCT'" label="问题" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.issueCount === -1" type="warning" size="small">待核查</el-tag>
            <el-tag v-else-if="row.issueCount > 0" type="danger" size="small">
              {{ row.issueCount }}
            </el-tag>
            <el-tag v-else type="success" size="small">0</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="versionCount" label="版本数" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openVersions(row)">
              {{ row.versionCount }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态摘要" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.hasDraft" type="info">有草稿</el-tag>
            <el-tag v-if="row.hasPending" type="warning">有待审</el-tag>
            <span v-if="!row.hasDraft && !row.hasPending">—</span>
          </template>
        </el-table-column>
        <el-table-column label="最近修改时间" width="170">
          <template #default="{ row }">{{ parseTime(row.updateTime) || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openVersions(row)">版本维护</el-button>
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
import { ref, reactive, onMounted, computed, watch } from 'vue'
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
import type { EngineeringBom, ProductBomRow, BomReviewBatchResult } from '@/types/product/bom'

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
</style>

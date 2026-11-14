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

      <template v-if="activeTab === 'PRODUCT'">
        <el-button v-hasPermi="['engineering:bom:add']" type="primary" @click="add">新增BOM</el-button>
        <el-button
          v-hasPermi="['engineering:bom:edit']"
          :disabled="!selectedRows.length || batchStarting"
          @click="openBatch('SUBMIT')"
        >
          批量送审
        </el-button>
        <el-button
          v-hasPermi="['engineering:bom:approve']"
          :disabled="!selectedRows.length || batchStarting"
          @click="openBatch('APPROVE')"
        >
          批量审核
        </el-button>
      </template>
      <el-button @click="exportList">导出全部版本</el-button>

      <el-table
        v-loading="loading"
        :data="rows"
        :row-key="rowKey"
        style="margin-top: 16px"
        @selection-change="handleSelectionChange"
      >
        <el-table-column
          v-if="activeTab === 'PRODUCT'"
          type="selection"
          width="45"
          :selectable="rowSelectable"
        />
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
            （{{ row.currentEffectiveDate ? parseTime(row.currentEffectiveDate, 'yyyy-MM-dd') : '—' }}）
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
        <el-table-column
          v-if="activeTab === 'PRODUCT'"
          label="问题"
          width="90"
          align="center"
        >
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
        <el-table-column label="操作" :width="activeTab === 'PRODUCT' ? 320 : 300">
          <template #default="{ row }">
            <el-button
              v-if="activeTab === 'PRODUCT'"
              link
              type="primary"
              @click="openWholeSet(row)"
            >
              查看整套
            </el-button>
            <el-button link type="primary" @click="openVersions(row)">版本维护</el-button>
            <el-button
              v-if="canSubmit(row)"
              v-hasPermi="['engineering:bom:edit']"
              link
              type="primary"
              @click="submitLatest(row)"
            >
              送审
            </el-button>
            <el-button
              v-if="canApprove(row)"
              v-hasPermi="['engineering:bom:approve']"
              link
              type="primary"
              @click="openApprove(row)"
            >
              审批
            </el-button>
            <el-button
              v-if="activeTab === 'MATERIAL' && canApprove(row)"
              v-hasPermi="['engineering:bom:reject']"
              link
              type="danger"
              @click="openMaterialReject(row)"
            >
              驳回
            </el-button>
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
    <BomApproveDialog v-model="approveVisible" :bom-id="approveId" @success="refresh" />
    <BomSubmitCheckDialog
      v-model="submitCheckVisible"
      :issues="submitCheckIssues"
      :target-name="submitCheckTarget"
      @navigate="openSchemeVersions"
    />
    <VersionMaintenanceDialog
      v-model="schemeVisible"
      :title="schemeTitle"
      domain="bom"
      :rows="schemeRows"
      :loading="schemeLoading"
      :busy="actionBusy"
      :supports-validity="true"
      :supports-review-audit="true"
      :fetch-detail="fetchVersionDetail"
      @action="handleSchemeAction"
    />
    <TraceTimeline v-model="traceVisible" biz-type="bom" :biz-id="String(traceId || '')" />

    <BomReviewScope
      v-model="scopeVisible"
      :action="scopeAction"
      :root-bom-ids="scopeRoots"
      :readonly="scopeReadonly"
      @confirm="handleScopeConfirm"
      @open-bom="openBomFromScope"
    />
    <BomBatchReviewDialog
      v-model="batchVisible"
      :action="batchAction"
      :scopes="batchScopes"
      @done="handleBatchDone"
      @retry-failed="handleRetryFailed"
    />

    <el-dialog
      v-model="materialRejectVisible"
      title="驳回材料BOM"
      width="800px"
      :before-close="closeMaterialReject"
    >
      <div v-loading="materialRejectLoading">
        <el-alert
          v-if="materialRejectRow"
          type="warning"
          :closable="false"
          show-icon
          :title="'即将驳回 ' + (materialRejectRow.productCode || '') + ' ' + (materialRejectRow.latestVersion || '')"
          description="只退回该材料方案本身，不自动把引用它的产品改成已批准或驳回；其待审引用产品会因此受阻。"
        />
        <div class="impact-title">受影响的产品/方案版本：</div>
        <el-empty
          v-if="!materialRejectLoading && !materialImpact.length"
          description="当前没有上层方案直接或间接引用该材料BOM"
        />
        <ul v-else class="impact-list">
          <li v-for="item in materialImpact" :key="item.bomId">
            {{ item.outputCode || item.bomCode || item.bomId }} {{ item.outputName || '' }}
            · {{ item.bomCode || '' }} {{ item.bomVersion || '' }}
            <el-tag size="small" :type="item.root ? 'danger' : 'info'">
              {{ item.root ? '产品根方案' : '下层方案' }}
            </el-tag>
            <el-tag v-if="item.status != null" size="small" :type="statusTagType(item.status)">
              {{ statusLabel(item.status) }}
            </el-tag>
          </li>
        </ul>
        <el-form label-width="90px" class="reject-form">
          <el-form-item label="驳回原因" required>
            <el-input
              v-model="materialRejectRemark"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="请填写驳回原因"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeMaterialReject">取消</el-button>
          <el-button
            type="danger"
            :loading="materialRejectBusy"
            :disabled="materialRejectLoading || !materialRejectRemark.trim()"
            @click="confirmMaterialReject"
          >
            确认驳回
          </el-button>
        </span>
      </template>
    </el-dialog>
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
import { BomStatusEnum } from '@/enums/product'
import { productBomApi } from '@/api/product/bom'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import RouteCopyDialog from '../route/components/RouteCopyDialog.vue'
import BomFormDialog from './components/BomFormDialog.vue'
import BomDetail from './components/BomDetail.vue'
import BomApproveDialog from './components/BomApproveDialog.vue'
import BomSubmitCheckDialog from './components/BomSubmitCheckDialog.vue'
import BomReviewScope from './components/BomReviewScope.vue'
import type { ReviewAction } from './components/BomReviewScope.vue'
import BomBatchReviewDialog from './components/BomBatchReviewDialog.vue'
import type { SchemeNavigateTarget } from './components/bomSubmitCheck'
import type {
  EngineeringBom,
  ProductBomRow,
  BomCheckIssue,
  BomReviewProductScope,
  BomReviewBatchResult,
  BomReferenceImpact,
} from '@/types/product/bom'

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
const selectedRows = ref<ProductBomRow[]>([])
const rowKey = (row: ProductBomRow) => row.outputType + ':' + row.outputId
const rowSelectable = (row: ProductBomRow) => Boolean(row.latestId)
const handleSelectionChange = (selection: ProductBomRow[]) => {
  selectedRows.value = selection
}
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
const canSubmit = (row: ProductBomRow) =>
  row.latestId != null &&
  (row.latestStatus === BomStatusEnum.DRAFT.value ||
    row.latestStatus === BomStatusEnum.REJECTED.value)
const canApprove = (row: ProductBomRow) =>
  row.latestId != null && row.latestStatus === BomStatusEnum.REVIEWING.value

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
  selectedRows.value = []
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
const schemeVisible = ref(false)
const schemeTitle = ref('')
const schemeVersions = ref<Version[]>([])
const schemeLoading = ref(false)
const schemeObject = ref<{ outputType: string; outputId: number } | null>(null)
let schemeRequest = 0
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
const loadSchemeVersions = async (): Promise<boolean> => {
  if (!schemeObject.value) return false
  const requestId = ++schemeRequest
  schemeLoading.value = true
  try {
    const response = await request.get('/engineering/bom/object-versions', {
      params: {
        outputType: schemeObject.value.outputType,
        outputId: schemeObject.value.outputId,
      },
    })
    if (requestId === schemeRequest) schemeVersions.value = response.data || []
    return requestId === schemeRequest
  } catch (error: any) {
    if (requestId === schemeRequest) {
      schemeVersions.value = []
      ElMessage.error(error?.message || '生产BOM版本加载失败，请刷新后重试')
    }
    return false
  } finally {
    if (requestId === schemeRequest) schemeLoading.value = false
  }
}
const openSchemeVersions = async (target: SchemeNavigateTarget) => {
  if (!target?.outputType || !target.outputId) return
  schemeObject.value = { outputType: target.outputType, outputId: target.outputId }
  schemeTitle.value = [target.name, target.version].filter(Boolean).join(' · ')
  schemeVersions.value = []
  schemeVisible.value = true
  await loadSchemeVersions()
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
  if (schemeVisible.value) await loadSchemeVersions()
}
const formVisible = ref(false)
const formId = ref<number>()
const formDialogRef = ref<InstanceType<typeof BomFormDialog>>()
const detailVisible = ref(false)
const detailId = ref<number>()
const approveVisible = ref(false)
const approveId = ref<number>()
const submitCheckVisible = ref(false)
const submitCheckIssues = ref<BomCheckIssue[]>([])
const submitCheckTarget = ref('')
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
const openApprove = (row: ProductBomRow) => {
  if (!row.latestId) return
  approveId.value = row.latestId
  approveVisible.value = true
}
const submitLatest = async (row: ProductBomRow) => {
  if (!row.latestId || actionBusy.value) return
  actionBusy.value = true
  try {
    const check = await productBomApi.checkEngineeringBom(row.latestId)
    const issues = (check.data || []) as BomCheckIssue[]
    if (issues.length) {
      submitCheckIssues.value = issues
      submitCheckTarget.value = (row.productCode || '') + ' ' + (row.latestVersion || '')
      submitCheckVisible.value = true
      return
    }
    await ElMessageBox.confirm('提交 ' + (row.latestVersion || '') + ' 审核？', '提交审核')
    await productBomApi.submitEngineeringBom(row.latestId)
    ElMessage.success('提交成功')
    await refresh()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '提交失败，请刷新重试')
    }
  } finally {
    actionBusy.value = false
  }
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
const schemeRows = computed<VersionMaintenanceRow[]>(() => schemeVersions.value.map(toVersionRow))
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
const handleSchemeAction = (key: string, row: VersionMaintenanceRow) =>
  handleVersionActionIn(schemeVersions.value, key, row)
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
  if (key === 'approve') {
    approveId.value = row.id
    approveVisible.value = true
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
    if (key === 'submit') {
      const check = await productBomApi.checkEngineeringBom(row.id)
      const issues = (check.data || []) as BomCheckIssue[]
      if (issues.length) {
        submitCheckIssues.value = issues
        submitCheckTarget.value = row.version
        submitCheckVisible.value = true
        return
      }
      await ElMessageBox.confirm('提交 ' + row.version + ' 审核？', '提交审核')
      await productBomApi.submitEngineeringBom(row.id)
    } else if (key === 'setCurrent') {
      await ElMessageBox.confirm('将 ' + row.version + ' 设为当前版本？', '设置当前版本', {
        type: 'warning',
      })
      await request.put('/engineering/bom/setDefault/' + row.id, null, {
        params: { expectedCurrentId },
      })
    } else if (key === 'delete') {
      await ElMessageBox.confirm('删除草稿版本 ' + row.version + '？', '删除版本', { type: 'warning' })
      await productBomApi.removeEngineeringBom(row.id)
    } else if (key === 'void') {
      await ElMessageBox.confirm('作废已生效版本 ' + row.version + '？', '作废版本', { type: 'warning' })
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

const scopeVisible = ref(false)
const scopeReadonly = ref(false)
const scopeAction = ref<ReviewAction>('SUBMIT')
const scopeRoots = ref<number[]>([])
const batchVisible = ref(false)
const batchAction = ref<ReviewAction>('SUBMIT')
const batchScopes = ref<BomReviewProductScope[]>([])
const batchStarting = ref(false)

const openWholeSet = (row: ProductBomRow) => {
  if (!row.latestId) return
  scopeReadonly.value = true
  scopeAction.value = row.latestStatus === BomStatusEnum.REVIEWING.value ? 'APPROVE' : 'SUBMIT'
  scopeRoots.value = [row.latestId]
  scopeVisible.value = true
}
const openBatch = (action: ReviewAction) => {
  const roots = selectedRows.value
    .map((row) => row.latestId)
    .filter((id): id is number => Number.isFinite(id) && Number(id) > 0)
  if (!roots.length) {
    ElMessage.warning('请先勾选有维护版本的产品BOM')
    return
  }
  scopeReadonly.value = false
  scopeAction.value = action
  scopeRoots.value = roots
  scopeVisible.value = true
}
const handleScopeConfirm = (scopes: BomReviewProductScope[]) => {
  if (scopeReadonly.value) {
    scopeVisible.value = false
    return
  }
  if (!scopes.length) {
    ElMessage.warning('没有可执行的产品范围')
    return
  }
  batchStarting.value = true
  batchAction.value = scopeAction.value
  batchScopes.value = scopes
  scopeVisible.value = false
  batchVisible.value = true
  batchStarting.value = false
}
const handleBatchDone = async (result: BomReviewBatchResult) => {
  const success = result.successes?.length || 0
  const failures = result.failures?.length || 0
  if (failures) ElMessage.warning('成功 ' + success + ' 个，失败 ' + failures + ' 个，可只重试失败产品')
  else ElMessage.success('全部成功：' + success + ' 个产品')
  await refresh()
}
const handleRetryFailed = (rootBomIds: number[]) => {
  if (!rootBomIds.length) return
  batchVisible.value = false
  scopeReadonly.value = false
  scopeRoots.value = rootBomIds
  scopeVisible.value = true
}
const openBomFromScope = (bomId: number) => {
  scopeVisible.value = false
  approveId.value = bomId
  approveVisible.value = true
}

const materialRejectVisible = ref(false)
const materialRejectLoading = ref(false)
const materialRejectBusy = ref(false)
const materialRejectRow = ref<ProductBomRow>()
const materialRejectRemark = ref('')
const materialRejectContentRevision = ref<string | null>(null)
const materialImpact = ref<BomReferenceImpact[]>([])
const closeMaterialReject = () => {
  materialRejectVisible.value = false
  materialRejectRow.value = undefined
  materialImpact.value = []
  materialRejectRemark.value = ''
  materialRejectContentRevision.value = null
}
const openMaterialReject = async (row: ProductBomRow) => {
  if (!row.latestId) return
  materialRejectRow.value = row
  materialRejectRemark.value = ''
  materialRejectContentRevision.value = null
  materialImpact.value = []
  materialRejectVisible.value = true
  materialRejectLoading.value = true
  try {
    const [detail, impact] = await Promise.all([
      productBomApi.getEngineeringBomInfo(row.latestId),
      productBomApi.getBomReferenceImpact(row.latestId),
    ])
    materialRejectContentRevision.value = detail.data?.contentRevision || null
    materialImpact.value = impact.data || []
  } catch (error: any) {
    ElMessage.error(error?.message || '影响范围读取失败，请刷新后重试')
  } finally {
    materialRejectLoading.value = false
  }
}
const confirmMaterialReject = async () => {
  if (!materialRejectRow.value?.latestId || materialRejectBusy.value) return
  const remark = materialRejectRemark.value.trim()
  if (!remark) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  materialRejectBusy.value = true
  try {
    await productBomApi.rejectEngineeringBom(
      materialRejectRow.value.latestId,
      remark,
      materialRejectContentRevision.value
    )
    ElMessage.success('已驳回该材料方案')
    closeMaterialReject()
    await refresh()
  } catch (error: any) {
    ElMessage.error(error?.message || '驳回失败，请刷新后重试')
  } finally {
    materialRejectBusy.value = false
  }
}

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
.bom-domain-tabs { margin-bottom: 4px; }
.inline-tag { margin-left: 6px; }
.impact-title { margin: 12px 0 6px; font-weight: 600; }
.impact-list { margin: 0; padding-left: 18px; line-height: 26px; }
.reject-form { margin-top: 16px; }
</style>

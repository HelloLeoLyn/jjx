<template>
  <div class="app-container">
    <el-card class="bom-list-card" shadow="never">
      <el-form
        ref="queryForm"
        :model="queryParams"
        class="bom-search"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="产品编码" prop="productCode">
          <el-input
            v-model="queryParams.productCode"
            placeholder="输入产品编码"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input
            v-model="queryParams.productName"
            placeholder="输入产品名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="BOM名称" prop="bomName">
          <el-input
            v-model="queryParams.bomName"
            placeholder="输入BOM名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="审核状态" prop="approveStatus">
          <el-select v-model="queryParams.approveStatus" placeholder="全部状态" clearable>
            <el-option
              v-for="dict in bomStatusOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <div class="search-actions">
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </div>
      </el-form>

      <div class="bom-toolbar">
        <div class="toolbar-main">
          <el-button
            type="primary"
            icon="Plus"
            v-hasPermi="['engineering:bom:add']"
            @click="handleAdd"
            >新增 BOM</el-button
          >
          <template v-if="ids.length">
            <span class="selection-count">已选 {{ ids.length }} 项</span>
            <el-button
              v-hasPermi="['engineering:bom:delete']"
              :disabled="ids.length !== 1 || !canDeleteSelected"
              @click="() => handleDelete()"
              >删除所选</el-button
            >
          </template>
        </div>
        <div class="toolbar-secondary">
          <el-switch
            v-model="queryParams.showAllVersions"
            active-text="展开全部版本"
            @change="handleExpandAll"
          />
          <el-button icon="Download" @click="handleExport">导出</el-button>
        </div>
      </div>

      <el-table
        :key="tableKey"
        ref="bomTableRef"
        class="bom-list-table"
        v-loading="loading"
        :data="bomList"
        :row-key="getRowKey"
        :lazy="true"
        :load="loadVersions"
        :tree-props="{ hasChildren: 'hasVersionChildren', children: 'versions' }"
        :row-class-name="({ row }) => (isParentRow(row) ? '' : 'bom-version-row')"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="44" align="center" :selectable="isParentRow" />
        <el-table-column label="产品" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="isParentRow(row)" class="product-cell">
              <el-button link type="primary" class="product-name" @click="handleView(row)">{{
                row.productName || '未命名产品'
              }}</el-button>
              <span class="secondary-text product-code">{{ row.productCode || '—' }}</span>
            </div>
            <span v-else class="version-row-label">版本记录</span>
          </template>
        </el-table-column>
        <el-table-column label="BOM 编码" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="isParentRow(row)" link type="primary" @click="handleView(row)">{{
              row.bomCode
            }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="145">
          <template #default="{ row }">
            <div class="version-cell">
              <el-button link type="primary" @click="handleView(row)">{{
                row.bomVersion
              }}</el-button>
              <el-tag v-if="row.isCurrent" size="small" type="success" effect="plain">现行</el-tag>
              <el-tag v-else-if="isParentRow(row)" size="small" type="info" effect="plain"
                >待设现行</el-tag
              >
            </div>
          </template>
        </el-table-column>
        <el-table-column label="版本记录" width="115">
          <template #default="{ row }">
            <el-button
              v-if="isParentRow(row) && row.hasVersionChildren"
              link
              type="primary"
              @click="toggleVersions(row)"
            >
              全部版本 {{ row.versionCount }}
            </el-button>
            <span v-else-if="isParentRow(row)" class="secondary-text">单版本</span>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="105">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="ProductEnum.bomStatus.getTagProps(row.approveStatus)?.type"
              effect="light"
            >
              {{ ProductEnum.bomStatus.getLabel(row.approveStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="165">
          <template #default="{ row }">
            <span class="secondary-text">{{
              parseTime(row.updateTime || row.createTime) || '—'
            }}</span>
          </template>
        </el-table-column>
        <TableActionColumn
          :actions="bomActions"
          :width="245"
          :max-visible="0"
          display="text"
          @action="handleBomAction"
        >
          <template #before="{ row }">
            <el-button
              v-if="canEditBom(row)"
              v-hasPermi="['engineering:bom:edit']"
              link
              type="primary"
              @click="handleUpdate(row)"
              >修改</el-button
            >
            <el-button
              v-if="ProductEnum.bomStatus.canDo(row.approveStatus, ProductActions.SUBMIT)"
              v-hasPermi="['engineering:bom:add']"
              link
              type="primary"
              @click="handleSubmitApprove(row)"
              >提交审核</el-button
            >
            <el-button
              v-if="ProductEnum.bomStatus.canDo(row.approveStatus, ProductActions.APPROVE)"
              v-hasPermi="['engineering:bom:approve']"
              link
              type="primary"
              @click="handleApprove(row)"
              >审核</el-button
            >
            <el-button
              v-if="row.approveStatus === BomStatusEnum.APPROVED.value && !row.isCurrent"
              v-hasPermi="['engineering:bom:edit']"
              link
              type="primary"
              @click="handleSetDefaultBom(row)"
              >设为现行</el-button
            >
            <el-button
              v-if="versionErrors[getRowKey(row)]"
              link
              type="danger"
              @click="retryVersions(row)"
              >重试加载版本</el-button
            >
          </template>
        </TableActionColumn>
      </el-table>

      <!-- 分页 -->
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="handlePageChange"
      />
    </el-card>

    <!-- 添加或修改BOM对话框 -->
    <BomFormDialog ref="bomFormRef" v-model="open" :bom-id="selectedBomId" @success="getList" />
    <BomDetail :bom-id="selectedBomId" v-model="bomDetailOpen" />

    <!-- BOM审核对话框 -->
    <BomApproveDialog
      :bom-id="selectedBomForApprove"
      v-model="bomApproveOpen"
      @success="handleApproveSuccess"
      @close="handleApproveClose"
    />

    <!-- 流水抽屉（bizType=bom，按 bizId 聚合该 BOM 全部操作） -->
    <TraceTimeline v-model="traceVisible" :biz-type="'bom'" :biz-id="String(traceBomId || '')" />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'EngineeringBom',
})

import { ref, reactive, onMounted, watch, computed, nextTick } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { productBomApi } from '@/api/product/bom'
import { parseTime } from '@/utils/format'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import BomDetail from './components/BomDetail.vue'
import BomApproveDialog from './components/BomApproveDialog.vue'
import BomFormDialog from './components/BomFormDialog.vue'
import type { EngineeringBomQueryParams, EngineeringBom } from '@/types/product/bom'
import { BomStatusEnum, ProductEnum, ProductActions } from '@/enums/product'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'

const canEditBom = (bom?: EngineeringBom | null) =>
  !!bom && BomStatusEnum.canDo(bom.approveStatus, ProductActions.EDIT)

// 次要动作统一收进「更多」，权限仍由公共操作列校验。
const bomActions: TableAction<EngineeringBom>[] = [
  { key: 'copy', label: '复制BOM', permission: 'engineering:bom:add' },
  { key: 'trace', label: '流水' },
  {
    key: 'delete',
    label: '删除',
    type: 'danger',
    permission: 'engineering:bom:delete',
    visible: ({ row }) => ProductEnum.bomStatus.canDo(row.approveStatus, ProductActions.DELETE),
  },
]
// 查询参数
const queryParams = reactive<EngineeringBomQueryParams>({
  pageNum: 1,
  pageSize: 10,
  showAllVersions: false,
  bomCode: undefined,
  bomName: undefined,
  productCode: undefined,
  productName: undefined,
  approveStatus: undefined,
  startDate: undefined,
  endDate: undefined,
})

// 响应式数据
const loading = ref(false)
const ids = ref<number[]>([])
const total = ref(0)
const open = ref(false)
const bomFormRef = ref<InstanceType<typeof BomFormDialog>>()
const bomDetailOpen = ref(false)
const bomApproveOpen = ref(false)
const selectedBom = ref<EngineeringBom | null>(null)
const selectedBomId = ref<number | undefined>(undefined)
const selectedBomForApprove = ref<number | undefined>(undefined)

// 父行保留完整业务数据，子行通过模板留空摘要列，操作仍使用该版本的 bomId。
type BomTreeRow = EngineeringBom & {
  rowType: 'code' | 'version'
  hasVersionChildren: boolean
  versions?: BomTreeRow[]
}
const bomList = ref<BomTreeRow[]>([])
const bomTableRef = ref<TableInstance>()
const listRevision = ref(0)
const tableKey = computed(() => JSON.stringify({ ...queryParams, revision: listRevision.value }))
const getRowKey = (row: BomTreeRow) =>
  row.rowType === 'version' ? `ver:${row.bomId}` : `code:${row.bomCode}`
const isParentRow = (row: BomTreeRow) => row.rowType !== 'version'
const versionErrors = ref<Record<string, boolean>>({})
let versionRequests = new Map<string, Promise<BomTreeRow[]>>()
let listRequestId = 0

// 重建表格时丢弃本页缓存；旧请求只能完成旧表格的 resolve，不能写入新表格。
watch(
  tableKey,
  () => {
    versionRequests = new Map()
    versionErrors.value = {}
    handleSelectionChange([])
  },
  { flush: 'sync' }
)

const loadVersions = async (
  row: BomTreeRow,
  _treeNode: unknown,
  resolve: (children: BomTreeRow[]) => void
) => {
  if (!isParentRow(row) || !row.hasVersionChildren) {
    resolve([])
    return
  }
  const key = getRowKey(row)
  const generation = tableKey.value
  let request = versionRequests.get(key)
  if (!request) {
    request = productBomApi.getBomVersions(row.bomCode).then((response) =>
      (response.data || []).map(
        (version): BomTreeRow => ({
          ...version,
          rowType: 'version',
          hasVersionChildren: false,
        })
      )
    )
    versionRequests.set(key, request)
  }
  try {
    const children = await request
    resolve(generation === tableKey.value ? children : [])
  } catch {
    resolve([])
    if (generation !== tableKey.value) return
    versionRequests.delete(key)
    versionErrors.value[key] = true
    ElMessage.warning('版本加载失败，请点击该 BOM 行的「重试加载版本」')
  }
}

const toggleVersions = (row: BomTreeRow) => {
  if (isParentRow(row) && row.hasVersionChildren) {
    bomTableRef.value?.toggleRowExpansion(row)
  }
}

const expandParents = async () => {
  const generation = tableKey.value
  await nextTick()
  if (generation !== tableKey.value || !queryParams.showAllVersions) return
  bomList.value
    .filter((row) => row.hasVersionChildren)
    .forEach((row) => {
      // Element Plus 的公开展开方法会触发同一个 lazy load，避免预取与展开各发一次请求。
      bomTableRef.value?.toggleRowExpansion(row, true)
    })
}

const handleExpandAll = async () => {
  // 收起时重建表格，同时清理 Element Plus 的懒加载缓存和在途请求归属。
  if (!queryParams.showAllVersions) listRevision.value++
  else await expandParents()
}

const retryVersions = async (row: BomTreeRow) => {
  listRevision.value++
  await nextTick()
  if (queryParams.showAllVersions) await expandParents()
  else bomTableRef.value?.toggleRowExpansion(row, true)
}

// 字典选项
const bomStatusOptions = BomStatusEnum.items.map((item) => ({
  value: String(item.value),
  label: item.label,
}))

// 获取BOM列表
const getList = async () => {
  const requestId = ++listRequestId
  loading.value = true
  listRevision.value++
  bomList.value = []
  try {
    // 页面始终按编码分页；开关只控制展开与导出口径。
    const response = await productBomApi.listEngineeringBom({
      ...queryParams,
      showAllVersions: false,
    })
    if (requestId !== listRequestId) return
    bomList.value = (response.data?.records || []).map(
      (bom): BomTreeRow => ({
        ...bom,
        rowType: 'code',
        hasVersionChildren: (bom.versionCount || 0) > 1,
      })
    )
    total.value = response.data?.total || 0
    if (queryParams.showAllVersions) await expandParents()
  } catch (error) {
    console.error('获取BOM列表失败:', error)
  } finally {
    if (requestId === listRequestId) loading.value = false
  }
}

const handlePageChange = () => {
  queryParams.showAllVersions = false
  getList()
}

// 搜索按钮操作
const handleQuery = () => {
  queryParams.showAllVersions = false
  queryParams.pageNum = 1
  getList()
}

// 重置按钮操作
const resetQuery = () => {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    bomCode: undefined,
    productCode: undefined,
    productName: undefined,
    bomVersion: undefined,
    bomName: undefined,
    approveStatus: undefined,
    showAllVersions: false,
    startDate: undefined,
    endDate: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  getList()
}

// 多选框选中数据
const handleSelectionChange = (rows: EngineeringBom[]) => {
  const selection = rows.filter((row) => (row as BomTreeRow).rowType !== 'version')
  ids.value = selection.map((item) => item.bomId)
  selectedBom.value = selection.length === 1 ? selection[0] : null
  // 勾选中有不可删除状态（已批准/审核中等）则禁用批量删除（2026-08-08）
  canDeleteSelected.value =
    selection.length > 0 &&
    selection.every((b) => ProductEnum.bomStatus.canDo(b.approveStatus, ProductActions.DELETE))
}

// 批量删除可用性（2026-08-08）
const canDeleteSelected = ref(true)

// 新增按钮操作
const handleAdd = () => {
  selectedBomId.value = undefined
  open.value = true
}

// 修改按钮操作
const handleUpdate = (row?: EngineeringBom) => {
  const bom = row || selectedBom.value
  if (!bom || !canEditBom(bom)) {
    ElMessage.warning('只有草稿或已驳回状态的BOM可以修改')
    return
  }
  selectedBomId.value = bom.bomId
  open.value = true
}

// 删除接口一次处理一个版本；多选时不把首行误当作全部所选。
const handleDelete = (row?: EngineeringBom) => {
  const bom = row || selectedBom.value
  if (!bom || !ProductEnum.bomStatus.canDo(bom.approveStatus, ProductActions.DELETE)) {
    ElMessage.warning('请选择一项可删除的BOM')
    return
  }
  ElMessageBox.confirm(`是否确认删除 BOM【${bom.bomCode}】的 ${bom.bomVersion} 版本？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return productBomApi.removeEngineeringBom(bom.bomId)
    })
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}

// 导出按钮操作
const handleExport = () => {
  ElMessageBox.confirm(
    queryParams.showAllVersions
      ? '导出当前筛选的全部版本（包含历史版本），不受分页限制？'
      : '导出当前筛选的每个BOM现行/最新版本；历史版本请打开「展开全部版本」后导出。',
    '导出范围',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      return productBomApi.exportEngineeringBom(queryParams)
    })
    .then((res) => {
      const blob = new Blob([res as any], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = `BOM列表_${new Date().toISOString().slice(0, 10)}.xlsx`
      link.click()
      URL.revokeObjectURL(link.href)
      ElMessage.success('导出成功')
    })
    .catch(() => {})
}

// 审核按钮操作
const handleApprove = (row?: EngineeringBom) => {
  const bomId = row?.bomId || ids.value[0]
  if (!bomId) {
    ElMessage.warning('请选择要审核的BOM')
    return
  }

  selectedBomForApprove.value = bomId
  bomApproveOpen.value = true
}

// 设为默认BOM（2026-08-18：审批通过后需手动设为当前生效，生成计划/领料依赖）
const handleSetDefaultBom = (row: EngineeringBom) => {
  ElMessageBox.confirm(
    `将 BOM【${row.bomCode}】设为当前生效版本？（同产品其它BOM将取消当前标记）`,
    '设为默认',
    {
      type: 'warning',
    }
  )
    .then(async () => {
      await productBomApi.setCurrentEngineeringBom(row.bomId)
      ElMessage.success('已设为当前生效BOM')
      getList()
    })
    .catch(() => {})
}

// 流水抽屉
const traceVisible = ref(false)
const traceBomId = ref<number | undefined>(undefined)
const openTrace = (row: EngineeringBom) => {
  traceBomId.value = row.bomId
  traceVisible.value = true
}

// BOM 数据完整性预检（提交审核前）——2026-10-07 dev-20261007-006
const escapeHtml = (s: string) =>
  s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c] || c)

// 提交BOM审核（草稿→审核中）
const handleSubmitApprove = async (row: EngineeringBom) => {
  try {
    // 先只读体检：不通过就把问题清单列给用户并中止，避免提交后才被后端拦
    const checkRes = await productBomApi.checkEngineeringBom(row.bomId)
    const issues = checkRes.data || []
    if (issues.length > 0) {
      const html = issues
        .map((it) => {
          const loc = it.materialCode || (it.itemId != null ? `行#${it.itemId}` : '主表')
          return `· [${escapeHtml(loc)}] ${escapeHtml(it.message || '')}`
        })
        .join('<br/>')
      await ElMessageBox.alert(
        html,
        `BOM [${escapeHtml(row.bomCode)}] 不满足提交审核条件（${issues.length} 项）`,
        {
          dangerouslyUseHTMLString: true,
          confirmButtonText: '知道了',
          type: 'warning',
        }
      )
      return
    }
    await ElMessageBox.confirm(`确定提交 BOM [${row.bomCode}] 审核吗？`, '提交审核', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await productBomApi.submitEngineeringBom(row.bomId)
    ElMessage.success('提交审核成功')
    getList()
  } catch (e: any) {
    // confirm / alert 被取消或关闭（'cancel'/'close'）时不报错
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.message || '提交审核失败')
    }
  }
}

// 查看详情按钮操作
const handleView = (row: EngineeringBom) => {
  selectedBomId.value = row.bomId
  bomDetailOpen.value = true
}

const handleBomAction = (key: string, row: EngineeringBom) => {
  if (key === 'copy') handleCopyBom(row)
  if (key === 'edit') handleUpdate(row)
  if (key === 'delete') handleDelete(row)
  if (key === 'submit') void handleSubmitApprove(row)
  if (key === 'approve') handleApprove(row)
  if (key === 'setDefault') handleSetDefaultBom(row)
  if (key === 'trace') openTrace(row)
}

// 复制BOM为新版本（DEV-619：真接口，版本号递增+明细复制，替代原“清ID重建”假复制）
const handleCopyBom = (row: EngineeringBom) => {
  const bomId = row.bomId
  ElMessageBox.prompt('请输入新版本号', '复制为新版本', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: suggestNextVersion(row.bomVersion),
    inputValidator: (value: string) => (value?.trim() ? true : '版本号不能为空'),
  })
    .then(({ value }) => {
      return productBomApi.copyEngineeringBom(bomId!, value.trim())
    })
    .then(() => {
      ElMessage.success('复制成功')
      getList()
    })
    .catch(() => {})
}

// 建议下一个版本号：V1.0 → V2.0
function suggestNextVersion(version?: string): string {
  if (!version) return 'V1.0'
  const m = version.match(/V(\d+)\.(\d+)/)
  if (!m) return 'V1.0'
  return `V${parseInt(m[1]) + 1}.${m[2]}`
}

// 审核成功处理
const handleApproveSuccess = (result: any) => {
  ElMessage.success(
    `BOM审核${result.approveResult === ProductActions.APPROVE ? '通过' : '驳回'}成功`
  )
  getList() // 刷新列表
}

// 审核对话框关闭处理
const handleApproveClose = () => {
  selectedBomForApprove.value = undefined
}

// 监听详情对话框关闭事件，重置selectedBomId
watch(
  () => bomDetailOpen.value,
  (newValue) => {
    if (!newValue) {
      // 对话框关闭时重置selectedBomId
      selectedBomId.value = undefined
    }
  }
)

// 监听审核对话框关闭事件
watch(
  () => bomApproveOpen.value,
  (newValue) => {
    if (!newValue) {
      // 对话框关闭时重置selectedBomForApprove
      selectedBomForApprove.value = undefined
    }
  }
)

// 组件挂载时获取数据
onMounted(() => {
  getList()
})

// ==================== 路由离开保护（dev-20261021-015） ====================
// 弹窗打开且有未保存修改时，切换左侧菜单前走同一套「保存并离开 / 放弃修改 / 继续编辑」。
onBeforeRouteLeave(async () => {
  if (!open.value) return true
  const dialog = bomFormRef.value
  if (!dialog?.formIsDirty?.()) return true
  return await dialog.resolveLeave()
})
</script>

<style scoped>
.bom-list-card :deep(.el-card__body) {
  padding: 20px;
}

.bom-search {
  display: grid;
  grid-template-columns: repeat(3, minmax(130px, 1fr)) minmax(130px, 0.8fr) auto;
  align-items: end;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.bom-search :deep(.el-form-item) {
  margin-bottom: 0;
  min-width: 0;
}

.bom-search :deep(.el-form-item__label) {
  margin-bottom: 6px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.bom-search :deep(.el-select) {
  width: 100%;
}

.search-actions,
.bom-toolbar,
.toolbar-main,
.toolbar-secondary,
.version-cell {
  display: flex;
  align-items: center;
}

.search-actions {
  padding-bottom: 1px;
}

.bom-toolbar {
  justify-content: space-between;
  gap: 16px;
  padding: 18px 0;
}

.toolbar-main,
.toolbar-secondary {
  gap: 16px;
  flex-wrap: wrap;
}

.toolbar-main :deep(.el-button + .el-button) {
  margin-left: 0;
}

.selection-count,
.secondary-text,
.version-row-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.bom-list-table :deep(th.el-table__cell) {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-weight: 500;
  height: 42px;
}

.bom-list-table :deep(td.el-table__cell) {
  padding: 12px 0;
}

.bom-list-table :deep(.bom-version-row) {
  --el-table-tr-bg-color: var(--el-fill-color-lighter);
}

.bom-list-table :deep(.bom-version-row td.el-table__cell) {
  padding: 9px 0;
}

.product-cell {
  display: inline-flex;
  vertical-align: middle;
  flex-direction: column;
  align-items: flex-start;
  max-width: calc(100% - 30px);
  gap: 5px;
}

.product-name {
  max-width: 100%;
  font-weight: 500;
}

.product-name :deep(span),
.product-code {
  display: block;
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.version-cell {
  gap: 8px;
}

@media (max-width: 1100px) {
  .bom-search {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-actions {
    grid-column: 1 / -1;
  }
}

@media (max-width: 600px) {
  .bom-list-card :deep(.el-card__body) {
    padding: 12px;
  }

  .bom-search {
    grid-template-columns: minmax(0, 1fr);
  }

  .bom-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .bom-list-table :deep(.el-table-fixed-column--right) {
    position: static !important;
  }

  .bom-list-table :deep(.el-table-fixed-column--right::before) {
    display: none;
  }
}
</style>

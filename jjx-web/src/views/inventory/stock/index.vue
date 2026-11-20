<template>
  <div class="stock-list">
    <StockSearchForm v-model="queryParams" @search="handleQuery" @reset="handleQuery" />
    <div class="query-scope">统计范围：{{ appliedQuery.warehouseId ? '所选仓库' : '全部仓库' }}{{ appliedQuery.locationId ? ' / 所选库位' : '' }}；列表与统计均按当前已应用条件计算</div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #409eff">
              <el-icon><Box /></el-icon>
            </div>
            <div class="stat-info">
              <div v-if="quantitySummary.length" class="stat-unit-quantities"><div v-for="[unit, quantity] in quantitySummary" :key="unit">{{ quantity }} {{ unit }}</div></div>
              <div v-else class="stat-value">{{ summaryData.totalQuantity || 0 }}</div>
              <div class="stat-label">{{ quantitySummary.length > 1 ? '库存数量（分单位）' : '总库存数量' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #67c23a">
              <el-icon><Money /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">¥ {{ formatCurrency(summaryData.totalCost || 0) }}</div>
              <div class="stat-label">{{ summaryData.missingCostBatches ? `已知库存金额（${summaryData.missingCostBatches}批成本待补）` : '总库存金额' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #e6a23c">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ summaryData.materialCount || 0 }}</div>
              <div class="stat-label">物料种类</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #f56c6c">
              <el-icon><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ summaryData.lowStockMaterialCount || 0 }}</div>
              <div class="stat-label">低库存物料</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 操作栏 -->
    <el-card class="operation-card">
      <el-row :gutter="10">
        <el-col :span="1.5">
          <el-button type="primary" @click="handleExport">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-dropdown trigger="click" @command="handleImportMode">
            <el-button type="success" v-hasPermi="['inventory:stock:import']">
              <el-icon><Upload /></el-icon>导入<el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="quick">① 快速导入（≤100行）</el-dropdown-item>
                <el-dropdown-item command="batch" divided>③ 批量校验导入（推荐）</el-dropdown-item>
                <el-dropdown-item command="preview" disabled>② 大文件预览（开发中）</el-dropdown-item>
                <el-dropdown-item command="server" disabled>④ 服务端直导（开发中）</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleRefresh">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button v-hasPermi="['inventory:stock:view']" @click="migrationVisible = true">库存迁移数据维护</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card">
      <el-table v-loading="loading" :data="stockList" border style="width: 100%">
        <el-table-column label="库存类型" prop="itemTypeName" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="InventoryItemTypeEnum.getTagProps(row.itemType).type" size="small">
              {{ InventoryItemTypeEnum.getLabel(row.itemType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="150">
          <template #default="{ row }">
            <el-link v-if="canViewArchive(row)" type="primary" @click="handleViewArchive(row)">{{ row.materialCode }}</el-link>
            <span v-else>{{ row.materialCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="物料名称" prop="materialName" width="150" show-overflow-tooltip />
        <el-table-column label="规格型号" prop="specification" width="120" show-overflow-tooltip />
        <el-table-column label="库存数量" width="130" align="right">
          <template #default="{ row }"><span :class="{ 'low-stock': row.lowStock }">{{ formatNumber(row.physicalQuantity ?? row.totalQuantity) }} {{ row.physicalQuantity == null ? row.unit : row.physicalUnit }}</span></template>
        </el-table-column>
        <el-table-column label="预留数量" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.reservedPhysicalQuantity ?? row.totalReserved) }} {{ row.reservedPhysicalQuantity == null ? row.unit : row.physicalUnit }}</template>
        </el-table-column>
        <el-table-column label="可用数量" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.availablePhysicalQuantity ?? row.availableQuantity) }} {{ row.availablePhysicalQuantity == null ? row.unit : row.physicalUnit }}</template>
        </el-table-column>
        <el-table-column label="库存面积（㎡）" width="145" align="right">
          <template #default="{ row }">{{ row.inventoryArea == null ? '—' : Number(row.inventoryArea).toLocaleString('zh-CN', { maximumFractionDigits: 6 }) }}</template>
        </el-table-column>
        <el-table-column label="计价数量" width="140" align="right">
          <template #default="{ row }">{{ row.priceQuantity == null ? '—' : formatNumber(row.priceQuantity) }} {{ row.priceQuantity == null ? '' : row.priceUnit }}</template>
        </el-table-column>
        <el-table-column label="最早库位" prop="locationName" width="150" />
        <el-table-column label="最早有效期" prop="earliestExpiry" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.earliestExpiry" :class="{ expiring: row.expiring }">
              {{ row.earliestExpiry }}
            </span>
          </template>
        </el-table-column>
        <!-- <el-table-column label="剩余天数" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="row.daysToExpiry !== undefined"
              :type="getRemainingDaysTagType(row.daysToExpiry)"
              size="small"
            >
              {{ row.daysToExpiry }}天
            </el-tag>
          </template>
        </el-table-column> -->
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.lowStock" type="warning" size="small">低库存</el-tag>
            <el-tag v-else-if="row.expiring" type="danger" size="small">临期</el-tag>
            <el-tag v-else-if="row.obsolete" type="info" size="small">呆滞</el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">批次库存</el-button>
            <el-button
              v-hasPermi="['inventory:transaction:view']"
              link
              type="primary"
              @click="handleViewTransactions(row)"
            >收发明细</el-button>
            <el-button v-if="canAdjust" link type="primary" @click="handleAdjust(row)">调整</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 导入对话框组件 -->
    <StockImportDialog v-model:visible="importDialogVisible" @success="handleImportSuccess" />
    <!-- DEV-697 模式③：批量校验导入 -->
    <StockBatchImportDialog
      v-model:visible="batchImportVisible"
      @success="handleImportSuccess"
    />

    <MaterialDetailDialog v-if="materialDetailVisible" v-model="materialDetailVisible" :material-id="archiveId" allow-edit @changed="handleRefresh" />
    <ProductDetailDialog v-if="productDetailVisible" v-model="productDetailVisible" :product-id="archiveId ?? undefined" />
    <QuickStocktakeDialog v-if="adjustVisible" v-model="adjustVisible" :inventory-item-id="adjustInventoryItemId"
      :material-name="adjustMaterialName" :warehouse-id="appliedQuery.warehouseId" :location-id="appliedQuery.locationId" @changed="handleRefresh" />
    <!-- 批次库存对话框 -->
    <InventoryMigrationDialog v-model="migrationVisible" @changed="handleRefresh" />
    <StockDetailDialog
      v-model:visible="detailDialogVisible"
      :stock-id="currentStockId"
      :inventory-item-id="currentInventoryItemId"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'StockList',
})

import { computed, ref, shallowRef, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Box,
  Money,
  Warning,
  Clock,
  Download,
  Refresh,
  Upload,
  ArrowDown,
} from '@element-plus/icons-vue'
import { stockApi } from '@/api/inventory/stock'
import { formatCurrency, formatNumber } from '@/utils/format'
import type { StockVO, StockSummaryVO } from '@/types/inventory/stock'
import { InventoryItemTypeEnum } from '@/enums/inventory'
import StockImportDialog from '@/components/inventory/StockImportDialog.vue'
import StockBatchImportDialog from '@/components/inventory/StockBatchImportDialog.vue'
import StockDetailDialog from '@/components/inventory/StockDetailDialog.vue'
import InventoryMigrationDialog from '@/components/inventory/InventoryMigrationDialog.vue'
import MaterialDetailDialog from '@/components/inventory/MaterialDetailDialog.vue'
import ProductDetailDialog from '@/views/product/list/components/ProductDetailDialog.vue'
import { usePermission } from '@/composables/usePermission'
import StockSearchForm from './StockSearchForm.vue'
import QuickStocktakeDialog from '@/components/inventory/QuickStocktakeDialog.vue'
import { stockSearchPayload, type StockSearchModel } from './stockSearch'

const migrationVisible = ref(false)
const materialDetailVisible = ref(false)
const productDetailVisible = ref(false)
const archiveId = ref<number | null>(null)
const { hasPermission, hasAnyPermission } = usePermission()
const canAdjust = computed(() => hasPermission('inventory:stocktake:view') && hasAnyPermission(['inventory:stocktake:add', 'inventory:stocktake:approve']))
const adjustVisible = ref(false)
const adjustInventoryItemId = ref('')
const adjustMaterialName = ref('')
const canViewArchive = (row: StockVO) => row.itemType === InventoryItemTypeEnum.MATERIAL.value
  ? hasAnyPermission(['inventory:material:view', 'engineering:bom:add', 'engineering:bom:edit'])
  : row.itemType === InventoryItemTypeEnum.PRODUCT.value && hasPermission('product:list:view')
const handleViewArchive = (row: StockVO) => {
  if (!canViewArchive(row)) return
  const id = Number(row.sourceId || (row.itemType === InventoryItemTypeEnum.MATERIAL.value ? row.materialId : undefined))
  if (!Number.isSafeInteger(id) || id <= 0) {
    ElMessage.warning('该库存尚未关联有效档案，无法查看详情')
    return
  }
  archiveId.value = id
  materialDetailVisible.value = row.itemType === InventoryItemTypeEnum.MATERIAL.value
  productDetailVisible.value = row.itemType === InventoryItemTypeEnum.PRODUCT.value
}

const router = useRouter()

// 查询参数
const queryParams = reactive<StockSearchModel>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  minQuantity: undefined,
  maxQuantity: undefined,
})

// 响应式数据
const loading = ref(false)
const stockList = ref<StockVO[]>([])
const summaryData = ref<StockSummaryVO>({
  totalQuantity: 0,
  totalReservedQuantity: 0,
  totalAvailableQuantity: 0,
  totalCost: 0,
  materialCount: 0,
})
const quantitySummary = computed(() => Object.entries(summaryData.value.quantityByUnit || {}))
const alertInfo = ref({
  lowStockCount: 0,
  expiringStockCount: 0,
  obsoleteStockCount: 0,
})
const total = ref(0)

// 导入对话框
const importDialogVisible = ref(false)
// DEV-697 模式③：批量校验导入
const batchImportVisible = ref(false)

// 批次库存对话框
const detailDialogVisible = ref(false)
const currentStockId = ref<string>('')
const currentInventoryItemId = ref<string>('')

let listVersion = 0
let summaryVersion = 0
// 查询时复制已应用条件，翻页沿用；正在编辑的表单不改变当前结果口径。
const appliedQuery = shallowRef(stockSearchPayload(queryParams))
const getList = async () => {
  const version = ++listVersion
  loading.value = true
  try {
    const res = await stockApi.list({ ...appliedQuery.value, pageNum: queryParams.pageNum, pageSize: queryParams.pageSize })
    if (version !== listVersion) return
    stockList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    if (version === listVersion) { stockList.value = []; total.value = 0 }
    console.error('获取库存列表失败:', error)
  } finally { if (version === listVersion) loading.value = false }
}
const getSummary = async () => {
  const version = ++summaryVersion
  try {
    const res = await stockApi.summary({ ...appliedQuery.value })
    if (version === summaryVersion && res.data) summaryData.value = res.data
  } catch (error) {
    if (version === summaryVersion) summaryData.value = { totalQuantity: 0, totalReservedQuantity: 0, totalAvailableQuantity: 0, totalCost: 0, materialCount: 0 }
    console.error('获取库存汇总失败:', error)
  }
}

// 获取预警信息
const getAlertInfo = async () => {
  try {
    const res = await stockApi.getAlertInfo()
    alertInfo.value = res.data || alertInfo.value
  } catch (error) {
    console.error('获取预警信息失败:', error)
  }
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  appliedQuery.value = stockSearchPayload(queryParams)
  getList()
  getSummary()
}

// 刷新
const handleRefresh = () => {
  getList()
  getSummary()
  getAlertInfo()
  ElMessage.success('数据已刷新')
}

// 导出
const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

// 显示导入对话框（模式选择）
const handleShowImport = () => {
  importDialogVisible.value = true
}

// 导入模式选择（DEV-696/697：①快速导入 ≤100行；③批量校验推荐）
const handleImportMode = (mode: string) => {
  if (mode === 'quick') {
    importDialogVisible.value = true
  } else if (mode === 'batch') {
    batchImportVisible.value = true
  }
}

// 导入成功回调
const handleImportSuccess = () => {
  getList()
  getSummary()
  getAlertInfo()
}

// 查看批次库存
const handleViewDetail = (row: StockVO) => {
  currentStockId.value = row.stockId
  currentInventoryItemId.value = row.inventoryItemId
  detailDialogVisible.value = true
}

// 从即时库存联查该物料全部批次的收发明细
const handleViewTransactions = (row: StockVO) => {
  void router.push({
    path: '/inventory/transaction',
    query: {
      inventoryItemId: row.inventoryItemId,
      materialCode: row.materialCode,
      materialName: row.materialName,
    },
  })
}

// 库存调整
const handleAdjust = (row: StockVO) => {
  if (!canAdjust.value || !row.inventoryItemId) return
  adjustInventoryItemId.value = row.inventoryItemId
  adjustMaterialName.value = row.materialName
  adjustVisible.value = true
}

// 获取剩余天数标签类型
const getRemainingDaysTagType = (days: number): 'success' | 'warning' | 'info' | 'danger' => {
  if (days <= 0) return 'danger'
  if (days <= 7) return 'warning'
  if (days <= 30) return 'info'
  return 'success'
}

onMounted(() => {
  getList()
  getSummary()
  getAlertInfo()
})
</script>

<style scoped>
.stat-unit-quantities { font-size: 16px; font-weight: 600; line-height: 1.45; }

.stock-list {
  padding: 20px;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  height: 100px;
}

.stat-content {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
}

.stat-icon .el-icon {
  font-size: 24px;
  color: white;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.advanced-filter {
  margin-top: 16px;
}

.low-stock {
  color: #f56c6c;
  font-weight: bold;
}

.expiring {
  color: #e6a23c;
  font-weight: bold;
}
</style>

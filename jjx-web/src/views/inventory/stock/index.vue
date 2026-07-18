<template>
  <div class="stock-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="物料编码">
          <el-input
            v-model="queryParams.materialCode"
            placeholder="请输入物料编码"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input
            v-model="queryParams.materialName"
            placeholder="请输入物料名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #409eff">
              <el-icon><Box /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ summaryData.totalQuantity || 0 }}</div>
              <div class="stat-label">总库存数量</div>
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
              <div class="stat-label">总库存金额</div>
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
              <div class="stat-value">{{ alertInfo.lowStockCount || 0 }}</div>
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
          <el-button type="success" @click="handleShowImport">
            <el-icon><Upload /></el-icon>导入
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleRefresh">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="showFilter = !showFilter">
            <el-icon><Filter /></el-icon>{{ showFilter ? '隐藏筛选' : '更多筛选' }}
          </el-button>
        </el-col>
      </el-row>

      <!-- 更多筛选条件 -->
      <el-collapse-transition>
        <div v-show="showFilter" class="advanced-filter">
          <el-divider />
          <el-form :model="queryParams" :inline="true" label-width="100px">
            <el-form-item label="数量范围">
              <el-input-number
                v-model="queryParams.minQuantity"
                :min="0"
                placeholder="最小"
                style="width: 100px"
              />
              <span style="margin: 0 8px">-</span>
              <el-input-number
                v-model="queryParams.maxQuantity"
                :min="0"
                placeholder="最大"
                style="width: 100px"
              />
            </el-form-item>
            <el-form-item label="库存状态">
              <el-select
                v-model="stockStatusFilter"
                placeholder="请选择"
                clearable
                style="width: 120px"
                @change="handleStatusFilter"
              >
                <el-option label="低库存" value="low" />
                <el-option label="临期" value="expiring" />
                <el-option label="呆滞" value="obsolete" />
              </el-select>
            </el-form-item>
            <el-form-item label="创建时间">
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
            <el-form-item>
              <el-button type="primary" @click="handleQuery">应用筛选</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-collapse-transition>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card">
      <el-table v-loading="loading" :data="stockList" border style="width: 100%">
        <el-table-column label="物料编码" prop="materialCode" width="150" />
        <el-table-column label="物料名称" prop="materialName" width="150" show-overflow-tooltip />
        <el-table-column label="规格型号" prop="specification" width="120" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="80" align="center" />
        <el-table-column label="总库存" prop="totalQuantity" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'low-stock': row.lowStock }">
              {{ formatNumber(row.totalQuantity) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="预留数量" prop="totalReserved" width="100" align="right" />
        <el-table-column label="可用数量" prop="availableQuantity" width="100" align="right" />
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
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">批次明细</el-button>
            <el-button link type="primary" @click="handleAdjust(row)">调整</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 导入对话框组件 -->
    <StockImportDialog v-model:visible="importDialogVisible" @success="handleImportSuccess" />

    <!-- 批次明细对话框 -->
    <StockDetailDialog v-model:visible="detailDialogVisible" :material-id="currentMaterialId" />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'StockList',
})

import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Box,
  Money,
  Warning,
  Clock,
  Download,
  Refresh,
  Filter,
  Upload,
} from '@element-plus/icons-vue'
import { stockApi } from '@/api/inventory/stock'
import { formatCurrency, formatNumber } from '@/utils/format'
import type { StockQueryParams, StockVO, StockSummaryVO } from '@/types/inventory/stock'
import StockImportDialog from '@/components/inventory/StockImportDialog.vue'
import StockDetailDialog from '@/components/inventory/StockDetailDialog.vue'

const router = useRouter()

// 查询参数
const queryParams = reactive<StockQueryParams>({
  current: 1,
  pageSize: 10,
  materialCode: '',
  materialName: '',
  minQuantity: undefined,
  maxQuantity: undefined,
})

// 日期范围
const dateRange = ref<string[]>([])

// 库存状态筛选
const stockStatusFilter = ref<string>('')

// 响应式数据
const loading = ref(false)
const showFilter = ref(false)
const stockList = ref<StockVO[]>([])
const summaryData = ref<StockSummaryVO>({
  totalQuantity: 0,
  totalReservedQuantity: 0,
  totalAvailableQuantity: 0,
  totalCost: 0,
  materialCount: 0,
})
const alertInfo = ref({
  lowStockCount: 0,
  expiringStockCount: 0,
  obsoleteStockCount: 0,
})
const total = ref(0)

// 导入对话框
const importDialogVisible = ref(false)

// 批次明细对话框
const detailDialogVisible = ref(false)
const currentMaterialId = ref<string>('')

// 获取库存列表
const getList = async () => {
  loading.value = true
  try {
    const res = await stockApi.list(queryParams)
    stockList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取库存列表失败:', error)
    ElMessage.error('获取库存列表失败')
  } finally {
    loading.value = false
  }
}

// 获取库存汇总
const getSummary = async () => {
  try {
    const res = await stockApi.summary(queryParams)
    summaryData.value = res.data || summaryData.value
  } catch (error) {
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
  queryParams.current = 1
  getList()
  getSummary()
}

// 重置
const handleReset = () => {
  queryParams.current = 1
  queryParams.materialCode = ''
  queryParams.materialName = ''
  queryParams.minQuantity = undefined
  queryParams.maxQuantity = undefined
  queryParams.lowStock = undefined
  queryParams.expiring = undefined
  queryParams.obsolete = undefined
  dateRange.value = []
  stockStatusFilter.value = ''
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

// 显示导入对话框
const handleShowImport = () => {
  importDialogVisible.value = true
}

// 导入成功回调
const handleImportSuccess = () => {
  getList()
  getSummary()
  getAlertInfo()
}

// 查看批次明细
const handleViewDetail = (row: StockVO) => {
  currentMaterialId.value = row.materialId || ''
  detailDialogVisible.value = true
}

// 库存调整
const handleAdjust = (row: StockVO) => {
  ElMessage.info(`调整库存: ${row.materialName}`)
}

// 库存状态筛选
const handleStatusFilter = (val: string) => {
  queryParams.lowStock = val === 'low' ? true : undefined
  queryParams.expiring = val === 'expiring' ? true : undefined
  queryParams.obsolete = val === 'obsolete' ? true : undefined
  handleQuery()
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

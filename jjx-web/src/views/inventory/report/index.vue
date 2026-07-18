<template>
  <div class="report-list">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #409eff">
              <el-icon><Box /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ reportData.totalMaterials || 0 }}</div>
              <div class="stat-label">物料种类</div>
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
              <div class="stat-value">
                ¥ {{ formatCurrency(reportData.totalCost || 0) }}
              </div>
              <div class="stat-label">库存总金额</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #e6a23c">
              <el-icon><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">
                {{ reportData.todayInboundCount || 0 }}
              </div>
              <div class="stat-label">今日入库</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #f56c6c">
              <el-icon><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">
                {{ reportData.todayOutboundCount || 0 }}
              </div>
              <div class="stat-label">今日出库</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 日期范围选择 -->
    <el-card class="date-range-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="报表类型">
          <el-select
            v-model="queryParams.reportType"
            placeholder="请选择"
            style="width: 150px"
          >
            <el-option label="库存汇总" value="stock_summary" />
            <el-option label="出入库统计" value="inbound_outbound" />
            <el-option label="库存周转率" value="turnover_rate" />
            <el-option label="呆滞物料分析" value="obsolete_analysis" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
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
        <el-form-item label="仓库">
          <el-select
            v-model="queryParams.warehouseId"
            placeholder="请选择仓库"
            clearable
            style="width: 150px"
          >
            <el-option label="全部仓库" value="" />
            <el-option label="原材料仓库" value="1" />
            <el-option label="成品仓库" value="2" />
            <el-option label="半成品仓库" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button @click="handleExport">导出报表</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 图表区域 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="chart-header">
              <span>库存金额分布</span>
              <el-select
                v-model="chartType"
                placeholder="请选择"
                size="small"
                style="width: 120px"
              >
                <el-option label="饼图" value="pie" />
                <el-option label="柱状图" value="bar" />
              </el-select>
            </div>
          </template>
          <div class="chart-container">
            <div v-if="chartType === 'pie'" class="chart-placeholder">
              <el-empty description="库存金额分布饼图" />
            </div>
            <div v-else class="chart-placeholder">
              <el-empty description="库存金额分布柱状图" />
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="chart-header">
              <span>出入库趋势</span>
              <el-select
                v-model="trendPeriod"
                placeholder="请选择"
                size="small"
                style="width: 120px"
              >
                <el-option label="近7天" value="7" />
                <el-option label="近30天" value="30" />
                <el-option label="近90天" value="90" />
              </el-select>
            </div>
          </template>
          <div class="chart-container">
            <div class="chart-placeholder">
              <el-empty description="出入库趋势折线图" />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>库存明细报表</span>
          <el-button type="primary" size="small" @click="handleRefresh"
            >刷新数据</el-button
          >
        </div>
      </template>
      <el-table
        v-loading="loading"
        :data="reportList"
        border
        style="width: 100%"
      >
        <el-table-column
          v-if="queryParams.reportType === 'stock_summary'"
          label="物料编码"
          prop="materialCode"
          width="120"
        />
        <el-table-column
          v-if="queryParams.reportType === 'stock_summary'"
          label="物料名称"
          prop="materialName"
          width="150"
          show-overflow-tooltip
        />
        <el-table-column
          v-if="queryParams.reportType === 'inbound_outbound'"
          label="日期"
          prop="date"
          width="120"
          align="center"
        />
        <el-table-column
          v-if="queryParams.reportType === 'inbound_outbound'"
          label="入库数量"
          prop="inboundQuantity"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatNumber(row.inboundQuantity) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="queryParams.reportType === 'inbound_outbound'"
          label="出库数量"
          prop="outboundQuantity"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatNumber(row.outboundQuantity) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="queryParams.reportType === 'stock_summary'"
          label="仓库"
          prop="warehouseName"
          width="120"
        />
        <el-table-column
          v-if="queryParams.reportType === 'stock_summary'"
          label="当前库存"
          prop="currentStock"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatNumber(row.currentStock) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="queryParams.reportType === 'stock_summary'"
          label="安全库存"
          prop="safeStock"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatNumber(row.safeStock) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="queryParams.reportType === 'stock_summary'"
          label="库存金额"
          prop="stockAmount"
          width="120"
          align="right"
        >
          <template #default="{ row }">
            ¥ {{ formatCurrency(row.stockAmount) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="queryParams.reportType === 'turnover_rate'"
          label="物料编码"
          prop="materialCode"
          width="120"
        />
        <el-table-column
          v-if="queryParams.reportType === 'turnover_rate'"
          label="物料名称"
          prop="materialName"
          width="150"
          show-overflow-tooltip
        />
        <el-table-column
          v-if="queryParams.reportType === 'turnover_rate'"
          label="周转率"
          prop="turnoverRate"
          width="100"
          align="right"
        >
          <template #default="{ row }"> {{ row.turnoverRate }}% </template>
        </el-table-column>
        <el-table-column
          v-if="queryParams.reportType === 'turnover_rate'"
          label="周转天数"
          prop="turnoverDays"
          width="100"
          align="right"
        >
          <template #default="{ row }"> {{ row.turnoverDays }}天 </template>
        </el-table-column>
        <el-table-column
          v-if="queryParams.reportType === 'obsolete_analysis'"
          label="物料编码"
          prop="materialCode"
          width="120"
        />
        <el-table-column
          v-if="queryParams.reportType === 'obsolete_analysis'"
          label="物料名称"
          prop="materialName"
          width="150"
          show-overflow-tooltip
        />
        <el-table-column
          v-if="queryParams.reportType === 'obsolete_analysis'"
          label="呆滞天数"
          prop="obsoleteDays"
          width="100"
          align="right"
        >
          <template #default="{ row }"> {{ row.obsoleteDays }}天 </template>
        </el-table-column>
        <el-table-column
          v-if="queryParams.reportType === 'obsolete_analysis'"
          label="呆滞数量"
          prop="obsoleteQuantity"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatNumber(row.obsoleteQuantity) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="queryParams.reportType === 'obsolete_analysis'"
          label="呆滞金额"
          prop="obsoleteAmount"
          width="120"
          align="right"
        >
          <template #default="{ row }">
            ¥ {{ formatCurrency(row.obsoleteAmount) }}
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Box, Money, TrendCharts } from '@element-plus/icons-vue'
import { formatCurrency, formatNumber } from '@/utils/format'

// 查询参数
const queryParams = reactive({
  current: 1,
  pageSize: 10,
  reportType: 'stock_summary',
  warehouseId: '',
  startDate: '',
  endDate: '',
})

// 日期范围
const dateRange = ref<string[]>([])

// 响应式数据
const loading = ref(false)
const reportList = ref<any[]>([])
const total = ref(0)
const reportData = ref({
  totalMaterials: 156,
  totalCost: 1250000,
  todayInboundCount: 15,
  todayOutboundCount: 12,
  lowStockCount: 8,
  expiringCount: 5,
})
const chartType = ref('pie')
const trendPeriod = ref('30')

// 计算属性：设置日期范围
const setDateRange = computed({
  get: () => dateRange.value,
  set: (val: string[]) => {
    dateRange.value = val
    if (val && val.length === 2) {
      queryParams.startDate = val[0]
      queryParams.endDate = val[1]
    } else {
      queryParams.startDate = ''
      queryParams.endDate = ''
    }
  },
})

// 模拟数据 - 库存汇总
const mockStockSummaryData = [
  {
    materialId: 'MAT001',
    materialCode: 'MAT001',
    materialName: '螺丝钉',
    warehouseName: '原材料仓库',
    currentStock: 1500,
    safeStock: 200,
    stockAmount: 7500,
  },
  {
    materialId: 'MAT002',
    materialCode: 'MAT002',
    materialName: '润滑油',
    warehouseName: '成品仓库',
    currentStock: 800,
    safeStock: 100,
    stockAmount: 16000,
  },
  {
    materialId: 'MAT003',
    materialCode: 'MAT003',
    materialName: '包装箱',
    warehouseName: '原材料仓库',
    currentStock: 2000,
    safeStock: 500,
    stockAmount: 10000,
  },
]

// 模拟数据 - 出入库统计
const mockInboundOutboundData = [
  {
    date: '2024-03-28',
    inboundQuantity: 150,
    outboundQuantity: 120,
  },
  {
    date: '2024-03-27',
    inboundQuantity: 180,
    outboundQuantity: 150,
  },
  {
    date: '2024-03-26',
    inboundQuantity: 200,
    outboundQuantity: 180,
  },
]

// 模拟数据 - 周转率
const mockTurnoverRateData = [
  {
    materialId: 'MAT001',
    materialCode: 'MAT001',
    materialName: '螺丝钉',
    turnoverRate: 85.5,
    turnoverDays: 42,
  },
  {
    materialId: 'MAT002',
    materialCode: 'MAT002',
    materialName: '润滑油',
    turnoverRate: 72.3,
    turnoverDays: 50,
  },
  {
    materialId: 'MAT003',
    materialCode: 'MAT003',
    materialName: '包装箱',
    turnoverRate: 65.8,
    turnoverDays: 55,
  },
]

// 模拟数据 - 呆滞物料分析
const mockObsoleteAnalysisData = [
  {
    materialId: 'MAT004',
    materialCode: 'MAT004',
    materialName: '旧型号零件',
    obsoleteDays: 180,
    obsoleteQuantity: 500,
    obsoleteAmount: 25000,
  },
  {
    materialId: 'MAT005',
    materialCode: 'MAT005',
    materialName: '过时包装',
    obsoleteDays: 120,
    obsoleteQuantity: 300,
    obsoleteAmount: 9000,
  },
  {
    materialId: 'MAT006',
    materialCode: 'MAT006',
    materialName: '淘汰配件',
    obsoleteDays: 90,
    obsoleteQuantity: 200,
    obsoleteAmount: 6000,
  },
]

// 获取报表数据
const getList = async () => {
  loading.value = true
  try {
    // 模拟API调用
    await new Promise((resolve) => setTimeout(resolve, 500))

    // 根据报表类型选择数据
    let data: any[] = []
    switch (queryParams.reportType) {
      case 'stock_summary':
        data = [...mockStockSummaryData]
        break
      case 'inbound_outbound':
        data = [...mockInboundOutboundData]
        break
      case 'turnover_rate':
        data = [...mockTurnoverRateData]
        break
      case 'obsolete_analysis':
        data = [...mockObsoleteAnalysisData]
        break
      default:
        data = [...mockStockSummaryData]
    }

    // 模拟分页
    const start = (queryParams.current - 1) * queryParams.pageSize
    const end = start + queryParams.pageSize
    reportList.value = data.slice(start, end)
    total.value = data.length
  } catch (error) {
    console.error('获取报表数据失败:', error)
    ElMessage.error('获取报表数据失败')
  } finally {
    loading.value = false
  }
}

// 查询
const handleQuery = () => {
  queryParams.current = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.current = 1
  queryParams.reportType = 'stock_summary'
  queryParams.warehouseId = ''
  dateRange.value = []
  queryParams.startDate = ''
  queryParams.endDate = ''
  getList()
}

// 导出报表
const handleExport = () => {
  ElMessage.info('导出报表功能开发中')
}

// 刷新数据
const handleRefresh = () => {
  getList()
  ElMessage.success('数据已刷新')
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.report-list {
  padding: 20px;
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

.date-range-card,
.table-card {
  margin-bottom: 16px;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-card {
  height: 400px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-container {
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-placeholder {
  text-align: center;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>

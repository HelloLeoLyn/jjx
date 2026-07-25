<template>
  <div class="report-list">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #409eff">
              <el-icon><ShoppingCart /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ reportData.orderCount || 0 }}</div>
              <div class="stat-label">销售订单数</div>
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
                ¥ {{ formatCurrency(reportData.totalAmount || 0) }}
              </div>
              <div class="stat-label">销售总额</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #e6a23c">
              <el-icon><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ reportData.customerCount || 0 }}</div>
              <div class="stat-label">客户数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #f56c6c">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ reportData.quotationCount || 0 }}</div>
              <div class="stat-label">报价单数</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 查询条件 -->
    <el-card class="filter-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="报表类型">
          <el-select v-model="queryParams.reportType" style="width: 150px">
            <el-option label="销售订单统计" value="order" />
            <el-option label="客户统计" value="customer" />
            <el-option label="报价统计" value="quotation" />
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
              <span>销售趋势</span>
              <el-select
                v-model="trendPeriod"
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
            <el-empty description="销售趋势折线图" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="chart-header">
              <span>销售分布</span>
            </div>
          </template>
          <div class="chart-container">
            <el-empty description="销售分布饼图" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <template #header>
        <div class="table-header">
          <span>{{ tableTitle }}</span>
          <el-button type="primary" size="small" @click="handleRefresh">刷新数据</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="reportList" border style="width: 100%">
        <el-table-column v-if="queryParams.reportType === 'order'" label="订单编号" prop="orderNo" width="160" />
        <el-table-column v-if="queryParams.reportType === 'order'" label="客户名称" prop="customerName" width="140" show-overflow-tooltip />
        <el-table-column v-if="queryParams.reportType === 'order'" label="订单金额" prop="totalAmount" width="120" align="right">
          <template #default="{ row }">¥ {{ formatCurrency(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column v-if="queryParams.reportType === 'order'" label="订单日期" prop="orderDate" width="120" align="center" />
        <el-table-column v-if="queryParams.reportType === 'order'" label="订单状态" prop="statusName" width="100" align="center" />
        <el-table-column v-if="queryParams.reportType === 'customer'" label="客户名称" prop="customerName" width="140" show-overflow-tooltip />
        <el-table-column v-if="queryParams.reportType === 'customer'" label="客户等级" prop="levelName" width="100" align="center" />
        <el-table-column v-if="queryParams.reportType === 'customer'" label="联系人" prop="contactPerson" width="100" />
        <el-table-column v-if="queryParams.reportType === 'customer'" label="联系电话" prop="contactPhone" width="130" />
        <el-table-column v-if="queryParams.reportType === 'customer'" label="订单数" prop="orderCount" width="90" align="right" />
        <el-table-column v-if="queryParams.reportType === 'customer'" label="总金额" prop="totalAmount" width="120" align="right">
          <template #default="{ row }">¥ {{ formatCurrency(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column v-if="queryParams.reportType === 'quotation'" label="报价单号" prop="quotationNo" width="160" />
        <el-table-column v-if="queryParams.reportType === 'quotation'" label="客户名称" prop="customerName" width="140" show-overflow-tooltip />
        <el-table-column v-if="queryParams.reportType === 'quotation'" label="报价金额" prop="totalAmount" width="120" align="right">
          <template #default="{ row }">¥ {{ formatCurrency(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column v-if="queryParams.reportType === 'quotation'" label="报价日期" prop="quotationDate" width="120" align="center" />
        <el-table-column v-if="queryParams.reportType === 'quotation'" label="状态" prop="statusName" width="100" align="center" />
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ShoppingCart, Money, User, Document } from '@element-plus/icons-vue'
import { formatCurrency, formatNumber } from '@/utils/format'
import { getSalesReport, getCustomerReport, getQuotationReport } from '@/api/sales/report'

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  reportType: 'order',
  startDate: '',
  endDate: '',
})
const dateRange = ref<string[]>([])
const loading = ref(false)
const reportList = ref<any[]>([])
const total = ref(0)
const trendPeriod = ref('30')

const reportData = ref({
  orderCount: 0,
  totalAmount: 0,
  customerCount: 0,
  quotationCount: 0,
})

const tableTitle = computed(() => {
  const map: Record<string, string> = {
    order: '销售订单明细',
    customer: '客户统计明细',
    quotation: '报价单明细',
  }
  return map[queryParams.reportType] || '销售报表'
})

const mockData: Record<string, any[]> = {
  order: [
    { orderNo: 'SO202607001', customerName: '客户A', totalAmount: 58000, orderDate: '2026-07-25', statusName: '已审核' },
    { orderNo: 'SO202607002', customerName: '客户B', totalAmount: 32000, orderDate: '2026-07-24', statusName: '待审核' },
    { orderNo: 'SO202607003', customerName: '客户C', totalAmount: 76000, orderDate: '2026-07-23', statusName: '已完成' },
    { orderNo: 'SO202607004', customerName: '客户A', totalAmount: 12000, orderDate: '2026-07-22', statusName: '已完成' },
  ],
  customer: [
    { customerName: '客户A', levelName: 'VIP', contactPerson: '张三', contactPhone: '13800001111', orderCount: 12, totalAmount: 450000 },
    { customerName: '客户B', levelName: '普通', contactPerson: '李四', contactPhone: '13800002222', orderCount: 8, totalAmount: 280000 },
    { customerName: '客户C', levelName: '黄金', contactPerson: '王五', contactPhone: '13800003333', orderCount: 5, totalAmount: 196000 },
  ],
  quotation: [
    { quotationNo: 'QT202607001', customerName: '客户A', totalAmount: 65000, quotationDate: '2026-07-24', statusName: '已报价' },
    { quotationNo: 'QT202607002', customerName: '客户D', totalAmount: 88000, quotationDate: '2026-07-23', statusName: '待确认' },
    { quotationNo: 'QT202607003', customerName: '客户B', totalAmount: 15000, quotationDate: '2026-07-21', statusName: '已成交' },
  ],
}

const getList = async () => {
  loading.value = true
  try {
    await new Promise((r) => setTimeout(r, 500))
    const data = mockData[queryParams.reportType] || []
    const start = (queryParams.pageNum - 1) * queryParams.pageSize
    const end = start + queryParams.pageSize
    reportList.value = data.slice(start, end)
    total.value = data.length

    reportData.value = {
      orderCount: 4,
      totalAmount: 178000,
      customerCount: 3,
      quotationCount: 3,
    }
  } catch (error) {
    console.error('获取销售报表失败:', error)
    ElMessage.error('获取报表数据失败')
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => {
  queryParams.pageNum = 1
  queryParams.reportType = 'order'
  dateRange.value = []
  queryParams.startDate = ''
  queryParams.endDate = ''
  getList()
}
const handleExport = () => ElMessage.info('导出报表功能开发中')
const handleRefresh = () => { getList(); ElMessage.success('数据已刷新') }

onMounted(() => getList())
</script>

<style scoped>
.report-list { padding: 20px; }
.stats-row { margin-bottom: 16px; }
.stat-card { height: 100px; }
.stat-content { display: flex; align-items: center; }
.stat-icon {
  width: 48px; height: 48px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  margin-right: 16px;
}
.stat-icon .el-icon { font-size: 24px; color: white; }
.stat-info { flex: 1; }
.stat-value { font-size: 24px; font-weight: bold; margin-bottom: 4px; }
.stat-label { font-size: 14px; color: #666; }
.filter-card, .table-card { margin-bottom: 16px; }
.chart-row { margin-bottom: 16px; }
.chart-card { height: 380px; }
.chart-header { display: flex; justify-content: space-between; align-items: center; }
.chart-container { height: 300px; display: flex; align-items: center; justify-content: center; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
</style>

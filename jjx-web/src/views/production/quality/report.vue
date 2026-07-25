<template>
  <div class="report-list">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #67c23a">
              <el-icon><Check /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ reportData.passRate }}%</div>
              <div class="stat-label">综合良品率</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #f56c6c">
              <el-icon><Close /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ reportData.failRate }}%</div>
              <div class="stat-label">不良品率</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #409eff">
              <el-icon><List /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ reportData.totalInspections || 0 }}</div>
              <div class="stat-label">检验总次数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #e6a23c">
              <el-icon><WarningFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ reportData.failCount || 0 }}</div>
              <div class="stat-label">不合格数</div>
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
            <el-option label="检验统计" value="inspection" />
            <el-option label="缺陷分析" value="defect" />
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
              <span>质量趋势</span>
              <el-select v-model="trendPeriod" size="small" style="width: 120px">
                <el-option label="近7天" value="7" />
                <el-option label="近30天" value="30" />
                <el-option label="近90天" value="90" />
              </el-select>
            </div>
          </template>
          <div class="chart-container">
            <el-empty description="质量趋势折线图" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="chart-header">
              <span>缺陷分布</span>
            </div>
          </template>
          <div class="chart-container">
            <el-empty description="缺陷分布饼图" />
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
        <el-table-column label="检验编号" prop="inspectionNo" width="160" />
        <el-table-column label="检验类型" prop="inspectionTypeName" width="100" />
        <el-table-column label="关联单号" prop="orderNo" width="140" />
        <el-table-column label="检验员" prop="inspector" width="100" />
        <el-table-column label="检验时间" prop="inspectTime" width="140" align="center" />
        <el-table-column label="检验结果" prop="resultName" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.result === 'pass' ? 'success' : row.result === 'fail' ? 'danger' : 'warning'">
              {{ row.resultName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="合格数/总数" width="130" align="center">
          <template #default="{ row }">{{ row.passQty || 0 }} / {{ row.totalQty || 0 }}</template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
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
import { Check, Close, List, WarningFilled } from '@element-plus/icons-vue'
import { qualityApi } from '@/api/production/quality'

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  reportType: 'inspection',
  startDate: '',
  endDate: '',
})
const dateRange = ref<string[]>([])
const loading = ref(false)
const reportList = ref<any[]>([])
const total = ref(0)
const trendPeriod = ref('30')

const reportData = ref({
  passRate: 98.5,
  failRate: 1.5,
  totalInspections: 0,
  failCount: 0,
})

const tableTitle = computed(() => '质检记录明细')

const mockData = [
  { inspectionId: 1, inspectionNo: 'QC202607001', inspectionTypeName: '来料检验', orderNo: 'PO202607001', inspector: '张三', inspectTime: '2026-07-25 10:30', result: 'pass', resultName: '合格', totalQty: 500, passQty: 498, remark: '轻微划痕可接受' },
  { inspectionId: 2, inspectionNo: 'QC202607002', inspectionTypeName: '过程检验', orderNo: 'MO202607001', inspector: '李四', inspectTime: '2026-07-24 15:20', result: 'pass', resultName: '合格', totalQty: 200, passQty: 200, remark: '' },
  { inspectionId: 3, inspectionNo: 'QC202607003', inspectionTypeName: '成品检验', orderNo: 'MO202607002', inspector: '王五', inspectTime: '2026-07-23 09:15', result: 'fail', resultName: '不合格', totalQty: 100, passQty: 95, remark: '尺寸偏差超标' },
  { inspectionId: 4, inspectionNo: 'QC202607004', inspectionTypeName: '来料检验', orderNo: 'PO202607002', inspector: '张三', inspectTime: '2026-07-22 14:00', result: 'pass', resultName: '合格', totalQty: 1000, passQty: 1000, remark: '' },
]

const getList = async () => {
  loading.value = true
  try {
    await new Promise((r) => setTimeout(r, 500))
    const data = [...mockData]
    const start = (queryParams.pageNum - 1) * queryParams.pageSize
    const end = start + queryParams.pageSize
    reportList.value = data.slice(start, end)
    total.value = data.length
    reportData.value = {
      passRate: 98.5,
      failRate: 1.5,
      totalInspections: data.length,
      failCount: data.filter((d) => d.result === 'fail').length,
    }
  } catch (error) {
    console.error('获取质量报表失败:', error)
    ElMessage.error('获取报表数据失败')
  } finally {
    loading.value = false
  }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => {
  queryParams.pageNum = 1
  queryParams.reportType = 'inspection'
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

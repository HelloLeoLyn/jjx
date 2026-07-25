<template>
  <div class="production-quality">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">质量管理</h1>
      <div class="page-actions">
        <el-button type="primary" icon="Plus" @click="handleCreate">新建检验</el-button>
        <el-button icon="Setting" @click="showSettings">检验标准</el-button>
        <el-button icon="Document" @click="handleReport">质量报告</el-button>
      </div>
    </div>

    <!-- 质量概览 -->
    <div class="quality-overview">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #67c23a">
                <el-icon><Check /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">98.5%</div>
                <div class="stat-label">综合良品率</div>
                <div class="stat-trend trend-up">+0.5%</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #f56c6c">
                <el-icon><Close /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">1.5%</div>
                <div class="stat-label">不良品率</div>
                <div class="stat-trend trend-down">-0.2%</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #e6a23c">
                <el-icon><Warning /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">12</div>
                <div class="stat-label">今日检验批次</div>
                <div class="stat-trend trend-up">+3</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #409eff">
                <el-icon><TrendCharts /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">95.8%</div>
                <div class="stat-label">一次检验合格率</div>
                <div class="stat-trend trend-up">+1.2%</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 质量数据 -->
    <div class="quality-data">
      <el-row :gutter="20">
        <!-- 检验记录 -->
        <el-col :span="16">
          <el-card class="section-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span class="card-title">检验记录</span>
                <div class="card-actions">
                  <el-button link @click="viewAllInspections">查看全部</el-button>
                </div>
              </div>
            </template>

            <el-table :data="inspectionData" style="width: 100%" height="400">
              <el-table-column prop="inspectionNo" label="检验单号" width="180" />
              <el-table-column prop="productName" label="产品名称" width="150" />
              <el-table-column prop="batchNo" label="批次号" width="120" />
              <el-table-column prop="inspectionType" label="检验类型" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="getInspectionTypeTag(row.inspectionType)">
                    {{ row.inspectionType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="totalQuantity" label="检验数量" width="100" />
              <el-table-column prop="qualifiedQuantity" label="合格数量" width="100" />
              <el-table-column prop="defectiveQuantity" label="不良数量" width="100" />
              <el-table-column prop="qualifiedRate" label="合格率" width="100">
                <template #default="{ row }">
                  {{ (row.qualifiedRate * 100).toFixed(1) }}%
                </template>
              </el-table-column>
              <el-table-column prop="inspector" label="检验员" width="100" />
              <el-table-column prop="inspectionTime" label="检验时间" width="180" />
              <el-table-column label="操作" width="80" fixed="right">
                <template #default="{ row }">
                  <el-button link size="small" @click="viewInspectionDetail(row)"> 详情 </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>

        <!-- 不良品分析 -->
        <el-col :span="8">
          <el-card class="section-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span class="card-title">不良品分析</span>
              </div>
            </template>

            <div class="defect-analysis">
              <div class="defect-chart-placeholder">
                <el-empty description="不良品分析图表待开发" />
              </div>

              <div class="defect-types">
                <div class="defect-type-item" v-for="item in defectTypes" :key="item.type">
                  <div class="defect-type-info">
                    <span class="defect-type-name">{{ item.type }}</span>
                    <span class="defect-type-count">{{ item.count }}件</span>
                  </div>
                  <div class="defect-type-progress">
                    <el-progress
                      :percentage="item.percentage"
                      :stroke-width="8"
                      :show-text="false"
                      :color="getDefectColor(item.type)"
                    />
                  </div>
                  <div class="defect-type-percentage">{{ item.percentage }}%</div>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 质量趋势 -->
    <div class="quality-trend">
      <el-card class="section-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">质量趋势分析</span>
            <div class="time-range">
              <el-radio-group v-model="trendTimeRange" size="small">
                <el-radio-button value="week">本周</el-radio-button>
                <el-radio-button value="month">本月</el-radio-button>
                <el-radio-button value="quarter">本季度</el-radio-button>
              </el-radio-group>
            </div>
          </div>
        </template>

        <div class="trend-chart-placeholder">
          <el-empty description="质量趋势图表待开发" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Check,
  Close,
  Warning,
  TrendCharts,
  Plus,
  Setting,
  Document,
} from '@element-plus/icons-vue'

interface InspectionRecord {
  inspectionNo: string
  productName: string
  batchNo: string
  inspectionType: string
  totalQuantity: number
  qualifiedQuantity: number
  defectiveQuantity: number
  qualifiedRate: number
  inspector: string
  inspectionTime: string
}

interface DefectType {
  type: string
  count: number
  percentage: number
}

// 响应式数据
const trendTimeRange = ref('week')

const inspectionData = ref<InspectionRecord[]>([
  {
    inspectionNo: 'QC-20240410-001',
    productName: '薄膜开关-A型',
    batchNo: 'BATCH-001',
    inspectionType: '首件检验',
    totalQuantity: 100,
    qualifiedQuantity: 98,
    defectiveQuantity: 2,
    qualifiedRate: 0.98,
    inspector: '张三',
    inspectionTime: '2024-04-10 09:30:00',
  },
  {
    inspectionNo: 'QC-20240410-002',
    productName: '薄膜开关-B型',
    batchNo: 'BATCH-002',
    inspectionType: '过程检验',
    totalQuantity: 200,
    qualifiedQuantity: 197,
    defectiveQuantity: 3,
    qualifiedRate: 0.985,
    inspector: '李四',
    inspectionTime: '2024-04-10 11:15:00',
  },
  {
    inspectionNo: 'QC-20240410-003',
    productName: '薄膜开关-C型',
    batchNo: 'BATCH-003',
    inspectionType: '成品检验',
    totalQuantity: 150,
    qualifiedQuantity: 148,
    defectiveQuantity: 2,
    qualifiedRate: 0.987,
    inspector: '王五',
    inspectionTime: '2024-04-10 14:45:00',
  },
  {
    inspectionNo: 'QC-20240409-001',
    productName: '薄膜开关-A型',
    batchNo: 'BATCH-004',
    inspectionType: '首件检验',
    totalQuantity: 100,
    qualifiedQuantity: 97,
    defectiveQuantity: 3,
    qualifiedRate: 0.97,
    inspector: '张三',
    inspectionTime: '2024-04-09 10:20:00',
  },
  {
    inspectionNo: 'QC-20240409-002',
    productName: '薄膜开关-B型',
    batchNo: 'BATCH-005',
    inspectionType: '过程检验',
    totalQuantity: 180,
    qualifiedQuantity: 176,
    defectiveQuantity: 4,
    qualifiedRate: 0.978,
    inspector: '李四',
    inspectionTime: '2024-04-09 13:30:00',
  },
])

const defectTypes = ref<DefectType[]>([
  { type: '外观缺陷', count: 8, percentage: 40 },
  { type: '尺寸偏差', count: 5, percentage: 25 },
  { type: '功能失效', count: 4, percentage: 20 },
  { type: '材料问题', count: 3, percentage: 15 },
])

// 方法
const getInspectionTypeTag = (type: string) => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
    首件检验: 'success',
    过程检验: 'warning',
    成品检验: 'info',
    特殊检验: 'danger',
  }
  return map[type] || 'info'
}

const getDefectColor = (type: string) => {
  const map: Record<string, string> = {
    外观缺陷: '#e6a23c',
    尺寸偏差: '#f56c6c',
    功能失效: '#409eff',
    材料问题: '#67c23a',
  }
  return map[type] || '#909399'
}

// 事件处理
const handleCreate = () => {
  console.log('新建检验')
  // TODO: 跳转到新建检验页面
}

const showSettings = () => {
  console.log('显示检验标准设置')
  // TODO: 跳转到检验标准设置页面
}

const router = useRouter()

const handleReport = () => {
  router.push('/production/quality/report')
}

const viewAllInspections = () => {
  console.log('查看全部检验记录')
  // TODO: 跳转到检验记录列表页面
}

const viewInspectionDetail = (record: InspectionRecord) => {
  console.log('查看检验详情:', record)
  // TODO: 跳转到检验详情页面
}
</script>

<style scoped>
.production-quality {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.quality-overview {
  margin-bottom: 24px;
}

.stat-card {
  border-radius: 8px;
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
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.stat-trend {
  font-size: 12px;
  margin-top: 2px;
}

.trend-up {
  color: #67c23a;
}

.trend-down {
  color: #f56c6c;
}

.quality-data {
  margin-bottom: 20px;
}

.section-card {
  border-radius: 8px;
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.defect-analysis {
  height: 400px;
  display: flex;
  flex-direction: column;
}

.defect-chart-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.defect-types {
  flex: 1;
}

.defect-type-item {
  margin-bottom: 16px;
}

.defect-type-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.defect-type-name {
  font-size: 14px;
  color: #606266;
}

.defect-type-count {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.defect-type-progress {
  margin-bottom: 4px;
}

.defect-type-percentage {
  font-size: 12px;
  color: #909399;
  text-align: right;
}

.quality-trend {
  margin-bottom: 20px;
}

.time-range {
  display: flex;
  align-items: center;
}

.trend-chart-placeholder {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>

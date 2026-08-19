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
                <div class="stat-value">{{ stats.passRate ?? '-' }}%</div>
                <div class="stat-label">综合良品率</div>
                <div class="stat-trend">合格 {{ stats.passCount ?? 0 }} 批</div>
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
                <div class="stat-value">{{ failRate }}%</div>
                <div class="stat-label">不良品率</div>
                <div class="stat-trend">不合格 {{ stats.failCount ?? 0 }} 批</div>
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
                <div class="stat-value">{{ stats.totalCount ?? 0 }}</div>
                <div class="stat-label">检验批次</div>
                <div class="stat-trend">待检 {{ stats.pendingCount ?? 0 }} 批</div>
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
                <div class="stat-value">{{ stats.passRate ?? '-' }}%</div>
                <div class="stat-label">一次检验合格率</div>
                <div class="stat-trend">总数量 {{ stats.totalQty ?? 0 }}</div>
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

            <el-table :data="inspectionData" style="width: 100%" height="400" v-loading="tableLoading">
              <el-table-column prop="inspectionNo" label="检验单号" width="180" />
              <el-table-column prop="productName" label="产品名称" width="150" />
              <el-table-column prop="orderNo" label="关联订单" width="140" />
              <el-table-column label="检验类型" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="getInspectionTypeTag(row.inspectionTypeName || row.inspectionType)">
                    {{ row.inspectionTypeName || row.inspectionType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="totalQty" label="检验数量" width="100" />
              <el-table-column prop="passQty" label="合格数量" width="100" />
              <el-table-column prop="failQty" label="不良数量" width="100" />
              <el-table-column label="合格率" width="100">
                <template #default="{ row }">
                  {{ calcRate(row) }}
                </template>
              </el-table-column>
              <el-table-column prop="inspector" label="检验员" width="100" />
              <el-table-column label="检验时间" width="170">
                <template #default="{ row }">
                  {{ formatTime(row.inspectTime) }}
                </template>
              </el-table-column>
              <el-table-column label="结果" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="resultTagType(row.result)">{{ row.resultName || row.result || '待检' }}</el-tag>
                </template>
              </el-table-column>
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
import { ref, computed, onMounted } from 'vue'
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
import { qualityApi } from '@/api/production/quality'
import type { QualityVO } from '@/api/production/quality'

interface DefectType {
  type: string
  count: number
  percentage: number
}

// 响应式数据
const trendTimeRange = ref('week')

const inspectionData = ref<QualityVO[]>([])
const tableLoading = ref(false)

// 后端统计接口返回
const stats = ref<Record<string, any>>({})

// 不良品率 = 100 - 良品率
const failRate = computed(() => {
  const rate = Number(stats.value.passRate)
  return Number.isFinite(rate) ? (100 - rate).toFixed(1) : '0.0'
})

// 缺陷类型分布：由统计兜底为空数组（后端暂无缺陷分类统计）
const defectTypes = ref<DefectType[]>([])

// 方法
const getInspectionTypeTag = (type: string) => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
    来料检验: 'warning', // IQC
    过程检验: 'warning', // IPQC
    完工检验: 'warning', // FQC（P0-01 补齐）
    出货检验: 'info', // OQC
    首件检验: 'success',
    成品检验: 'info',
    特殊检验: 'danger',
  }
  return map[type] || 'info'
}

const resultTagType = (result: string) => {
  const map: Record<string, 'success' | 'danger' | 'info' | 'warning'> = {
    pass: 'success',
    fail: 'danger',
    pending: 'info',
  }
  return map[result] || 'info'
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

// 计算合格率（%）
function calcRate(row: any): string {
  const total = Number(row.totalQty)
  const pass = Number(row.passQty)
  if (!total) return '-'
  return ((pass / total) * 100).toFixed(1) + '%'
}

function formatTime(t?: string): string {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '-'
}

// 加载统计 + 检验记录
async function loadData() {
  try {
    const [statRes, pageRes] = await Promise.all([
      qualityApi.getStatistics(),
      qualityApi.page({ pageNum: 1, pageSize: 10 }),
    ])
    if (statRes.code === 200 || statRes.code === 0) stats.value = statRes.data || {}
    if ((pageRes.code === 200 || pageRes.code === 0) && pageRes.data) {
      inspectionData.value = (pageRes.data as any).records || (pageRes.data as any).list || []
    }
  } catch (e) {
    console.error('加载质量数据失败:', e)
  } finally {
    tableLoading.value = false
  }
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

const viewInspectionDetail = (record: any) => {
  router.push({ path: '/production/quality/report', query: { id: record.inspectionId } })
}

onMounted(() => {
  loadData()
})
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

<template>
  <div class="report-page">
    <div class="page-header">
      <h1 class="page-title">生产报表</h1>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 产量报表 -->
      <el-tab-pane label="📊 产量报表" name="output">
        <el-card shadow="never">
          <el-row :gutter="16">
            <el-col :span="8">
              <div class="metric-card">
                <div class="metric-value">{{ formatNum(output.totalPlanned) }}</div>
                <div class="metric-label">计划产量</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="metric-card">
                <div class="metric-value done">{{ formatNum(output.totalCompleted) }}</div>
                <div class="metric-label">完成产量</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="metric-card">
                <div class="metric-value rate">{{ output.completionRate }}%</div>
                <div class="metric-label">完成率</div>
              </div>
            </el-col>
          </el-row>
          <el-table :data="output.byProduct" style="margin-top:16px" v-if="output.byProduct?.length">
            <el-table-column prop="productName" label="产品名称" />
            <el-table-column prop="productCode" label="编码" width="120" />
            <el-table-column prop="planned" label="计划" width="100" align="right" />
            <el-table-column prop="completed" label="完成" width="100" align="right" />
          </el-table>
          <el-empty v-else description="暂无产量数据" />
        </el-card>
      </el-tab-pane>

      <!-- 效率报表 -->
      <el-tab-pane label="⚡ 效率报表" name="efficiency">
        <el-card shadow="never">
          <el-row :gutter="16">
            <el-col :span="8">
              <div class="metric-card">
                <div class="metric-value">{{ formatNum(efficiency.totalOperations) }}</div>
                <div class="metric-label">完成工序数</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="metric-card">
                <div class="metric-value done">{{ formatNum(efficiency.onTimeCount) }}</div>
                <div class="metric-label">按时完成</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="metric-card">
                <div class="metric-value" :style="{ color: parseFloat(efficiency.onTimeRate) >= 80 ? '#67c23a' : '#f56c6c' }">{{ efficiency.onTimeRate }}%</div>
                <div class="metric-label">按时完成率</div>
              </div>
            </el-col>
          </el-row>
          <el-empty v-else-if="efficiency.totalOperations === 0" description="暂无效率数据" />
        </el-card>
      </el-tab-pane>

      <!-- 质量报表 -->
      <el-tab-pane label="✅ 质量报表" name="quality">
        <el-card shadow="never">
          <el-row :gutter="16">
            <el-col :span="6">
              <div class="metric-card">
                <div class="metric-value">{{ formatNum(quality.totalInspections) }}</div>
                <div class="metric-label">检验总数</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="metric-card">
                <div class="metric-value done">{{ formatNum(quality.passCount) }}</div>
                <div class="metric-label">合格</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="metric-card">
                <div class="metric-value" style="color:#f56c6c">{{ formatNum(quality.failCount) }}</div>
                <div class="metric-label">不合格</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="metric-card">
                <div class="metric-value rate">{{ quality.passRate }}%</div>
                <div class="metric-label">合格率</div>
              </div>
            </el-col>
          </el-row>
          <el-empty v-else-if="quality.totalInspections === 0" description="暂无质量数据" />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getOutputReport, getEfficiencyReport, getQualityReport } from '@/api/production/report'

const activeTab = ref('output')

const output = reactive({
  totalPlanned: 0, totalCompleted: 0, completionRate: '0.0', byProduct: [] as any[]
})
const efficiency = reactive({
  totalOperations: 0, onTimeRate: '0.0', onTimeCount: 0, delayedCount: 0
})
const quality = reactive({
  totalInspections: 0, passCount: 0, failCount: 0, passRate: '0.0'
})

function formatNum(v: number | string) {
  return Number(v || 0).toLocaleString()
}

onMounted(async () => {
  const [outRes, effRes, qualRes] = await Promise.all([
    getOutputReport(), getEfficiencyReport(), getQualityReport()
  ])
  if (outRes?.data) Object.assign(output, outRes.data)
  if (effRes?.data) Object.assign(efficiency, effRes.data)
  if (qualRes?.data) Object.assign(quality, qualRes.data)
})
</script>

<style scoped>
.report-page { padding: 20px; }
.page-header { margin-bottom: 20px; }
.page-title { margin: 0; font-size: 24px; font-weight: 500; }
.metric-card {
  text-align: center; padding: 24px; background: #f5f7fa; border-radius: 8px;
}
.metric-value { font-size: 36px; font-weight: 700; color: #303133; }
.metric-value.done { color: #67c23a; }
.metric-value.rate { color: #409eff; }
.metric-label { font-size: 13px; color: #909399; margin-top: 8px; }
</style>

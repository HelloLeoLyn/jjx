<template>
  <div class="production-dashboard">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">生产看板</h1>
      <div class="page-actions">
        <el-button type="primary" icon="Refresh" @click="refreshData">刷新</el-button>
        <el-button icon="Setting" @click="showSettings">设置</el-button>
      </div>
    </div>

    <!-- 生产概览卡片 -->
    <div class="overview-cards">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #409eff">
                <el-icon><DataBoard /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">1,250</div>
                <div class="stat-label">今日产量</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #67c23a">
                <el-icon><Operation /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">48</div>
                <div class="stat-label">在制品</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #e6a23c">
                <el-icon><Monitor /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">85%</div>
                <div class="stat-label">设备利用率</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #f56c6c">
                <el-icon><Check /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">98.5%</div>
                <div class="stat-label">良品率</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 工单状态看板 -->
    <div class="workorder-board">
      <el-card class="section-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">工单状态</span>
            <el-button link @click="viewAllWorkOrders">查看全部</el-button>
          </div>
        </template>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="待生产" name="pending">
            <el-empty description="暂无待生产工单" />
          </el-tab-pane>
          <el-tab-pane label="生产中" name="processing">
            <el-empty description="暂无生产中工单" />
          </el-tab-pane>
          <el-tab-pane label="已完成" name="completed">
            <el-empty description="暂无已完成工单" />
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>

    <!-- 生产进度图表 -->
    <div class="production-charts">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-card class="section-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span class="card-title">生产进度</span>
              </div>
            </template>
            <div class="chart-placeholder">
              <el-empty description="生产进度图表待开发" />
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="section-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span class="card-title">质量统计</span>
              </div>
            </template>
            <div class="chart-placeholder">
              <el-empty description="质量统计图表待开发" />
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 实时通知 -->
    <div class="real-time-notifications">
      <el-card class="section-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">实时通知</span>
            <el-badge :value="3" class="badge">
              <el-button link @click="viewAllNotifications">查看全部</el-button>
            </el-badge>
          </div>
        </template>
        <el-empty description="暂无实时通知" />
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProductionDashboard',
})

import { ref } from 'vue'
import { DataBoard, Operation, Monitor, Check, Refresh, Setting } from '@element-plus/icons-vue'

const activeTab = ref('pending')

const refreshData = () => {
  console.log('刷新数据')
  // TODO: 实现数据刷新逻辑
}

const showSettings = () => {
  console.log('显示设置')
  // TODO: 实现设置逻辑
}

const viewAllWorkOrders = () => {
  console.log('查看全部工单')
  // TODO: 跳转到工单管理页面
}

const viewAllNotifications = () => {
  console.log('查看全部通知')
  // TODO: 跳转到通知页面
}
</script>

<style scoped>
.production-dashboard {
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

.overview-cards {
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

.section-card {
  margin-bottom: 20px;
  border-radius: 8px;
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

.chart-placeholder {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.badge {
  margin-right: 8px;
}
</style>

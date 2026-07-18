<template>
  <div class="stats-cards">
    <div class="stats-header">
      <h3 class="stats-title">订单统计</h3>
      <el-button link icon="Refresh" :loading="loading" @click="handleRefresh" class="refresh-btn">
        刷新
      </el-button>
    </div>
    <el-row :gutter="20">
      <el-col v-for="card in statCards" :key="card.title" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" :style="{ backgroundColor: card.color }">
              <el-icon>
                <component :is="card.icon" />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.title }}</div>
              <div class="stat-description">{{ card.description }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ProductionOrderStats } from '@/types/production/order'

interface StatCard {
  title: string
  value: number
  icon: string
  color: string
  description: string
}

interface Props {
  stats: ProductionOrderStats
  loading?: boolean
}

interface Emits {
  (e: 'refresh'): void
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
})

const emit = defineEmits<Emits>()

const statCards = computed<StatCard[]>(() => [
  {
    title: '总订单数',
    value: props.stats.totalCount || 0,
    icon: 'Document',
    color: '#409eff',
    description: '全部生产订单数量',
  },
  {
    title: '生产计划',
    value: props.stats.planCount || 0,
    icon: 'Calendar',
    color: '#67c23a',
    description: '待审批和已批准的计划',
  },
  {
    title: '生产工单',
    value: props.stats.workOrderCount || 0,
    icon: 'Tools',
    color: '#e6a23c',
    description: '执行中的工单',
  },
  {
    title: '逾期订单',
    value: props.stats.overdueCount || 0,
    icon: 'Clock',
    color: '#f56c6c',
    description: '已超过计划完成日期的订单',
  },
])

const handleRefresh = () => {
  emit('refresh')
}
</script>

<style scoped>
.stats-cards {
  margin-bottom: 20px;
}

.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.stats-title {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
  color: #303133;
}

.refresh-btn {
  padding: 4px 8px;
}

.stat-card {
  height: 120px;
}

.stat-content {
  display: flex;
  align-items: center;
  height: 100%;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  flex-shrink: 0;
}

.stat-icon .el-icon {
  font-size: 28px;
  color: white;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
  line-height: 1;
}

.stat-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 4px;
}

.stat-description {
  font-size: 12px;
  color: #909399;
  line-height: 1.2;
}
</style>

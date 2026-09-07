<template>
  <WidgetCard title="🏭 生产概况" tone="production">
    <div class="widget-grid">
      <div class="wg-item">
        <div class="wg-value danger">{{ data.activeOrders }}</div>
        <div class="wg-label">在产工单</div>
      </div>
      <div class="wg-item">
        <div class="wg-value warning">{{ data.pendingOrders }}</div>
        <div class="wg-label">待开工</div>
      </div>
      <div class="wg-item">
        <div class="wg-value success">{{ data.todayCompleted }}</div>
        <div class="wg-label">今日完工</div>
      </div>
      <div class="wg-item">
        <div class="wg-value primary">{{ data.totalOrders }}</div>
        <div class="wg-label">工单总数</div>
      </div>
    </div>
  </WidgetCard>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import request from '@/utils/request'
import WidgetCard from '../components/WidgetCard.vue'

const data = reactive({ activeOrders: 0, pendingOrders: 0, todayCompleted: 0, totalOrders: 0 })
onMounted(async () => {
  try {
    const res = await request.get('/dashboard/production-overview')
    if (res?.data) Object.assign(data, res.data)
  } catch (error) {
    console.warn('生产概况加载失败', error)
  }
})
</script>

<style scoped lang="scss">
.widget-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.wg-item {
  padding: 10px;
  text-align: center;
  background: #f7f8fa;
  border-radius: 8px;
}
.wg-value {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  &.primary {
    color: #409eff;
  }
  &.success {
    color: #67c23a;
  }
  &.warning {
    color: #e6a23c;
  }
  &.danger {
    color: #f56c6c;
  }
}
.wg-label {
  margin-top: 2px;
  font-size: 11px;
  color: #909399;
}
</style>

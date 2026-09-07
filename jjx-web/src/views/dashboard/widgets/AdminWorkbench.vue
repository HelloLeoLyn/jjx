<template>
  <WidgetCard title="📊 公司总览" tone="admin">
    <div class="widget-grid">
      <div class="wg-item">
        <div class="wg-value primary">¥{{ fmt(data.monthSalesAmount) }}</div>
        <div class="wg-label">本月销售订单额</div>
      </div>
      <div class="wg-item">
        <div class="wg-value success">{{ data.monthOrderCount }}</div>
        <div class="wg-label">本月订单数</div>
      </div>
      <div class="wg-item">
        <div class="wg-value">{{ data.materialCount }}</div>
        <div class="wg-label">材料总数</div>
      </div>
      <div class="wg-item">
        <div class="wg-value">{{ data.userCount }}</div>
        <div class="wg-label">用户总数</div>
      </div>
    </div>
  </WidgetCard>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import request from '@/utils/request'
import WidgetCard from '../components/WidgetCard.vue'

const data = reactive({ monthSalesAmount: 0, monthOrderCount: 0, materialCount: 0, userCount: 0 })
function fmt(value: unknown) {
  return (Number(value) || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}
onMounted(async () => {
  try {
    const res = await request.get('/dashboard/admin-overview')
    if (res?.data) Object.assign(data, res.data)
  } catch (error) {
    console.warn('公司总览加载失败', error)
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
}
.wg-label {
  margin-top: 2px;
  font-size: 11px;
  color: #909399;
}
</style>

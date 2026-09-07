<template>
  <WidgetCard :title="`📈 我的销售工作台 · ${salesMonth}`" tone="sales">
    <div class="todo-title">待办提醒（点击直达）</div>
    <div class="todo-grid">
      <div
        v-for="item in todoList"
        :key="item.key"
        class="todo-item"
        @click="router.push(item.path)"
      >
        <span class="todo-label">{{ item.label }}</span>
        <el-badge
          :value="Number(item.count) || 0"
          :hidden="!Number(item.count)"
          :type="Number(item.count) ? 'danger' : 'info'"
        />
      </div>
    </div>
    <div class="todo-title performance-title">本月业绩</div>
    <div class="widget-grid">
      <div class="wg-item">
        <div class="wg-value primary">¥{{ fmt(salesWB.monthQuotationAmount) }}</div>
        <div class="wg-label">报价额</div>
      </div>
      <div class="wg-item">
        <div class="wg-value success">¥{{ fmt(salesWB.monthOrderAmount) }}</div>
        <div class="wg-label">订单额</div>
      </div>
      <div class="wg-item">
        <div class="wg-value">¥{{ fmt(salesWB.monthReceiptAmount) }}</div>
        <div class="wg-label">回款额</div>
      </div>
      <div class="wg-item">
        <div class="wg-value warning">{{ salesWB.monthNewCustomerCount }}</div>
        <div class="wg-label">新增客户</div>
      </div>
      <div class="wg-item">
        <div class="wg-value primary">{{ salesWB.monthSampleCount }}</div>
        <div class="wg-label">打样单数</div>
      </div>
    </div>
  </WidgetCard>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import WidgetCard from '../components/WidgetCard.vue'
const router = useRouter()
const salesMonth = ref('')
const salesWB = reactive({
  inquiryPending: 0,
  quotationSent: 0,
  quotationReviewing: 0,
  orderReviewing: 0,
  orderReadyProduction: 0,
  deliveryUnreceived: 0,
  receivableUnpaid: 0,
  monthQuotationAmount: 0,
  monthOrderAmount: 0,
  monthReceiptAmount: 0,
  monthNewCustomerCount: 0,
  monthSampleCount: 0,
})
const todoList = computed(() => [
  { key: 'inquiry', label: '待处理询价', count: salesWB.inquiryPending, path: '/sales/inquiry' },
  {
    key: 'quotSent',
    label: '报价待客户回复',
    count: salesWB.quotationSent,
    path: '/sales/quotation',
  },
  {
    key: 'quotReview',
    label: '报价待审核',
    count: salesWB.quotationReviewing,
    path: '/sales/quotation',
  },
  { key: 'orderReview', label: '订单待审核', count: salesWB.orderReviewing, path: '/sales/order' },
  {
    key: 'orderProd',
    label: '待转生产订单',
    count: salesWB.orderReadyProduction,
    path: '/sales/order',
  },
  {
    key: 'delivery',
    label: '已发货未签收',
    count: salesWB.deliveryUnreceived,
    path: '/sales/delivery',
  },
  { key: 'receivable', label: '应收未清', count: salesWB.receivableUnpaid, path: '/sales/order' },
])
function fmt(value: unknown) {
  return (Number(value) || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}
onMounted(async () => {
  salesMonth.value = `${new Date().getMonth() + 1}月`
  try {
    const res = await request.get('/dashboard/sales-workbench')
    if (res?.data) Object.assign(salesWB, res.data)
  } catch (error) {
    console.warn('销售工作台加载失败', error)
  }
})
</script>
<style scoped lang="scss">
.todo-title {
  margin: 4px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}
.performance-title {
  margin-top: 14px;
}
.todo-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.todo-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.15s;
}
.todo-item:hover {
  color: #409eff;
  background: #f5f9ff;
  border-color: #409eff;
}
.todo-label {
  color: #303133;
}
.todo-item:hover .todo-label {
  color: #409eff;
}
.widget-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
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
}
.wg-label {
  margin-top: 2px;
  font-size: 11px;
  color: #909399;
}
@media (max-width: 900px) {
  .todo-grid,
  .widget-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 520px) {
  .todo-grid,
  .widget-grid {
    grid-template-columns: 1fr;
  }
}
</style>

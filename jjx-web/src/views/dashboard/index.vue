<template>
  <div class="dashboard-container">
    <DashboardSkeleton v-if="loading" />
    <template v-else>
      <div class="stats-grid">
        <StatCard icon="Box" :value="stats.materialCount" label="物料总数" />
        <StatCard
          v-if="hasPermi('inventory:stock:view')"
          icon="Wallet"
          tone="success"
          :value="stats.stockCount"
          label="库存项数"
          to="/inventory/stock"
        />
        <StatCard
          v-if="hasPermi('purchase:order:view')"
          icon="ShoppingCart"
          tone="warning"
          :value="stats.orderCount"
          label="采购订单"
          to="/purchase/order"
        />
        <StatCard
          v-if="hasPermi('inventory:stock:view')"
          icon="WarningFilled"
          tone="danger"
          :value="stats.lowStockCount"
          label="低库存预警"
          to="/inventory/stock"
        />
      </div>
      <section v-if="widgets.length" class="dashboard-section">
        <div class="section-title">📌 我的工作台</div>
        <div class="widgets-grid">
          <component
            v-for="widget in widgets"
            :is="widget.comp"
            :key="widget.key"
            :class="{ 'span-full': widget.full }"
          />
        </div>
      </section>
      <div
        class="dashboard-section operations-grid"
        :class="{ 'single-column': !hasPermi('inventory:stock:view') }"
      >
        <el-card v-if="hasPermi('inventory:stock:view')" shadow="never" class="content-card">
          <template #header
            ><div class="card-header">
              <span>⚠️ 库存预警</span
              ><el-tag v-if="alertItems.length" type="danger" size="small"
                >{{ alertItems.length }} 项</el-tag
              >
            </div></template
          >
          <div v-if="alertItems.length" class="alert-list">
            <div v-for="item in alertItems" :key="item.materialId" class="alert-item">
              <div class="alert-info">
                <div class="alert-name">{{ item.materialName }}</div>
                <div class="alert-spec">{{ item.specification }}</div>
              </div>
              <div class="alert-qty">
                <span :class="item.quantity <= 0 ? 'out-of-stock' : 'low'"
                  >{{ item.quantity }}{{ item.unit }}</span
                >
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无库存预警" :image-size="60" />
        </el-card>
        <el-card shadow="never" class="content-card"
          ><template #header
            ><div class="card-header"><span>⚡ 快捷操作</span></div></template
          ><QuickActions :actions="quickActions"
        /></el-card>
      </div>
      <el-card shadow="never" class="dashboard-section content-card">
        <template #header
          ><div class="card-header"><span>📋 系统信息</span></div></template
        >
        <div class="info-grid">
          <div v-for="item in systemInfo" :key="item.label" class="info-item">
            <span>{{ item.label }}</span
            ><strong>{{ item.value }}</strong>
          </div>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, reactive, ref, type Component } from 'vue'
import request from '@/utils/request'
import { useUserStore } from '@/store/modules/user'
import { stockApi } from '@/api/inventory/stock'
import { getOrderCount } from '@/api/purchase/order'
import { deptApi } from '@/api/system/dept'
import StatCard from './components/StatCard.vue'
import QuickActions, { type QuickAction } from './components/QuickActions.vue'
import DashboardSkeleton from './components/DashboardSkeleton.vue'
import SalesWorkbench from './widgets/SalesWorkbench.vue'
import ProductionWorkbench from './widgets/ProductionWorkbench.vue'
import AdminWorkbench from './widgets/AdminWorkbench.vue'
import TodoNoticeCard from './widgets/TodoNoticeCard.vue'

const userStore = useUserStore()
const loading = ref(true)
const stats = reactive({
  materialCount: 0,
  stockCount: 0,
  orderCount: 0,
  lowStockCount: 0,
  productCount: 0,
  customerCount: 0,
  userCount: 0,
  deptCount: '-' as number | string,
})
const alertItems = ref<any[]>([])
function hasPermi(permission: string) {
  const perms = userStore.getPermissions
  if (!perms.length || perms.includes('*') || perms.includes('*:*:*')) return true
  return perms.includes(permission)
}
const widgets = computed<{ key: string; comp: Component; full?: boolean }[]>(() => {
  const list: { key: string; comp: Component; full?: boolean }[] = []
  if (hasPermi('sales:dashboard'))
    list.push({ key: 'sales', comp: markRaw(SalesWorkbench), full: true })
  if (hasPermi('production:dashboard'))
    list.push({ key: 'production', comp: markRaw(ProductionWorkbench) })
  if (hasPermi('admin:dashboard')) list.push({ key: 'admin', comp: markRaw(AdminWorkbench) })
  list.push({ key: 'todo', comp: markRaw(TodoNoticeCard), full: true })
  return list
})
const quickActions: QuickAction[] = [
  {
    label: '物料管理',
    icon: 'Box',
    path: '/inventory/material',
    perms: ['inventory:material:view'],
  },
  {
    label: '采购订单',
    icon: 'ShoppingCart',
    path: '/purchase/order',
    type: 'success',
    perms: ['purchase:order:view'],
  },
  {
    label: '库存管理',
    icon: 'Wallet',
    path: '/inventory/stock',
    type: 'warning',
    perms: ['inventory:stock:view'],
  },
  { label: '产品管理', icon: 'Goods', path: '/product/list', perms: ['product:list:view'] },
  {
    label: '客户管理',
    icon: 'User',
    path: '/sales/customer',
    type: 'info',
    perms: ['sales:customer:view'],
  },
  { label: '系统设置', icon: 'Setting', path: '/system/user', perms: ['system:user:view'] },
]
const systemInfo = computed(() => [
  { label: '系统名称', value: 'JJX ERP 薄膜开关管理系统' },
  { label: '系统版本', value: 'v1.0.0' },
  { label: '当前用户', value: userStore.userInfo?.userName || '-' },
  { label: '数据库状态', value: '已连接 ✅' },
  { label: '物料总数', value: stats.materialCount },
  { label: '产品总数', value: stats.productCount },
  { label: '用户数量', value: stats.userCount },
  { label: '部门数量', value: stats.deptCount },
])
async function loadStats() {
  const res = await request.get('/dashboard/my-stats')
  const data = res?.data || {}
  stats.materialCount = Number(data.materialCount) || 0
  stats.productCount = Number(data.productCount) || 0
  stats.customerCount = Number(data.customerCount) || 0
  stats.userCount = Number(data.userCount) || 0
}
async function loadStock() {
  const [summary, alerts] = await Promise.all([stockApi.summary(), stockApi.getLowStock()])
  stats.stockCount = Number((summary?.data as any)?.totalQuantity) || 0
  const items = Array.isArray(alerts?.data) ? alerts.data : []
  alertItems.value = items.slice(0, 8)
  stats.lowStockCount = items.length
}
async function loadOrderCount() {
  const res = await getOrderCount()
  stats.orderCount = Number(res?.data) || 0
}
async function loadDeptCount() {
  const res = await deptApi.list({} as any)
  const countTree = (nodes: any[]): number =>
    nodes.reduce(
      (total, node) => total + 1 + (Array.isArray(node.children) ? countTree(node.children) : 0),
      0
    )
  stats.deptCount = Array.isArray(res?.data) ? countTree(res.data) : '-'
}
onMounted(async () => {
  const tasks: Promise<unknown>[] = [loadStats()]
  if (hasPermi('inventory:stock:view')) tasks.push(loadStock())
  if (hasPermi('purchase:order:view')) tasks.push(loadOrderCount())
  if (hasPermi('system:dept:view')) tasks.push(loadDeptCount())
  const results = await Promise.allSettled(tasks)
  results
    .filter((item) => item.status === 'rejected')
    .forEach((item) => console.warn('仪表盘数据加载失败', (item as PromiseRejectedResult).reason))
  loading.value = false
})
</script>

<style scoped lang="scss">
.dashboard-container {
  padding: 0;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}
.dashboard-section {
  margin-top: 16px;
}
.section-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.widgets-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 16px;
}
.span-full {
  grid-column: 1/-1;
}
.operations-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.operations-grid.single-column {
  grid-template-columns: 1fr;
}
.content-card {
  border: 1px solid #e8eaef;
  border-radius: 12px;
  transition: box-shadow 0.18s;
}
.content-card:hover {
  box-shadow: 0 6px 18px rgb(31 45 61 / 6%);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.alert-list {
  max-height: 320px;
  overflow-y: auto;
}
.alert-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f6;
}
.alert-item:last-child {
  border-bottom: 0;
}
.alert-info {
  min-width: 0;
  flex: 1;
}
.alert-name {
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.alert-spec {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
.alert-qty {
  flex-shrink: 0;
  margin-left: 12px;
  font-size: 14px;
  font-weight: 600;
}
.low {
  color: #e6a23c;
}
.out-of-stock {
  color: #f56c6c;
}
.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1px;
  background: #ebeef5;
  border: 1px solid #ebeef5;
}
.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  background: #fff;
}
.info-item span {
  color: #909399;
}
.info-item strong {
  color: #303133;
  text-align: right;
}
@media (max-width: 800px) {
  .operations-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 480px) {
  .widgets-grid,
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>

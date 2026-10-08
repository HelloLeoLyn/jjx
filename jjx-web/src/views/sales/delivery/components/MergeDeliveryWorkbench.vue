<template>
  <div class="delivery-workbench">
    <header class="page-heading">
      <div>
        <h2>发货管理</h2>
        <p>先选择订单明细，再安排本次发货</p>
      </div>
      <el-tag type="info" effect="plain">前端交互样稿</el-tag>
    </header>
    <el-alert
      title="上区使用演示订单；生成的演示单据刷新后清空。真实发货记录在下区单独查看。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-card shadow="never" class="arrange-panel">
      <template #header
        ><div class="section-heading">
          <div>
            <strong>① 待安排发货明细</strong><span>演示销售订单 → 勾选明细 → 合并发货</span>
          </div>
          <el-button link @click="expanded = !expanded">{{ expanded ? '收起' : '展开' }}</el-button>
        </div></template
      >
      <div v-show="expanded">
        <el-form inline class="filters">
          <el-form-item label="客户"
            ><el-select
              v-model="customerId"
              style="width: 270px"
              :disabled="selectedIds.length > 0"
              @change="changeCustomer"
              ><el-option
                v-for="customer in mockCustomers"
                :key="customer.id"
                :label="customer.name"
                :value="customer.id" /></el-select
          ></el-form-item>
          <el-form-item label="收货地址"
            ><el-select v-model="addressId" style="width: 190px" :disabled="selectedIds.length > 0"
              ><el-option
                v-for="address in customer.addresses"
                :key="address.id"
                :label="address.label"
                :value="address.id" /></el-select
          ></el-form-item>
          <el-form-item label="币种"
            ><el-select v-model="currency" style="width: 100px" :disabled="selectedIds.length > 0"
              ><el-option label="CNY" value="CNY" /><el-option label="USD" value="USD" /></el-select
          ></el-form-item>
          <el-form-item label="交货方式"
            ><el-select
              v-model="deliveryMethod"
              style="width: 110px"
              :disabled="selectedIds.length > 0"
              ><el-option label="自送" value="自送" /><el-option
                label="快递"
                value="快递" /></el-select
          ></el-form-item>
          <el-form-item label="搜索"
            ><el-input
              v-model="keyword"
              clearable
              placeholder="销售单号 / 客户订单号 / 料号 / 品名"
              style="width: 290px"
          /></el-form-item>
        </el-form>
        <div class="scope-note">
          同客户、同地址、同币种、同交货方式可合并；交期不同允许合并。选择明细后锁定合并条件。
        </div>
        <el-table :data="rows" border max-height="360" row-key="id" :row-class-name="rowClass">
          <el-table-column width="55" align="center">
            <template #header
              ><el-checkbox
                :model-value="allVisibleSelected"
                :indeterminate="someVisibleSelected && !allVisibleSelected"
                :disabled="!eligibleRows.length"
                aria-label="选择当前可合并明细"
                @change="toggleAll"
            /></template>
            <template #default="{ row }"
              ><el-checkbox
                :model-value="selectedIds.includes(row.id)"
                :disabled="!canSelect(row)"
                :aria-label="`选择 ${row.orderNo} ${row.customerMaterialNo}`"
                @change="toggleRow(row.id)"
            /></template>
          </el-table-column>
          <el-table-column label="来源订单 / 客户订单" min-width="175"
            ><template #default="{ row }"
              ><strong>{{ row.orderNo }}</strong>
              <div class="secondary">{{ row.customerOrderNo }}</div></template
            ></el-table-column
          >
          <el-table-column label="客户料号 / 品名" min-width="225"
            ><template #default="{ row }"
              >{{ row.customerMaterialNo }}
              <div class="secondary">{{ row.productName }}</div></template
            ></el-table-column
          >
          <el-table-column prop="dueDate" label="交期" width="110" sortable />
          <el-table-column prop="quantity" label="订单量" width="85" align="right" />
          <el-table-column prop="shipped" label="已发量" width="85" align="right" />
          <el-table-column label="订单剩余" width="95" align="right"
            ><template #default="{ row }">{{
              row.quantity - row.shipped
            }}</template></el-table-column
          >
          <el-table-column prop="occupied" label="待发占用" width="95" align="right" />
          <el-table-column label="可安排量" width="95" align="right"
            ><template #default="{ row }"
              ><strong>{{ availableQuantity(row) }}</strong></template
            ></el-table-column
          >
          <el-table-column prop="stockAvailable" label="成品可用量" width="110" align="right" />
          <el-table-column label="合并条件" min-width="145"
            ><template #default="{ row }"
              ><el-tag :type="canSelect(row) ? 'success' : 'info'" size="small">{{
                unavailableReason(row) || '可选择'
              }}</el-tag></template
            ></el-table-column
          >
          <template #empty
            ><el-empty description="没有匹配的演示订单明细" :image-size="65"
          /></template>
        </el-table>
        <div class="selection-bar">
          <div>
            已选 <strong>{{ selectedLines.length }}</strong> 条明细 ·
            <strong>{{ selectedOrderCount }}</strong> 张订单
            <span v-if="differentDueDates" class="due-warning">交期不同，请核对统一送货日期</span>
          </div>
          <div class="selection-actions">
            <el-button :disabled="!selectedIds.length" @click="selectedIds = []">清空选择</el-button
            ><el-button type="primary" :disabled="!selectedLines.length" @click="openMerge"
              >合并发货（预览）</el-button
            >
          </div>
        </div>
        <p class="stock-note">
          可安排量 = 订单量 − 已发量 −
          待发占用。成品可用量单独展示；当前样稿只演示订单占用，不模拟库存锁定。
        </p>
      </div>
    </el-card>
    <el-card shadow="never" class="records-panel">
      <template #header
        ><div class="section-heading">
          <strong>② 发货单记录</strong><span>建单 → OQC → 发货 → 签收</span>
        </div></template
      >
      <el-tabs v-model="recordTab">
        <el-tab-pane :label="`演示发货单（${deliveries.length}）`" name="demo">
          <el-table v-if="deliveries.length" :data="deliveries" border>
            <el-table-column prop="deliveryNo" label="发货单号" min-width="170" />
            <el-table-column label="客户" min-width="220"
              ><template #default="{ row }">{{
                mockCustomers.find((item) => item.id === row.customerId)?.name
              }}</template></el-table-column
            >
            <el-table-column label="关联销售订单" min-width="200"
              ><template #default="{ row }">{{
                [...new Set(row.lines.map((line: MockDeliveryLine) => line.orderNo))].join('、')
              }}</template></el-table-column
            >
            <el-table-column prop="deliveryDate" label="安排日期" width="115" />
            <el-table-column prop="deliveryMethod" label="交货方式" width="95" />
            <el-table-column label="本次数量" width="100" align="right"
              ><template #default="{ row }">{{
                row.lines.reduce(
                  (sum: number, line: MockMergedDelivery['lines'][number]) =>
                    sum + (line.sendQuantity || 0),
                  0
                )
              }}</template></el-table-column
            >
            <el-table-column label="发货状态" width="110"
              ><template #default="{ row }"
                ><el-tag :type="DeliveryStatusEnum.getTagProps(row.deliveryStatus).type">{{
                  DeliveryStatusEnum.getLabel(row.deliveryStatus)
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column label="操作" width="120"
              ><template #default="{ row }"
                ><el-button link type="primary" @click="mergePreview?.open(row)"
                  >查看演示详情</el-button
                ></template
              ></el-table-column
            >
          </el-table>
          <el-empty
            v-else
            description="暂无演示发货单，从上区勾选明细并合并发货后显示在这里"
            :image-size="80"
          />
        </el-tab-pane>
        <el-tab-pane label="真实发货记录" name="live"><slot /></el-tab-pane>
      </el-tabs>
    </el-card>
    <MergeDeliveryPreview ref="mergePreview" :orders="orders" @created="onCreated" />
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { DeliveryStatusEnum } from '@/enums/sales/DeliveryEnum'
import MergeDeliveryPreview from './MergeDeliveryPreview.vue'
import {
  availableQuantity,
  mockCustomers,
  mockOrders,
  type MockDeliveryLine,
  type MockDeliveryOrder,
  type MockMergedDelivery,
} from './mergeDeliveryMock'
const customerId = ref(mockCustomers[0]!.id)
const addressId = ref(mockCustomers[0]!.addresses[0]!.id)
const currency = ref('CNY')
const deliveryMethod = ref('自送')
const keyword = ref('')
const expanded = ref(true)
const recordTab = ref('demo')
const selectedIds = ref<number[]>([])
const deliveries = ref<MockMergedDelivery[]>([])
const mergePreview = ref<InstanceType<typeof MergeDeliveryPreview>>()
const customer = computed(() => mockCustomers.find((item) => item.id === customerId.value)!)
const orders = computed<MockDeliveryOrder[]>(() =>
  mockOrders.map((order) => ({
    ...order,
    lines: order.lines.map((line) => ({
      ...line,
      occupied:
        line.occupied +
        deliveries.value.reduce(
          (sum, delivery) =>
            sum +
            delivery.lines
              .filter((item) => item.id === line.id)
              .reduce((amount, item) => amount + (item.sendQuantity || 0), 0),
          0
        ),
    })),
  }))
)
type ArrangeRow = MockDeliveryLine &
  Pick<MockDeliveryOrder, 'dueDate' | 'addressId' | 'currency' | 'deliveryMethod'>
const customerRows = computed<ArrangeRow[]>(() =>
  orders.value
    .filter((order) => order.customerId === customerId.value)
    .flatMap((order) =>
      order.lines.map((line) => ({
        ...line,
        dueDate: order.dueDate,
        addressId: order.addressId,
        currency: order.currency,
        deliveryMethod: order.deliveryMethod,
      }))
    )
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate) || a.id - b.id)
)
const rows = computed(() =>
  customerRows.value.filter((line) =>
    [line.orderNo, line.customerOrderNo, line.customerMaterialNo, line.productName]
      .join(' ')
      .toLowerCase()
      .includes(keyword.value.trim().toLowerCase())
  )
)
function unavailableReason(row: ArrangeRow) {
  if (row.addressId !== addressId.value) return '收货地址不同'
  if (row.currency !== currency.value) return '币种不同'
  if (row.deliveryMethod !== deliveryMethod.value) return '交货方式不同'
  if (!availableQuantity(row)) return '已全部安排'
  return ''
}
const canSelect = (row: ArrangeRow) => !unavailableReason(row)
const rowClass = ({ row }: { row: ArrangeRow }) => (canSelect(row) ? '' : 'unavailable-row')
const eligibleRows = computed(() => rows.value.filter(canSelect))
const selectedLines = computed(() =>
  customerRows.value.filter((row) => selectedIds.value.includes(row.id))
)
const selectedOrderCount = computed(
  () => new Set(selectedLines.value.map((line) => line.orderId)).size
)
const differentDueDates = computed(
  () => new Set(selectedLines.value.map((line) => line.dueDate)).size > 1
)
const allVisibleSelected = computed(
  () =>
    eligibleRows.value.length > 0 &&
    eligibleRows.value.every((row) => selectedIds.value.includes(row.id))
)
const someVisibleSelected = computed(() =>
  eligibleRows.value.some((row) => selectedIds.value.includes(row.id))
)
function toggleRow(id: number) {
  const row = customerRows.value.find((item) => item.id === id)
  if (!row || !canSelect(row)) return
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((item) => item !== id)
    : [...selectedIds.value, id]
}
function toggleAll() {
  const visibleIds = new Set(eligibleRows.value.map((row) => row.id))
  selectedIds.value = allVisibleSelected.value
    ? selectedIds.value.filter((id) => !visibleIds.has(id))
    : [...new Set([...selectedIds.value, ...visibleIds])]
}
function changeCustomer() {
  selectedIds.value = []
  addressId.value = customer.value.addresses[0]!.id
  keyword.value = ''
}
function openMerge() {
  const first = selectedLines.value[0]
  const order = first && orders.value.find((item) => item.id === first.orderId)
  if (order) mergePreview.value?.openSelection(selectedLines.value, order)
}
function onCreated(delivery: MockMergedDelivery) {
  deliveries.value.unshift(delivery)
  selectedIds.value = []
  recordTab.value = 'demo'
}
</script>
<style scoped>
.page-heading,
.section-heading,
.selection-bar,
.selection-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.page-heading {
  margin-bottom: 16px;
}
.page-heading h2 {
  margin: 0 0 6px;
  font-size: 20px;
  color: #334155;
}
.page-heading p,
.section-heading span,
.secondary,
.scope-note,
.stock-note {
  color: #64748b;
  font-size: 12px;
}
.page-heading p {
  margin: 0;
}
.section-heading > div {
  display: flex;
  align-items: center;
  gap: 16px;
}
.arrange-panel,
.records-panel {
  margin-top: 16px;
}
.filters :deep(.el-form-item) {
  margin-bottom: 12px;
}
.scope-note {
  margin-bottom: 12px;
}
.secondary {
  margin-top: 4px;
}
.selection-bar {
  padding: 14px 16px;
  background: #f1f5f9;
  border-radius: 6px;
  margin-top: 12px;
  font-size: 13px;
}
.selection-bar strong {
  color: #2563eb;
}
.selection-actions {
  gap: 8px;
}
.stock-note {
  margin: 10px 0 0;
  line-height: 1.6;
}
.due-warning {
  color: #b45309;
  margin-left: 12px;
}
:deep(.unavailable-row) {
  color: #94a3b8;
  background: #f8fafc;
}
@media (max-width: 850px) {
  .section-heading > div,
  .selection-bar {
    align-items: flex-start;
    flex-direction: column;
  }
  .section-heading span {
    line-height: 1.6;
  }
  .due-warning {
    display: block;
    margin: 6px 0 0;
  }
}
</style>

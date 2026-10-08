<template>
  <el-dialog
    v-model="visible"
    :title="draft ? '合并发货单 · 演示详情' : '新建合并发货'"
    width="min(1320px, 96vw)"
    top="4vh"
    append-to-body
    :close-on-click-modal="false"
    class="merge-delivery-dialog"
  >
    <div class="prototype-note">
      <el-tag type="info" effect="plain" size="small">交互预览</el-tag
      ><span>使用演示数据，创建的单据仅保留在当前页面，刷新后清空。</span>
    </div>
    <div class="merge-layout">
      <div class="merge-main">
        <section class="recipient-panel">
          <div class="section-title">
            <el-icon><Location /></el-icon>收货信息<span>同一客户 · 同一地址 · 同一币种</span>
          </div>
          <el-form label-position="top" class="recipient-form">
            <el-form-item label="客户" required>
              <el-select
                v-model="form.customerId"
                :disabled="!!draft || selected.length > 0"
                @change="changeCustomer"
              >
                <el-option
                  v-for="item in mockCustomers"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="收货地址" required>
              <el-select v-model="form.addressId" :disabled="!!draft || selected.length > 0">
                <el-option
                  v-for="item in customer.addresses"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="送货日期" required
              ><el-date-picker
                v-model="form.deliveryDate"
                :disabled="!!draft"
                type="date"
                value-format="YYYY-MM-DD"
            /></el-form-item>
          </el-form>
          <div class="recipient-line">
            <span>{{ address?.address }}</span
            ><span>{{ address?.contact }} · {{ address?.phone }}</span>
          </div>
          <div v-if="!draft && selected.length" class="scope-hint">
            已选明细后锁定客户与地址；清空明细后可切换。
          </div>
        </section>

        <el-tabs v-model="activeTab" class="merge-tabs">
          <el-tab-pane v-if="!draft" name="available">
            <template #label
              ><span class="tab-label"
                ><el-icon><Files /></el-icon>可选订单<span class="tab-count">{{
                  customerOrders.length
                }}</span></span
              ></template
            >
            <div class="picker-toolbar">
              <el-input
                v-model="keyword"
                placeholder="搜索销售单号、客户订单号、料号或品名"
                clearable
                :prefix-icon="Search"
              /><el-button @click="addAllEligible">加入可发明细</el-button>
            </div>
            <div class="order-scroll">
              <article
                v-for="order in filteredOrders"
                :key="order.id"
                class="order-card"
                :class="{ unavailable: !canSelectOrder(order) }"
              >
                <header class="order-heading">
                  <div>
                    <strong>{{ order.orderNo }}</strong
                    ><span>客户订单 {{ order.customerOrderNo }}</span>
                  </div>
                  <div>
                    <el-tag size="small" effect="plain">交期 {{ order.dueDate }}</el-tag
                    ><el-tag v-if="!canSelectOrder(order)" type="info" size="small"
                      >收货地址不同</el-tag
                    ><el-button
                      v-else
                      size="small"
                      :disabled="allSelected(order)"
                      @click="addOrder(order)"
                      >{{ allSelected(order) ? '已加入' : '整单加入' }}</el-button
                    >
                  </div>
                </header>
                <el-table :data="order.lines" size="small" style="width: 100%">
                  <el-table-column label="选择" width="56" align="center"
                    ><template #default="{ row }"
                      ><el-checkbox
                        :model-value="isSelected(row.id)"
                        :disabled="!canSelectOrder(order) || availableQuantity(row) <= 0"
                        :aria-label="`选择 ${row.customerMaterialNo}`"
                        @change="toggleLine(row)" /></template
                  ></el-table-column>
                  <el-table-column label="客户料号 / 产品" min-width="210"
                    ><template #default="{ row }"
                      ><div class="line-code">{{ row.customerMaterialNo }}</div>
                      <div class="secondary">{{ row.productName }}</div></template
                    ></el-table-column
                  >
                  <el-table-column prop="quantity" label="订单量" width="78" align="right" />
                  <el-table-column prop="shipped" label="已发" width="70" align="right" />
                  <el-table-column prop="occupied" label="待发占用" width="84" align="right" />
                  <el-table-column label="可建单" width="85" align="right"
                    ><template #default="{ row }"
                      ><strong class="available-qty">{{ availableQuantity(row) }}</strong></template
                    ></el-table-column
                  >
                  <el-table-column label="OQC" width="92"
                    ><template #default="{ row }"
                      ><el-tag
                        :type="InspectionResultEnum.getTagProps(row.inspectionResult).type"
                        size="small"
                        effect="plain"
                        >{{ InspectionResultEnum.getLabel(row.inspectionResult) }}</el-tag
                      ></template
                    ></el-table-column
                  >
                </el-table>
              </article>
              <el-empty
                v-if="!filteredOrders.length"
                description="没有匹配的订单明细"
                :image-size="64"
              />
            </div>
          </el-tab-pane>

          <el-tab-pane name="selected">
            <template #label
              ><span class="tab-label"
                ><el-icon><Box /></el-icon>{{ draft ? '发货明细' : '本次发货'
                }}<span class="tab-count">{{ selected.length }}</span></span
              ></template
            >
            <div class="selected-toolbar">
              <span>同一产品来自不同订单时分行展示，分别计算发货数量。</span
              ><el-button
                v-if="!draft"
                type="danger"
                link
                :disabled="!selected.length"
                @click="selected = []"
                >清空明细</el-button
              >
            </div>
            <el-table :data="selected" border max-height="330" class="selected-table">
              <el-table-column label="来源订单" min-width="145"
                ><template #default="{ row }"
                  ><div class="line-code">{{ row.orderNo }}</div>
                  <div class="secondary">{{ row.customerOrderNo }}</div></template
                ></el-table-column
              >
              <el-table-column label="客户料号 / 品名" min-width="210"
                ><template #default="{ row }"
                  ><div class="line-code">{{ row.customerMaterialNo }}</div>
                  <div class="secondary">{{ row.productName }}</div></template
                ></el-table-column
              >
              <el-table-column label="可建单" width="85" align="right"
                ><template #default="{ row }">{{
                  availableQuantity(row)
                }}</template></el-table-column
              >
              <el-table-column label="本次发货" width="175"
                ><template #default="{ row }"
                  ><template v-if="draft">{{ row.sendQuantity }} PCS</template
                  ><template v-else
                    ><el-input-number
                      v-model="row.sendQuantity"
                      :min="1"
                      :precision="0"
                      controls-position="right"
                      :class="{ 'invalid-quantity': invalidQuantity(row) }"
                      style="width: 140px"
                    />
                    <div v-if="invalidQuantity(row)" class="field-error">
                      请输入 1～{{ availableQuantity(row) }} 的整数
                    </div></template
                  ></template
                ></el-table-column
              >
              <el-table-column v-if="!draft" label="操作" width="68"
                ><template #default="{ row }"
                  ><el-button type="danger" link @click="removeLine(row.id)"
                    >移除</el-button
                  ></template
                ></el-table-column
              >
            </el-table>
            <el-form label-position="top" class="settings-form">
              <el-form-item label="送货单备注"
                ><el-input
                  v-model="form.remark"
                  :disabled="!!draft"
                  type="textarea"
                  :rows="2"
                  maxlength="300"
                  show-word-limit
              /></el-form-item>
              <el-form-item label="打印内容"
                ><el-checkbox v-model="form.showAmount" :disabled="!!draft">显示金额</el-checkbox
                ><el-checkbox v-model="form.showWeight" :disabled="!!draft"
                  >显示单重（g）</el-checkbox
                ></el-form-item
              >
            </el-form>
          </el-tab-pane>

          <el-tab-pane name="paper">
            <template #label
              ><span class="tab-label"
                ><el-icon><Printer /></el-icon>送货单预览</span
              ></template
            >
            <div class="paper-scroll">
              <article v-for="(page, pageIndex) in pages" :key="pageIndex" class="delivery-paper">
                <h3>深圳市精捷信科技有限公司</h3>
                <div class="paper-company">TEL：0755-29856711　FAX：0755-29856700</div>
                <h2>送　货　单</h2>
                <div class="paper-meta">
                  <div>
                    TO：{{ customer.name }}<br />ATTN：{{ address?.contact }} {{ address?.phone }}
                  </div>
                  <div>
                    编号：JJX QR-026<br />NO：{{ draft?.deliveryNo || '创建后生成' }}<br />DATE：{{
                      form.deliveryDate
                    }}
                  </div>
                </div>
                <table>
                  <thead>
                    <tr>
                      <th>NO</th>
                      <th>物料料号</th>
                      <th>品名规格</th>
                      <th>单位</th>
                      <th>数量</th>
                      <th>销售单号</th>
                      <th v-if="form.showAmount">金额</th>
                      <th>订单号码</th>
                      <th v-if="form.showWeight">单重 g</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, index) in page" :key="row.id">
                      <td>{{ pageIndex * 6 + index + 1 }}</td>
                      <td>{{ row.customerMaterialNo }}</td>
                      <td>{{ row.productName }}</td>
                      <td>PCS</td>
                      <td>{{ row.sendQuantity ?? '—' }}</td>
                      <td>{{ row.orderNo }}</td>
                      <td v-if="form.showAmount">
                        {{ money((row.sendQuantity || 0) * row.unitPrice) }}
                      </td>
                      <td>{{ row.customerOrderNo }}</td>
                      <td v-if="form.showWeight">{{ row.unitWeight }}</td>
                    </tr>
                    <tr v-if="!page.length">
                      <td
                        :colspan="
                          form.showAmount && form.showWeight
                            ? 9
                            : form.showAmount || form.showWeight
                              ? 8
                              : 7
                        "
                        class="paper-empty"
                      >
                        请先选择本次发货明细
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p class="paper-remark">备注：{{ form.remark || '—' }}</p>
                <div class="paper-signatures">
                  <span>送货单位经手人：____________</span><span>收货单位经手人：____________</span>
                </div>
                <div class="paper-page">
                  第 {{ pageIndex + 1 }} / {{ pages.length }} 页 · 演示预览
                </div>
              </article>
            </div>
          </el-tab-pane>

          <el-tab-pane v-if="draft" name="orders" label="关联订单">
            <el-table :data="selectedOrders" border
              ><el-table-column prop="orderNo" label="销售单号" /><el-table-column
                prop="customerOrderNo"
                label="客户订单号"
              /><el-table-column prop="dueDate" label="交期" /><el-table-column
                label="本次发货量"
                align="right"
                ><template #default="{ row }">{{
                  selected
                    .filter((item) => item.orderId === row.id)
                    .reduce((sum, item) => sum + (item.sendQuantity || 0), 0)
                }}</template></el-table-column
              ></el-table
            >
          </el-tab-pane>
          <el-tab-pane v-if="draft" name="quality" label="质量检验">
            <el-alert
              title="演示记录：待检明细可先建单，所有明细检验合格后才可确认发货。"
              type="info"
              :closable="false"
              show-icon
            />
            <el-table :data="selected" style="margin-top: 12px"
              ><el-table-column prop="orderNo" label="来源订单" /><el-table-column
                prop="customerMaterialNo"
                label="客户料号"
              /><el-table-column label="OQC"
                ><template #default="{ row }"
                  ><el-tag :type="InspectionResultEnum.getTagProps(row.inspectionResult).type">{{
                    InspectionResultEnum.getLabel(row.inspectionResult)
                  }}</el-tag></template
                ></el-table-column
              ></el-table
            >
          </el-tab-pane>
          <el-tab-pane v-if="draft" name="records" label="出库与签收记录"
            ><el-empty description="演示单据尚未发货，没有出库和签收记录" :image-size="80"
          /></el-tab-pane>
        </el-tabs>
      </div>
      <aside class="merge-summary">
        <div class="summary-title">
          <el-icon><Van /></el-icon>本次发货概览
        </div>
        <el-tag v-if="draft" :type="DeliveryStatusEnum.getTagProps(draft.deliveryStatus).type">{{
          DeliveryStatusEnum.getLabel(draft.deliveryStatus)
        }}</el-tag>
        <div class="summary-number">{{ totalQuantity.toLocaleString() }}<span>PCS</span></div>
        <div class="summary-stats">
          <div>
            <strong>{{ selectedOrders.length }}</strong
            >张订单
          </div>
          <div>
            <strong>{{ selected.length }}</strong
            >项明细
          </div>
        </div>
        <div class="summary-divider" />
        <div class="summary-row"><span>币种</span><strong>CNY</strong></div>
        <div class="summary-row">
          <span>货品金额</span><strong>¥ {{ money(totalAmount) }}</strong>
        </div>
        <div class="summary-row">
          <span>待检明细</span><strong>{{ pendingInspections }} 项</strong>
        </div>
        <div v-if="selectedOrders.length > 1" class="summary-tip">
          <el-icon><Warning /></el-icon
          ><span>已合并多个来源订单，订单号将在送货单中逐行显示。</span>
        </div>
        <div v-if="differentDueDates" class="summary-tip">
          <el-icon><Calendar /></el-icon
          ><span>来源订单交期不同，请核对客户要求后统一安排送货。</span>
        </div>
        <div class="summary-footnote">创建待发货单 → 完成 OQC → 确认发货 → 客户签收</div>
      </aside>
    </div>
    <template #footer
      ><div class="dialog-footer">
        <span>{{
          draft ? '演示单据，不会进入真实发货或库存流程。' : '创建待发货单后，再完成出货检验。'
        }}</span>
        <div>
          <el-button @click="visible = false">{{ draft ? '关闭' : '取消' }}</el-button
          ><el-button v-if="!draft" :disabled="!selected.length" @click="activeTab = 'paper'"
            >预览送货单</el-button
          ><el-button v-if="!draft" type="primary" :disabled="!canCreate" @click="createDraft"
            >创建待发货单（演示）</el-button
          >
        </div>
      </div></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Box,
  Calendar,
  Files,
  Location,
  Printer,
  Search,
  Van,
  Warning,
} from '@element-plus/icons-vue'
import { DeliveryStatusEnum } from '@/enums/sales/DeliveryEnum'
import { InspectionResult, InspectionResultEnum } from '@/enums/quality/InspectionEnum'
import {
  availableQuantity,
  mockCustomers,
  mockOrders,
  mockPendingStatus,
  type MockDeliveryLine,
  type MockDeliveryOrder,
  type MockMergedDelivery,
} from './mergeDeliveryMock'

const emit = defineEmits<{ created: [delivery: MockMergedDelivery] }>()
const visible = ref(false)
const draft = ref<MockMergedDelivery | null>(null)
const activeTab = ref('available')
const keyword = ref('')
const selected = ref<MockMergedDelivery['lines']>([])
const form = reactive({
  customerId: 1,
  addressId: 11,
  deliveryDate: '2026-10-08',
  remark: '以上货品有不符问题，请在10天内通知，方便我司处理。',
  showAmount: false,
  showWeight: true,
})
const customer = computed(
  () => mockCustomers.find((item) => item.id === form.customerId) || mockCustomers[0]!
)
const address = computed(() => customer.value.addresses.find((item) => item.id === form.addressId))
const customerOrders = computed(() =>
  mockOrders.filter((item) => item.customerId === form.customerId)
)
const filteredOrders = computed(() =>
  customerOrders.value
    .map((order) => ({
      ...order,
      lines: order.lines.filter((line) =>
        [line.orderNo, line.customerOrderNo, line.customerMaterialNo, line.productName]
          .join(' ')
          .toLowerCase()
          .includes(keyword.value.trim().toLowerCase())
      ),
    }))
    .filter((order) => order.lines.length)
)
const selectedOrders = computed(() =>
  mockOrders.filter((order) => selected.value.some((line) => line.orderId === order.id))
)
const totalQuantity = computed(() =>
  selected.value.reduce((sum, line) => sum + (line.sendQuantity || 0), 0)
)
const totalAmount = computed(() =>
  selected.value.reduce((sum, line) => sum + (line.sendQuantity || 0) * line.unitPrice, 0)
)
const pendingInspections = computed(
  () => selected.value.filter((line) => line.inspectionResult !== InspectionResult.PASS).length
)
const differentDueDates = computed(
  () => new Set(selectedOrders.value.map((order) => order.dueDate)).size > 1
)
const pages = computed(() => {
  const result: MockMergedDelivery['lines'][] = []
  for (let i = 0; i < selected.value.length; i += 6) result.push(selected.value.slice(i, i + 6))
  return result.length ? result : [[]]
})
const money = (value: number) =>
  value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const canSelectOrder = (order: MockDeliveryOrder) =>
  order.addressId === form.addressId && order.currency === 'CNY'
const isSelected = (id: number) => selected.value.some((line) => line.id === id)
const invalidQuantity = (line: MockMergedDelivery['lines'][number]) =>
  !Number.isInteger(line.sendQuantity) ||
  !line.sendQuantity ||
  line.sendQuantity < 1 ||
  line.sendQuantity > availableQuantity(line)
const canCreate = computed(
  () =>
    !!address.value &&
    !!form.deliveryDate &&
    selected.value.length > 0 &&
    selected.value.every((line) => !invalidQuantity(line))
)
const removeLine = (id: number) => {
  selected.value = selected.value.filter((line) => line.id !== id)
}
const addLine = (line: MockDeliveryLine) => {
  if (!isSelected(line.id) && availableQuantity(line) > 0)
    selected.value.push({ ...line, sendQuantity: availableQuantity(line) })
}
const toggleLine = (line: MockDeliveryLine) => {
  if (isSelected(line.id)) removeLine(line.id)
  else addLine(line)
}
const addOrder = (order: MockDeliveryOrder) => {
  if (canSelectOrder(order)) order.lines.forEach(addLine)
}
const allSelected = (order: MockDeliveryOrder) => order.lines.every((line) => isSelected(line.id))
const addAllEligible = () => filteredOrders.value.forEach(addOrder)
const changeCustomer = () => {
  form.addressId = customer.value.addresses[0]!.id
  keyword.value = ''
}
let draftSequence = 0
function createDraft() {
  if (!canCreate.value) return
  const created: MockMergedDelivery = {
    ...form,
    deliveryNo: `DEMO-DL-${String(++draftSequence).padStart(3, '0')}`,
    deliveryStatus: mockPendingStatus,
    lines: selected.value.map((line) => ({ ...line })),
  }
  draft.value = created
  emit('created', created)
  activeTab.value = 'selected'
  ElMessage.success('演示待发货单已创建，可查看明细和送货单预览')
}
function open(delivery?: MockMergedDelivery) {
  draft.value = delivery || null
  selected.value = delivery?.lines.map((line) => ({ ...line })) || []
  Object.assign(form, {
    customerId: delivery?.customerId ?? 1,
    addressId: delivery?.addressId ?? 11,
    deliveryDate: delivery?.deliveryDate ?? '2026-10-08',
    remark: delivery?.remark ?? '以上货品有不符问题，请在10天内通知，方便我司处理。',
    showAmount: delivery?.showAmount ?? false,
    showWeight: delivery?.showWeight ?? true,
  })
  keyword.value = ''
  activeTab.value = delivery ? 'selected' : 'available'
  visible.value = true
}
defineExpose({ open })
</script>

<style scoped>
.prototype-note {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #64748b;
  font-size: 12px;
  margin: -5px 0 18px;
}
.merge-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 224px;
  gap: 20px;
}
.merge-main {
  min-width: 0;
}
.recipient-panel {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  background: #f8fafc;
}
.section-title,
.summary-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-weight: 600;
  color: #334155;
}
.section-title > span {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: #64748b;
}
.recipient-form {
  display: grid;
  grid-template-columns: 1.4fr 1fr 160px;
  gap: 16px;
  margin-top: 14px;
}
.recipient-form :deep(.el-form-item) {
  margin-bottom: 10px;
}
.recipient-form :deep(.el-select),
.recipient-form :deep(.el-date-editor) {
  width: 100%;
}
.recipient-line {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: #64748b;
  flex-wrap: wrap;
}
.scope-hint {
  font-size: 12px;
  color: #64748b;
  margin-top: 8px;
}
.merge-tabs {
  margin-top: 12px;
}
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tab-count {
  background: #eff6ff;
  color: #2563eb;
  border-radius: 10px;
  font-size: 11px;
  padding: 0 7px;
  line-height: 18px;
}
.picker-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.picker-toolbar .el-input {
  max-width: 440px;
}
.order-scroll {
  max-height: 395px;
  overflow-y: auto;
  padding-right: 4px;
}
.order-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 12px;
}
.order-heading {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  background: #f8fafc;
}
.order-heading > div {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.order-heading strong {
  color: #334155;
  font-size: 13px;
}
.order-heading span,
.secondary {
  font-size: 12px;
  color: #64748b;
}
.unavailable {
  background: #fafafa;
}
.line-code {
  color: #334155;
  font-size: 13px;
  line-height: 23px;
}
.available-qty {
  color: #2563eb;
}
.selected-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  color: #64748b;
  font-size: 12px;
}
.selected-table :deep(.el-input-number .el-input__inner) {
  text-align: right;
}
.field-error {
  font-size: 11px;
  color: #dc2626;
  line-height: 18px;
}
.invalid-quantity :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #dc2626 inset;
}
.settings-form {
  margin-top: 16px;
}
.merge-summary {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 18px;
  align-self: start;
}
.summary-title {
  margin-bottom: 18px;
}
.summary-number {
  font-size: 32px;
  font-weight: 650;
  color: #1e40af;
  line-height: 1.3;
  margin-top: 12px;
}
.summary-number span {
  font-size: 12px;
  font-weight: 400;
  margin-left: 6px;
  color: #64748b;
}
.summary-stats {
  display: flex;
  gap: 24px;
  margin-top: 14px;
  font-size: 12px;
  color: #64748b;
}
.summary-stats strong {
  font-size: 18px;
  color: #334155;
  margin-right: 5px;
}
.summary-divider {
  height: 1px;
  background: #e2e8f0;
  margin: 20px 0;
}
.summary-row {
  display: flex;
  justify-content: space-between;
  margin: 12px 0;
  font-size: 12px;
  color: #64748b;
}
.summary-row strong {
  color: #334155;
}
.summary-tip {
  display: flex;
  gap: 6px;
  padding: 10px;
  background: #fffbeb;
  color: #92400e;
  font-size: 12px;
  line-height: 1.6;
  border-radius: 6px;
  margin-top: 14px;
}
.summary-tip .el-icon {
  margin-top: 3px;
  flex-shrink: 0;
}
.summary-footnote {
  font-size: 12px;
  color: #64748b;
  line-height: 1.8;
  margin-top: 20px;
}
.paper-scroll {
  max-height: 430px;
  overflow: auto;
  background: #eef2f6;
  padding: 16px;
}
.delivery-paper {
  background: white;
  color: #222;
  padding: 24px 22px;
  min-width: 760px;
  margin-bottom: 16px;
  font-family: 'SimSun', 'Songti SC', serif;
}
.delivery-paper h3 {
  text-align: center;
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 5px;
}
.paper-company {
  text-align: center;
  font-size: 11px;
}
.delivery-paper h2 {
  text-align: center;
  font-size: 22px;
  margin: 14px 0;
}
.paper-meta {
  display: flex;
  justify-content: space-between;
  line-height: 1.7;
  font-size: 12px;
  margin-bottom: 10px;
  gap: 12px;
}
.delivery-paper table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.delivery-paper th,
.delivery-paper td {
  border: 1px solid #555;
  padding: 9px 5px;
  text-align: center;
}
.delivery-paper th {
  font-weight: 600;
  white-space: nowrap;
}
.paper-empty {
  height: 70px;
  color: #888;
}
.paper-remark {
  border: 1px solid #555;
  border-top: 0;
  margin: 0;
  padding: 9px;
  font-size: 12px;
}
.paper-signatures {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-top: 18px;
}
.paper-page {
  text-align: right;
  font-size: 11px;
  color: #666;
  margin-top: 16px;
}
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.dialog-footer > span {
  color: #64748b;
  font-size: 12px;
}
@media (max-width: 1050px) {
  .merge-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .recipient-form {
    grid-template-columns: 1fr 1fr;
  }
  .recipient-form :deep(.el-form-item:last-child) {
    grid-column: span 2;
  }
}
@media (max-width: 640px) {
  .recipient-form {
    grid-template-columns: 1fr;
  }
  .recipient-form :deep(.el-form-item:last-child) {
    grid-column: auto;
  }
  .section-title > span {
    display: none;
  }
  .dialog-footer {
    flex-wrap: wrap;
  }
  .order-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

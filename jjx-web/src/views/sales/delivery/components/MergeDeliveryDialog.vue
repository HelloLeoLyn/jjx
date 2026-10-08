<template>
  <el-dialog v-model="visible" title="新建合并发货" width="min(1320px, 96vw)" top="4vh" append-to-body :close-on-click-modal="false" :close-on-press-escape="!submitting" :show-close="!submitting" :before-close="beforeClose" class="merge-delivery-dialog">
    <div class="workflow-note"><el-tag type="info" effect="plain" size="small">本次发货</el-tag><span>建单后完成出货检验，再安排仓库出库。</span></div>
    <div class="merge-layout">
      <div class="merge-main">
        <section class="recipient-panel">
          <div class="section-title"><el-icon><Location /></el-icon>收货信息<span>同一客户 · 同一地址 · 同一币种</span></div>
          <el-form :disabled="submitting" label-position="top" class="recipient-form">
            <el-form-item label="客户" required><el-input :model-value="first?.customerName" readonly /></el-form-item>
            <el-form-item label="收货地址" required><el-input v-model="form.deliveryAddress" :readonly="orderCount > 1 && lines.some(line => !!line.deliveryAddress?.trim())" maxlength="500" placeholder="填写本次统一收货地址" /></el-form-item>
            <el-form-item label="送货日期" required><el-date-picker v-model="form.deliveryDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          </el-form>
          <div class="recipient-line"><span>{{ form.deliveryAddress || '请填写本次收货地址' }}</span><span v-if="form.contactPerson || form.contactPhone">{{ form.contactPerson }} · {{ form.contactPhone }}</span></div>
        </section>
        <el-tabs v-model="activeTab" class="merge-tabs">
          <el-tab-pane name="lines">
            <template #label><span class="tab-label"><el-icon><Box /></el-icon>本次发货<span class="tab-count">{{ lines.length }}</span></span></template>
            <div class="selected-toolbar"><span>同一产品来自不同订单时分行展示，分别计算发货数量。</span><el-button type="danger" link :disabled="submitting || !lines.length" @click="lines = []">清空明细</el-button></div>
            <el-alert v-if="quantityError" :title="quantityError" type="warning" :closable="false" style="margin-bottom:12px" />
            <el-table :data="lines" border max-height="330" class="selected-table">
              <el-table-column label="来源订单" min-width="145"><template #default="{ row }"><div class="line-code">{{ row.orderNo }}</div><div v-if="row.customerOrderNo" class="secondary">{{ row.customerOrderNo }}</div></template></el-table-column>
              <el-table-column label="客户料号 / 品名" min-width="210"><template #default="{ row }"><div class="line-code">{{ row.customerMaterialNo || row.productName }}</div><div v-if="row.customerMaterialNo && row.customerMaterialNo !== row.productName" class="secondary">{{ row.productName }}</div><div v-if="row.specification" class="secondary">{{ row.specification }}</div></template></el-table-column>
              <el-table-column label="可建单" width="90" align="right"><template #default="{ row }"><el-tooltip :content="`订单待安排 ${row.orderRemainingQuantity}，成品可用 ${row.stockAvailable}`"><strong class="available-qty">{{ row.availableQuantity }}</strong></el-tooltip></template></el-table-column>
              <el-table-column label="本次发货" width="175"><template #default="{ row }"><el-input-number v-model="row.sendQuantity" :disabled="submitting" :min="0" :max="row.availableQuantity" :precision="0" controls-position="right" :class="{ 'invalid-quantity': invalidQuantity(row) }" style="width:140px" /><div v-if="invalidQuantity(row)" class="field-error">请输入 1～{{ row.availableQuantity }} 的整数</div></template></el-table-column>
              <el-table-column label="操作" width="68"><template #default="{ row }"><el-button type="danger" link :disabled="submitting" @click="lines = lines.filter(item => item.id !== row.id)">移除</el-button></template></el-table-column>
            </el-table>
            <el-form :disabled="submitting" label-position="top" class="settings-form">
              <div class="delivery-settings">
                <el-form-item label="交货方式（本次统一）" required><el-select v-model="form.deliveryMethod"><el-option v-for="option in deliveryMethods" :key="option.itemValue" :label="option.label || option.itemKey" :value="option.itemValue" /></el-select></el-form-item>
                <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
                <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
              </div>
              <el-form-item label="送货单备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
              <el-form-item label="打印内容"><el-checkbox v-model="showAmount">显示金额</el-checkbox><el-checkbox v-model="showWeight">显示单重（g）</el-checkbox></el-form-item>
              <div v-if="form.deliveryMethod && needsCarrier" class="carrier-panel">
                <div class="section-title"><el-icon><Van /></el-icon>物流信息</div>
                <div class="delivery-settings">
                  <el-form-item label="承运商"><el-input v-model="form.carrier" /></el-form-item><el-form-item label="物流单号"><el-input v-model="form.trackingNo" /></el-form-item>
                  <el-form-item label="运费"><el-input-number v-model="form.freightAmount" :min="0" :precision="2" /></el-form-item><el-form-item label="保价费"><el-input-number v-model="form.insuranceAmount" :min="0" :precision="2" /></el-form-item><el-form-item label="其他费用"><el-input-number v-model="form.otherCharges" :min="0" :precision="2" /></el-form-item>
                </div>
              </div>
            </el-form>
          </el-tab-pane>
          <el-tab-pane name="paper">
            <template #label><span class="tab-label"><el-icon><Printer /></el-icon>送货单预览</span></template>
            <div class="paper-scroll"><Qr026DeliverySheet :info="preview" :items="preview.items || []" :company="company" :show-amount="showAmount" :show-weight="showWeight" preview /></div>
          </el-tab-pane>
        </el-tabs>
      </div>
      <aside class="merge-summary">
        <div class="summary-title"><el-icon><Van /></el-icon>本次发货概览</div>
        <div class="summary-number">{{ totalQuantity.toLocaleString() }}<span>{{ summaryUnit }}</span></div>
        <div class="summary-stats"><div><strong>{{ orderCount }}</strong>张订单</div><div><strong>{{ lines.length }}</strong>项明细</div></div>
        <div class="summary-divider" />
        <div class="summary-row"><span>交货方式</span><strong>{{ deliveryMethodLabel || '待选择' }}</strong></div>
        <div class="summary-row"><span>币种</span><strong>{{ first?.currency || '—' }}</strong></div>
        <div class="summary-row"><span>货品金额</span><strong>{{ first?.currency }} {{ totalAmount.toLocaleString('zh-CN', { minimumFractionDigits:2, maximumFractionDigits:2 }) }}</strong></div>
        <div class="summary-row"><span>本次需检</span><strong>{{ lines.length }} 项</strong></div>
        <div v-if="orderCount > 1" class="summary-tip"><el-icon><Warning /></el-icon><span>已合并多个来源订单，销售单号将在送货单中逐行显示。</span></div>
        <div v-if="differentDueDates" class="summary-tip"><el-icon><Calendar /></el-icon><span>来源订单交期不同，请核对客户要求后统一安排送货。</span></div>
        <div class="summary-footnote">创建待发货单 → 完成本次 OQC → 安排出库 → 仓库确认 → 客户签收</div>
      </aside>
    </div>
    <template #footer><div class="dialog-footer"><span>创建待发货单后，再完成本次出货检验。</span><div><el-button :disabled="submitting" @click="visible = false">取消</el-button><el-button :disabled="!lines.length || submitting" @click="activeTab = 'paper'">预览送货单</el-button><el-button type="primary" :loading="submitting" :disabled="!canCreate" @click="submit">创建待发货单</el-button></div></div></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { deliveryApi, type DeliveryArrangeLine, type SalesDeliveryCreateDTO } from '@/api/sales/delivery'
import { Box, Calendar, Location, Printer, Van, Warning } from '@element-plus/icons-vue'
import { useDeliveryPrintOptions } from './useDeliveryPrintOptions'
import { useDict } from '@/composables/useDict'
import { useCompanyConfig } from '@/composables/useCompanyConfig'
import Qr026DeliverySheet from '../Qr026DeliverySheet.vue'
import { defaultDeliveryQuantities, deliveryQuantityError } from './mergeDeliveryQuantity'
const emit = defineEmits<{ created: [deliveryId: number] }>()
type SelectedLine = DeliveryArrangeLine & { sendQuantity: number | undefined }
const visible = ref(false)
const submitting = ref(false)
const lines = ref<SelectedLine[]>([])
const activeTab = ref('lines')
const first = computed(() => lines.value[0])
const form = reactive<SalesDeliveryCreateDTO>({})
const { options: deliveryMethods } = useDict('sales_delivery_method')
const { company } = useCompanyConfig()
const { showAmount, showWeight } = useDeliveryPrintOptions()
const deliveryMethodLabel = computed(() => {
  const option = deliveryMethods.value.find(item => item.itemValue === form.deliveryMethod)
  return option?.label || option?.itemKey || form.deliveryMethod
})
const summaryUnit = computed(() => {
  const units = [...new Set(lines.value.map(line => line.unit).filter(Boolean))]
  return units.length === 1 ? units[0] : '件'
})
const needsCarrier = computed(() => {
  const option = deliveryMethods.value.find(item => item.itemValue === form.deliveryMethod)
  try { const ext = typeof option?.extData === 'string' ? JSON.parse(option.extData) : option?.extData; return ext?.needCarrier !== false } catch { return true }
})
const orderCount = computed(() => new Set(lines.value.map(line => line.orderId)).size)
const differentDueDates = computed(() => new Set(lines.value.map(line => line.dueDate)).size > 1)
const totalQuantity = computed(() => lines.value.reduce((sum, line) => sum + (line.sendQuantity || 0), 0))
const totalAmount = computed(() => lines.value.reduce((sum, line) => sum + (line.sendQuantity || 0) * (line.unitPrice || 0), 0))
const invalidQuantity = (line: SelectedLine) => !Number.isInteger(line.sendQuantity) || !line.sendQuantity || line.sendQuantity < 1 || line.sendQuantity > line.availableQuantity
const quantityError = computed(() => deliveryQuantityError(lines.value))
const canCreate = computed(() => !quantityError.value && !!form.deliveryAddress?.trim() && !submitting.value && !!form.deliveryMethod && !!form.deliveryDate && lines.value.length > 0 && lines.value.every(line => !invalidQuantity(line)))
const preview = computed(() => ({ ...form, deliveryNo: '创建后生成', customerName: first.value?.customerName || '', totalQuantity: totalQuantity.value, totalAmount: totalAmount.value, items: lines.value.map(line => ({ ...line, orderProductId: line.id, quantity: line.sendQuantity, amount: (line.sendQuantity || 0) * (line.unitPrice || 0) })) }))
function open(selected: DeliveryArrangeLine[]) {
  if (!selected.length || submitting.value) return
  const quantities = defaultDeliveryQuantities(selected)
  lines.value = selected.map((line, index) => ({ ...line, sendQuantity: quantities[index] }))
  Object.assign(form, { deliveryAddress: selected[0]!.deliveryAddress, contactPerson: selected[0]!.contactPerson || '', contactPhone: selected[0]!.contactPhone || '', deliveryDate: dayjs().format('YYYY-MM-DD'), deliveryMethod: '', remark: '', carrier: '', trackingNo: '', freightAmount: 0, insuranceAmount: 0, otherCharges: 0 })
  activeTab.value = 'lines'; visible.value = true
}
function beforeClose(done: () => void) { if (!submitting.value) done() }
async function submit() {
  if (!canCreate.value) return
  submitting.value = true
  try {
    const result = await deliveryApi.create({ ...form,
      carrier: needsCarrier.value ? form.carrier : '', trackingNo: needsCarrier.value ? form.trackingNo : '',
      freightAmount: needsCarrier.value ? form.freightAmount : 0, insuranceAmount: needsCarrier.value ? form.insuranceAmount : 0, otherCharges: needsCarrier.value ? form.otherCharges : 0,
      items: lines.value.map(line => ({ orderProductId: line.id, quantity: line.sendQuantity })),
    })
    if (!result.data) throw new Error('未收到发货单ID，请查询发货记录确认结果')
    visible.value = false; emit('created', result.data); ElMessage.success('待发货单已创建，已生成本次OQC检验批')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建失败，请刷新订单数量后重试')
  } finally { submitting.value = false }
}
defineExpose({ open })
</script>
<style scoped>
.workflow-note { display:flex; align-items:center; gap:10px; color:#64748b; font-size:12px; margin:-5px 0 18px; }
.merge-layout { display:grid; grid-template-columns:minmax(0,1fr) 224px; gap:20px; }
.merge-main { min-width:0; }
.recipient-panel { border:1px solid #e2e8f0; border-radius:8px; padding:16px; background:#f8fafc; }
.section-title,.summary-title { display:flex; align-items:center; gap:7px; font-weight:600; color:#334155; }
.section-title > span { margin-left:auto; font-size:12px; font-weight:400; color:#64748b; }
.recipient-form { display:grid; grid-template-columns:1.4fr 1fr 160px; gap:16px; margin-top:14px; }
.recipient-form :deep(.el-form-item) { margin-bottom:10px; }
.recipient-form :deep(.el-select),.recipient-form :deep(.el-date-editor),.delivery-settings :deep(.el-select) { width:100%; }
.recipient-line { display:flex; gap:20px; font-size:12px; color:#64748b; flex-wrap:wrap; }
.merge-tabs { margin-top:12px; }
.tab-label { display:inline-flex; align-items:center; gap:6px; }
.tab-count { background:#eff6ff; color:#2563eb; border-radius:10px; font-size:11px; padding:0 7px; line-height:18px; }
.line-code { color:#334155; font-size:13px; line-height:23px; }
.secondary { font-size:12px; color:#64748b; }
.available-qty { color:#2563eb; }
.selected-toolbar { display:flex; justify-content:space-between; gap:12px; margin-bottom:10px; color:#64748b; font-size:12px; }
.selected-table :deep(.el-input-number .el-input__inner) { text-align:right; }
.field-error { font-size:11px; color:#dc2626; line-height:18px; }
.invalid-quantity :deep(.el-input__wrapper) { box-shadow:0 0 0 1px #dc2626 inset; }
.settings-form { margin-top:16px; }
.delivery-settings { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:0 16px; }
.carrier-panel { border-top:1px solid #e2e8f0; padding-top:16px; }
.carrier-panel .section-title { margin-bottom:14px; }
.merge-summary { background:#f8fafc; border:1px solid #e2e8f0; border-radius:8px; padding:18px; align-self:start; }
.summary-title { margin-bottom:18px; }
.summary-number { font-size:32px; font-weight:650; color:#1e40af; line-height:1.3; margin-top:12px; }
.summary-number span { font-size:12px; font-weight:400; margin-left:6px; color:#64748b; }
.summary-stats { display:flex; gap:24px; margin-top:14px; font-size:12px; color:#64748b; }
.summary-stats strong { font-size:18px; color:#334155; margin-right:5px; }
.summary-divider { height:1px; background:#e2e8f0; margin:20px 0; }
.summary-row { display:flex; justify-content:space-between; gap:8px; margin:12px 0; font-size:12px; color:#64748b; }
.summary-row strong { color:#334155; text-align:right; }
.summary-tip { display:flex; gap:6px; padding:10px; background:#fffbeb; color:#92400e; font-size:12px; line-height:1.6; border-radius:6px; margin-top:14px; }
.summary-tip .el-icon { margin-top:3px; flex-shrink:0; }
.summary-footnote { font-size:12px; color:#64748b; line-height:1.8; margin-top:20px; }
.paper-scroll { max-height:430px; overflow:auto; background:#eef2f6; padding:16px; }
.dialog-footer { display:flex; justify-content:space-between; align-items:center; gap:12px; }
.dialog-footer > span { color:#64748b; font-size:12px; }
@media(max-width:1050px) { .merge-layout { grid-template-columns:minmax(0,1fr); }.recipient-form { grid-template-columns:1fr 1fr; }.recipient-form :deep(.el-form-item:last-child) { grid-column:span 2; } }
@media(max-width:640px) { .recipient-form,.delivery-settings { grid-template-columns:1fr; }.recipient-form :deep(.el-form-item:last-child) { grid-column:auto; }.section-title > span { display:none; }.dialog-footer { flex-wrap:wrap; }.selected-toolbar { align-items:flex-start; flex-direction:column; } }
</style>

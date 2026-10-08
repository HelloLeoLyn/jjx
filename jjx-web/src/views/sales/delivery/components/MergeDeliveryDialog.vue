<template>
  <el-dialog v-model="visible" title="新建合并发货" width="min(1320px, 96vw)" top="4vh" append-to-body :close-on-click-modal="false" :close-on-press-escape="!submitting" :show-close="!submitting" :before-close="beforeClose">
    <el-alert title="创建待发货单后自动生成本次OQC；OQC放行后安排出库，仓库确认后才记为已发货。" type="info" :closable="false" show-icon />
    <div class="merge-layout">
      <div class="merge-main">
        <el-form :disabled="submitting" label-position="top" class="recipient-form">
          <el-form-item label="客户"><el-input :model-value="first?.customerName" readonly /></el-form-item>
          <el-form-item label="收货地址" required><el-input v-model="form.deliveryAddress" :readonly="orderCount > 1 && lines.some(line => !!line.deliveryAddress?.trim())" maxlength="500" placeholder="填写本次统一收货地址" /></el-form-item>
          <el-form-item label="安排日期" required><el-date-picker v-model="form.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          <el-form-item label="交货方式（本次统一）" required><el-select v-model="form.deliveryMethod"><el-option v-for="option in deliveryMethods" :key="option.itemValue" :label="option.label || option.itemKey" :value="option.itemValue" /></el-select></el-form-item>
          <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        </el-form>
        <el-alert v-if="differentDueDates" title="来源订单交期不同，请核对客户要求后统一安排送货日期。" type="warning" :closable="false" style="margin-bottom: 12px" />
        <el-alert v-if="quantityError" :title="quantityError" type="warning" :closable="false" style="margin-bottom: 12px" />
        <el-tabs v-model="activeTab">
          <el-tab-pane label="本次发货" name="lines">
            <el-table :data="lines" border max-height="360" class="selected-table">
              <el-table-column prop="orderNo" label="来源销售订单" min-width="150" />
              <el-table-column label="客户料号 / 品名" min-width="220"><template #default="{ row }">{{ row.customerMaterialNo || row.productName }}<div class="secondary">{{ row.productName }}</div></template></el-table-column>
              <el-table-column prop="availableQuantity" label="本次可建单" width="100" align="right" />
              <el-table-column prop="stockAvailable" label="成品可用量" width="110" align="right" />
              <el-table-column label="本次数量" width="180"><template #default="{ row }"><el-input-number v-model="row.sendQuantity" :disabled="submitting" :min="0" :max="row.availableQuantity" :precision="0" controls-position="right" style="width: 140px" /><div v-if="invalidQuantity(row)" class="field-error">请输入1～{{ row.availableQuantity }}的整数</div></template></el-table-column>
              <el-table-column label="操作" width="75"><template #default="{ row }"><el-button type="danger" link :disabled="submitting" @click="lines = lines.filter(item => item.id !== row.id)">移除</el-button></template></el-table-column>
            </el-table>
            <el-form :disabled="submitting" label-position="top" class="settings-form">
              <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
              <div v-if="needsCarrier" class="recipient-form">
                <el-form-item label="承运商"><el-input v-model="form.carrier" /></el-form-item><el-form-item label="物流单号"><el-input v-model="form.trackingNo" /></el-form-item>
                <el-form-item label="运费"><el-input-number v-model="form.freightAmount" :min="0" :precision="2" /></el-form-item><el-form-item label="保价费"><el-input-number v-model="form.insuranceAmount" :min="0" :precision="2" /></el-form-item><el-form-item label="其他费用"><el-input-number v-model="form.otherCharges" :min="0" :precision="2" /></el-form-item>
              </div>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="送货单预览" name="paper"><div class="paper-preview"><Qr026DeliverySheet :info="preview" :items="preview.items || []" :order-no="''" :company="company" /></div></el-tab-pane>
        </el-tabs>
      </div>
      <aside class="merge-summary"><strong>本次发货概览</strong><div class="summary-number">{{ totalQuantity.toLocaleString() }} PCS</div><p>{{ orderCount }} 张订单 · {{ lines.length }} 条明细</p><el-divider /><p>币种：{{ first?.currency }}</p><p>货品金额：{{ totalAmount.toFixed(2) }}</p><p>创建后每条明细均需完成本次OQC放行。</p><p class="secondary">本次最多安排已有可用成品；同产品明细合计校验，仓库实际出库时再次核对。</p></aside>
    </div>
    <template #footer><el-button :disabled="submitting" @click="visible = false">取消</el-button><el-button :disabled="!lines.length || submitting" @click="activeTab = 'paper'">预览送货单</el-button><el-button type="primary" :loading="submitting" :disabled="!canCreate" @click="submit">创建待发货单</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { deliveryApi, type DeliveryArrangeLine, type SalesDeliveryCreateDTO, type SalesDeliveryVO } from '@/api/sales/delivery'
import { DeliveryStatusEnum } from '@/enums/sales/DeliveryEnum'
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
const preview = computed(() => ({ ...form, deliveryNo: '待生成', customerName: first.value?.customerName || '', totalQuantity: totalQuantity.value, totalAmount: totalAmount.value, items: lines.value.map(line => ({ ...line, orderProductId: line.id, quantity: line.sendQuantity, amount: (line.sendQuantity || 0) * (line.unitPrice || 0) })) }))
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
.merge-layout { display:grid; grid-template-columns:minmax(0,1fr) 224px; gap:20px; margin-top:16px; }.merge-main { min-width:0; }
.recipient-form { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:0 16px; }.recipient-form :deep(.el-select) { width:100%; }
.settings-form { margin-top:14px; }.merge-summary { background:#f8fafc; border:1px solid #e2e8f0; padding:16px; border-radius:8px; align-self:start; }
.summary-number { font-size:28px; font-weight:700; color:#2563eb; margin-top:20px; }.merge-summary p,.secondary { color:#64748b; font-size:12px; line-height:1.7; }.field-error { color:#dc2626; font-size:12px; margin-top:4px; }
.paper-preview { overflow:auto; background:#e2e8f0; padding:12px; }
@media(max-width:900px) { .merge-layout { grid-template-columns:1fr; }.recipient-form { grid-template-columns:1fr; } }
</style>

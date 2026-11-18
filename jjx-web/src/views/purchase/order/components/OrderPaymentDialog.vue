<template>
  <el-dialog
    :title="title"
    :model-value="props.visible"
    width="1200px"
    destroy-on-close
    append-to-body
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <el-alert
      title="提交后生成正式付款单，需在付款管理中审批并确认付款；订单已付金额将自动汇总。"
      type="info"
      :closable="false"
      show-icon
    />
    <PaymentForm
      v-if="props.visible"
      ref="formRef"
      :model-value="form"
      :order-no="props.orderNo"
      :disabled="submitting"
      @summary-loaded="handleSummaryLoaded"
    />

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取 消</el-button>
        <el-button
          type="primary"
          @click="handleSubmit"
          :loading="submitting"
          :disabled="!paymentSummary || Number(paymentSummary.availableAmount) <= 0"
          >提交申请</el-button
        >
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { PaymentMethodEnum, PaymentStatusEnum } from '@/enums/purchase'
import { addPayment, generatePaymentNo } from '@/api/purchase/payment'
import type { PurchasePayment } from '@/types/purchase'
import type { PurchasePaymentSummary } from '@/api/purchase/payment'
import PaymentForm from '@/views/purchase/components/PaymentForm.vue'

const props = defineProps<{
  visible: boolean
  orderId?: number
  orderNo: string
  orderTotalAmount: number
  paidAmount: number
  currency: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<InstanceType<typeof PaymentForm>>()
const submitting = ref(false)
const paymentSummary = ref<PurchasePaymentSummary | null>(null)

function handleSummaryLoaded(summary: PurchasePaymentSummary | null) {
  paymentSummary.value = summary
  if (summary) form.paymentAmount = Number(summary.availableAmount)
}

const form = reactive({
  orderId: props.orderId,
  paymentNo: '',
  paymentDate: '',
  paymentAmount: 0,
  paymentMethod: PaymentMethodEnum.items[0].value,
  bankAccount: '',
  remark: '',
})

const title = computed(() => `申请付款 - ${props.orderNo}`)

// 打开申请时初始化日期与单号，金额由共享额度组件加载。
let numberSequence = 0
watch(
  () => [props.visible, props.orderId] as const,
  async ([val]) => {
    const sequence = ++numberSequence
    if (val) {
      form.orderId = props.orderId
      form.paymentNo = ''
      form.paymentMethod = PaymentMethodEnum.items[0].value
      form.bankAccount = ''
      form.remark = ''
      form.paymentDate = today()
      paymentSummary.value = null
      form.paymentAmount = 0
      try {
        const numberResult = await generatePaymentNo()
        if (sequence === numberSequence) form.paymentNo = numberResult.data || ''
      } catch {
        if (sequence === numberSequence) ElMessage.error('付款单号生成失败，请重试或手动填写')
      }
    }
  },
  { immediate: true }
)

const handleSubmit = async () => {
  if (!formRef.value || !paymentSummary.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (!props.orderId) return
    const payment: PurchasePayment = {
      paymentNo: form.paymentNo,
      orderId: Number(props.orderId),
      paymentDate: form.paymentDate,
      paymentAmount: form.paymentAmount,
      paymentMethod: form.paymentMethod,
      bankAccount: form.bankAccount || undefined,
      paymentStatus: PaymentStatusEnum.PENDING.value,
      remark: form.remark || undefined,
    }
    await addPayment(payment)
    ElMessage.success('付款申请已创建，请到采购付款中审批并确认付款')

    emit('success')
    handleClose()
  } catch (error) {
    console.error('创建付款单失败:', error)
    ElMessage.error(error instanceof Error ? error.message : '创建付款单失败')
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  form.paymentNo = ''
  form.paymentDate = ''
  form.paymentAmount = 0
  form.paymentMethod = PaymentMethodEnum.items[0]?.value || 'bank'
  form.bankAccount = ''
  form.remark = ''
  emit('update:visible', false)
}

function today() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}
</script>

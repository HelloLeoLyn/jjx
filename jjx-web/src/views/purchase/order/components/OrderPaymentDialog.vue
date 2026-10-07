<template>
  <el-dialog
    :title="title"
    :model-value="props.visible"
    width="1200px"
    append-to-body
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="订单号">
        <el-input :model-value="orderNo" disabled />
      </el-form-item>
      <PaymentAmountSummary
        v-if="props.visible"
        :order-id="props.orderId"
        @loaded="handleSummaryLoaded"
      />
      <el-alert
        title="提交后生成正式付款单，需在付款管理中审批并确认付款；订单已付金额将自动汇总。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form-item label="付款单号" prop="paymentNo">
        <el-input v-model="form.paymentNo" disabled />
      </el-form-item>
      <el-form-item label="计划付款日" prop="paymentDate">
        <el-date-picker
          v-model="form.paymentDate"
          type="date"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="申请金额" prop="paymentAmount">
        <el-input-number
          v-model="form.paymentAmount"
          :min="Number(paymentSummary?.availableAmount || 0) > 0 ? 0.01 : 0"
          :max="paymentSummary ? Number(paymentSummary.availableAmount) : undefined"
          :disabled="!paymentSummary || Number(paymentSummary.availableAmount) <= 0"
          :precision="2"
          :step="100"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="付款方式" prop="paymentMethod">
        <el-select v-model="form.paymentMethod" placeholder="请选择" style="width: 100%">
          <el-option
            v-for="dict in PaymentMethodEnum.items"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="银行账户">
        <el-input v-model="form.bankAccount" maxlength="100" />
      </el-form-item>
      <el-form-item label="付款备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入付款备注"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取 消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting" :disabled="!paymentSummary || Number(paymentSummary.availableAmount) <= 0">提交申请</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { PaymentMethodEnum, PaymentStatusEnum } from '@/enums/purchase'
import { addPayment, generatePaymentNo } from '@/api/purchase/payment'
import type { PurchasePayment } from '@/types/purchase'
import type { PurchasePaymentSummary } from '@/api/purchase/payment'
import PaymentAmountSummary from '@/views/purchase/components/PaymentAmountSummary.vue'

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

const formRef = ref<FormInstance>()
const submitting = ref(false)
const paymentSummary = ref<PurchasePaymentSummary | null>(null)

function handleSummaryLoaded(summary: PurchasePaymentSummary | null) {
  paymentSummary.value = summary
  if (summary) form.paymentAmount = Number(summary.availableAmount)
}

const form = reactive({
  paymentNo: '',
  paymentDate: '',
  paymentAmount: 0,
  paymentMethod: 'bank',
  bankAccount: '',
  remark: '',
})

const title = computed(() => `申请付款 - ${props.orderNo}`)

const rules = reactive<FormRules>({
  paymentNo: [{ required: true, message: '付款单号生成失败，请关闭后重试', trigger: 'blur' }],
  paymentDate: [{ required: true, message: '请选择计划付款日期', trigger: 'change' }],
  paymentAmount: [{ required: true, message: '请输入付款金额', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: '请选择付款方式', trigger: 'change' }],
})

// 打开申请时初始化日期与单号，金额由共享额度组件加载。
watch(
  () => props.visible,
  async (val) => {
    if (val) {
      form.paymentDate = today()
      paymentSummary.value = null
      form.paymentAmount = 0
      const numberResult = await generatePaymentNo()
      form.paymentNo = numberResult.data || ''
    }
  }
)

const handleSubmit = async () => {
  if (!formRef.value || !paymentSummary.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (!props.orderId) return
    if (form.paymentAmount <= 0 || form.paymentAmount > Number(paymentSummary.value.availableAmount)) {
      ElMessage.warning('申请金额必须大于0且不超过可申请金额')
      return
    }
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

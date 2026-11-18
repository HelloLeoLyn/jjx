<template>
  <el-form
    ref="formRef"
    :model="modelValue"
    :rules="rules"
    label-width="100px"
    :disabled="disabled"
  >
    <el-form-item label="采购订单" prop="orderId">
      <el-input v-if="orderNo" :model-value="orderNo" disabled />
      <el-select
        v-else
        v-model="modelValue.orderId"
        filterable
        :disabled="editing"
        placeholder="选择采购订单"
        style="width: 100%"
      >
        <el-option
          v-for="order in orders"
          :key="order.orderId"
          :label="`${order.orderNo} - ${order.supplierName}`"
          :value="order.orderId"
        />
      </el-select>
    </el-form-item>
    <PaymentAmountSummary
      :order-id="modelValue.orderId"
      :excluded-payment-id="editing ? modelValue.paymentId : undefined"
      @loaded="onSummary"
    />
    <el-form-item label="付款单号" prop="paymentNo">
      <el-input
        v-model="modelValue.paymentNo"
        :disabled="editing"
        maxlength="50"
        placeholder="付款单号"
      />
    </el-form-item>
    <el-form-item label="计划付款日" prop="paymentDate">
      <el-date-picker
        v-model="modelValue.paymentDate"
        type="date"
        value-format="YYYY-MM-DD"
        style="width: 100%"
      />
    </el-form-item>
    <el-form-item label="申请金额" prop="paymentAmount">
      <el-input-number
        v-model="modelValue.paymentAmount"
        :min="0.01"
        :max="summary ? Number(summary.availableAmount) : undefined"
        :disabled="!summary || Number(summary.availableAmount) <= 0"
        :precision="2"
        :controls="false"
        style="width: 100%"
      />
    </el-form-item>
    <el-form-item label="付款方式" prop="paymentMethod">
      <el-select v-model="modelValue.paymentMethod" style="width: 100%">
        <el-option
          v-for="item in PaymentMethodEnum.items"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="银行账号" prop="bankAccount"
      ><el-input v-model="modelValue.bankAccount" maxlength="100"
    /></el-form-item>
    <el-form-item label="付款备注" prop="remark"
      ><el-input
        v-model="modelValue.remark"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
    /></el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { PaymentMethodEnum } from '@/enums/purchase/payment'
import type { PurchasePaymentSummary } from '@/api/purchase/payment'
import PaymentAmountSummary from './PaymentAmountSummary.vue'

const props = defineProps<{
  modelValue: {
    paymentId?: number
    orderId?: number
    paymentNo: string
    paymentDate: string
    paymentAmount?: number
    paymentMethod: string
    bankAccount?: string
    remark?: string
  }
  orders?: { orderId: number; orderNo: string; supplierName: string }[]
  orderNo?: string
  editing?: boolean
  disabled?: boolean
}>()
const emit = defineEmits<{ (e: 'summary-loaded', value: PurchasePaymentSummary | null): void }>()
const formRef = ref<FormInstance>()
const summary = ref<PurchasePaymentSummary | null>(null)
function onSummary(value: PurchasePaymentSummary | null) {
  summary.value = value
  emit('summary-loaded', value)
}
const rules: FormRules = {
  orderId: [{ required: true, message: '请选择采购订单', trigger: 'change' }],
  paymentNo: [
    { required: true, message: '请输入付款单号', trigger: 'blur' },
    { max: 50, message: '付款单号最多50字', trigger: 'blur' },
  ],
  paymentDate: [{ required: true, message: '请选择计划付款日期', trigger: 'change' }],
  paymentMethod: [{ required: true, message: '请选择付款方式', trigger: 'change' }],
  paymentAmount: [
    {
      validator: (_rule, value, callback) => {
        const amount = Number(value)
        if (
          !summary.value ||
          !Number.isFinite(amount) ||
          amount <= 0 ||
          amount > Number(summary.value.availableAmount)
        )
          callback(new Error('申请金额必须大于0且不超过可申请金额'))
        else callback()
      },
      trigger: 'change',
    },
  ],
  bankAccount: [{ max: 100, message: '银行账号最多100字', trigger: 'blur' }],
  remark: [{ max: 500, message: '付款备注最多500字', trigger: 'blur' }],
}
defineExpose({
  validate: async () => !props.disabled && !!(await formRef.value?.validate().catch(() => false)),
  resetFields: () => formRef.value?.resetFields(),
})
</script>

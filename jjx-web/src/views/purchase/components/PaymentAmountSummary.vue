<template>
  <el-descriptions v-loading="loading" :column="2" border class="payment-summary">
    <el-descriptions-item label="订单金额">{{ money(summary?.orderTotalAmount) }}</el-descriptions-item>
    <el-descriptions-item label="已付金额">{{ money(summary?.paidAmount) }}</el-descriptions-item>
    <el-descriptions-item label="申请中金额">{{ money(summary?.pendingAmount) }}</el-descriptions-item>
    <el-descriptions-item label="可申请金额">{{ money(summary?.availableAmount) }}</el-descriptions-item>
  </el-descriptions>
  <el-alert
    v-if="excludedPaymentId"
    title="编辑时，申请中金额不含本单；可申请金额为本单修改后的金额上限。"
    type="info"
    :closable="false"
  />
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { getOrderPaymentSummary, type PurchasePaymentSummary } from '@/api/purchase/payment'

const props = defineProps<{ orderId?: number; excludedPaymentId?: number }>()
const emit = defineEmits<{ (e: 'loaded', summary: PurchasePaymentSummary | null): void }>()
const summary = ref<PurchasePaymentSummary | null>(null)
const loading = ref(false)
let requestSequence = 0
onBeforeUnmount(() => { requestSequence++ })

watch(
  () => [props.orderId, props.excludedPaymentId],
  async () => {
    const sequence = ++requestSequence
    summary.value = null
    emit('loaded', null)
    loading.value = false
    if (!props.orderId) return
    loading.value = true
    try {
      const result = await getOrderPaymentSummary(Number(props.orderId), props.excludedPaymentId)
      if (sequence !== requestSequence) return
      summary.value = result.data
      emit('loaded', result.data)
    } catch (error) {
      console.error('加载付款申请额度失败:', error)
    } finally {
      if (sequence === requestSequence) loading.value = false
    }
  },
  { immediate: true }
)

function money(value?: number) {
  return value == null ? '-' : `${Number(value).toFixed(2)} ${summary.value?.currency || ''}`.trim()
}
</script>

<style scoped>
.payment-summary {
  margin-bottom: 16px;
}
</style>

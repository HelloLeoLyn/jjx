<template>
  <el-dialog
    title="客户详情"
    :model-value="modelValue"
    width="900px"
    append-to-body
    destroy-on-close
    @close="close"
  >
    <el-skeleton v-if="loading" :rows="8" animated />
    <el-descriptions v-else-if="detail" :column="2" border>
      <el-descriptions-item label="客户编码">{{ display(detail.customerCode) }}</el-descriptions-item>
      <el-descriptions-item label="客户名称">{{ display(detail.customerName) }}</el-descriptions-item>
      <el-descriptions-item label="客户简称">{{ display(detail.customerShortName) }}</el-descriptions-item>
      <el-descriptions-item label="客户类型">
        <el-tag :type="CustomerTypeEnum.getTagProps(detail.customerType ?? 1).type">
          {{ CustomerTypeEnum.getLabel(detail.customerType ?? 1) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="客户等级">
        <el-tag :type="CustomerLevelEnum.getTagProps(detail.customerLevel ?? 1).type">
          {{ CustomerLevelEnum.getLabel(detail.customerLevel ?? 1) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="客户状态">
        <el-tag type="info">{{ CustomerStatusEnum.getLabel(detail.customerStatus) }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="行业分类">{{ display(detail.industryCategory) }}</el-descriptions-item>
      <el-descriptions-item label="客户来源">
        <el-tag>{{ getSourceLabel(detail.customerSource) }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="联系人">{{ display(detail.contactPerson) }}</el-descriptions-item>
      <el-descriptions-item label="联系电话">{{ display(detail.contactPhone) }}</el-descriptions-item>
      <el-descriptions-item label="联系邮箱">{{ display(detail.contactEmail) }}</el-descriptions-item>
      <el-descriptions-item label="传真">{{ display(detail.fax) }}</el-descriptions-item>
      <el-descriptions-item label="所在地区" :span="2">
        {{ addressRegion }}
        <span v-if="detail.postalCode" class="addr-postal">（邮编 {{ detail.postalCode }}）</span>
      </el-descriptions-item>
      <el-descriptions-item label="详细地址" :span="2">{{ display(detail.address) }}</el-descriptions-item>
      <el-descriptions-item label="信用额度">{{ formatCurrency(detail.creditLimit) }}</el-descriptions-item>
      <el-descriptions-item label="已用额度">{{ formatCurrency(detail.usedCreditLimit) }}</el-descriptions-item>
      <el-descriptions-item label="客户评分">
        <el-rate :model-value="detail.customerScore" disabled :max="5" show-score />
      </el-descriptions-item>
      <el-descriptions-item label="付款方式">
        <el-tag>{{ getPaymentMethodLabel(detail.paymentMethod) }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="是否VIP">
        <el-tag :type="detail.vip ? 'success' : 'info'">{{ detail.vip ? '是' : '否' }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ display(detail.remark) }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ parseTime(detail.createTime) }}</el-descriptions-item>
      <el-descriptions-item label="更新时间">{{ parseTime(detail.updateTime) }}</el-descriptions-item>
    </el-descriptions>
    <el-empty v-else description="暂无客户详情" />

    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { customerApi } from '@/api/sales/customer'
import { CustomerLevelEnum, CustomerStatusEnum, CustomerTypeEnum } from '@/enums/sales/CustomerEnum'
import { parseTime } from '@/utils/format'
import type { CustomerDetail } from '@/types/sales/customer'
import { useCustomerOptions } from '../composables/useCustomerOptions'

const props = defineProps<{
  modelValue: boolean
  customerId?: number
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const loading = ref(false)
const detail = ref<CustomerDetail>()
const { getSourceLabel, getPaymentMethodLabel } = useCustomerOptions()

const addressRegion = computed(() =>
  detail.value
    ? [detail.value.country, detail.value.province, detail.value.city].filter(Boolean).join(' / ') || '-'
    : '-'
)

watch(
  () => [props.modelValue, props.customerId] as const,
  async ([visible, customerId]) => {
    if (!visible || !customerId) return
    loading.value = true
    detail.value = undefined
    try {
      const response = await customerApi.getCustomer(customerId)
      detail.value = response.data || undefined
    } catch (error: any) {
      ElMessage.error(error?.message || '加载客户详情失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)

const display = (value?: string) => value || '-'

const formatCurrency = (value?: number) =>
  (value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })

const close = () => emit('update:modelValue', false)
</script>

<style scoped>
.addr-postal {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}
</style>

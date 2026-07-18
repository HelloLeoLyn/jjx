import { ref } from 'vue'

// 客户类型选项
export const customerTypeOptions = ref([
  { value: 1, label: '终端客户' },
  { value: 2, label: '代理商' },
  { value: 3, label: '经销商' },
])

// 客户等级选项
export const customerLevelOptions = ref([
  { value: 1, label: 'A级' },
  { value: 2, label: 'B级' },
  { value: 3, label: 'C级' },
])

// 客户状态选项
export const customerStatusOptions = ref([
  { value: 1, label: '潜在客户' },
  { value: 2, label: '正式客户' },
  { value: 3, label: '暂停合作' },
  { value: 4, label: '终止合作' },
])

// 客户来源选项
export const customerSourceOptions = ref([
  { value: 1, label: '展会' },
  { value: 2, label: '网络' },
  { value: 3, label: '转介绍' },
  { value: 4, label: '主动开发' },
])

// 付款方式选项
export const paymentMethodOptions = ref([
  { value: 1, label: '预付' },
  { value: 2, label: '货到付款' },
  { value: 3, label: '月结30天' },
  { value: 4, label: '月结60天' },
])

// 获取客户来源标签
export function getSourceLabel(source?: number): string {
  const option = customerSourceOptions.value.find((opt) => opt.value === source)
  return option ? option.label : '未知'
}

// 获取付款方式标签
export function getPaymentMethodLabel(method?: number): string {
  const option = paymentMethodOptions.value.find((opt) => opt.value === method)
  return option ? option.label : '未知'
}

export function useCustomerOptions() {
  return {
    customerTypeOptions,
    customerLevelOptions,
    customerStatusOptions,
    customerSourceOptions,
    paymentMethodOptions,
    getSourceLabel,
    getPaymentMethodLabel,
  }
}

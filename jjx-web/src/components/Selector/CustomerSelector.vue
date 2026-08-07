<template>
  <el-select
    v-model="selectedValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    :size="size"
    filterable
    remote
    :remote-method="handleRemoteSearch"
    :loading="loading"
    :default-first-option="autoSelectFirst"
    @clear="handleClear"
    @change="handleChange"
    class="customer-selector"
  >
    <el-option
      v-for="item in displayOptions"
      :key="item.customerId"
      :label="getOptionLabel(item)"
      :value="item.customerId"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import type { CustomerSearchVO } from '@/types/sales/customer'
import { customerApi } from '@/api/sales/customer'

interface Props {
  modelValue: CustomerSearchVO | number | string | null | undefined
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
  size?: 'large' | 'default' | 'small'
  valueType?: 'object' | 'customerId' | 'customerCode' | 'customerName'
  debounceDelay?: number
  minKeywordLength?: number
  autoSelectFirst?: boolean
  options?: CustomerSearchVO[]
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请搜索并选择客户',
  clearable: true,
  disabled: false,
  size: 'default',
  valueType: 'object',
  debounceDelay: 300,
  minKeywordLength: 2,
  autoSelectFirst: false,
  options: () => [],
})

const emit = defineEmits<{
  'update:modelValue': [value: any]
  change: [value: any, customer: CustomerSearchVO | null]
  search: [keyword: string]
  clear: []
}>()

const remoteOptions = ref<CustomerSearchVO[]>([])
const loading = ref(false)
const keyword = ref('')
let debounceTimer: ReturnType<typeof setTimeout> | null = null

// 选项数据源：优先使用外部传入的 options，否则使用远程搜索结果
const displayOptions = computed(() => {
  if (props.options && props.options.length > 0) {
    return props.options
  }
  return remoteOptions.value
})

// 选中值
const selectedValue = computed({
  get: () => {
    if (!props.modelValue) return null
    // 如果是对象，取 customerId
    if (typeof props.modelValue === 'object') {
      return (props.modelValue as CustomerSearchVO).customerId
    }
    return props.modelValue
  },
  set: (val) => {
    if (!val) {
      emit('update:modelValue', null)
      emit('change', null, null)
      return
    }

    // 根据 customerId 找到完整对象
    const customer = displayOptions.value.find((item) => item.customerId === val)
    if (!customer) return

    // 根据 valueType 返回
    if (props.valueType === 'customerId') {
      emit('update:modelValue', customer.customerId)
      emit('change', customer.customerId, customer)
    } else if (props.valueType === 'customerCode') {
      emit('update:modelValue', customer.customerCode)
      emit('change', customer.customerCode, customer)
    } else if (props.valueType === 'customerName') {
      emit('update:modelValue', customer.customerName)
      emit('change', customer.customerName, customer)
    } else {
      emit('update:modelValue', customer)
      emit('change', customer, customer)
    }
  },
})

const getOptionLabel = (item: CustomerSearchVO) => {
  const shortName = item.customerShortName || item.customerName.substring(0, 3)
  return `${shortName} (${item.customerName})`
}

const handleRemoteSearch = (query: string) => {
  keyword.value = query

  if (debounceTimer) clearTimeout(debounceTimer)

  // 如果有外部传入的 options，不执行远程搜索
  if (props.options && props.options.length > 0) {
    return
  }

  if (!query || query.length < props.minKeywordLength) {
    remoteOptions.value = []
    return
  }

  debounceTimer = setTimeout(async () => {
    loading.value = true
    try {
      const res = await customerApi.searchCustomers(query)
      if (res.code === 200 && res.data) {
        remoteOptions.value = res.data
        emit('search', query)

        if (props.autoSelectFirst && remoteOptions.value.length > 0) {
          selectedValue.value = remoteOptions.value[0].customerId
        }
      }
    } catch (error) {
      ElMessage.error('搜索客户失败')
    } finally {
      loading.value = false
    }
  }, props.debounceDelay)
}

const handleClear = () => {
  remoteOptions.value = []
  keyword.value = ''
  emit('update:modelValue', null)
  emit('clear')
}

const handleChange = (val: number) => {
  // 可在此处添加额外逻辑
}

// 编辑回显：modelValue 是数字/对象时，按 ID 加载客户名称（否则 el-select 直接显示原始值如 1）
watch(
  () => props.modelValue,
  (val) => {
    if (!val) return
    if (props.options && props.options.length > 0) return
    const currentId = typeof val === 'object' ? (val as CustomerSearchVO).customerId : val
    if (!currentId) return
    if (remoteOptions.value.some((o) => o.customerId === currentId)) return

    loading.value = true
    customerApi
      .getCustomer(Number(currentId))
      .then((res: any) => {
        if (res.code === 200 && res.data) {
          const target = res.data as CustomerSearchVO
          if (!remoteOptions.value.some((o) => o.customerId === target.customerId)) {
            remoteOptions.value = [target, ...remoteOptions.value]
          }
        }
      })
      .catch(() => {})
      .finally(() => {
        loading.value = false
      })
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped>
.customer-selector {
  width: 100%;
}
</style>

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
    class="product-selector"
  >
    <el-option
      v-for="item in displayOptions"
      :key="item.productId"
      :label="getOptionLabel(item)"
      :value="item.productId"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import type { ProductItem } from '@/types/product'
import { productApi } from '@/api/product'

interface Props {
  modelValue: ProductItem | number | string | null
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
  size?: 'large' | 'default' | 'small'
  valueType?: 'object' | 'productId' | 'productCode' | 'productName'
  debounceDelay?: number
  minKeywordLength?: number
  autoSelectFirst?: boolean
  options?: ProductItem[]
  /** DEV-1121：专属客户过滤（可选，不传时全库搜索，行为不变） */
  customerId?: number
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请搜索并选择产品',
  clearable: true,
  disabled: false,
  size: 'default',
  valueType: 'object',
  debounceDelay: 300,
  minKeywordLength: 2,
  autoSelectFirst: false,
  options: () => [],
  customerId: undefined,
})

const emit = defineEmits<{
  'update:modelValue': [value: any]
  change: [value: any, product: ProductItem | null]
  search: [keyword: string]
  clear: []
}>()

const remoteOptions = ref<ProductItem[]>([])
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
    // 如果是对象，取 productId
    if (typeof props.modelValue === 'object') {
      return (props.modelValue as ProductItem).productId
    }
    return props.modelValue
  },
  set: (val) => {
    if (!val) {
      emit('update:modelValue', null)
      emit('change', null, null)
      return
    }

    // 根据 productId 找到完整对象
    const product = displayOptions.value.find((item) => item.productId === val)
    if (!product) return

    // 根据 valueType 返回
    if (props.valueType === 'productId') {
      emit('update:modelValue', product.productId)
      emit('change', product.productId, product)
    } else if (props.valueType === 'productCode') {
      emit('update:modelValue', product.productCode)
      emit('change', product.productCode, product)
    } else if (props.valueType === 'productName') {
      emit('update:modelValue', product.productName)
      emit('change', product.productName, product)
    } else {
      emit('update:modelValue', product)
      emit('change', product, product)
    }
  },
})

const getOptionLabel = (item: ProductItem) => {
  const spec = item.specification ? ` - ${item.specification}` : ''
  return `${item.productName} (${item.productCode})${spec}`
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
      const res = await productApi.search(query, props.customerId)
      if (res.code === 200 && res.data) {
        remoteOptions.value = res.data
        emit('search', query)

        if (props.autoSelectFirst && remoteOptions.value.length > 0) {
          selectedValue.value = remoteOptions.value[0].productId
        }
      }
    } catch (error) {
      ElMessage.error('搜索产品失败')
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

// DEV-1121：客户切换时清空远程搜索缓存
watch(
  () => props.customerId,
  () => {
    remoteOptions.value = []
  },
)

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped>
.product-selector {
  width: 100%;
}
</style>

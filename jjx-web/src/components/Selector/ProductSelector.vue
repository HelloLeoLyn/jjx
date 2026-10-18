<template>
  <EditableSearchSelect
    :model-value="selectedValue"
    :display-value="selectedLabel"
    :options="displayOptions"
    option-key="productId"
    :label-of="getOptionLabel"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    :size="size"
    :loading="loading"
    class="product-selector"
    @search="handleRemoteSearch"
    @select="onPick"
    @clear="handleClear"
  />
</template>

<script setup lang="ts">
/**
 * 产品选择器（dev-20261018-011 改为可编辑搜索框）
 * 保留：valueType、客户过滤 customerId、状态范围 statusScope、外部 options 与 allowRemoteWithOptions 合并语义。
 */
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import type { ProductItem } from '@/types/product'
import { productApi } from '@/api/product'
import EditableSearchSelect from './EditableSearchSelect.vue'

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
  customerId?: number
  statusScope?: string
  allowRemoteWithOptions?: boolean
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
  statusScope: 'released',
  allowRemoteWithOptions: false,
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

const displayOptions = computed(() => {
  if (props.options && props.options.length > 0) {
    if (!props.allowRemoteWithOptions) return props.options
    const merged = [...props.options]
    for (const item of remoteOptions.value) {
      if (!merged.some((option) => option.productId === item.productId)) merged.push(item)
    }
    return merged
  }
  return remoteOptions.value
})

const selectedValue = computed(() => {
  if (props.modelValue == null) return null
  if (typeof props.modelValue === 'object') return (props.modelValue as ProductItem).productId
  return props.modelValue
})

const matchesModel = (item: ProductItem) => {
  const mv: any = props.modelValue
  if (mv == null) return false
  if (typeof mv === 'object') return item.productId === mv.productId
  if (props.valueType === 'productCode') return item.productCode === mv
  if (props.valueType === 'productName') return item.productName === mv
  return item.productId === mv
}

const getOptionLabel = (item: ProductItem) => {
  const spec = item.specification ? ' - ' + item.specification : ''
  return item.productName + ' (' + item.productCode + ')' + spec
}

const selectedLabel = computed(() => {
  if (props.modelValue == null) return ''
  const hit = displayOptions.value.find(matchesModel)
  if (hit) return getOptionLabel(hit)
  return typeof props.modelValue === 'object' ? '' : String(props.modelValue)
})

function onPick(product: ProductItem) {
  if (!product) return
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
}

const handleRemoteSearch = (query: string) => {
  keyword.value = query
  if (debounceTimer) clearTimeout(debounceTimer)
  if (props.options && props.options.length > 0 && !props.allowRemoteWithOptions) return
  if (!query || query.length < props.minKeywordLength) {
    remoteOptions.value = []
    return
  }
  debounceTimer = setTimeout(async () => {
    loading.value = true
    try {
      const res = await productApi.search(query, props.customerId, props.statusScope)
      if (res.code === 200 && res.data) {
        remoteOptions.value = res.data
        emit('search', query)
        if (props.autoSelectFirst && remoteOptions.value.length > 0) onPick(remoteOptions.value[0])
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
  emit('change', null, null)
  emit('clear')
}

watch(
  () => props.customerId,
  () => {
    remoteOptions.value = []
  }
)

watch(
  () => props.statusScope,
  () => {
    remoteOptions.value = []
  }
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

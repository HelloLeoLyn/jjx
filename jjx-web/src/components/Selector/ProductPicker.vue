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
    @focus="ensureLoaded"
    @clear="handleClear"
    @change="handleChange"
    class="product-picker"
  >
    <el-option
      v-for="item in options"
      :key="item.productId"
      :label="getOptionLabel(item)"
      :value="item.productId"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { productApi } from '@/api/product'

/**
 * 产品选择器（2026-08-11）
 * 与 CustomerSelector 同款交互，但解决"点开无下拉"问题：
 * - 聚焦时自动加载全部已发布产品（空关键字搜索），点开即有列表
 * - 输入关键字时远程搜索过滤
 * - change 回传完整产品对象
 */
export interface ProductOption {
  productId: number
  productCode: string
  productName: string
  specification?: string
  unit?: string
  [key: string]: any
}

interface Props {
  modelValue: ProductOption | number | string | null | undefined
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
  size?: 'large' | 'default' | 'small'
  /** 回传值类型：object=完整产品 / productId=产品ID / productCode=产品编码 */
  valueType?: 'object' | 'productId' | 'productCode'
  /** 是否在聚焦时预加载全部产品（默认 true，点开即有列表） */
  preload?: boolean
  debounceDelay?: number
  minKeywordLength?: number
  /** DEV-1121：专属客户过滤（可选，不传时全库搜索，行为不变） */
  customerId?: number
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择产品',
  clearable: true,
  disabled: false,
  size: 'default',
  valueType: 'object',
  preload: true,
  debounceDelay: 300,
  minKeywordLength: 1,
  customerId: undefined,
})

const emit = defineEmits<{
  'update:modelValue': [value: any]
  change: [value: any, product: ProductOption | null]
  clear: []
}>()

const options = ref<ProductOption[]>([])
const loading = ref(false)
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let preloaded = false

const selectedValue = computed({
  get: () => {
    if (!props.modelValue) return null
    if (typeof props.modelValue === 'object') {
      return (props.modelValue as ProductOption).productId
    }
    return props.modelValue
  },
  set: (val) => {
    if (!val) {
      emit('update:modelValue', null)
      emit('change', null, null)
      return
    }
    const product = options.value.find((item) => item.productId === val)
    if (!product) return

    if (props.valueType === 'productId') {
      emit('update:modelValue', product.productId)
      emit('change', product.productId, product)
    } else if (props.valueType === 'productCode') {
      emit('update:modelValue', product.productCode)
      emit('change', product.productCode, product)
    } else {
      emit('update:modelValue', product)
      emit('change', product, product)
    }
  },
})

const getOptionLabel = (item: ProductOption) => {
  return `${item.productCode} - ${item.productName}`
}

/** 预加载全部已发布产品（空关键字 → 后端只查 RELEASED 返回全部） */
async function preloadAll() {
  if (preloaded || !props.preload) return
  preloaded = true
  loading.value = true
  try {
    const res = await productApi.search('', props.customerId)
    if (res.code === 200 && res.data) {
      options.value = res.data
    }
  } catch {
    options.value = []
  } finally {
    loading.value = false
  }
}

/** 聚焦时确保有数据（点开即有下拉） */
function ensureLoaded() {
  if (options.value.length === 0) {
    preloadAll()
  }
}

const handleRemoteSearch = (query: string) => {
  if (debounceTimer) clearTimeout(debounceTimer)

  if (!query || query.length < props.minKeywordLength) {
    // 输入过短：恢复为预加载的全量列表
    if (preloaded) preloadAll()
    else options.value = []
    return
  }

  debounceTimer = setTimeout(async () => {
    loading.value = true
    try {
      const res = await productApi.search(query, props.customerId)
      if (res.code === 200 && res.data) {
        options.value = res.data
      }
    } catch (error) {
      ElMessage.error('搜索产品失败')
    } finally {
      loading.value = false
    }
  }, props.debounceDelay)
}

const handleClear = () => {
  options.value = []
  preloaded = false
  emit('update:modelValue', null)
  emit('clear')
}

const handleChange = () => {
  // 无需额外逻辑
}

// 编辑回显：modelValue 为数字时按 ID 加载产品名
watch(
  () => props.modelValue,
  (val) => {
    if (!val) return
    const currentId = typeof val === 'object' ? (val as ProductOption).productId : val
    if (!currentId) return
    if (options.value.some((o) => o.productId === currentId)) return

    loading.value = true
    productApi
      .info(Number(currentId))
      .then((res: any) => {
        if (res.code === 200 && res.data) {
          const target = res.data as ProductOption
          if (!options.value.some((o) => o.productId === target.productId)) {
            options.value = [target, ...options.value]
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

// DEV-1121：客户切换时清空缓存，下拉按新客户重新加载
watch(
  () => props.customerId,
  () => {
    options.value = []
    preloaded = false
  },
)

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped>
.product-picker {
  width: 100%;
}
</style>

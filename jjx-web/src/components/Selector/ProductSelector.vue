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
  /**
   * 状态范围（2026-09-15）：'released'（默认，仅已发布）|
   * 'active'（除 停产/取消 外均可选，用于 BOM/工艺路线建档场景）
   */
  statusScope?: string
  /**
   * 有外部 options 时是否仍执行远程搜索并与 options 合并（2026-09-15）：
   * false（默认）= 保持旧行为，options 非空即完全走本地 options，不请求远程；
   * true = 仅用于「options 只是回填当前选中项」的场景（如 BOM 修改回填单条产品），
   *        搜索时远程结果与 options 按 productId 去重合并，使当前选中项保持可显示、同时能改选其它产品。
   */
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

// 选项数据源：优先使用外部传入的 options，否则使用远程搜索结果
const displayOptions = computed(() => {
  if (props.options && props.options.length > 0) {
    // allowRemoteWithOptions：options 只用于回填当前选中项，搜索结果需并入（2026-09-15）
    if (!props.allowRemoteWithOptions) {
      return props.options
    }
    const merged = [...props.options]
    for (const item of remoteOptions.value) {
      if (!merged.some((option) => option.productId === item.productId)) {
        merged.push(item)
      }
    }
    return merged
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

  // 如果有外部传入的 options，不执行远程搜索（除非显式要求与远程结果合并）
  if (props.options && props.options.length > 0 && !props.allowRemoteWithOptions) {
    return
  }

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

// 2026-09-15：状态范围切换时清空缓存，避免旧范围结果残留
watch(
  () => props.statusScope,
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

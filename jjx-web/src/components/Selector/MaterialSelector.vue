<!-- components/MaterialSelector.vue -->
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
    class="material-selector"
  >
    <el-option
      v-for="item in displayOptions"
      :key="item.materialId"
      :label="getOptionLabel(item)"
      :value="item.materialId"
      :disabled="item.status === '禁用'"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import type { InventoryMaterial } from '@/types/inventory/material'
import { materialApi } from '@/api/inventory/material'

interface Props {
  modelValue: InventoryMaterial | number | string | null
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
  size?: 'large' | 'default' | 'small'
  valueType?: 'object' | 'materialId' | 'materialCode' | 'materialName'
  debounceDelay?: number
  minKeywordLength?: number
  autoSelectFirst?: boolean
  options?: InventoryMaterial[]
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请搜索并选择材料',
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
  change: [value: any, material: InventoryMaterial | null]
  search: [keyword: string]
  clear: []
}>()

const remoteOptions = ref<InventoryMaterial[]>([])
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

// 选中值 - 简化版
const selectedValue = computed({
  get: () => {
    if (!props.modelValue) return null
    // 如果是对象，取 materialId
    if (typeof props.modelValue === 'object') {
      return props.modelValue.materialId
    }
    return props.modelValue
  },
  set: (val) => {
    if (!val) {
      emit('update:modelValue', null)
      emit('change', null, null)
      return
    }

    // 根据 materialId 找到完整对象
    const material = displayOptions.value.find((item) => item.materialId === val)
    if (!material) return

    // 根据 valueType 返回
    if (props.valueType === 'materialId') {
      emit('update:modelValue', material.materialId)
      emit('change', material.materialId, material)
    } else if (props.valueType === 'materialCode') {
      emit('update:modelValue', material.materialCode)
      emit('change', material.materialCode, material)
    } else if (props.valueType === 'materialName') {
      emit('update:modelValue', material.materialName)
      emit('change', material.materialName, material)
    } else {
      emit('update:modelValue', material)
      emit('change', material, material)
    }
  },
})

const getOptionLabel = (item: InventoryMaterial) => {
  return `${item.materialName} (${item.materialCode})${item.specification ? ` - ${item.specification}` : ''}`
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
      const res = await materialApi.search(query)
      if (res.code === 200 && res.data) {
        remoteOptions.value = res.data
        emit('search', query)

        if (props.autoSelectFirst && remoteOptions.value.length > 0) {
          selectedValue.value = remoteOptions.value[0].materialId
        }
      }
    } catch (error) {
      ElMessage.error('搜索失败')
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
  // 你可以在这里做任何想做的事情
}

onBeforeUnmount(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped>
.material-selector {
  width: 100%;
}
</style>

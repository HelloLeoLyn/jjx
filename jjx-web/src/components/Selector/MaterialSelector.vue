<!-- components/Selector/MaterialSelector.vue -->
<template>
  <EditableSearchSelect
    :model-value="selectedValue"
    :display-value="selectedLabel"
    :options="displayOptions"
    option-key="materialId"
    :label-of="getOptionLabel"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    :size="size"
    :loading="loading"
    class="material-selector"
    @search="handleRemoteSearch"
    @select="onPick"
    @clear="handleClear"
  />
</template>

<script setup lang="ts">
/**
 * 物料选择器（dev-20261018-011 改为可编辑搜索框）
 * v-model 语义、valueType、远程搜索防抖、外部 options、自动选首项均保留。
 */
import { ref, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import type { InventoryMaterial } from '@/types/inventory/material'
import { materialApi } from '@/api/inventory/material'
import EditableSearchSelect from './EditableSearchSelect.vue'

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

const displayOptions = computed(() => {
  if (props.options && props.options.length > 0) return props.options
  return remoteOptions.value
})

const selectedValue = computed(() => {
  if (props.modelValue == null) return null
  if (typeof props.modelValue === 'object') return props.modelValue.materialId
  return props.modelValue
})

const matchesModel = (item: InventoryMaterial) => {
  const mv: any = props.modelValue
  if (mv == null) return false
  if (typeof mv === 'object') return item.materialId === mv.materialId
  if (props.valueType === 'materialCode') return item.materialCode === mv
  if (props.valueType === 'materialName') return item.materialName === mv
  return item.materialId === mv
}

const selectedLabel = computed(() => {
  if (props.modelValue == null) return ''
  const hit = displayOptions.value.find(matchesModel)
  if (hit) return getOptionLabel(hit)
  return typeof props.modelValue === 'object' ? '' : String(props.modelValue)
})

const getOptionLabel = (item: InventoryMaterial) => {
  const spec = item.specification ? ' - ' + item.specification : ''
  return item.materialName + ' (' + item.materialCode + ')' + spec
}

function onPick(material: InventoryMaterial) {
  if (!material) return
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
}

const handleRemoteSearch = (query: string) => {
  keyword.value = query
  if (debounceTimer) clearTimeout(debounceTimer)
  if (props.options && props.options.length > 0) return
  if (!query || query.length < props.minKeywordLength) {
    remoteOptions.value = []
    return
  }
  debounceTimer = setTimeout(async () => {
    loading.value = true
    try {
      const res = await materialApi.search({ keyword: query } as any)
      if (res.code === 200 && res.data) {
        remoteOptions.value = (res as any).data
        emit('search', query)
        if (props.autoSelectFirst && remoteOptions.value.length > 0) {
          onPick(remoteOptions.value[0])
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
  emit('change', null, null)
  emit('clear')
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

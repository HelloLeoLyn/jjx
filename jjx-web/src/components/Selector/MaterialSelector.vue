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
    :loading="effLoading"
    :loading-more="loadingMore"
    :min-width="520"
    :failed="effFailed"
    :total="effTotal"
    class="material-selector"
    @search="handleRemoteSearch"
    @select="onPick"
    @clear="handleClear"
    @load-more="loadMore"
  >
    <template #option="{ item }">
      <MaterialSearchOption :item="item" />
    </template>
    <template #footer>
      <div v-if="effFailed" class="material-search-failed">
        查询失败，
        <el-button link type="primary" size="small" @click="search.retry()">重试</el-button>
      </div>
    </template>
  </EditableSearchSelect>
</template>

<script setup lang="ts">
/**
 * 物料选择器（dev-20261024-018 公共搜索升级）
 * v-model 语义、valueType、外部 options、自动选首项保留；
 * 搜索改用 useMaterialSearch：修复把分页对象当数组（records）的问题，支持多词/分页/失败重试。
 */
import { computed } from 'vue'
import type { InventoryMaterial } from '@/types/inventory/material'
import EditableSearchSelect from './EditableSearchSelect.vue'
import MaterialSearchOption from './MaterialSearchOption.vue'
import { useMaterialSearch } from './useMaterialSearch'

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

const search = useMaterialSearch({
  debounceMs: props.debounceDelay,
  minKeywordLength: props.minKeywordLength,
  pageSize: 10,
  onFirstPage: (items) => {
    emit('search', search.keyword.value)
    if (props.autoSelectFirst && items.length > 0 && props.modelValue == null) onPick(items[0])
  },
})

const external = computed(() => (props.options || []).length > 0)
const displayOptions = computed(() => (external.value ? props.options : search.items.value))
const effLoading = computed(() => (external.value ? false : search.loading.value))
const loadingMore = computed(() => (external.value ? false : search.loadingMore.value))
const effFailed = computed(() => (external.value ? false : search.failed.value))
// 分页总数必须取后端 total，不能用当前页条数（复核缺陷 1）
const effTotal = computed(() => (external.value ? displayOptions.value.length : search.total.value))

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
  if (typeof props.modelValue === 'object') {
    return props.modelValue.materialName
      ? getOptionLabel(props.modelValue as InventoryMaterial)
      : ''
  }
  return String(props.modelValue)
})

const getOptionLabel = (item: InventoryMaterial) =>
  [item.materialName || '', item.materialCode ? `(${item.materialCode})` : '']
    .filter(Boolean)
    .join(' ')

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

function handleRemoteSearch(query: string) {
  if (props.options && props.options.length > 0) return
  search.search(query)
}

async function loadMore() {
  if (external.value) return
  await search.loadMore()
}

function handleClear() {
  search.reset()
  emit('update:modelValue', null)
  emit('change', null, null)
  emit('clear')
}
</script>

<style scoped>
.material-selector {
  width: 100%;
}
.material-search-failed {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>

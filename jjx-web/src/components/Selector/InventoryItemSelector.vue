<template>
  <EditableSearchSelect
    :model-value="modelKey"
    :display-value="selectedLabel"
    :options="options"
    option-key="inventoryItemId"
    :label-of="optionLabel"
    :placeholder="placeholder"
    :disabled="disabled"
    :loading="loading"
    @search="onSearch"
    @select="onPick"
    @clear="onClear"
  >
    <template #option="{ item }">
      <span class="item-name">{{ item.materialName }}</span>
      <span class="item-meta">{{ item.materialCode }} · 可用 {{ formatQty(item.availableQuantity) }}</span>
    </template>
  </EditableSearchSelect>
</template>

<script setup lang="ts">
/**
 * 「库存物品」选择器（dev-20260930-038；dev-20261018-011 改为可编辑搜索框）
 * 出库/领料选的是库存物品（inventory_item：PRODUCT / MATERIAL）；保留 itemType/warehouseId 过滤与可用量展示。
 */
import { computed, ref, watch } from 'vue'
import { debounce } from 'lodash-es'
import { stockApi } from '@/api/inventory/stock'
import type { StockVO } from '@/types/inventory/stock'
import EditableSearchSelect from './EditableSearchSelect.vue'

interface Props {
  modelValue?: string | number | null
  itemType?: 'MATERIAL' | 'PRODUCT' | ''
  warehouseId?: string | number | null
  placeholder?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  itemType: '',
  warehouseId: null,
  placeholder: '搜索并选择库存物品（名称或编码）',
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | undefined): void
  (e: 'change', item: StockVO | null): void
}>()

const options = ref<StockVO[]>([])
const loading = ref(false)
const itemCache = new Map<string, StockVO>()

const modelKey = computed(() =>
  props.modelValue == null || props.modelValue === '' ? null : String(props.modelValue)
)

const optionLabel = (item: StockVO) => item.materialName + '（' + item.materialCode + '）'
const formatQty = (n?: number) => (n == null ? '-' : Number(n).toLocaleString())

const selectedLabel = computed(() => {
  const key = modelKey.value
  if (!key) return ''
  const hit = itemCache.get(key) || options.value.find((o) => String(o.inventoryItemId) === key)
  return hit ? optionLabel(hit) : key
})

/** 关键词像编码（无中文）就按编码搜，否则按名称搜 —— 后端两个条件是 AND，只能挑一个传 */
const isCodeLike = (kw: string) => !/[\u4e00-\u9fff]/.test(kw)

async function fetchOptions(keyword: string) {
  loading.value = true
  try {
    const kw = (keyword || '').trim()
    const params: any = { current: 1, pageSize: 50 }
    if (props.itemType) params.itemType = props.itemType
    if (props.warehouseId) params.warehouseId = String(props.warehouseId)
    if (kw) {
      if (isCodeLike(kw)) params.materialCode = kw
      else params.materialName = kw
    }
    const res: any = await stockApi.list(params)
    const records = (res?.data?.records || []) as StockVO[]
    records.forEach((r) => itemCache.set(String(r.inventoryItemId), r))
    options.value = records
  } catch (e) {
    console.error('加载库存物品失败:', e)
    options.value = []
  } finally {
    loading.value = false
  }
}

const onSearch = debounce((kw: string) => {
  void fetchOptions(kw)
}, 300)

function onPick(item: StockVO) {
  if (!item) return
  const key = String(item.inventoryItemId)
  itemCache.set(key, item)
  emit('update:modelValue', key)
  emit('change', item)
}

function onClear() {
  emit('update:modelValue', undefined)
  emit('change', null)
}

watch(
  () => [props.itemType, props.warehouseId],
  () => {
    void fetchOptions('')
  },
  { immediate: true }
)

defineExpose({ refresh: () => fetchOptions('') })
</script>

<style scoped>
.item-name {
  font-weight: 500;
}
.item-meta {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

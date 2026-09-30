<template>
  <el-select
    v-model="selectedValue"
    filterable
    remote
    clearable
    :remote-method="remoteSearch"
    :loading="loading"
    :placeholder="placeholder"
    :disabled="disabled"
    style="width: 100%"
    @change="handleChange"
  >
    <el-option
      v-for="item in options"
      :key="item.inventoryItemId"
      :label="optionLabel(item)"
      :value="item.inventoryItemId"
    >
      <span class="item-name">{{ item.materialName }}</span>
      <span class="item-meta">{{ item.materialCode }} · 可用 {{ formatQty(item.availableQuantity) }}</span>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
// 「库存物品」选择器（2026-09-30 dev-20260930-038）
// 出库/领料等业务选的是**库存物品**（inventory_item：成品 PRODUCT / 原材料 MATERIAL），不是物料主数据；
// 按 itemType + warehouseId 缩小候选，标签带可用量，避免选到不存在的对象或显示 undefined。
import { ref, watch } from 'vue'
import { debounce } from 'lodash-es'
import { stockApi } from '@/api/inventory/stock'
import type { StockVO } from '@/types/inventory/stock'

interface Props {
  modelValue?: string | number | null
  /** 物品类型：PRODUCT=成品（销售发货）/ MATERIAL=原材料（生产领料）；不传=都列 */
  itemType?: 'MATERIAL' | 'PRODUCT' | ''
  /** 只看该仓库的库存物品 */
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
const selectedValue = ref<string | undefined>(
  props.modelValue == null || props.modelValue === '' ? undefined : String(props.modelValue)
)

watch(
  () => props.modelValue,
  (v) => {
    const next = v == null || v === '' ? undefined : String(v)
    if (next !== selectedValue.value) selectedValue.value = next
  }
)

const optionLabel = (item: StockVO) => `${item.materialName}（${item.materialCode}）`
const formatQty = (n?: number) => (n == null ? '-' : Number(n).toLocaleString())

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
    options.value = (res?.data?.records || []) as StockVO[]
  } catch (e) {
    console.error('加载库存物品失败:', e)
    options.value = []
  } finally {
    loading.value = false
  }
}

const remoteSearch = debounce((kw: string) => {
  void fetchOptions(kw)
}, 300)

function handleChange() {
  const found = options.value.find((o) => String(o.inventoryItemId) === selectedValue.value) || null
  emit('update:modelValue', selectedValue.value)
  emit('change', found)
}

// 物品类型/仓库变化时刷新候选（含首次）
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

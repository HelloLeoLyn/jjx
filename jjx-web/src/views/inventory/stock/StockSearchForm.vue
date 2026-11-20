<template>
  <SearchForm v-model="model" :fields="fields" @search="$emit('search')" @reset="reset">
    <template #quantityRange>
      <el-input-number v-model="model.minQuantity" :min="0" :precision="4" placeholder="最小" :controls="false" style="width: 110px" />
      <span style="margin: 0 6px">至</span>
      <el-input-number v-model="model.maxQuantity" :min="0" :precision="4" placeholder="最大" :controls="false" style="width: 110px" />
    </template>
  </SearchForm>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import SearchForm from '@/components/common-ui/SearchForm.vue'
import type { SearchOptions } from '@/components/common-ui/type'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import { stockApi } from '@/api/inventory/stock'
import { InventoryItemTypeEnum } from '@/enums/inventory'
import { StockSearchStateEnum } from '@/enums/inventory/StockSearchEnum'
import type { MaterialMasterCategory, MaterialDictionaries, MaterialAttributeField } from '@/types/inventory/materialMaster'
import type { StockSearchModel } from './stockSearch'
import { clearStockSearch } from './stockSearch'
import { ElMessage } from 'element-plus'
const props = defineProps<{ modelValue: StockSearchModel }>()
const emit = defineEmits<{ 'update:modelValue': [StockSearchModel]; search: []; reset: [] }>()
const model = computed({ get: () => props.modelValue, set: value => { Object.assign(props.modelValue, value); emit('update:modelValue', props.modelValue) } })
const categories = ref<MaterialMasterCategory[]>([])
const dictionaries = ref<MaterialDictionaries>({})
const warehouses = ref<{ value: number; label: string }[]>([])
const locations = ref<{ value: number; label: string }[]>([])
const attributes = ref<MaterialAttributeField[]>([])
const isProduct = computed(() => model.value.itemType === InventoryItemTypeEnum.PRODUCT.value)
const children = computed(() => categories.value.filter(c => c.parentId === model.value.parentCategoryId && c.selectable))
const fields = computed<SearchOptions[]>(() => {
  const result: SearchOptions[] = [
    { prop: 'keyword', label: '搜索词', type: 'input', width: 340 },
    { prop: 'itemType', label: '库存类型', type: 'select', options: InventoryItemTypeEnum.items },
  ]
  if (!isProduct.value) {
    result.push({ prop: 'parentCategoryId', label: '品类', type: 'select', options: categories.value
      .filter(c => [2, 3].includes(c.categoryLevel ?? 0) && (c.selectable || categories.value.some(child => child.parentId === c.categoryId)))
      .map(c => ({ value: c.categoryId, label: c.pathName })) })
    if (children.value.length) result.push({ prop: 'subCategoryId', label: '子类', type: 'select', options: children.value.map(c => ({ value: c.categoryId, label: c.categoryName })) })
  }
  result.push(
    { prop: 'warehouseId', label: '仓库', type: 'select', options: warehouses.value },
    { prop: 'locationId', label: '库位', type: 'select', advanced: true, options: locations.value },
    { prop: 'stockState', label: '库存状态', type: 'select', advanced: true, options: StockSearchStateEnum.items },
    { prop: 'onlyHasStock', label: '是否有库存', type: 'select', advanced: true, options: [{ value: true, label: '有库存' }, { value: false, label: '零库存' }] },
    { prop: 'quantityRange', label: '记账数量范围', type: 'custom', advanced: true },
    { prop: 'createTimeRange', label: '库存物品创建时间', type: 'daterange', advanced: true },
  )
  if (!isProduct.value) {
    for (const [prop, label, dict] of [['materialMeta', '材质', 'material_meta'], ['materialForm', '形态', 'form'], ['color', '颜色', 'color'], ['brand', '品牌', 'brand']]) {
      result.push({ prop, label, type: 'select', advanced: true, options: (dictionaries.value[dict] || []).map(o => ({ value: o.code, label: o.label })) })
    }
    for (const field of attributes.value) result.push({ prop: `attr__${field.key}`, label: field.label, type: 'select', advanced: true,
      options: field.type === 'boolean' ? [{ value: 'true', label: '是' }, { value: 'false', label: '否' }] : (field.options || []).map(o => ({ value: o.code, label: o.label })) })
  }
  return result
})
let attributeVersion = 0
watch(() => [model.value.parentCategoryId, model.value.subCategoryId], async () => {
  const version = ++attributeVersion
  for (const key of Object.keys(model.value)) if (key.startsWith('attr__')) delete model.value[key]
  attributes.value = []
  const id = model.value.subCategoryId ?? model.value.parentCategoryId
  if (!id || !categories.value.find(c => c.categoryId === id)?.selectable) return
  try {
    const response = await materialMasterApi.configuration(id)
    if (version !== attributeVersion) return
    attributes.value = (response.data?.schema?.fields || []).filter(f => f.key !== 'thickness_mm' && (f.type === 'boolean' || (f.type === 'enum' && f.options?.length))).slice(0, 8)
  } catch (error) { if (version === attributeVersion) ElMessage.error('品类属性加载失败，请重试'); console.error(error) }
})
watch(() => model.value.parentCategoryId, () => { model.value.subCategoryId = undefined })
watch(isProduct, product => {
  if (!product) return
  model.value.parentCategoryId = undefined; model.value.subCategoryId = undefined
  for (const key of ['categoryId', 'materialMeta', 'materialForm', 'color', 'brand', 'attributeFilters']) delete model.value[key]
  for (const key of Object.keys(model.value)) if (key.startsWith('attr__')) delete model.value[key]
})
let locationVersion = 0
watch(() => model.value.warehouseId, async warehouseId => {
  const version = ++locationVersion
  model.value.locationId = undefined; locations.value = []
  if (!warehouseId) return
  try { const res = await stockApi.locations(Number(warehouseId)); if (version === locationVersion) locations.value = res.data || [] }
  catch (error) { if (version === locationVersion) ElMessage.error('库位选项加载失败'); console.error(error) }
})
function reset() { clearStockSearch(model.value); emit('reset') }
onMounted(async () => {
  const results = await Promise.allSettled([materialMasterApi.categories(), materialMasterApi.dictionaries(), stockApi.warehouses()])
  if (results[0].status === 'fulfilled') categories.value = results[0].value.data || []
  if (results[1].status === 'fulfilled') dictionaries.value = results[1].value.data || {}
  if (results[2].status === 'fulfilled') warehouses.value = results[2].value.data || []
  if (results.some(r => r.status === 'rejected')) ElMessage.error('部分搜索选项加载失败，请刷新重试')
})
</script>

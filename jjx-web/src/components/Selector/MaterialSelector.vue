<template>
  <EditableSearchSelect
    :key="selectorRevision"
    :model-value="selectedValue"
    :display-value="displayValue"
    :options="displayOptions"
    option-key="materialId"
    :label-of="labelOf"
    :loading="effLoading"
    :loading-more="effLoadingMore"
    :disabled="disabled"
    :clearable="clearable"
    :min-width="520"
    :total="effTotal"
    :show-create="canShowCreate"
    :create-label="'新建物料「' + search.keyword.value + '」'"
    :placeholder="placeholder"
    :size="size"
    :keep-text-on-blur="keepTextOnBlur"
    :track-input="confirmSelection || allowRawText"
    class="material-selector"
    @focusin="onFocus"
    @draft="handleDraft"
    @search="handleSearch"
    @select="selectMaterial"
    @clear="clear"
    @create="(kw: string) => emit('create', kw)"
    @load-more="handleLoadMore"
  >
    <template #option="{ item }">
      <MaterialSearchOption :item="item" />
    </template>
    <template #footer>
      <div v-if="pending && !allowRawText" role="status" class="material-search-pending">
        搜索文字尚未确认，请选择候选物料，或取消修改
        <el-button link @click="cancelDraft">取消修改</el-button>
      </div>
      <div v-if="effFailed" class="material-search-failed">
        查询失败，<el-button link type="primary" size="small" @click="search.retry()">重试</el-button>
      </div>
    </template>
  </EditableSearchSelect>
</template>

<script setup lang="ts">
/**
 * 公共物料选择器（唯一一套，dev-20261124-006）
 *
 * 以原 BomMaterialSelector 为准合并，覆盖四类场景：
 * 1) BOM/打样：materialId + select + allowRawText/draft；
 * 2) 采购订单：外部候选 options（按供应商）+ labelField=supplierMaterialName + change(id, material)；
 * 3) 入库新建：v-model + valueType（materialId/materialCode/materialName/object）+ change；
 * 4) 工程资源等只选档案的场景。
 *
 * 搜索统一走 useMaterialSearch（keyword 归一、防抖、分页、去重、过期请求丢弃、失败重试）；
 * 外部候选模式下不再自搜，改为把关键词通过 search 事件抛给父组件。
 */
import { computed, ref } from 'vue'
import type { InventoryMaterial } from '@/types/inventory/material'
import EditableSearchSelect from './EditableSearchSelect.vue'
import MaterialSearchOption from './MaterialSearchOption.vue'
import { useMaterialSearch } from './useMaterialSearch'
import { hasPermi } from '@/directives'

type ValueType = 'object' | 'materialId' | 'materialCode' | 'materialName'

const props = withDefaults(defineProps<{
  /** v-model 兼容：对象 / materialId / materialCode / materialName */
  modelValue?: InventoryMaterial | number | string | null
  valueType?: ValueType
  /** BOM 场景直接传 materialId（与 modelValue 二选一） */
  materialId?: number
  materialName?: string
  specification?: string
  materialCode?: string
  showMaterialCode?: boolean
  confirmSelection?: boolean
  /** BOM 原文录入专用：输入同时搜索，未选候选时由调用方保存原文 */
  allowRawText?: boolean
  keepTextOnBlur?: boolean
  browseOnFocus?: boolean
  allowCreate?: boolean
  createPermissions?: string[]
  /** 外部候选（如采购按供应商取料）：有值时不再自搜 */
  options?: InventoryMaterial[]
  /** 展示名优先字段（采购用供商品名） */
  labelField?: 'materialName' | 'supplierMaterialName'
  placeholder?: string
  size?: 'large' | 'default' | 'small'
  disabled?: boolean
  /** 外部候选加载中（如采购按供应商拉料） */
  loading?: boolean
  clearable?: boolean
}>(), {
  modelValue: null,
  valueType: 'object',
  confirmSelection: false,
  allowRawText: false,
  keepTextOnBlur: true,
  browseOnFocus: false,
  allowCreate: true,
  showMaterialCode: false,
  createPermissions: () => ['engineering:bom:add', 'engineering:bom:edit', 'inventory:material:add'],
  options: () => [],
  labelField: 'materialName',
  placeholder: '输入物料名称/编码搜索',
  size: 'small',
  disabled: false,
  loading: false,
  clearable: true,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
  (e: 'change', value: any, material: InventoryMaterial | null): void
  (e: 'select', material: InventoryMaterial): void
  (e: 'clear'): void
  (e: 'create', keyword: string): void
  (e: 'draft-change', value: { text: string; pending: boolean }): void
  (e: 'raw-input', text: string): void
  (e: 'search', keyword: string): void
}>()

const search = useMaterialSearch({
  debounceMs: 400,
  browseOnFocus: props.browseOnFocus,
  pageSize: 10,
})

const external = computed(() => (props.options || []).length > 0)
const displayOptions = computed(() => (external.value ? props.options : search.items.value))
const effLoading = computed(() => props.loading || (external.value ? false : search.loading.value))
const effLoadingMore = computed(() => (external.value ? false : search.loadingMore.value))
const effFailed = computed(() => (external.value ? false : search.failed.value))
const effTotal = computed(() => (external.value ? displayOptions.value.length : search.total.value))

const selectedValue = computed<string | number | null>(() => {
  const mv: any = props.modelValue
  if (mv == null) return props.materialId ?? null
  // EditableSearchSelect 的 model-value 只接受原始主键，对象入参取 materialId
  if (typeof mv === 'object') return mv.materialId ?? null
  return mv
})

const optionLabel = (item: Partial<InventoryMaterial>) => {
  const name = props.labelField === 'supplierMaterialName'
    ? ((item as any).supplierMaterialName || item.materialName || '')
    : (item.materialName || '')
  const base = [name, item.model, item.specification].map((part) => part?.trim()).filter(Boolean).join(' · ')
  return base + (props.showMaterialCode && item.materialCode ? ` (${item.materialCode})` : '')
}

// 与合并前的 BomMaterialSelector 保持一致：默认只回显名称，showMaterialCode 时才带型号/规格/编码
const labelOf = (item: Partial<InventoryMaterial>) => {
  if (props.showMaterialCode) return optionLabel(item)
  if (props.labelField === 'supplierMaterialName') {
    return (item as any).supplierMaterialName || item.materialName || ''
  }
  return item.materialName || ''
}

const displayValue = computed(() => {
  if (props.materialName) {
    return props.showMaterialCode && props.materialId ? optionLabel(props) : props.materialName
  }
  if (props.modelValue != null && typeof props.modelValue === 'object') {
    return labelOf(props.modelValue as InventoryMaterial)
  }
  if (props.modelValue != null) {
    const hit = displayOptions.value.find((o: InventoryMaterial) => {
      const mv: any = props.modelValue
      if (props.valueType === 'materialCode') return o.materialCode === mv
      if (props.valueType === 'materialName') return o.materialName === mv
      return o.materialId === mv
    })
    return hit ? labelOf(hit) : String(props.modelValue)
  }
  return ''
})

// 显示「新建物料」入口：搜过 & 有非空关键字 & 没有与关键字完全同名的物料
const canShowCreate = computed(() => {
  if (external.value) return false
  const kw = search.keyword.value.trim()
  if (!props.allowCreate || !hasPermi(props.createPermissions) || !search.searched.value || search.loading.value || !kw) return false
  return !search.items.value.some((o) => (o.materialName || '').trim() === kw)
})

const pending = ref(false)
function handleDraft(value: { text: string; composing: boolean }) {
  if (props.allowRawText) {
    pending.value = false
    emit('raw-input', value.text)
    return
  }
  if (!props.confirmSelection) return
  pending.value = value.composing || value.text !== (props.materialName || '')
  emit('draft-change', { text: value.text, pending: pending.value })
}

function handleSearch(keyword: string) {
  if (external.value) {
    emit('search', keyword)
    return
  }
  search.search(keyword)
}

function handleLoadMore() {
  if (external.value) return
  search.loadMore()
}

function onFocus() {
  if (external.value) return
  search.browse()
}

const selectorRevision = ref(0)
function cancelDraft() {
  pending.value = false
  emit('draft-change', { text: props.materialName || '', pending: false })
  search.reset()
  selectorRevision.value++
}

function valueOf(material: InventoryMaterial) {
  if (props.valueType === 'materialId') return material.materialId
  if (props.valueType === 'materialCode') return material.materialCode
  if (props.valueType === 'materialName') return material.materialName
  return material
}

function selectMaterial(item: InventoryMaterial) {
  if (!item) return
  pending.value = false
  if (props.confirmSelection) emit('draft-change', { text: item.materialName || '', pending: false })
  emit('select', item)
  emit('update:modelValue', valueOf(item))
  emit('change', valueOf(item), item)
}

function clear() {
  pending.value = false
  if (props.confirmSelection) emit('draft-change', { text: '', pending: false })
  search.reset()
  emit('clear')
  emit('update:modelValue', null)
  emit('change', null, null)
}
</script>

<style scoped>
.material-selector {
  width: 100%;
}
.material-search-pending {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--el-color-warning);
}
.material-search-failed {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>

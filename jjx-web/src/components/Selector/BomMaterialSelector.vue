<template>
  <EditableSearchSelect
    :key="selectorRevision"
    :model-value="materialId"
    :display-value="displayValue"
    :options="search.items.value"
    option-key="materialId"
    :label-of="labelOf"
    :loading="search.loading.value"
    :loading-more="search.loadingMore.value"
    :min-width="520"
    :total="search.total.value"
    :show-create="showCreate"
    :create-label="'新建物料「' + search.keyword.value + '」'"
    placeholder="输入物料名称/编码搜索"
    size="small"
    :keep-text-on-blur="keepTextOnBlur"
    :track-input="confirmSelection"
    @focusin="onFocus"
    @draft="handleDraft"
    @search="search.search"
    @select="selectMaterial"
    @clear="clear"
    @create="(kw: string) => emit('create', kw)"
    @load-more="search.loadMore"
  >
    <template #option="{ item }">
      <MaterialSearchOption :item="item" />
    </template>
    <template #footer>
      <div v-if="pending" role="status" class="bom-search-pending">
        搜索文字尚未确认，请选择候选物料，或取消修改
        <el-button link @click="cancelDraft">取消修改</el-button>
      </div>
      <div v-if="search.failed.value" style="padding: 6px 10px; font-size: 12px; color: var(--el-color-danger)">
        查询失败，<el-button link type="primary" size="small" @click="search.retry()">重试</el-button>
      </div>
    </template>
  </EditableSearchSelect>
</template>

<script setup lang="ts">
/**
 * 公共物料选择器：BOM 行与打样工作台共用（dev-20261018-011 / dev-20261024-013 / 018）
 * 候选展示升级：名称+编码为主，辅以品类/规格/尺寸/单位、客供/样品标识、可展开备注。
 * 搜索统一走 useMaterialSearch：keyword 归一（含全角空格）、防抖、分页 records/total、
 * 去重、过期请求丢弃、失败重试；输入文字不改物料身份，选中/清空才改。
 */
import { computed, ref } from 'vue'
import type { InventoryMaterial } from '@/types/inventory/material'
import EditableSearchSelect from './EditableSearchSelect.vue'
import MaterialSearchOption from './MaterialSearchOption.vue'
import { useMaterialSearch } from './useMaterialSearch'
import { hasPermi } from '@/directives'

const props = withDefaults(defineProps<{
  materialId?: number
  materialName?: string
  specification?: string
  materialCode?: string
  showMaterialCode?: boolean
  confirmSelection?: boolean
  keepTextOnBlur?: boolean
  browseOnFocus?: boolean
  allowCreate?: boolean
  createPermissions?: string[]
}>(), {
  confirmSelection: false,
  keepTextOnBlur: true,
  browseOnFocus: false,
  allowCreate: true,
  showMaterialCode: false,
  createPermissions: () => ['engineering:bom:add', 'engineering:bom:edit', 'inventory:material:add'],
})
const emit = defineEmits<{
  (e: 'select', material: InventoryMaterial): void
  (e: 'clear'): void
  (e: 'create', keyword: string): void
  (e: 'draft-change', value: { text: string; pending: boolean }): void
}>()

const search = useMaterialSearch({
  debounceMs: 400,
  browseOnFocus: props.browseOnFocus,
  pageSize: 10,
})

const pending = ref(false)
function handleDraft(value: { text: string; composing: boolean }) {
  if (!props.confirmSelection) return
  pending.value = value.composing || value.text !== (props.materialName || '')
  emit('draft-change', { text: value.text, pending: pending.value })
}
const selectorRevision = ref(0)
function cancelDraft() {
  pending.value = false
  emit('draft-change', { text: props.materialName || '', pending: false })
  search.reset()
  selectorRevision.value++
}

const canCreate = computed(() => props.allowCreate && hasPermi(props.createPermissions))

const optionLabel = (item: Partial<InventoryMaterial>) => {
  const label = [item.materialName, item.model, item.specification]
    .map((part) => part?.trim())
    .filter(Boolean)
    .join(' · ')
  return label + (props.showMaterialCode && item.materialCode ? ` (${item.materialCode})` : '')
}
const labelOf = (item: InventoryMaterial) => props.showMaterialCode ? optionLabel(item) : item.materialName || ''
const displayValue = computed(() => props.showMaterialCode && props.materialId
  ? optionLabel(props)
  : props.materialName || '')

// 显示「新建物料」入口：搜过 & 有非空关键字 & 没有与关键字完全同名的物料
const showCreate = computed(() => {
  const kw = search.keyword.value.trim()
  if (!canCreate.value || !search.searched.value || search.loading.value || !kw) return false
  return !search.items.value.some((o) => (o.materialName || '').trim() === kw)
})

function onFocus() {
  search.browse()
}

function selectMaterial(item: InventoryMaterial) {
  if (!item) return
  pending.value = false
  if (props.confirmSelection) emit('draft-change', { text: item.materialName || '', pending: false })
  emit('select', item)
}

function clear() {
  pending.value = false
  if (props.confirmSelection) emit('draft-change', { text: '', pending: false })
  search.reset()
  emit('clear')
}
</script>

<style scoped>
.bom-search-pending {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--el-color-warning);
}
</style>

<template>
  <EditableSearchSelect
    :key="selectorRevision"
    :model-value="materialId"
    :display-value="displayValue"
    :options="options"
    option-key="materialId"
    :label-of="labelOf"
    :loading="loading"
    :loading-more="loadingMore"
    :min-width="460"
    :total="total"
    :show-create="showCreate"
    :create-label="'新建物料「' + keyword + '」'"
    placeholder="输入物料名称搜索"
    size="small"
    :keep-text-on-blur="keepTextOnBlur"
    :track-input="confirmSelection"
    @focusin="onFocus"
    @draft="handleDraft"
    @search="search"
    @select="selectMaterial"
    @clear="clear"
    @create="(kw: string) => emit('create', kw)"
    @load-more="loadMore"
  >
    <template #option="{ item }">
      {{ optionLabel(item) }}
    </template>
    <template #footer>
      <div v-if="pending" role="status" class="bom-search-pending">
        搜索文字尚未确认，请选择候选物料，或取消修改
        <el-button link @click="cancelDraft">取消修改</el-button>
      </div>
      <div v-if="failed" style="padding: 6px 10px; font-size: 12px; color: var(--el-color-danger)">
        查询失败，<el-button link type="primary" size="small" @click="retry">重试</el-button>
      </div>
    </template>
  </EditableSearchSelect>
</template>

<script setup lang="ts">
/**
 * 公共物料选择器：BOM 行与打样工作台共用（dev-20261018-011 / dev-20261024-013）
 * 由 el-select 改为可编辑搜索框（EditableSearchSelect）：选中后文字可直接删改并续搜。
 * 统一 keyword 多词搜索、400ms 防抖、分页和请求隔离；默认保留 BOM 草稿/权限语义。
 * 工作台可显式开启编码回显、空词浏览，并传入原有建档权限；无建档入口的场景关闭 allowCreate。
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { materialApi } from '@/api/inventory/material'
import type { InventoryMaterial } from '@/types/inventory/material'
import EditableSearchSelect from './EditableSearchSelect.vue'
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
  search('')
  selectorRevision.value++
}

const options = ref<InventoryMaterial[]>([])
const keyword = ref('')
const loading = ref(false)
const loadingMore = ref(false)
const failed = ref(false)
const searched = ref(false)
const total = ref(0)

// 默认沿用 BOM/物料建档权限；工作台显式传自己的原有权限，不扩大入口授权。
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
// 2026-10-21：同名不同规格是常态（库内单个名称最多 43 个规格），只在 100% 同名时才隐藏建档行
const showCreate = computed(() => {
  const kw = keyword.value.trim()
  if (!canCreate.value || !searched.value || loading.value || !kw) return false
  return !options.value.some((o) => (o.materialName || '').trim() === kw)
})

let page = 1
let requestId = 0
let timer: ReturnType<typeof setTimeout> | undefined

async function fetchPage(id: number, nextPage: number) {
  try {
    const res = await materialApi.search({
      keyword: keyword.value,
      pageNum: nextPage,
      pageSize: 10,
    })
    if (id !== requestId) return
    if (!res.data) throw new Error('物料查询未返回分页数据')
    const records = res.data.records
    const seen = new Set(options.value.map((item) => item.materialId))
    options.value = nextPage === 1 ? records : [...options.value, ...records.filter((item) => {
      if (seen.has(item.materialId)) return false
      seen.add(item.materialId)
      return true
    })]
    total.value = res.data.total
    page = nextPage
    searched.value = true
    failed.value = false
  } catch (error) {
    if (id !== requestId) return
    failed.value = true
    searched.value = false
    console.error('BOM物料搜索失败:', error)
  } finally {
    if (id === requestId) loading.value = false
  }
}

function search(value: string) {
  clearTimeout(timer)
  const id = ++requestId
  keyword.value = value.replace(/[\s\u3000]+/g, ' ').trim()
  options.value = []
  total.value = 0
  searched.value = false
  failed.value = false
  page = 1
  loadingMore.value = false
  loading.value = !!keyword.value || props.browseOnFocus
  if (loading.value) timer = setTimeout(() => fetchPage(id, 1), 400)
}

function onFocus() {
  if (props.browseOnFocus && !searched.value && !loading.value) search(keyword.value)
}

async function loadMore() {
  if (loading.value || loadingMore.value || failed.value || options.value.length >= total.value) return
  if (!keyword.value && !props.browseOnFocus) return
  const id = requestId
  loadingMore.value = true
  try {
    await fetchPage(id, page + 1)
  } finally {
    if (id === requestId) loadingMore.value = false
  }
}

function selectMaterial(item: InventoryMaterial) {
  if (item) {
    pending.value = false
    if (props.confirmSelection) emit('draft-change', { text: item.materialName || '', pending: false })
    emit('select', item)
  }
}

function retry() {
  search(keyword.value)
}

function clear() {
  pending.value = false
  if (props.confirmSelection) emit('draft-change', { text: '', pending: false })
  search('')
  emit('clear')
}

onBeforeUnmount(() => {
  clearTimeout(timer)
  requestId++
})
</script>

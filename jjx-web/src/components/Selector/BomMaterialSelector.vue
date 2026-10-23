<template>
  <EditableSearchSelect
    :key="selectorRevision"
    :model-value="materialId"
    :display-value="materialName"
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
    keep-text-on-blur
    :track-input="confirmSelection"
    @draft="handleDraft"
    @search="search"
    @select="selectMaterial"
    @clear="clear"
    @create="(kw: string) => emit('create', kw)"
    @load-more="loadMore"
  >
    <template #option="{ item }">
      {{ item.materialName }} · {{ item.specification || '无规格' }}
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
 * BOM 行「物料名称」选择器（dev-20261018-011）
 * 由 el-select 改为可编辑搜索框（EditableSearchSelect）：选中后文字可直接删改并续搜。
 * 保留：远程搜索 400ms 防抖（至少 2 字符）、加载更多、新建物料入口（权限在父级/指令侧）、已选回填、清空。
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { materialApi } from '@/api/inventory/material'
import type { InventoryMaterial } from '@/types/inventory/material'
import EditableSearchSelect from './EditableSearchSelect.vue'
import { hasPermi } from '@/directives'

const props = withDefaults(defineProps<{ materialId?: number; materialName?: string; confirmSelection?: boolean }>(), { confirmSelection: false })
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

// 建档入口权限：工程编 BOM 可顺手补材料；审核员凭 inventory:material:add 在审核页补档（dev-20261020-029 D）
const BOM_MATERIAL_PERMS = [
  'engineering:bom:add',
  'engineering:bom:edit',
  'inventory:material:add',
]
const canCreate = computed(() => hasPermi(BOM_MATERIAL_PERMS))

const labelOf = (item: InventoryMaterial) => item.materialName || ''

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
    options.value = nextPage === 1 ? res.data.records : [...options.value, ...res.data.records]
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
  keyword.value = value.trim()
  options.value = []
  total.value = 0
  searched.value = false
  failed.value = false
  page = 1
  loading.value = !!keyword.value
  if (loading.value) timer = setTimeout(() => fetchPage(id, 1), 400)
}

async function loadMore() {
  if (loading.value || loadingMore.value || !keyword.value) return
  loadingMore.value = true
  try {
    await fetchPage(requestId, page + 1)
  } finally {
    loadingMore.value = false
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

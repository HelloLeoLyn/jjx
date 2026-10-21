<template>
  <EditableSearchSelect
    :model-value="materialId"
    :display-value="materialName"
    :options="options"
    option-key="materialId"
    :label-of="labelOf"
    :loading="loading"
    :total="total"
    :show-create="showCreate"
    :create-label="'新建物料「' + keyword + '」'"
    placeholder="输入物料名称搜索"
    size="small"
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
 * 保留：远程搜索 300ms 防抖、加载更多、新建物料入口（权限在父级/指令侧）、已选回填、清空。
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { materialApi } from '@/api/inventory/material'
import type { InventoryMaterial } from '@/types/inventory/material'
import EditableSearchSelect from './EditableSearchSelect.vue'
import { hasPermi } from '@/directives'

const props = defineProps<{ materialId?: number; materialName?: string }>()
const emit = defineEmits<{
  (e: 'select', material: InventoryMaterial): void
  (e: 'clear'): void
  (e: 'create', keyword: string): void
}>()

const options = ref<InventoryMaterial[]>([])
const keyword = ref('')
const loading = ref(false)
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

// 显示「新建物料」入口：搜过 & 有非空关键字 & 结果里没有与关键字同名的物料
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
      materialName: keyword.value,
      pageNum: nextPage,
      pageSize: 20,
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
  loading.value = !!keyword.value
  if (keyword.value) timer = setTimeout(() => fetchPage(id, 1), 300)
}

function loadMore() {
  if (loading.value) return
  loading.value = true
  void fetchPage(requestId, page + 1)
}

function selectMaterial(item: InventoryMaterial) {
  if (item) emit('select', item)
}

function retry() {
  search(keyword.value)
}

function clear() {
  search('')
  emit('clear')
}

onBeforeUnmount(() => {
  clearTimeout(timer)
  requestId++
})
</script>

<template>
  <el-select
    :model-value="materialId || materialName || undefined"
    filterable
    remote
    clearable
    :remote-method="search"
    :loading="loading"
    placeholder="输入物料名称搜索"
    size="small"
    style="width: 100%"
    @change="selectMaterial"
    @clear="clear"
  >
    <el-option
      v-if="materialName && !options.some((item) => item.materialId === materialId)"
      :value="materialId || materialName"
      :label="materialName"
      hidden
    />
    <el-option
      v-for="item in options"
      :key="item.materialId"
      :value="item.materialId!"
      :label="item.materialName"
    >
      {{ item.materialName }} · {{ item.specification || '无规格' }} · {{ item.materialCode }}
    </el-option>
    <template #footer>
      <div v-if="failed">
        查询失败，<el-button link type="primary" @click="search(keyword)">重试</el-button>
      </div>
      <template v-else>
        <el-button
          v-if="showCreate"
          v-hasPermi="BOM_MATERIAL_PERMS"
          link
          type="primary"
          @click="emit('create', keyword)"
          >新建物料「{{ keyword }}」</el-button
        >
        <el-button v-if="options.length < total" link :loading="loading" @click="loadMore">
          加载更多
        </el-button>
      </template>
    </template>
  </el-select>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { materialApi } from '@/api/inventory/material'
import type { InventoryMaterial } from '@/types/inventory/material'

const props = defineProps<{ materialId?: number; materialName?: string }>()
const emit = defineEmits<{
  (e: 'select', material: InventoryMaterial): void
  (e: 'clear'): void
  (e: 'create', keyword: string): void
}>()
// 建档入口权限：跟随 BOM 新增/修改权限（谁能编 BOM 谁就能顺手补材料）
const BOM_MATERIAL_PERMS = ['engineering:bom:add', 'engineering:bom:edit']

const options = ref<InventoryMaterial[]>([])
const keyword = ref('')
const loading = ref(false)
const failed = ref(false)
const searched = ref(false)
const total = ref(0)

// 显示「新建物料」入口：搜过 & 有非空关键字 & 结果里没有与关键字同名的物料
const showCreate = computed(() => {
  const kw = keyword.value.trim()
  if (!searched.value || loading.value || !kw) return false
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

function selectMaterial(value: number | string | undefined) {
  const material = options.value.find((item) => item.materialId === value)
  if (material) emit('select', material)
}

function clear() {
  search('')
  emit('clear')
}

watch(
  () => props.materialId,
  () => search('')
)

onBeforeUnmount(() => {
  clearTimeout(timer)
  requestId++
})
</script>

<template>
  <el-select
    v-model="selectedValue"
    filterable
    remote
    reserve-keyword
    allow-create
    :remote-method="handleRemoteSearch"
    :placeholder="placeholder"
    :size="size"
    :clearable="clearable"
    :disabled="disabled"
    :loading="searchLoading"
    :popper-class="'material-select-popper'"
    @change="handleSelect"
    @clear="handleClear"
    @visible-change="handleVisibleChange"
    class="material-complete-selector"
  >
    <el-option
      v-for="item in optionList"
      :key="item.materialId"
      :label="item.materialCode"
      :value="item.materialCode"
    >
      <div class="material-suggestion">
        <span class="code">{{ item.materialCode }}</span>
        <span class="name">{{ item.materialName }}</span>
        <span class="spec">{{ item.specification || '' }}</span>
      </div>
    </el-option>

    <!-- 加载更多按钮 -->
    <div
      v-if="hasMore && optionList.length > 0"
      class="load-more-tip"
      ref="loadMoreRef"
      @click.stop="loadNextPage"
    >
      <span v-if="loadingMore" class="loading-text">加载中...</span>
      <span v-else class="scroll-tip">点击加载更多</span>
    </div>
    <div v-else-if="optionList.length > 0 && !searchLoading" class="no-more-tip">没有更多了</div>
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch, onUnmounted } from 'vue'
import type { InventoryMaterial } from '@/types/inventory/material'
import { materialApi } from '@/api/inventory/material'

interface Props {
  modelValue: string
  placeholder?: string
  size?: 'large' | 'default' | 'small'
  clearable?: boolean
  disabled?: boolean
  debounceDelay?: number
  minKeywordLength?: number
  autoSelectFirst?: boolean
}

interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'materialSelect', material: InventoryMaterial): void
  (e: 'clear'): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '请输入物料编码',
  size: 'small',
  clearable: true,
  disabled: false,
  debounceDelay: 500,
  minKeywordLength: 2,
  autoSelectFirst: false,
})

const emit = defineEmits<Emits>()

// ==================== 响应式数据 ====================

const selectedValue = ref<string>(props.modelValue)
const optionList = ref<InventoryMaterial[]>([])
const searchLoading = ref(false)
const loadingMore = ref(false)
const loadMoreRef = ref<HTMLElement | null>(null)

// 分页状态
const currentKeyword = ref('')
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const hasMore = computed(() => optionList.value.length < total.value)

// 搜索结果缓存: key -> { records, total }
interface CacheEntry {
  records: InventoryMaterial[]
  total: number
}
const searchCache = new Map<string, CacheEntry>()

// 物料缓存（按编码）
const materialCache = new Map<string, InventoryMaterial>()

// 是否正在加载中（用于防抖）
let searchTimer: ReturnType<typeof setTimeout> | null = null

// ==================== 监听外部值变化 ====================

watch(
  () => props.modelValue,
  (val) => {
    selectedValue.value = val
  }
)

// ==================== 搜索方法 ====================

/**
 * 远程搜索（el-select 的 remote-method）
 */
const handleRemoteSearch = async (query: string) => {
  if (!query || query.length < props.minKeywordLength) {
    optionList.value = []
    return
  }

  // 清除之前的定时器
  if (searchTimer) {
    clearTimeout(searchTimer)
  }

  searchTimer = setTimeout(async () => {
    currentKeyword.value = query
    pageNum.value = 1

    // 检查缓存
    const cacheKey = query.toLowerCase().trim()
    const cached = searchCache.get(cacheKey)
    if (cached) {
      optionList.value = cached.records
      total.value = cached.total
      return
    }

    searchLoading.value = true

    try {
      const res = await materialApi.search({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        materialCode: query,
        materialName: query,
      } as any)

      if (res.data) {
        const records = res.data.records || []
        total.value = res.data.total || 0
        optionList.value = records

        // 缓存搜索结果
        searchCache.set(cacheKey, { records: [...records], total: total.value })

        // 缓存物料（按编码）
        records.forEach((item: InventoryMaterial) => {
          materialCache.set(item.materialCode.toLowerCase(), item)
        })
      } else {
        optionList.value = []
        total.value = 0
      }
    } catch (error) {
      console.error('搜索物料失败:', error)
      optionList.value = []
      total.value = 0
    } finally {
      searchLoading.value = false
    }
  }, props.debounceDelay)
}

/**
 * 加载下一页
 */
const loadNextPage = async () => {
  if (loadingMore.value || !hasMore.value || !currentKeyword.value) return

  loadingMore.value = true
  pageNum.value++

  try {
    const res = await materialApi.search({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      materialCode: currentKeyword.value,
      materialName: currentKeyword.value,
    } as any)

    if (res.data) {
      const records = res.data.records || []
      total.value = res.data.total || 0

      // 追加到列表
      optionList.value = [...optionList.value, ...records]

      // 更新缓存
      const cacheKey = currentKeyword.value.toLowerCase().trim()
      searchCache.set(cacheKey, { records: [...optionList.value], total: total.value })

      // 缓存新物料
      records.forEach((item: InventoryMaterial) => {
        materialCache.set(item.materialCode.toLowerCase(), item)
      })
    }
  } catch (error) {
    console.error('加载更多物料失败:', error)
    pageNum.value--
  } finally {
    loadingMore.value = false
  }
}

// ==================== 下拉面板事件 ====================

/**
 * 下拉面板显示/隐藏
 */
const handleVisibleChange = (visible: boolean) => {
  // 不需要滚动监听了，改用点击加载更多按钮
}

// ==================== 选择/清除 ====================

/**
 * 选择物料
 */
const handleSelect = (value: string) => {
  if (!value) return

  // 从缓存或列表中查找
  const material =
    materialCache.get(value.toLowerCase()) ||
    optionList.value.find((item) => item.materialCode === value)

  if (material) {
    emit('materialSelect', material)
  }
}

/**
 * 清除
 */
const handleClear = () => {
  selectedValue.value = ''
  optionList.value = []
  currentKeyword.value = ''
  pageNum.value = 1
  total.value = 0
  emit('clear')
}

// ==================== 生命周期 ====================

onUnmounted(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
})
</script>

<style scoped>
.material-complete-selector {
  width: 100%;
}

.material-suggestion {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 4px 0;

  .code {
    font-weight: 500;
    color: #409eff;
    min-width: 100px;
  }

  .name {
    flex: 1;
    color: #606266;
  }

  .spec {
    color: #909399;
    font-size: 12px;
  }
}
</style>

<style>
/* 全局样式 - 加载更多提示 */
.material-select-popper .load-more-tip {
  text-align: center;
  padding: 8px 0;
  font-size: 12px;
  color: #909399;
  cursor: pointer;
  user-select: none;
  transition: background-color 0.2s;
}

.material-select-popper .load-more-tip:hover {
  background-color: #f5f7fa;
}

.material-select-popper .load-more-tip .loading-text {
  color: #409eff;
  cursor: default;
}

.material-select-popper .load-more-tip .scroll-tip {
  color: #409eff;
}

.material-select-popper .no-more-tip {
  text-align: center;
  padding: 8px 0;
  font-size: 12px;
  color: #c0c4cc;
}
</style>

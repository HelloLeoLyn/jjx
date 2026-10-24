import { getCurrentInstance, onBeforeUnmount, ref } from 'vue'
import { materialApi } from '@/api/inventory/material'
import type { InventoryMaterial } from '@/types/inventory/material'

/** 全角空格/连续空白归一，作为后端 keyword（多词 AND 由后端处理）。 */
export function normalizeMaterialKeyword(value: string): string {
  return (value || '').replace(/[\s\u3000]+/g, ' ').trim()
}

export interface MaterialSearchOptions {
  pageSize?: number
  debounceMs?: number
  minKeywordLength?: number
  browseOnFocus?: boolean
  onFirstPage?: (items: InventoryMaterial[]) => void
}

/**
 * 公共物料搜索（dev-20261024-018）：
 * 统一 keyword 归一、防抖、分页 records/total、去重、过期请求丢弃、失败重试、清空作废。
 * 只负责取数与状态，不改调用方身份；选候选/清空由调用方决定。
 */
export function useMaterialSearch(options: MaterialSearchOptions = {}) {
  const pageSize = options.pageSize ?? 10
  const debounceMs = options.debounceMs ?? 400
  const minKeywordLength = options.minKeywordLength ?? 0

  const keyword = ref('')
  const items = ref<InventoryMaterial[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const failed = ref(false)
  const searched = ref(false)
  const total = ref(0)
  let page = 1
  let requestId = 0
  let timer: ReturnType<typeof setTimeout> | undefined

  function cancelPending() {
    clearTimeout(timer)
    timer = undefined
    requestId++
  }

  async function fetchPage(id: number, nextPage: number) {
    try {
      const response = await materialApi.search({
        keyword: keyword.value,
        pageNum: nextPage,
        pageSize,
      })
      if (id !== requestId) return
      if (!response.data) throw new Error('物料查询未返回分页数据')
      const records = response.data.records || []
      const seen = new Set(items.value.map((item) => item.materialId))
      items.value =
        nextPage === 1
          ? records
          : [...items.value, ...records.filter((item) => {
              if (seen.has(item.materialId)) return false
              seen.add(item.materialId)
              return true
            })]
      total.value = response.data.total || 0
      page = nextPage
      searched.value = true
      failed.value = false
      if (nextPage === 1) options.onFirstPage?.(items.value)
    } catch (error) {
      if (id !== requestId) return
      failed.value = true
      searched.value = false
      console.error('物料搜索失败:', error)
    } finally {
      if (id === requestId) {
        loading.value = false
        loadingMore.value = false
      }
    }
  }

  /** 输入变化：作废旧请求与定时器，清空结果；空词且未开启浏览时不发请求。 */
  function search(value: string) {
    cancelPending()
    const id = requestId
    keyword.value = normalizeMaterialKeyword(value)
    items.value = []
    total.value = 0
    searched.value = false
    failed.value = false
    page = 1
    loadingMore.value = false
    const shouldSearch = !!keyword.value && keyword.value.length >= minKeywordLength
    loading.value = shouldSearch || !!options.browseOnFocus
    if (shouldSearch) {
      timer = setTimeout(() => void fetchPage(id, 1), debounceMs)
    } else if (!options.browseOnFocus || !keyword.value) {
      loading.value = false
    }
  }

  /** 聚焦浏览：未搜过且开启了 browseOnFocus 时拉第一页。 */
  function browse() {
    if (!options.browseOnFocus || searched.value || loading.value) return
    cancelPending()
    const id = requestId
    loading.value = true
    void fetchPage(id, 1)
  }

  async function loadMore() {
    if (loading.value || loadingMore.value || failed.value) return
    if (items.value.length >= total.value) return
    if (!keyword.value && !options.browseOnFocus) return
    loadingMore.value = true
    await fetchPage(requestId, page + 1)
  }

  function retry() {
    search(keyword.value)
  }

  /** 显式清空：作废在途请求，状态归零。 */
  function reset() {
    cancelPending()
    keyword.value = ''
    items.value = []
    total.value = 0
    searched.value = false
    failed.value = false
    loading.value = false
    loadingMore.value = false
    page = 1
  }

  if (getCurrentInstance()) onBeforeUnmount(cancelPending)

  return { keyword, items, loading, loadingMore, failed, searched, total, search, browse, loadMore, retry, reset }
}

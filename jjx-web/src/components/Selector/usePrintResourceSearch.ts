import { onBeforeUnmount, ref } from 'vue'
import { printingSearchApi, type PrintSearchRow } from '@/api/engineering/printingSearch'

/**
 * 印刷资源搜索（dev-20261125-011）：防抖、分页、丢弃晚到结果、失败重试。
 * 交互语义对齐 useMaterialSearch，但不复用物料数据结构。
 */
export function usePrintResourceSearch(options?: { debounceMs?: number; pageSize?: number; scene?: string }) {
  const debounceMs = options?.debounceMs ?? 300
  const pageSize = options?.pageSize ?? 10
  const keyword = ref('')
  const items = ref<PrintSearchRow[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const failed = ref(false)
  const total = ref(0)
  const searched = ref(false)
  let seq = 0
  let timer: ReturnType<typeof setTimeout> | undefined

  async function fetchPage(kw: string, pageNum: number, append: boolean) {
    const current = ++seq
    if (append) loadingMore.value = true
    else loading.value = true
    failed.value = false
    try {
      const res = await printingSearchApi.search({ q: kw, pageNum, pageSize, ...(options?.scene ? { scene: options.scene } : {}) })
      if (current !== seq) return
      const page = res.data
      const records = page?.records || []
      items.value = append ? [...items.value, ...records] : records
      total.value = Number(page?.total || 0)
      searched.value = true
    } catch {
      if (current !== seq) return
      failed.value = true
    } finally {
      if (current === seq) {
        loading.value = false
        loadingMore.value = false
      }
    }
  }

  function search(kw: string) {
    keyword.value = kw
    if (timer) clearTimeout(timer)
    const trimmed = (kw || '').trim()
    if (!trimmed) {
      seq++
      items.value = []
      total.value = 0
      searched.value = false
      loading.value = false
      loadingMore.value = false
      return
    }
    timer = setTimeout(() => { void fetchPage(trimmed, 1, false) }, debounceMs)
  }

  async function loadMore() {
    if (loading.value || loadingMore.value) return
    if (items.value.length >= total.value) return
    const next = Math.floor(items.value.length / pageSize) + 1
    await fetchPage(keyword.value.trim(), next, true)
  }

  async function retry() {
    if (!keyword.value.trim()) return
    await fetchPage(keyword.value.trim(), 1, false)
  }

  function reset() {
    seq++
    if (timer) clearTimeout(timer)
    keyword.value = ''
    items.value = []
    total.value = 0
    searched.value = false
    failed.value = false
    loading.value = false
    loadingMore.value = false
  }

  onBeforeUnmount(reset)

  return { keyword, items, loading, loadingMore, failed, total, searched, search, loadMore, retry, reset }
}

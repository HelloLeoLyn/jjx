// src/composables/useTable.ts
import { ref, reactive } from 'vue'

interface UseTableOptions<T, P> {
  api: (params: P) => Promise<{ data: { records: T[]; total: number } }>
  immediate?: boolean
  defaultParams?: Partial<P>
}

export function useTable<T, P extends Record<string, any>>(
  options: UseTableOptions<T, P>,
) {
  const data = ref<T[]>([])
  const loading = ref(false)
  const total = ref(0)
  const pageNum = ref(1)
  const pageSize = ref(10)

  const queryParams = reactive({
    ...options.defaultParams,
    pageNum: pageNum.value,
    pageSize: pageSize.value,
  }) as P

  const getList = async () => {
    loading.value = true
    try {
      const res = await options.api({
        ...queryParams,
        pageNum: pageNum.value,
        pageSize: pageSize.value,
      })
      data.value = res.data.records || []
      total.value = res.data.total || 0
    } finally {
      loading.value = false
    }
  }

  const handleSearch = () => {
    pageNum.value = 1
    getList()
  }

  const handleReset = () => {
    Object.keys(queryParams).forEach((key) => {
      if (key !== 'pageNum' && key !== 'pageSize') {
        delete (queryParams as any)[key]
      }
    })
    pageNum.value = 1
    getList()
  }

  const handlePageChange = (page: number) => {
    pageNum.value = page
    getList()
  }

  const handleSizeChange = (size: number) => {
    pageSize.value = size
    pageNum.value = 1
    getList()
  }

  if (options.immediate !== false) {
    getList()
  }

  return {
    data,
    loading,
    total,
    pageNum,
    pageSize,
    queryParams,
    getList,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
  }
}

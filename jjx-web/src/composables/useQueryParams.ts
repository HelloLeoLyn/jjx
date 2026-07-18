import { reactive, watch, ref, computed } from 'vue'
import { debounce } from 'lodash-es'

export interface QueryParamsOptions {
  /**
   * 防抖时间（毫秒）
   * 设置为0或false禁用防抖
   */
  debounce?: number | false
  /**
   * 是否立即执行搜索
   */
  immediate?: boolean
  /**
   * 搜索回调函数
   */
  onSearch?: (params: Record<string, any>) => void | Promise<void>
  /**
   * 字段映射（将表单字段映射到查询参数字段）
   */
  fieldMapping?: Record<string, string>
  /**
   * 是否保留分页参数
   */
  keepPagination?: boolean
  /**
   * 分页字段名
   */
  paginationFields?: {
    pageNum?: string
    pageSize?: string
  }
}

/**
 * 响应式查询参数管理组合式函数
 * 自动处理表单数据到查询参数的转换，避免手动赋值
 *
 * @example
 * ```typescript
 * const { queryParams, searchParams, reset } = useQueryParams(
 *   {
 *     userName: '',
 *     phoneNumber: '',
 *     status: '',
 *     pageNum: 1,
 *     pageSize: 10
 *   },
 *   {
 *     onSearch: (params) => {
 *       // 直接使用处理好的查询参数
 *       getList()
 *     }
 *   }
 * )
 *
 * // 在SearchContainer中绑定searchParams
 * <SearchContainer v-model="searchParams" @search="() => {}" />
 * ```
 */
export function useQueryParams(
  defaultParams: Record<string, any>,
  options: QueryParamsOptions = {},
) {
  const {
    debounce: debounceTime = 300,
    immediate = false,
    onSearch,
    fieldMapping = {},
    keepPagination = true,
    paginationFields = {
      pageNum: 'pageNum',
      pageSize: 'pageSize',
    },
  } = options

  // 查询参数（用于API请求）
  const queryParams = reactive({ ...defaultParams })

  // 搜索参数（表单绑定）
  const searchParams = reactive({ ...defaultParams })

  // 是否正在搜索
  const searching = ref(false)

  // 上次搜索的参数（用于比较）
  const lastSearchParams = ref({ ...defaultParams })

  /**
   * 将表单参数转换为查询参数
   */
  const transformToQueryParams = (
    formParams: Record<string, any>,
  ): Record<string, any> => {
    const result: Record<string, any> = {}

    Object.keys(formParams).forEach((key) => {
      const value = formParams[key]
      const targetKey = fieldMapping[key] || key

      // 过滤空值（但保留0和false）
      if (value !== '' && value !== null && value !== undefined) {
        // 处理日期范围
        if (Array.isArray(value) && value.length === 2) {
          // 日期范围转换为开始和结束时间
          result[`${targetKey}Start`] = value[0]
          result[`${targetKey}End`] = value[1]
        } else {
          result[targetKey] = value
        }
      }
    })

    return result
  }

  /**
   * 更新查询参数
   */
  const updateQueryParams = (formParams: Record<string, any>) => {
    const transformed = transformToQueryParams(formParams)

    // 清除非分页字段
    if (!keepPagination) {
      Object.keys(queryParams).forEach((key) => {
        if (
          key !== paginationFields.pageNum &&
          key !== paginationFields.pageSize &&
          !key.endsWith('Start') &&
          !key.endsWith('End')
        ) {
          queryParams[key] = defaultParams[key]
        }
      })
    }

    // 设置新的查询参数
    Object.keys(transformed).forEach((key) => {
      queryParams[key] = transformed[key]
    })

    // 重置分页到第一页（如果查询条件变化）
    if (paginationFields.pageNum && shouldResetPage(formParams)) {
      queryParams[paginationFields.pageNum] = 1
    }

    // 保存上次搜索参数
    lastSearchParams.value = { ...formParams }
  }

  /**
   * 判断是否需要重置分页
   */
  const shouldResetPage = (currentParams: Record<string, any>): boolean => {
    const last = lastSearchParams.value
    const searchFields = Object.keys(currentParams).filter(
      (key) =>
        key !== paginationFields.pageNum && key !== paginationFields.pageSize,
    )

    return searchFields.some((key) => {
      const current = currentParams[key]
      const lastValue = last[key]

      // 简单的值比较
      if (Array.isArray(current) && Array.isArray(lastValue)) {
        return JSON.stringify(current) !== JSON.stringify(lastValue)
      }
      return current !== lastValue
    })
  }

  /**
   * 执行搜索
   */
  const executeSearch = async () => {
    if (searching.value) return

    searching.value = true
    try {
      updateQueryParams(searchParams)

      if (onSearch) {
        await onSearch(queryParams)
      }
    } finally {
      searching.value = false
    }
  }

  /**
   * 防抖搜索函数
   */
  let debouncedSearch: any = null
  if (debounceTime) {
    debouncedSearch = debounce(executeSearch, debounceTime)
  }

  /**
   * 重置搜索参数
   */
  const reset = () => {
    Object.keys(searchParams).forEach((key) => {
      searchParams[key] = defaultParams[key]
    })

    // 立即执行搜索（重置后）
    if (immediate) {
      executeSearch()
    }
  }

  /**
   * 手动触发搜索
   */
  const search = () => {
    if (debounceTime && debouncedSearch) {
      debouncedSearch()
    } else {
      executeSearch()
    }
  }

  /**
   * 取消防抖搜索
   */
  const cancelDebouncedSearch = () => {
    if (debounceTime && debouncedSearch && debouncedSearch.cancel) {
      debouncedSearch.cancel()
    }
  }

  /**
   * 立即搜索（跳过防抖）
   */
  const searchImmediate = () => {
    cancelDebouncedSearch()
    executeSearch()
  }

  /**
   * 获取当前查询参数（只读）
   */
  const currentQueryParams = computed(() => ({ ...queryParams }))

  /**
   * 获取当前搜索参数（只读）
   */
  const currentSearchParams = computed(() => ({ ...searchParams }))

  /**
   * 是否有查询条件（排除分页和空值）
   */
  const hasSearchConditions = computed(() => {
    return Object.keys(searchParams).some((key) => {
      if (
        key === paginationFields.pageNum ||
        key === paginationFields.pageSize
      ) {
        return false
      }
      const value = searchParams[key]
      const defaultValue = defaultParams[key]
      return (
        value !== '' &&
        value !== null &&
        value !== undefined &&
        value !== defaultValue
      )
    })
  })

  // 监听searchParams变化，自动触发搜索
  if (immediate) {
    watch(
      () => ({ ...searchParams }),
      (newParams, oldParams) => {
        // 避免初始化和重置时重复触发
        if (JSON.stringify(newParams) !== JSON.stringify(oldParams)) {
          search()
        }
      },
      { deep: true },
    )
  }

  return {
    // 状态
    queryParams,
    searchParams,
    searching,

    // 计算属性
    currentQueryParams,
    currentSearchParams,
    hasSearchConditions,

    // 方法
    reset,
    search,
    searchImmediate,
    cancelDebouncedSearch,
    executeSearch,

    // 工具方法
    transformToQueryParams,
    updateQueryParams,
  }
}

/**
 * 简化的查询参数Hook（适用于简单场景）
 */
export function useSimpleQueryParams(
  defaultParams: Record<string, any>,
  onSearch?: (params: Record<string, any>) => void,
) {
  return useQueryParams(defaultParams, {
    debounce: 300,
    immediate: true,
    onSearch,
  })
}

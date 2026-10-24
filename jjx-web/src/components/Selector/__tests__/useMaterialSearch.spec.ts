import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { effectScope } from 'vue'
import { useMaterialSearch, normalizeMaterialKeyword } from '../useMaterialSearch'
import { materialApi } from '@/api/inventory/material'

vi.mock('@/api/inventory/material', () => ({
  materialApi: { search: vi.fn() },
}))

const page = (ids: number[], total = ids.length) => ({
  data: {
    records: ids.map((id) => ({ materialId: id, materialName: 'M' + id, materialCode: 'C' + id })),
    total,
  },
})

function withSearch(options = {}) {
  const scope = effectScope()
  const search = scope.run(() => useMaterialSearch({ debounceMs: 50, pageSize: 10, ...options }))!
  return { scope, search }
}

describe('useMaterialSearch 公共物料搜索', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(materialApi.search).mockReset()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('keyword 归一：全角空格与连续空白压成一个空格', () => {
    expect(normalizeMaterialKeyword('  PET　008  ')).toBe('PET 008')
    expect(normalizeMaterialKeyword('a\u3000 \u3000b')).toBe('a b')
  })

  it('防抖后按 records/total 取第一页，输入变化会重置结果', async () => {
    vi.mocked(materialApi.search).mockResolvedValue(page([1, 2], 5) as any)
    const { search } = withSearch()
    search.search('PET')
    expect(materialApi.search).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(60)
    expect(materialApi.search).toHaveBeenCalledWith({ keyword: 'PET', pageNum: 1, pageSize: 10 })
    expect(search.items.value.map((item) => item.materialId)).toEqual([1, 2])
    expect(search.total.value).toBe(5)

    search.search('PC')
    expect(search.items.value).toEqual([])
    expect(search.total.value).toBe(0)
  })

  it('过期响应晚返不覆盖新结果', async () => {
    let resolveFirst!: (value: unknown) => void
    vi.mocked(materialApi.search)
      .mockImplementationOnce(() => new Promise((resolve) => (resolveFirst = resolve)) as any)
      .mockResolvedValueOnce(page([9]) as any)
    const { search } = withSearch()
    search.search('A')
    await vi.advanceTimersByTimeAsync(60)
    search.search('B')
    await vi.advanceTimersByTimeAsync(60)
    expect(search.items.value.map((item) => item.materialId)).toEqual([9])
    resolveFirst(page([1]))
    await Promise.resolve()
    expect(search.items.value.map((item) => item.materialId)).toEqual([9])
  })

  it('加载更多去重且保持已有顺序', async () => {
    vi.mocked(materialApi.search)
      .mockResolvedValueOnce(page([1, 2], 4) as any)
      .mockResolvedValueOnce(page([2, 3], 4) as any)
    const { search } = withSearch()
    search.search('PET')
    await vi.advanceTimersByTimeAsync(60)
    await search.loadMore()
    expect(search.items.value.map((item) => item.materialId)).toEqual([1, 2, 3])
    expect(materialApi.search).toHaveBeenLastCalledWith({ keyword: 'PET', pageNum: 2, pageSize: 10 })
  })

  it('失败标记可在重试后恢复', async () => {
    vi.mocked(materialApi.search)
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce(page([7]) as any)
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    const { search } = withSearch()
    search.search('PET')
    await vi.advanceTimersByTimeAsync(60)
    expect(search.failed.value).toBe(true)
    search.retry()
    await vi.advanceTimersByTimeAsync(60)
    expect(search.failed.value).toBe(false)
    expect(search.items.value.map((item) => item.materialId)).toEqual([7])
    error.mockRestore()
  })

  it('空关键词浏览失败后重试会重新发请求（复核缺陷 3）', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.mocked(materialApi.search)
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce(page([5], 1) as any)
    const { search } = withSearch({ browseOnFocus: true })
    search.browse()
    await vi.advanceTimersByTimeAsync(10)
    expect(search.failed.value).toBe(true)
    expect(materialApi.search).toHaveBeenCalledTimes(1)

    search.retry()
    await vi.advanceTimersByTimeAsync(10)
    expect(materialApi.search).toHaveBeenCalledTimes(2)
    expect(materialApi.search).toHaveBeenLastCalledWith({ keyword: '', pageNum: 1, pageSize: 10 })
    expect(search.failed.value).toBe(false)
    expect(search.items.value.map((item) => item.materialId)).toEqual([5])
    error.mockRestore()
  })

  it('reset 清空状态并作废在途请求', async () => {
    vi.mocked(materialApi.search).mockResolvedValue(page([1]) as any)
    const { search } = withSearch()
    search.search('PET')
    await vi.advanceTimersByTimeAsync(60)
    search.reset()
    expect(search.keyword.value).toBe('')
    expect(search.items.value).toEqual([])
    expect(search.total.value).toBe(0)
  })
})

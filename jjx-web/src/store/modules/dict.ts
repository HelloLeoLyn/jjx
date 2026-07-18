import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { SysDictItem } from '@/types/system/dict'

/**
 * 字典缓存 Store
 * 全局缓存字典项数据，避免重复请求
 */
export const useDictStore = defineStore('dict', () => {
  /** 字典缓存 Map<dictCode, items> */
  const cache = ref<Map<string, SysDictItem[]>>(new Map())
  const loading = ref(false)

  /**
   * 获取字典项列表
   */
  const getItems = (dictCode: string): SysDictItem[] => {
    return cache.value.get(dictCode) || []
  }

  /**
   * 判断字典是否已缓存
   */
  const hasItems = (dictCode: string): boolean => {
    return cache.value.has(dictCode)
  }

  /**
   * 设置字典缓存
   */
  const setItems = (dictCode: string, items: SysDictItem[]) => {
    cache.value.set(dictCode, items)
  }

  /**
   * 移除字典缓存
   */
  const removeItems = (dictCode: string) => {
    cache.value.delete(dictCode)
  }

  /**
   * 清空所有字典缓存
   */
  const clearAll = () => {
    cache.value.clear()
  }

  const setLoading = (val: boolean) => {
    loading.value = val
  }

  return {
    getItems,
    hasItems,
    setItems,
    removeItems,
    clearAll,
    setLoading,
    loading,
  }
})

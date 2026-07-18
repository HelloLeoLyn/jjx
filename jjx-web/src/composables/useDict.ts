import { computed } from 'vue'
import { useDictStore } from '@/store/modules/dict'
import { dictApi } from '@/api/system/dict'
import type { SysDictItem } from '@/types/system/dict'

/**
 * 字典数据获取 composable
 * 优先从 Pinia store 缓存获取，未命中则调用后端 API
 * 后端 API 也有 Redis 缓存，避免频繁查数据库
 *
 * @example
 * const { options, loading } = useDict('process_type')
 * // options.value 即为字典项列表 [{ itemKey, itemValue, ... }]
 */
export function useDict(dictCode: string) {
  const dictStore = useDictStore()

  /** 字典选项列表（响应式） */
  const options = computed<SysDictItem[]>(() => dictStore.getItems(dictCode))

  /** 加载状态 */
  const loading = computed(() => dictStore.loading)

  /**
   * 加载字典数据
   * 如果已有缓存则跳过
   */
  const load = async () => {
    if (dictStore.hasItems(dictCode)) {
      return // 已有缓存，直接返回
    }
    dictStore.setLoading(true)
    try {
      const res = await dictApi.getItems(dictCode)
      dictStore.setItems(dictCode, res.data || [])
    } catch (error) {
      console.error(`加载字典数据失败 [${dictCode}]:`, error)
    } finally {
      dictStore.setLoading(false)
    }
  }

  /**
   * 强制刷新字典数据（清除缓存后重新加载）
   */
  const refresh = async () => {
    dictStore.removeItems(dictCode)
    await load()
  }

  // 立即加载
  load()

  return {
    /** 字典选项列表 */
    options,
    /** 加载状态 */
    loading,
    /** 强制刷新 */
    refresh,
  }
}

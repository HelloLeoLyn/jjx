import { ref } from 'vue'
import { standardProcessApi } from '@/api/product/standardProcess'
import type { StandardProcessItem } from '@/types/product/standardProcess'

/**
 * 启用的标准工序 —— 全局单例缓存
 * 同一页面多个组件（如 BOM 明细每行一个选择器）共用一份数据，只请求一次；并发调用去重。
 */
const processes = ref<StandardProcessItem[]>([])
let loaded = false
let inflight: Promise<void> | null = null

async function ensureLoaded(force = false): Promise<void> {
  if (loaded && !force) return
  if (inflight) return inflight
  inflight = standardProcessApi
    .getEnabledProcesses()
    .then((res) => {
      processes.value = res.data || []
      loaded = true
    })
    .catch((error) => {
      console.error('加载标准工序失败:', error)
    })
    .finally(() => {
      inflight = null
    })
  return inflight
}

/**
 * 使用启用的标准工序列表
 * @param auto 是否立即加载（默认 true）
 */
export function useEnabledProcesses(auto = true) {
  if (auto) ensureLoaded()
  return {
    processes,
    /** 强制重新拉取（忽略缓存） */
    reload: () => ensureLoaded(true),
  }
}

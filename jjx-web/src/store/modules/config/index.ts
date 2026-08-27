import { reactive, ref } from 'vue'
import { ConfigModule, type ConfigLoadState } from '@/types/config'
import { usePdfTemplateStore } from './pdfTemplate'
import { useProductionConfigStore } from './productionConfig'

/** 模块 → Store 工厂 */
const moduleStores = {
  [ConfigModule.PdfTemplate]: usePdfTemplateStore,
  [ConfigModule.ProductionConfig]: useProductionConfigStore,
} as const

/** 已加载模块缓存 */
const loadedModules = ref<Set<ConfigModule>>(new Set())
/** 各模块加载状态（供 UI 展示） */
const moduleStates = reactive<Record<ConfigModule, ConfigLoadState>>({
  [ConfigModule.PdfTemplate]: 'idle',
  [ConfigModule.ProductionConfig]: 'idle',
})

/** 进行中的加载（防止并发重复请求） */
const inflight = new Map<ConfigModule, Promise<void>>()

export function isModuleLoaded(key: ConfigModule): boolean {
  return loadedModules.value.has(key)
}

export function getModuleState(key: ConfigModule): ConfigLoadState {
  return moduleStates[key]
}

/**
 * 按需加载单个模块（幂等：已加载且未强制刷新时直接返回）
 */
export async function loadModule(key: ConfigModule, force = false): Promise<void> {
  if (!force && (isModuleLoaded(key) || inflight.has(key))) return

  const existing = inflight.get(key)
  if (existing) return existing

  const promise = (async () => {
    moduleStates[key] = 'loading'
    const store = moduleStores[key]()
    await store.init(force)
    if (store.loaded) {
      loadedModules.value.add(key)
      moduleStates[key] = 'loaded'
    } else {
      moduleStates[key] = 'error'
    }
  })().finally(() => {
    inflight.delete(key)
  })

  inflight.set(key, promise)
  return promise
}

/**
 * 启动时同时加载两个核心配置模块
 */
export async function initCore(): Promise<void> {
  await Promise.all([
    loadModule(ConfigModule.PdfTemplate),
    loadModule(ConfigModule.ProductionConfig),
  ])
}

/** 重置单个模块（清缓存 + 恢复默认值） */
export function resetModule(key: ConfigModule): void {
  moduleStores[key]().reset()
  loadedModules.value.delete(key)
  moduleStates[key] = 'idle'
}

/** 重置全部模块 */
export function resetAll(): void {
  Object.values(ConfigModule).forEach((key) => resetModule(key))
}

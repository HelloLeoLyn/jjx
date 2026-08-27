import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getConfigModule } from '@/api/config'
import {
  ConfigModule,
  type ConfigLoadState,
  type ConfigRecord,
  type ProductionConfigState,
} from '@/types/config'

/** 默认值（接口异常时兜底） */
export function createDefaultProductionConfigState(): ProductionConfigState {
  return {
    defaultLeadDays: 7,
    planAdvanceDays: 3,
    defaultShiftHours: 8,
    overdueWarnDays: 2,
    boardRefreshSeconds: 30,
    qualitySamplingRate: 10,
    qualityPassRateThreshold: 95,
    deviceUtilizationTarget: 85,
  }
}

/** 字符串 → number；空值/非法值回退默认 */
function toNumber(value: string | undefined, fallback: number): number {
  if (value === undefined || value === null || value === '') return fallback
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

/** 后端键值对 → 驼峰 State（统一 number） */
function toState(data: ConfigRecord): ProductionConfigState {
  return {
    defaultLeadDays: toNumber(data.default_lead_days, 7),
    planAdvanceDays: toNumber(data.plan_advance_days, 3),
    defaultShiftHours: toNumber(data.default_shift_hours, 8),
    overdueWarnDays: toNumber(data.overdue_warn_days, 2),
    boardRefreshSeconds: toNumber(data.board_refresh_seconds, 30),
    qualitySamplingRate: toNumber(data.quality_sampling_rate, 10),
    qualityPassRateThreshold: toNumber(data.quality_pass_rate_threshold, 95),
    deviceUtilizationTarget: toNumber(data.device_utilization_target, 85),
  }
}

export const useProductionConfigStore = defineStore('productionConfig', () => {
  /** 配置 State（默认值兜底） */
  const state = ref<ProductionConfigState>(createDefaultProductionConfigState())
  /** 是否已成功加载 */
  const loaded = ref(false)
  /** 加载状态 */
  const loadState = ref<ConfigLoadState>('idle')
  /** 最近一次加载错误信息 */
  const error = ref('')

  /**
   * 加载配置；已加载且未强制刷新时直接返回
   */
  async function init(forceRefresh = false): Promise<void> {
    if (!forceRefresh && loaded.value) return
    loadState.value = 'loading'
    error.value = ''
    try {
      const res = await getConfigModule(ConfigModule.ProductionConfig)
      state.value = toState(res.data ?? {})
      loaded.value = true
      loadState.value = 'loaded'
    } catch (e) {
      // 失败降级默认值，不阻断业务
      state.value = createDefaultProductionConfigState()
      loaded.value = false
      loadState.value = 'error'
      error.value = e instanceof Error ? e.message : String(e)
      console.error('[productionConfig] 配置加载失败，使用默认值', e)
    }
  }

  /** 重置为默认值 */
  function reset(): void {
    state.value = createDefaultProductionConfigState()
    loaded.value = false
    loadState.value = 'idle'
    error.value = ''
  }

  return { state, loaded, loadState, error, init, reset }
})

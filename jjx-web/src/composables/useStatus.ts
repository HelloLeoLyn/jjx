// composables/useStatus.ts
import { computed } from 'vue'

// 定义 Element Plus 支持的类型
export type ElementTagType = 'success' | 'info' | 'warning' | 'danger'

export function useStatus<
  T extends Record<string, { label: string; type: string }>,
>(config: T) {
  type StatusKey = keyof T

  // 明确返回 Element Plus 支持的类型
  const getType = (status: StatusKey): ElementTagType => {
    const type = config[status]?.type
    // 确保返回的是 Element Plus 支持的类型
    if (
      type === 'success' ||
      type === 'info' ||
      type === 'warning' ||
      type === 'danger'
    ) {
      return type
    }
    return 'info' // 默认返回 info
  }

  const getLabel = (status: StatusKey): string => {
    return config[status]?.label || '未知'
  }

  const options = computed(() => {
    return Object.entries(config).map(([value, item]) => ({
      value,
      label: item.label,
      type: item.type,
    }))
  })

  return {
    getLabel,
    getType,
    options,
  }
}

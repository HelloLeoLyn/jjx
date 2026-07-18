// src/enums/base.ts
import type { TagProps } from 'element-plus'

export type EnumItem<T = number> = {
  value: T
  label: string
  tagProps: TagProps
  /** 可执行的操作列表 */
  actions?: string[]
}

export type EnumObject<T = number> = {
  items: EnumItem<T>[]
  getLabel: (value: T) => string
  getTagProps: (value: T) => TagProps
  /** 检查是否有某个操作权限 */
  canDo: (value: T, action: string) => boolean
  /** 获取可执行的操作列表 */
  getActions: (value: T) => string[]
}

export function createEnum<T = number>(options: {
  items: EnumItem<T>[]
  defaultTag: TagProps
}): EnumObject<T> {
  const labelMap = new Map<T, string>()
  const tagMap = new Map<T, TagProps>()
  const actionsMap = new Map<T, string[]>()

  options.items.forEach((item) => {
    labelMap.set(item.value, item.label)
    tagMap.set(item.value, item.tagProps)
    actionsMap.set(item.value, item.actions || [])
  })

  return {
    items: options.items,
    getLabel(value: T): string {
      return labelMap.get(value) || '未知'
    },
    getTagProps(value: T): TagProps {
      return tagMap.get(value) || { type: options.defaultTag.type }
    },
    canDo(value: T, action: string): boolean {
      const actions = actionsMap.get(value) || []
      return actions.includes(action)
    },
    getActions(value: T): string[] {
      return actionsMap.get(value) || []
    },
  }
}

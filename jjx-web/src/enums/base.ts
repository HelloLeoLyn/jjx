import type { TagProps } from 'element-plus'
export type EnumObject<T> = {
  items: { value: T; label: string; tagProps: TagProps }[]
  getLabel: (value: T) => string
  getTagProps: (value: T) => TagProps
  canDo: (value: T) => boolean
}

export function createEnum<T>(options: {
  items: { value: T; label: string; tagProps: TagProps }[]
  defaultTag: TagProps
  canDo?: (value: T) => boolean
}): EnumObject<T> {
  const labelMap = new Map<T, string>()
  const tagMap = new Map<T, TagProps>()

  options.items.forEach((item) => {
    labelMap.set(item.value, item.label)
    tagMap.set(item.value, item.tagProps)
  })

  return {
    items: options.items,
    getLabel(value: T): string {
      return labelMap.get(value) || '未知'
    },
    getTagProps(value: T): TagProps {
      return tagMap.get(value) || { type: options.defaultTag.type, color: options.defaultTag.color }
    },
    canDo(value: T): boolean {
      if (options.canDo) {
        return options.canDo(value)
      }
      return !!tagMap.get(value)
    },
  }
}

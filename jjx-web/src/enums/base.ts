import type { TagProps } from 'element-plus'
export type EnumObject<T> = {
  items: { value: T; label: string; tagProps: TagProps }[]
  getLabel: (value: T) => string
  getTagProps: (value: T) => TagProps
  canDo: (value: T) => boolean
}

type NamedEnumItem<T> = {
  value: T
  label: string
  tagProps: TagProps
}

type NamedEnumValue<TDefinitions> =
  TDefinitions[keyof TDefinitions] extends NamedEnumItem<infer TValue> ? TValue : never

type WidenEnumValue<T> = T extends number ? number : T extends string ? string : T

/**
 * 同时提供具名状态成员和展示工具，避免业务分支回退到数字魔法值。
 */
export function createNamedEnum<const TDefinitions extends Record<string, NamedEnumItem<unknown>>>(
  definitions: TDefinitions,
  defaultTag: TagProps
): TDefinitions & EnumObject<WidenEnumValue<NamedEnumValue<TDefinitions>>> {
  type TValue = WidenEnumValue<NamedEnumValue<TDefinitions>>
  const enumObject = createEnum<TValue>({
    items: Object.values(definitions) as NamedEnumItem<TValue>[],
    defaultTag,
  })
  return Object.assign(definitions, enumObject)
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

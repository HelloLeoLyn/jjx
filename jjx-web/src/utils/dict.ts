import type { SysDictItem } from '@/types/system/dict'

/**
 * 根据字典值获取对应的显示文本（label）
 *
 * @param options - 字典选项列表
 * @param value - 字典值（itemValue）
 * @param defaultValue - 未找到时的默认值，默认返回原值
 * @returns 对应的显示文本
 *
 * @example
 * const label = getDictLabel(dictOptions, 'PRINTING')
 * // 返回 '印刷'
 */
export function getDictLabel(
  options: SysDictItem[],
  value?: string | number,
  defaultValue?: string
): string {
  if (!value) return defaultValue ?? ''
  const item = options.find((opt) => opt.itemValue === String(value))
  return item?.label ?? defaultValue ?? String(value)
}

/**
 * 根据字典值获取对应的标签类型（用于 el-tag）
 *
 * @param options - 字典选项列表
 * @param value - 字典值（itemValue）
 * @param defaultType - 默认类型，默认 'info'
 * @returns 对应的标签类型
 *
 * @example
 * const type = getDictTagType(dictOptions, 'PRINTING')
 * // 返回 'success'
 */
export function getDictTagType(
  options: SysDictItem[],
  value?: string | number,
  defaultType: string = 'info'
): string {
  if (!value) return defaultType
  const item = options.find((opt) => opt.itemValue === String(value))
  return (item as any)?.tagType ?? defaultType
}

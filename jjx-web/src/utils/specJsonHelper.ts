/**
 * 规格参数JSON处理工具
 */

import type { ProductSpecItem } from '@/types/product'

// 默认规格参数
export const DEFAULT_SPEC_ITEMS: ProductSpecItem[] = [
  { name: '颜色', value: '黑色', unit: '-' },
  { name: '材质', value: '金属', unit: '-' },
  { name: '尺寸', value: '10cm x 5cm x 2cm', unit: 'cm' },
  { name: '重量', value: '200g', unit: 'g' },
  { name: '品牌', value: '某品牌', unit: '-' },
  { name: '型号', value: 'XYZ123', unit: '-' },
]

/**
 * 解析规格参数JSON字符串
 * @param specJson JSON字符串
 * @returns 规格参数数组
 */
export function parseSpecJson(specJson: string): ProductSpecItem[] {
  if (!specJson || specJson.trim() === '') {
    return []
  }

  try {
    const parsed = JSON.parse(specJson)

    // 支持多种JSON结构
    if (Array.isArray(parsed)) {
      return parsed
    } else if (parsed.specifications && Array.isArray(parsed.specifications)) {
      return parsed.specifications
    } else if (typeof parsed === 'object') {
      // 如果是普通对象，转换为数组格式
      return Object.entries(parsed).map(([name, value]) => ({
        name,
        value: String(value),
        unit: '-',
      }))
    }
    return []
  } catch (error) {
    console.error('解析规格参数失败:', error)
    return []
  }
}

/**
 * 将规格参数数组转换为JSON字符串
 * @param specItems 规格参数数组
 * @returns JSON字符串
 */
export function stringifySpecJson(specItems: ProductSpecItem[]): string {
  if (!specItems || specItems.length === 0) {
    return ''
  }
  return JSON.stringify(specItems)
}

/**
 * 检查是否与默认值完全匹配
 * @param specItems 规格参数数组
 * @returns 是否完全匹配
 */
export function isExactMatchWithDefault(specItems: ProductSpecItem[]): boolean {
  if (specItems.length !== DEFAULT_SPEC_ITEMS.length) return false

  return specItems.every((item, index) => {
    const defaultItem = DEFAULT_SPEC_ITEMS[index]
    return (
      item.name === defaultItem.name &&
      item.value === defaultItem.value &&
      item.unit === defaultItem.unit
    )
  })
}

/**
 * 检查是否包含默认值（部分匹配）
 * @param specItems 规格参数数组
 * @returns 是否包含默认值
 */
export function containsDefaultItems(specItems: ProductSpecItem[]): boolean {
  if (specItems.length === 0) return false

  return specItems.some((item) =>
    DEFAULT_SPEC_ITEMS.some(
      (defaultItem) =>
        item.name === defaultItem.name &&
        item.value === defaultItem.value &&
        item.unit === defaultItem.unit
    )
  )
}

/**
 * 获取默认规格参数
 * @returns 默认规格参数数组的副本
 */
export function getDefaultSpecItems(): ProductSpecItem[] {
  return JSON.parse(JSON.stringify(DEFAULT_SPEC_ITEMS))
}

/**
 * 判断是否应该使用默认值
 * @param specJson JSON字符串
 * @returns 是否应该使用默认值
 */
export function shouldUseDefaultSpec(specJson: string): boolean {
  if (!specJson || specJson.trim() === '') {
    return false
  }

  const specItems = parseSpecJson(specJson)
  return isExactMatchWithDefault(specItems)
}

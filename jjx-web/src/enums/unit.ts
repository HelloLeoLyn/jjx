// enums/product/UnitEnum.ts

/**
 * 产品单位枚举
 */
export const UnitEnum = {
  // ==================== 数量单位 ====================
  PCS: {
    code: 'PCS',
    name: '个',
    category: 'quantity',
    categoryName: '数量单位',
    englishName: 'piece',
  },
  SET: {
    code: 'SET',
    name: '套',
    category: 'quantity',
    categoryName: '数量单位',
    englishName: 'set',
  },
  BOX: {
    code: 'BOX',
    name: '盒',
    category: 'quantity',
    categoryName: '数量单位',
    englishName: 'box',
  },
  CTN: {
    code: 'CTN',
    name: '箱',
    category: 'quantity',
    categoryName: '数量单位',
    englishName: 'carton',
  },

  // ==================== 长度单位 ====================
  MM: {
    code: 'MM',
    name: '毫米',
    category: 'length',
    categoryName: '长度单位',
    englishName: 'millimeter',
  },
  CM: {
    code: 'CM',
    name: '厘米',
    category: 'length',
    categoryName: '长度单位',
    englishName: 'centimeter',
  },
  M: { code: 'M', name: '米', category: 'length', categoryName: '长度单位', englishName: 'meter' },
  INCH: {
    code: 'INCH',
    name: '英寸',
    category: 'length',
    categoryName: '长度单位',
    englishName: 'inch',
  },
  FT: {
    code: 'FT',
    name: '英尺',
    category: 'length',
    categoryName: '长度单位',
    englishName: 'foot',
  },

  // ==================== 面积单位 ====================
  SQMM: {
    code: 'SQMM',
    name: '平方毫米',
    category: 'area',
    categoryName: '面积单位',
    englishName: 'square_millimeter',
  },
  SQCM: {
    code: 'SQCM',
    name: '平方厘米',
    category: 'area',
    categoryName: '面积单位',
    englishName: 'square_centimeter',
  },
  SQM: {
    code: 'SQM',
    name: '平方米',
    category: 'area',
    categoryName: '面积单位',
    englishName: 'square_meter',
  },

  // ==================== 重量单位 ====================
  G: { code: 'G', name: '克', category: 'weight', categoryName: '重量单位', englishName: 'gram' },
  KG: {
    code: 'KG',
    name: '千克',
    category: 'weight',
    categoryName: '重量单位',
    englishName: 'kilogram',
  },
  TON: {
    code: 'TON',
    name: '吨',
    category: 'weight',
    categoryName: '重量单位',
    englishName: 'ton',
  },
  LB: {
    code: 'LB',
    name: '磅',
    category: 'weight',
    categoryName: '重量单位',
    englishName: 'pound',
  },

  // ==================== 体积单位 ====================
  ML: {
    code: 'ML',
    name: '毫升',
    category: 'volume',
    categoryName: '体积单位',
    englishName: 'milliliter',
  },
  L: { code: 'L', name: '升', category: 'volume', categoryName: '体积单位', englishName: 'liter' },
  CBM: {
    code: 'CBM',
    name: '立方米',
    category: 'volume',
    categoryName: '体积单位',
    englishName: 'cubic_meter',
  },

  // ==================== 时间单位 ====================
  DAY: { code: 'DAY', name: '天', category: 'time', categoryName: '时间单位', englishName: 'day' },
  HOUR: {
    code: 'HOUR',
    name: '小时',
    category: 'time',
    categoryName: '时间单位',
    englishName: 'hour',
  },
  MINUTE: {
    code: 'MINUTE',
    name: '分钟',
    category: 'time',
    categoryName: '时间单位',
    englishName: 'minute',
  },
  SECOND: {
    code: 'SECOND',
    name: '秒',
    category: 'time',
    categoryName: '时间单位',
    englishName: 'second',
  },

  // ==================== 其他单位 ====================
  ROLL: {
    code: 'ROLL',
    name: '卷',
    category: 'other',
    categoryName: '其他单位',
    englishName: 'roll',
  },
  SHEET: {
    code: 'SHEET',
    name: '张',
    category: 'other',
    categoryName: '其他单位',
    englishName: 'sheet',
  },
  PAIR: {
    code: 'PAIR',
    name: '双',
    category: 'other',
    categoryName: '其他单位',
    englishName: 'pair',
  },
} as const

export type ProductUnitCode = keyof typeof UnitEnum

export interface ProductUnit {
  code: string
  name: string
  category: string
  categoryName: string
  englishName: string
}

/**
 * 获取单位信息
 */
export const getUnitInfo = (code: string): ProductUnit | undefined => {
  return Object.values(UnitEnum).find((u) => u.code === code)
}

/**
 * 获取单位名称
 */
export const getUnitName = (code: string): string => {
  const unit = getUnitInfo(code)
  return unit?.name || code
}

/**
 * 获取单位类别名称
 */
export const getUnitCategoryName = (code: string): string => {
  const unit = getUnitInfo(code)
  return unit?.categoryName || ''
}

/**
 * 根据类别获取单位列表
 */
export const getUnitsByCategory = (category: string): ProductUnit[] => {
  return Object.values(UnitEnum).filter((u) => u.category === category)
}

/**
 * 获取所有单位选项（用于下拉选择）
 */
export const getAllUnitOptions = (): Array<{ value: string; label: string; category: string }> => {
  return Object.values(UnitEnum).map((u) => ({
    value: u.code,
    label: `${u.name} (${u.code})`,
    category: u.category,
  }))
}

/**
 * 按类别分组获取单位选项
 */
export const getGroupedUnitOptions = () => {
  const categories = [
    { key: 'quantity', name: '数量单位' },
    { key: 'length', name: '长度单位' },
    { key: 'area', name: '面积单位' },
    { key: 'weight', name: '重量单位' },
    { key: 'volume', name: '体积单位' },
    { key: 'time', name: '时间单位' },
    { key: 'other', name: '其他单位' },
  ]

  return categories.map((cat) => ({
    label: cat.name,
    options: getUnitsByCategory(cat.key).map((u) => ({
      value: u.code,
      label: `${u.name} (${u.code})`,
    })),
  }))
}

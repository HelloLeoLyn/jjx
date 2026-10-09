/** 工程图纸按图种上传；“工程图集”是文档集合名称，不作为单个文件类别。 */
export const ENGINEERING_DRAWING_CATEGORIES = [
  '结构图',
  '外形尺寸图',
  '面板图',
  '线路图',
  '组装图',
  '包装图',
  '印刷指导图',
  '确认图',
  '菲林',
  '模具',
  '其他工程图',
]

/** 旧附件继续按原值读取；不再提供这个类别用于新上传。 */
export const LEGACY_PRODUCT_ATLAS_CATEGORY = '产品图集'

export const ENGINEERING_DRAWING_VISIBLE_CATEGORIES = [
  ...ENGINEERING_DRAWING_CATEGORIES,
  LEGACY_PRODUCT_ATLAS_CATEGORY,
]

/** 字典未加载到有效项时的兜底；正常选项仍由产品文件类别字典维护。 */
export const PRODUCT_FILE_FALLBACK_CATEGORIES = [
  '客供稿', '承认书', '规范',
  ...ENGINEERING_DRAWING_CATEGORIES,
  '样品照片', '客户确认样品', '分色检查表',
]

export function productFileCategoryLabel(category: string): string {
  return category === LEGACY_PRODUCT_ATLAS_CATEGORY ? '其他工程图（历史资料）' : category
}

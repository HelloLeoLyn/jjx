/**
 * 产品文件/工程图集分类常量。
 *
 * 2026-10-19（dev-20261019-018）：工程图集从「图种 category」收敛到「二级资料类型 documentType」，
 * 本文件中的中文图种常量仅作 legacy 兼容（历史数据读取/展示），新上传不再写入 category。
 */

/** @legacy 旧图种（中文 category）；新上传停用，仅历史数据读取用。 */
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
  '刀模参数图',
  '其他工程图',
]

/** 旧附件继续按原值读取；不再提供这个类别用于新上传。 */
export const LEGACY_PRODUCT_ATLAS_CATEGORY = '产品图集'

/** @legacy 旧图种可见范围（含历史“产品图集”），仅历史数据展示用。 */
export const ENGINEERING_DRAWING_VISIBLE_CATEGORIES = [
  ...ENGINEERING_DRAWING_CATEGORIES,
  LEGACY_PRODUCT_ATLAS_CATEGORY,
]

/** 字典未加载到有效项时的兜底（legacy 类别字典）；正常选项仍由产品文件类别字典维护。 */
export const PRODUCT_FILE_FALLBACK_CATEGORIES = [
  '客供稿', '承认书', '规范',
  ...ENGINEERING_DRAWING_CATEGORIES,
  '样品照片', '客户确认样品', '分色检查表',
]

export function productFileCategoryLabel(category: string): string {
  return category === LEGACY_PRODUCT_ATLAS_CATEGORY ? '其他工程图（历史资料）' : category
}

/**
 * 工程图集二级资料类型（attachment_document_type, parent=ENGINEERING_DOC）。
 * 这份列表只作接口失败时的兜底；正常选项以 /system/attachment/refs/document-types 为准。
 */
export const ENGINEERING_DOCUMENT_TYPES: { value: string; label: string }[] = [
  { value: 'STRUCTURE_DRAWING', label: '结构图' },
  { value: 'OUTLINE_DRAWING', label: '外形尺寸图' },
  { value: 'PANEL_DRAWING', label: '面板图' },
  { value: 'CIRCUIT_DRAWING', label: '线路图' },
  { value: 'ASSEMBLY_DRAWING', label: '组装图' },
  { value: 'PACKAGING_DRAWING', label: '包装图' },
  { value: 'PRINT_GUIDE', label: '印刷指导图' },
  { value: 'CONFIRM_DRAWING', label: '确认图' },
  { value: 'FILM', label: '菲林' },
  { value: 'MOLD_DRAWING', label: '模具' },
  { value: 'DIE_PARAMETER_DRAWING', label: '刀模参数图' },
  { value: 'ENGINEERING_DRAWING', label: '工程图' },
  { value: 'DIE_DRAWING', label: '刀模图' },
]

/** 工程图集默认二级类型编码范围（上传/筛选/分组共用）。 */
export const ENGINEERING_DOCUMENT_TYPE_CODES = ENGINEERING_DOCUMENT_TYPES.map((item) => item.value)

export function engineeringDocumentTypeLabel(code?: string | null): string {
  if (!code) return '未分类'
  return ENGINEERING_DOCUMENT_TYPES.find((item) => item.value === code)?.label || code
}

/**
 * 上传判重指纹（dev-20261019-018）：只认文件本身（名 + 大小 + 修改时间），
 * 图纸编号/版本不参与 —— 同一图纸编号+版本允许多个文件。
 */
export function fileFingerprint(fileName: string, fileSize: number, lastModified?: number): string {
  return `${fileName}|${fileSize}|${lastModified ?? ''}`
}

/** 返回出现一次以上的文件指纹（仅用于“可能重复”提示，不阻断上传）。 */
export function duplicateFingerprints(items: { fileName: string; fileSize: number; lastModified?: number }[]): string[] {
  const counts = new Map<string, number>()
  for (const item of items) {
    const key = fileFingerprint(item.fileName, item.fileSize, item.lastModified)
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  return [...counts.entries()].filter(([, count]) => count > 1).map(([key]) => key)
}

/**
 * 物料搜索器候选行的显示字段配置（代码引用配置）
 *
 * 想改下拉里显示什么、什么顺序、放第一行还是第二行、没值时怎么提示，只改下面这个数组，
 * 不用动 MaterialSearchOption.vue（BOM 物料选择器与通用物料选择器共用同一份渲染）。
 *
 * 字段说明：
 *   key      —— InventoryMaterial 上的字段名（物料名称 materialName、编码 materialCode 等）
 *   row      —— 1 = 第一行（名称/编码/规格），2 = 第二行（其余信息）
 *   label    —— 值前面加的前缀，如 '规格：'；不填则只显示值
 *   missing  —— 该字段为空时的灰字占位，如 '未填写名称'；不填表示"没值就整段不显示"
 *   emphasis —— 'strong' 名称加粗；'muted' 编码小灰字；不填为普通样式
 *
 * 注意：字段值来自搜索接口返回的 MaterialVO，后端没返回的字段这里加了也不会显示。
 */
import type { InventoryMaterial } from '@/types/inventory/material'

export type MaterialSearchOptionRow = 1 | 2

export interface MaterialSearchOptionField {
  key: keyof InventoryMaterial
  row: MaterialSearchOptionRow
  label?: string
  missing?: string
  emphasis?: 'strong' | 'muted'
  /** 额外样式类名（样式在 MaterialSearchOption.vue 里），一般不用填 */
  className?: string
}

export const MATERIAL_SEARCH_OPTION_FIELDS: MaterialSearchOptionField[] = [
  // 第一行：一眼认料的三个字段
  { key: 'materialName', row: 1, missing: '未填写名称', emphasis: 'strong' },
  { key: 'materialCode', row: 1, missing: '无编码', emphasis: 'muted' },
  {
    key: 'specification',
    row: 1,
    label: '规格：',
    missing: '规格：未填写',
    className: 'specification',
  },
  { key: 'unit', row: 1, label: '单位：' },
  // 第二行：区分同名料与判断可用性的信息
  // { key: 'categoryName', row: 2, label: '品类：', missing: '品类：未归类' },
  // { key: 'model', row: 2, label: '型号：' },
  { key: 'supplierName', row: 2, label: '供应商：' },
  { key: 'supplierMaterialName', row: 2, label: '供应商品名：' },
  { key: 'size', row: 2, label: '尺寸：' },
  { key: 'materialMeta', row: 2, label: '材质：' },
]

/** 取某一行要展示的字段；字段为空且没配 missing 的自动跳过。 */
export function fieldsOfRow(
  item: Partial<InventoryMaterial> | null | undefined,
  row: MaterialSearchOptionRow
): MaterialSearchOptionField[] {
  return MATERIAL_SEARCH_OPTION_FIELDS.filter(
    (field) => field.row === row && (!isBlank(item, field) || field.missing != null)
  )
}

export function isBlank(
  item: Partial<InventoryMaterial> | null | undefined,
  field: MaterialSearchOptionField
): boolean {
  const value = item == null ? undefined : item[field.key]
  return value == null || String(value).trim() === ''
}

/** 渲染文本：有值带前缀，没值用占位（调用方保证没占位的已过滤）。 */
export function fieldText(
  item: Partial<InventoryMaterial> | null | undefined,
  field: MaterialSearchOptionField
): string {
  if (isBlank(item, field)) return field.missing ?? ''
  return (field.label ?? '') + String(item![field.key]).trim()
}

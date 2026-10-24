import request from '@/utils/request'
import type { R } from '@/types'
import { materialSaveFromImport, type MaterialImportRow } from './materialCreateDefaults'

/**
 * 物料主数据字段口径（dev-20261023-008）。
 *
 * 后端 dev-20261023-012 已把 material_grade/model/color/brand/search_keywords/size
 * 落到 inventory_material，并按 inventory_material_required_field 做服务端必填校验。
 * 这里集中前端下拉词表、名称拼装、旧名称解析与必填矩阵，避免各处再散写。
 */

/** 材料：受控复合词（与后端 InventoryMaterialServiceImpl 导入解析候选表同一份，23 个）。 */
export const MATERIAL_GRADE_OPTIONS = [
  'PET保护膜',
  'PE保护膜',
  'EVA泡棉',
  'VHB胶带',
  'PC膜',
  'PET膜',
  'PVC膜',
  'PE膜',
  '离型纸',
  '离型膜',
  '标签纸',
  '胶带',
  '泡棉',
  '灯',
  '螺丝',
  '垫片',
  '不锈钢',
  '铜',
  '铝',
  '油墨',
  '箱',
  '袋',
  '其他',
]

/** 颜色：与后端导入回填词表一致。 */
export const MATERIAL_COLOR_OPTIONS = [
  '透明',
  '咖啡色',
  '白色',
  '黑色',
  '黄色',
  '红色',
  '蓝色',
  '绿色',
  '灰色',
  '银色',
  '金色',
  '紫色',
  '橙色',
]

/**
 * 必填矩阵：inventory_material_required_field 的前端镜像（按品类编码）。
 * 服务端仍会校验，这里只负责提前提示；后端接口补出后应改为读取接口。
 */
export const MATERIAL_REQUIRED_FIELDS: Record<string, string[]> = {
  'RM-FILM': ['materialGrade', 'color', 'specification'],
  'RM-PAPER': ['materialGrade', 'color', 'specification'],
  'RM-ADHESIVE': ['materialGrade', 'color', 'specification'],
  'RM-INK': ['color', 'specification'],
  'AUX-ADHESIVE': ['model', 'specification', 'brand'],
  'AUX-ELEC': ['model', 'color'],
  'AUX-HARDWARE': ['materialGrade', 'model', 'specification'],
  'AUX-PACK': ['specification'],
  'AUX-TOOL': [],
  'OTHER-R': [],
  'OTHER-A': [],
}

export const MATERIAL_FIELD_LABELS: Record<string, string> = {
  materialGrade: '材料',
  model: '型号',
  color: '颜色',
  brand: '品牌',
  specification: '规格',
  size: '尺寸',
  searchKeywords: '别名',
}

export interface MaterialCategoryOption {
  categoryId: number
  categoryCode: string
  categoryName: string
  rootType?: string | null
  categoryRootName?: string | null
}

// 品类下拉唯一真源 = /inventory/material/category-options（分类树收口后由后端下发）。
// dev-20261024-006：删除本地 11 条旧一级品类镜像，避免接口与前端两份分类并存。

/** 必填字段（按矩阵顺序）。 */
export function requiredMaterialFields(categoryCode?: string): string[] {
  if (!categoryCode) return []
  return MATERIAL_REQUIRED_FIELDS[categoryCode] || []
}

export interface MaterialMasterValues {
  materialGrade?: string
  model?: string
  color?: string
  brand?: string
  specification?: string
}

/**
 * 名称 = 材料 + 型号 + 颜色（品牌），无空格、缺项跳过、品牌用全角括号。
 * 例：PET膜 + PET125 → PET膜PET125；PC膜 + 11008 + 透明 + （3M） → PC膜11008透明（3M）。
 */
export function composeMaterialName(values: MaterialMasterValues): string {
  const base = [values.materialGrade, values.model, values.color]
    .map((item) => (item || '').trim())
    .filter(Boolean)
    .join('')
  const brand = (values.brand || '').trim()
  if (!brand) return base
  return base ? `${base}（${brand}）` : brand
}

/**
 * 旧名称/旧模板反解成结构化字段：品牌取末尾括号，材料取最长复合词，颜色取颜色词，余下为型号。
 * 用于「仅名称+规格」的旧模板与 BOM 行预填；解析不出来的部分整体留在型号里，名称不会被改丢。
 */
export function splitLegacyMaterialName(name: string): MaterialMasterValues {
  let rest = (name || '').trim()
  const result: MaterialMasterValues = {}
  if (!rest) return result

  const brandMatch = rest.match(/[（(]([^（）()]{1,30})[）)]\s*$/)
  if (brandMatch) {
    result.brand = brandMatch[1].trim()
    rest = rest.slice(0, brandMatch.index).trim()
  }

  const grades = [...MATERIAL_GRADE_OPTIONS].sort((a, b) => b.length - a.length)
  for (const grade of grades) {
    if (rest.includes(grade)) {
      result.materialGrade = grade
      rest = rest.replace(grade, '').trim()
      break
    }
  }

  const colors = [...MATERIAL_COLOR_OPTIONS].sort((a, b) => b.length - a.length)
  for (const color of colors) {
    if (rest.includes(color)) {
      result.color = color
      rest = rest.replace(color, '').trim()
      break
    }
  }

  const model = rest.replace(/^[-_·\s]+|[-_·\s]+$/g, '')
  if (model) result.model = model
  return result
}

/**
 * 必填校验（前端预检）。
 * - 新增：矩阵内字段为空即报。
 * - 编辑：只校验"本次改动"——原有值被清空、或品类发生变化时按新品类全量校验；
 *   历史就为空的字段不阻塞保存（与服务端 suppliedFields 口径一致）。
 */
export function findMissingRequiredFields(
  values: MaterialMasterValues,
  categoryCode: string | undefined,
  originals?: MaterialMasterValues,
  forceAll = false
): string[] {
  const required = requiredMaterialFields(categoryCode)
  if (!required.length) return []
  return required.filter((key) => {
    const value = ((values as Record<string, string | undefined>)[key] || '').trim()
    if (value) return false
    if (!originals || forceAll) return true
    const before = ((originals as Record<string, string | undefined>)[key] || '').trim()
    return before !== ''
  })
}

/** 读取品类下拉；接口不可用或为空时返回空数组（由调用方提示，不再使用本地镜像兜底）。 */
export async function loadMaterialCategoryOptions(): Promise<MaterialCategoryOption[]> {
  try {
    const res = await request.get<R<MaterialCategoryOption[]>>('/inventory/material/category-options')
    const options = (res.data || [])
      .filter((item) => item?.categoryId != null)
      .map((item) => ({
        categoryId: Number(item.categoryId),
        categoryCode: item.categoryCode || '',
        categoryName: item.categoryName || item.categoryCode || String(item.categoryId),
        rootType: item.rootType ?? null,
        categoryRootName: item.categoryRootName ?? null,
      }))
    if (options.length) return options
    console.warn('[material] 品类接口返回为空，请检查分类树与权限')
  } catch (error) {
    console.warn('[material] 品类接口不可用，请检查分类树与权限', error)
  }
  return []
}

export interface ImportedCategoryFields {
  categoryId?: number | string
  categoryCode?: string
  categoryName?: string
}

/** 两个库存导入入口只保留明确品类，不采信旧类型。 */
export function readImportedCategoryFields(row: Record<string, any>): ImportedCategoryFields {
  return {
    categoryId: row.categoryId || row.category_id || row['品类ID'],
    categoryCode: row.categoryCode || row.category_code || row['品类编码'],
    categoryName: row.categoryName || row.category_name || row['品类'] || row['品类名称'],
  }
}

/** 缺失/未匹配品类明确拒绝建档，不按旧类型或编码兜底归类。 */
export function materialImportSaveWithCategory(
  row: MaterialImportRow & ImportedCategoryFields,
  materialCode: string,
  options: MaterialCategoryOption[]
) {
  const matched = options.find((item) =>
    (row.categoryId != null && String(item.categoryId) === String(row.categoryId)) ||
    (!!row.categoryCode && item.categoryCode === row.categoryCode.trim()) ||
    (!!row.categoryName && [item.categoryName, item.categoryCode].includes(row.categoryName.trim()))
  )
  const category = matched
  if (!category?.categoryId) throw new Error('物料未归类，请先选择明确品类后建档')
  return {
    payload: {
      ...materialSaveFromImport(row, materialCode),
      categoryId: category.categoryId,
    },
    usedFallback: !matched,
  }
}


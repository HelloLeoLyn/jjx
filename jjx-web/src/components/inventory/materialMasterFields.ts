import request from '@/utils/request'
import type { R } from '@/types'
import { materialSaveFromImport, type MaterialImportRow } from './materialCreateDefaults'
import type { MaterialSpecAttrDef, MaterialSpecValue } from '@/types/inventory/material'

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
  materialType?: string
}

/**
 * 品类兜底列表。
 * 优先读取仅登录可用的物料品类选项接口；接口不可用时使用初始化种子兜底。
 * 接口可用时以接口返回为准（含 categoryId）。
 */
export const MATERIAL_CATEGORY_SEED: MaterialCategoryOption[] = [
  { categoryId: 1, categoryCode: 'RM-INK', categoryName: '油墨印刷', materialType: 'R' },
  { categoryId: 2, categoryCode: 'RM-FILM', categoryName: '片材/膜材', materialType: 'R' },
  { categoryId: 3, categoryCode: 'RM-PAPER', categoryName: '纸材（离型纸/离型膜）', materialType: 'R' },
  { categoryId: 4, categoryCode: 'RM-ADHESIVE', categoryName: '胶粘类-原材', materialType: 'R' },
  { categoryId: 5, categoryCode: 'AUX-ADHESIVE', categoryName: '胶粘类-辅料', materialType: 'A' },
  { categoryId: 6, categoryCode: 'AUX-ELEC', categoryName: '电子元件', materialType: 'A' },
  { categoryId: 7, categoryCode: 'AUX-HARDWARE', categoryName: '五金件', materialType: 'A' },
  { categoryId: 8, categoryCode: 'AUX-PACK', categoryName: '包材', materialType: 'A' },
  { categoryId: 9, categoryCode: 'AUX-TOOL', categoryName: '耗材工具', materialType: 'A' },
  { categoryId: 10, categoryCode: 'OTHER-R', categoryName: '其他（原材料）', materialType: 'R' },
  { categoryId: 11, categoryCode: 'OTHER-A', categoryName: '其他（辅助材料）', materialType: 'A' },
]

/** 大类（物料类型 R/A）由品类编码带出，只读展示。 */
export function deriveMaterialType(categoryCode?: string): string {
  if (!categoryCode) return ''
  if (categoryCode.startsWith('RM-')) return 'R'
  if (categoryCode.startsWith('AUX-')) return 'A'
  if (categoryCode === 'OTHER-A') return 'A'
  if (categoryCode === 'OTHER-R') return 'R'
  return ''
}

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

/** 读取品类下拉；接口不可用时退回种子列表。 */
export async function loadMaterialCategoryOptions(): Promise<MaterialCategoryOption[]> {
  try {
    const res = await request.get<R<MaterialCategoryOption[]>>('/inventory/material/category-options')
    const options = (res.data || [])
      .filter((item) => item?.categoryId != null)
      .map((item) => ({
        categoryId: Number(item.categoryId),
        categoryCode: item.categoryCode || '',
        categoryName: item.categoryName || item.categoryCode || String(item.categoryId),
        materialType: item.materialType || deriveMaterialType(item.categoryCode),
      }))
    if (options.length) return options
    console.warn('[material] 品类接口返回为空，改用本地种子列表')
  } catch (error) {
    console.warn(
      '[material] 品类接口不可用，改用本地种子列表',
      error
    )
  }
  return MATERIAL_CATEGORY_SEED.map((item) => ({ ...item }))
}

export interface ImportedCategoryFields {
  categoryId?: number | string
  categoryCode?: string
  categoryName?: string
  materialType?: string
  material_type?: string
}

/** 两个库存导入入口均保留行内品类和大类。 */
export function readImportedCategoryFields(row: Record<string, any>): ImportedCategoryFields {
  return {
    categoryId: row.categoryId || row.category_id || row['品类ID'],
    categoryCode: row.categoryCode || row.category_code || row['品类编码'],
    categoryName: row.categoryName || row.category_name || row['品类'] || row['品类名称'],
    materialType: row.materialType || row.material_type || row['物料类型'] || row['大类'],
  }
}

/** 缺失/未匹配品类按大类归入其他；兜底也不存在时明确拒绝建档。 */
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
  const type = (row.materialType || row.material_type || 'R').trim()
  const materialType = ['A', '辅助材料', '辅料'].includes(type) ? 'A' : 'R'
  const category = matched || options.find((item) => item.categoryCode === `OTHER-${materialType}`)
  if (!category?.categoryId) throw new Error(`未找到${materialType === 'A' ? '辅助材料' : '原材料'}默认品类，无法建档`)
  return {
    payload: {
      ...materialSaveFromImport(row, materialCode),
      categoryId: category.categoryId,
      materialType: category.materialType || deriveMaterialType(category.categoryCode),
    },
    usedFallback: !matched,
  }
}

// ==================== 规格属性（dev-20261023-022） ====================

/**
 * 规格属性口径（与服务端 MaterialSpecComposer 同一规则的前端预览）：
 * 1）规格由品类属性组合成文本，写回 inventory_material.specification，下游只读这段文本；
 * 2）数值 = 数字 + 单位；区间 = 下限-上限 + 单位；下拉/文本原样；
 * 3）补充说明 ≤30 字，用「；」接在生成文本之后，单独存 spec_supplement；
 * 4）总长 ≤200 字，单段 ≤40 字（打印「规格及模数」不能过长）。
 */
export const MATERIAL_SPEC_MAX_SUPPLEMENT = 30
export const MATERIAL_SPEC_MAX_TOTAL = 200
export const MATERIAL_SPEC_MAX_SEGMENT = 40
export const MATERIAL_SPEC_SUPPLEMENT_SEPARATOR = '；'

export interface MaterialSpecInputValue {
  value?: string
  value2?: string
}

/** 用户常把单位一起打进数字框（0.38mm），解析前统一剥掉。 */
export function stripSpecUnit(value: string | undefined, unit?: string | null): string {
  const text = (value || '').trim()
  const suffix = (unit || '').trim()
  if (!text || !suffix) return text
  if (text.toLowerCase().endsWith(suffix.toLowerCase())) {
    return text.slice(0, text.length - suffix.length).trim()
  }
  return text
}

/** 预览用的数值格式化：去掉尾随 0，非法值返回空串（服务端才是校验方）。 */
export function formatSpecNumber(raw: string | undefined): string {
  const text = (raw || '').trim()
  if (!text || !/^\d+(\.\d+)?$/.test(text)) return ''
  const num = Number(text)
  if (!Number.isFinite(num) || num <= 0) return ''
  return String(num)
}

/** 单个属性渲染成文本片段；未填或不合法返回空串（服务端负责报错）。 */
export function describeSpecValue(def: MaterialSpecAttrDef, input?: MaterialSpecInputValue): string {
  if (!def || !input) return ''
  const unit = (def.unit || '').trim()
  if (def.inputType === 'select') {
    const value = (input.value || '').trim()
    if (!value) return ''
    return (def.options || []).includes(value) ? value : ''
  }
  if (def.inputType === 'number') {
    const num = formatSpecNumber(stripSpecUnit(input.value, unit))
    return num ? `${num}${unit}` : ''
  }
  if (def.inputType === 'range') {
    const low = formatSpecNumber(stripSpecUnit(input.value, unit))
    const high = formatSpecNumber(stripSpecUnit(input.value2, unit))
    if (!low && !high) return ''
    if (!low) return `${high}${unit}`
    if (!high) return `${low}${unit}`
    return `${low}-${high}${unit}`
  }
  return (input.value || '').trim()
}

/** 组合规格文本（预览与提交文本用；服务端保存时会按同一规则重算并覆盖）。 */
export function composeSpecification(
  defs: MaterialSpecAttrDef[],
  values: Record<string, MaterialSpecInputValue>,
  supplement?: string
): string {
  const ordered = [...(defs || [])].sort(
    (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
  )
  const segments = ordered
    .map((def) => describeSpecValue(def, values?.[def.attrCode]))
    .filter(Boolean)
  const text = segments.join(' ')
  const extra = (supplement || '').trim()
  if (!extra) return text
  return text ? `${text}${MATERIAL_SPEC_SUPPLEMENT_SEPARATOR}${extra}` : extra
}

/** 必填属性缺失项（新增时前端预检，与服务端 required 同口径）。 */
export function findMissingSpecAttrs(
  defs: MaterialSpecAttrDef[],
  values: Record<string, MaterialSpecInputValue>
): MaterialSpecAttrDef[] {
  return (defs || []).filter(
    (def) => def.required && !describeSpecValue(def, values?.[def.attrCode])
  )
}

export interface SpecGuessSource {
  model?: string
  size?: string
  materialName?: string
}

/**
 * 存量反推（只做预填，不落库）：从型号/名称/尺寸里认出厚度、克重、离型力与下拉词。
 * 例：型号 0.05mm → 厚度 0.05；名称「离型纸120g黄色」→ 克重 120；
 *     名称「PC膜0.25透明」+ 表面处理选项含「幼砂」→ 命中「幼砂」。
 * 识别不出就不填，宁缺勿错（尺寸/颜色/料号不进规格）。
 */
export function guessSpecValues(
  defs: MaterialSpecAttrDef[],
  source: SpecGuessSource
): Record<string, MaterialSpecInputValue> {
  const text = [source?.model, source?.materialName, source?.size]
    .map((item) => (item || '').trim())
    .filter(Boolean)
    .join(' ')
  const lower = text.toLowerCase()
  const result: Record<string, MaterialSpecInputValue> = {}
  if (!text) return result

  for (const def of defs || []) {
    if (def.inputType === 'select') {
      const hit = [...(def.options || [])]
        .filter(Boolean)
        .sort((a, b) => b.length - a.length)
        .find((option) => lower.includes(option.toLowerCase()))
      if (hit) result[def.attrCode] = { value: hit }
    }
  }

  const numericDefs = (defs || []).filter(
    (def) => def.inputType === 'number' || def.inputType === 'range'
  )
  const unitPattern = (unit: string) =>
    unit.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

  // 第一轮：属性名紧跟着数字的写法最可靠，如「离型力10-15g」「克重 120g」
  for (const def of numericDefs) {
    const name = (def.attrName || '').trim()
    if (!name) continue
    const unit = (def.unit || '').trim()
    const suffix = unit ? `\\s*${unitPattern(unit)}` : ''
    const match = text.match(
      new RegExp(`${unitPattern(name)}[^\\d]{0,6}(\\d+(?:\\.\\d+)?)(?:\\s*[-~]\\s*(\\d+(?:\\.\\d+)?))?${suffix}`, 'i')
    )
    if (match) {
      result[def.attrCode] = match[2] ? { value: match[1], value2: match[2] } : { value: match[1] }
    }
  }

  // 第二轮：只按单位认（如型号 0.05mm → 厚度 0.05）；同单位已被别的属性占用时不再抢
  const claimedUnits = new Set(
    numericDefs
      .filter((def) => result[def.attrCode])
      .map((def) => (def.unit || '').trim().toLowerCase())
  )
  for (const def of numericDefs) {
    if (result[def.attrCode]) continue
    const unit = (def.unit || '').trim()
    if (!unit || claimedUnits.has(unit.toLowerCase())) continue
    const match = text.match(
      new RegExp(`(\\d+(?:\\.\\d+)?)(?:\\s*[-~]\\s*(\\d+(?:\\.\\d+)?))?\\s*${unitPattern(unit)}`, 'i')
    )
    if (!match) continue
    // 数值属性不吃区间写法（10-15g 只能属于区间属性）
    if (def.inputType === 'number' && match[2]) continue
    result[def.attrCode] = match[2] ? { value: match[1], value2: match[2] } : { value: match[1] }
    claimedUnits.add(unit.toLowerCase())
  }
  return result
}

/** 已保存的属性值反显成表单输入值。 */
export function specValueToInput(
  def: MaterialSpecAttrDef,
  saved?: MaterialSpecValue | null
): MaterialSpecInputValue {
  if (!def || !saved) return { value: '' }
  const numText = (num?: number | null) => (num == null ? '' : String(num))
  if (def.inputType === 'number') return { value: numText(saved.valueNum) }
  if (def.inputType === 'range') {
    return { value: numText(saved.valueNum), value2: numText(saved.valueNum2) }
  }
  return { value: saved.valueText || '' }
}


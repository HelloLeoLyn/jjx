import { composeMaterialName, type MaterialMasterValues } from './materialMasterFields'

/**
 * 物料「重新生成」弹窗的纯逻辑（dev-20261023-016）。
 * 口径全部复用 materialMasterFields：材料 23 词、颜色 13 色、必填矩阵、名称拼装。
 */

export interface OriginalRefs {
  name: string
  specification: string
}

const ORIGINAL_NAME_PATTERN = /【原名称】\s*([^【\n]*)/
const ORIGINAL_SPEC_PATTERN = /【原规格】\s*([^【\n]*)/

/** 参考区：优先取 remark 里的【原名称】【原规格】，没有则用当前值。 */
export function readOriginalRefs(
  remark: string | undefined,
  currentName: string | undefined,
  currentSpecification: string | undefined
): OriginalRefs {
  const text = remark || ''
  const name = text.match(ORIGINAL_NAME_PATTERN)?.[1]?.trim()
  const specification = text.match(ORIGINAL_SPEC_PATTERN)?.[1]?.trim()
  return {
    name: name || (currentName || '').trim() || '-',
    specification: specification || (currentSpecification || '').trim() || '-',
  }
}

const pad = (value: number) => String(value).padStart(2, '0')

/** 留痕：保留原备注，末尾追加【重生成】时间 + 操作人。 */
export function appendRegenerateTrace(
  remark: string | undefined,
  operator: string,
  at: Date = new Date()
): string {
  const stamp = `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())} ${pad(at.getHours())}:${pad(at.getMinutes())}`
  const trace = `【重生成】${stamp} ${(operator || '').trim() || 'system'}`
  const base = (remark || '').trim()
  return base ? `${base}\n${trace}` : trace
}

/** 卷料品类：两段尺寸的第二段按厚度解释（与后端 MaterialSizeParser 一致）。 */
export function isRollCategory(categoryCode?: string): boolean {
  return ['RM-ADHESIVE', 'AUX-ADHESIVE', 'RM-PAPER'].includes(categoryCode || '')
}

const NUMBER = '(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)'
const DIMENSION = new RegExp(`^(${NUMBER})\\s*(mm|cm|inch|in|m|毫米|厘米|米|英寸|\")?$`, 'i')

const toMillimeters = (value: string, unit?: string) => {
  const factor = unit?.toLowerCase()
  const parsed = Number(value)
  if (factor === 'cm' || factor === '厘米') return parsed * 10
  if (factor === 'm' || factor === '米') return parsed * 1000
  if (factor === 'in' || factor === 'inch' || factor === '英寸' || factor === '"') return parsed * 25.4
  return parsed
}

const formatNumber = (value: number) => {
  const rounded = Math.round(value * 10000) / 10000
  return String(rounded)
}

/**
 * 尺寸预览：每段单位独立，内部统一 mm；卷料两段=宽×厚，其余两段=宽×长。
 * 返回 '' 表示未填；返回「尺寸格式错误：xxx」时不可保存。
 */
export function buildSizePreview(size: string | undefined, categoryCode?: string): string {
  const text = (size || '').trim()
  if (!text) return ''
  if (text.length > 50) return '尺寸不能超过50个字符'
  const parts = text.split(/[×xX*＊]/)
  if (parts.length > 3) return '尺寸最多三维：宽×长/厚×厚'
  const values: number[] = []
  for (const part of parts) {
    const match = part.trim().match(DIMENSION)
    if (!match) return `尺寸格式错误：${part.trim()}`
    const value = toMillimeters(match[1], match[2])
    if (!(value > 0)) return '尺寸必须大于0'
    values.push(value)
  }
  if (values.length === 1) return `宽 ${formatNumber(values[0])} mm`
  if (values.length === 2 && isRollCategory(categoryCode)) {
    return `宽 ${formatNumber(values[0])} mm × 厚 ${formatNumber(values[1])} mm`
  }
  if (values.length === 2) {
    return `宽 ${formatNumber(values[0])} mm × 长 ${formatNumber(values[1])} mm`
  }
  return `宽 ${formatNumber(values[0])} mm × 长 ${formatNumber(values[1])} mm × 厚 ${formatNumber(values[2])} mm`
}

/** 自动名称（未手动命名时使用）。 */
export function buildAutoName(values: MaterialMasterValues): string {
  return composeMaterialName(values)
}

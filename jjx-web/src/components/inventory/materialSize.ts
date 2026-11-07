import type { MaterialDimensionUnit, MaterialSpecField } from '@/types/inventory/materialMaster'

/** 尺寸唯一编辑值：宽*长*厚；缺项保留位置，圆形为 Φ直径*厚。分隔符统一半角 *，输入兼容 ×、x/X 与全角 ＊。 */
export interface MaterialSizeParts {
  round: boolean
  width: string
  length: string
  thickness: string
  unit: string
}

/** 历史 ×、字母 x/X 与全角 ＊ 都按同一分隔符识别，写回统一为 *。 */
const SEPARATOR = /[×xX*＊]/

export const emptySizeParts = (): MaterialSizeParts => ({ round: false, width: '', length: '', thickness: '', unit: 'mm' })

export function parseMaterialSize(value: string | null | undefined): MaterialSizeParts | null {
  const raw = (value || '').trim()
  if (!raw) return emptySizeParts()
  const unit = /mm$/i.test(raw) ? raw.slice(-2) : ''
  let body = (unit ? raw.slice(0, -2) : raw).trim()
  const round = /^[ΦφØø⌀]/.test(body)
  if (round) body = body.slice(1).trim()
  const parts = body.split(SEPARATOR).map(part => part.trim())
  if (parts.length > (round ? 2 : 3) || parts.every(part => !part) ||
      parts.some(part => part && (!/^\d+(?:\.\d+)?$/.test(part) || Number(part) <= 0))) return null
  return { round, width: parts[0] || '', length: round ? '' : parts[1] || '',
    thickness: parts[round ? 1 : 2] || '', unit }
}

export function composeMaterialSize(parts: MaterialSizeParts): string {
  const values = (parts.round ? [parts.width, parts.thickness] : [parts.width, parts.length, parts.thickness])
    .map(value => value.trim())
  if (values.some(value => value && (!/^\d+(?:\.\d+)?$/.test(value) || Number(value) <= 0))) {
    throw new Error('尺寸须为大于0的数值，单位在右侧选择')
  }
  while (values.length && !values[values.length - 1]) values.pop()
  if (!values.length) return ''
  return `${parts.round ? 'Φ' : ''}${values.join('*')}${parts.unit}`
}

/** 回显/预览用：可识别尺寸统一半角 *；无法识别的原文原样返回，不改写不丢弃。 */
export function normalizeMaterialSize(value: string | null | undefined): string {
  const raw = (value || '').trim()
  if (!raw) return raw
  const parts = parseMaterialSize(raw)
  if (!parts) return raw
  return composeMaterialSize(parts)
}

/** 编辑时展示历史厚度；无法安全解析的原文不改写、不丢弃。 */
export function mergeLegacyThickness(size: string, thickness: unknown): string {
  if (thickness == null || thickness === '') return size
  const parts = parseMaterialSize(size)
  if (!parts || parts.thickness) return size
  const value = String(thickness).trim().replace(/mm$/i, '').trim()
  if (!/^\d+(?:\.\d+)?$/.test(value) || Number(value) <= 0) return size
  parts.thickness = value
  return composeMaterialSize(parts)
}

/** 编辑与提交仅使用数值字段；文本只供预览和列表展示。 */
export interface MaterialSizeValue {
  widthMm: number | null
  lengthMm: number | null
  thicknessMm: number | null
}

/** 十进制移位后四舍五入，避免 1005mm → 1.005m 在二进制 toFixed 中舍为 1。 */
function roundedDisplay(value: number, shift: number, decimals: number): string {
  const move = (number: number, places: number) => {
    const [coefficient, exponent = '0'] = String(number).split('e')
    return Number(`${coefficient}e${Number(exponent) + places}`)
  }
  return String(move(Math.round(move(value, shift + decimals)), -decimals))
}

/** 单位只影响显示，源值始终是 mm；mm 缺省保留原精度。 */
export function displayDimensionOf(valueMm: number, unit: MaterialDimensionUnit = 'mm', scale?: number | null): string {
  const shift = { mm: 0, cm: -1, m: -3, μm: 3 }[unit]
  const decimals = scale ?? (unit === 'μm' ? 1 : unit === 'mm' ? undefined : 2)
  return decimals == null ? String(valueMm) : roundedDisplay(valueMm, shift, decimals)
}

/** 字段键保持与规格模板一致，供列表、表单与编辑器共用。 */
export type MaterialDimensionFields = readonly MaterialSpecField[]
export const dimensionKey = { widthMm: 'width', lengthMm: 'length', thicknessMm: 'thickness_mm' } as const
export const dimensionAbbreviation = { widthMm: 'W', lengthMm: 'L', thicknessMm: 'T' } as const
export function dimensionField(fields: MaterialDimensionFields, key: keyof typeof dimensionKey): MaterialSpecField | undefined {
  return fields.find(field => field.key === dimensionKey[key])
}

/** 每个尺寸按自身单位解析，统一输出 mm；显式后缀优先。 */
export function parseMaterialDimension(text: string, unit: MaterialDimensionUnit = 'mm'): number | null {
  const raw = text.trim()
  if (!raw) return null
  const dimension = /^(\d+(?:\.\d+)?)\s*(mm|cm|m|[μµ]m|um)?$/i.exec(raw)
  if (!dimension) return NaN
  const shift = { mm: 0, cm: 1, m: 3, 'μm': -3, 'µm': -3, um: -3 }[dimension[2]?.toLowerCase() || unit]!
  return Number(`${dimension[1]}e${shift}`)
}

export const emptyMaterialSize = (): MaterialSizeValue => ({ widthMm: null, lengthMm: null, thicknessMm: null })

export function validateMaterialDimensions(value: MaterialSizeValue): void {
  for (const [number, scale, integers, label] of [
    [value.widthMm, 2, 8, '宽'], [value.lengthMm, 2, 10, '长'], [value.thicknessMm, 4, 6, '厚'],
  ] as const) {
    if (number == null) continue
    const precision = Number(number.toFixed(scale))
    if (!Number.isFinite(number) || number <= 0 || number >= 10 ** integers || precision !== number) {
      throw new Error(`${label}须为大于0的数值，最多${scale}位小数`)
    }
  }
}

export function renderMaterialDimensions(value: { widthMm?: number | null; lengthMm?: number | null; thicknessMm?: number | null; sizeUnit?: string | null }, round = false, fields: MaterialDimensionFields = []): string {
  const configured = fields.filter(field => Object.values(dimensionKey).includes(field.key as typeof dimensionKey[keyof typeof dimensionKey]) && (field.unit != null || field.scale != null))
  if (configured.length) {
    // 配置了独立单位时每段带单位；段序来自模板，缺项跳过。
    const keys = Object.keys(dimensionKey) as (keyof typeof dimensionKey)[]
    return fields.flatMap(field => {
      const key = keys.find(key => dimensionKey[key] === field.key)
      if (!key || value[key] == null || (round && key === 'lengthMm')) return []
      const unit = field.unit || 'mm'
      const text = field.unit != null || field.scale != null
        ? displayDimensionOf(value[key]!, field.unit || 'mm', field.scale) : String(value[key])
      return [`${round && key === 'widthMm' ? 'Φ' : dimensionAbbreviation[key]}${text}${unit}`]
    }).join('*')
  }
  // 每项明确标识维度和单位，缺项省略，不用占位符猜测含义。
  return (Object.keys(dimensionKey) as (keyof typeof dimensionKey)[]).flatMap(key => {
    if (value[key] == null || (round && key === 'lengthMm')) return []
    return [`${round && key === 'widthMm' ? 'Φ' : dimensionAbbreviation[key]}${value[key]}mm`]
  }).join('*')
}

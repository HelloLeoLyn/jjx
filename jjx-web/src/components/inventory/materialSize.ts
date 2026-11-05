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
  sizeUnit: string
}

export interface MaterialSizeContext {
  unit?: string | null
  materialForm?: string | null
}

/** 唯一判据与 Java 一致；FILM、M 不推断为卷材。 */
export const isRollMaterial = (context: MaterialSizeContext): boolean => context.unit === 'ROLL'

/** 十进制移位后四舍五入，避免 1005mm → 1.005m 在二进制 toFixed 中舍为 1。 */
function roundedDisplay(value: number, shift: number, decimals: number): string {
  const move = (number: number, places: number) => {
    const [coefficient, exponent = '0'] = String(number).split('e')
    return Number(`${coefficient}e${Number(exponent) + places}`)
  }
  return String(move(Math.round(move(value, shift + decimals)), -decimals))
}

export function displayMaterialDimension(value: number, key: 'widthMm' | 'lengthMm' | 'thicknessMm', context: MaterialSizeContext): string {
  if (!isRollMaterial(context)) return String(value)
  if (key === 'thicknessMm') return `${roundedDisplay(value, 3, 1)}μm`
  if (key === 'lengthMm') return `${roundedDisplay(value, -3, 2)}m`
  return `${value}mm`
}

/** 厚度显式带 μm 时换算为 mm；裸数沿用原有 mm 输入语义。 */
export function parseMaterialThickness(text: string): number | null {
  const raw = text.trim()
  if (!raw) return null
  const microns = /^(\d+(?:\.\d+)?)\s*[μµ]m$/.exec(raw)
  if (microns) return Number(`${microns[1]}e-3`)
  return /^\d+(?:\.\d+)?$/.test(raw) ? Number(raw) : NaN
}

export const emptyMaterialSize = (): MaterialSizeValue => ({ widthMm: null, lengthMm: null, thicknessMm: null, sizeUnit: 'mm' })

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
  if (!['mm', 'cm', 'm'].includes(value.sizeUnit)) throw new Error('尺寸单位只允许 mm/cm/m')
}

export function renderMaterialDimensions(value: { widthMm?: number | null; lengthMm?: number | null; thicknessMm?: number | null; sizeUnit?: string | null } & MaterialSizeContext, round = false, context: MaterialSizeContext = value): string {
  if (isRollMaterial(context)) {
    const entries = (['thicknessMm', 'widthMm', 'lengthMm'] as const).map(key => ({ key, value: value[key] }))
    // 每段已有独立单位，缺项与后端 spec_pattern 一样跳过，不会误读尺寸身份。
    return entries.flatMap(entry => entry.value == null ? [] : [displayMaterialDimension(entry.value, entry.key, context)]).join('*')
  }
  // 缺项保留中间位置；首个有值项之前的缺位不补占位（只填厚度即 0.175mm，dev-20261030-016）
  const values = [...(round ? [value.widthMm, value.thicknessMm] : [value.widthMm, value.lengthMm, value.thicknessMm])]
  while (values.length && values[0] == null) values.shift()
  while (values.length && values.at(-1) == null) values.pop()
  if (!values.length) return ''
  return `${round ? 'Φ' : ''}${values.map(v => v == null ? '' : String(v)).join('*')}${value.sizeUnit || 'mm'}`
}

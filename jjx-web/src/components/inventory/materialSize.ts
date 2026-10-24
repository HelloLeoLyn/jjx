/** 尺寸唯一编辑值：宽*长*厚；缺项保留位置，圆形为 Φ直径*厚。 */
export interface MaterialSizeParts {
  round: boolean
  width: string
  length: string
  thickness: string
  unit: string
}

export const emptySizeParts = (): MaterialSizeParts => ({ round: false, width: '', length: '', thickness: '', unit: 'mm' })

export function parseMaterialSize(value: string | null | undefined): MaterialSizeParts | null {
  const raw = (value || '').trim()
  if (!raw) return emptySizeParts()
  const unit = /mm$/i.test(raw) ? raw.slice(-2) : ''
  let body = (unit ? raw.slice(0, -2) : raw).trim()
  const round = /^[ΦφØø⌀]/.test(body)
  if (round) body = body.slice(1).trim()
  const parts = body.split(/[×xX*]/).map(part => part.trim())
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

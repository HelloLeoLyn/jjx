import { engineeringDocumentTypeLabel } from './productFileCategories'

export interface DrawingIdentity {
  drawingNo?: string | null
  drawingName?: string | null
  documentType?: string | null
  category?: string | null
  version?: string | null
}
const typeCodes: Record<string, string> = {
  STRUCTURE_DRAWING: 'ST',
  OUTLINE_DRAWING: 'OUT',
  PANEL_DRAWING: 'PNL',
  CIRCUIT_DRAWING: 'CIR',
  ASSEMBLY_DRAWING: 'ASM',
  PACKAGING_DRAWING: 'PKG',
  PRINT_GUIDE: 'PRT',
  CONFIRM_DRAWING: 'CFM',
  FILM: 'FLM',
  MOLD_DRAWING: 'MLD',
  DIE_PARAMETER_DRAWING: 'DIE',
  ENGINEERING_DRAWING: 'ENG',
  DIE_DRAWING: 'DWD',
}
/** 一张新图纸分配一个身份；版本和原稿/打印件不另占序号，历史身份不回写。 */
export function newDrawingIdentity(
  productCode: string,
  productName: string,
  documentType: string,
  existing: DrawingIdentity[],
  typeLabel = engineeringDocumentTypeLabel(documentType)
) {
  const base = `${productCode}-${typeCodes[documentType] || 'ENG'}`
  const sameType = existing.filter(
    (file) =>
      file.documentType === documentType ||
      (documentType === 'STRUCTURE_DRAWING' && !file.documentType && file.category === '结构图')
  )
  const identities = new Set(sameType.map((file) => file.drawingNo?.trim()).filter(Boolean))
  let ordinal = identities.size ? identities.size + 1 : 1
  for (const no of existing.map((file) => file.drawingNo?.trim() || '')) {
    if (no === base) ordinal = Math.max(ordinal, 2)
    if (no.startsWith(base + '-')) {
      const suffix = no.slice(base.length + 1)
      if (/^[1-9]\d*$/.test(suffix)) ordinal = Math.max(ordinal, Number(suffix) + 1)
    }
  }
  const suffix = ordinal > 1 ? `-${ordinal}` : ''
  return {
    drawingNo: base + suffix,
    drawingName: `${productName || productCode}${typeLabel}${suffix}`,
  }
}
/** 不能解析的历史版本返回 null，调用方保留人工确认入口；不猜测 V1 等版本。 */
export function nextDrawingVersion(versions: (string | null | undefined)[]): string | null {
  if (!versions.length) return 'A'
  const values = versions.map((value) => (value || '').trim().toUpperCase())
  if (values.some((value) => !/^[A-Z]+$/.test(value))) return null
  const latest = values.sort((a, b) => b.length - a.length || b.localeCompare(a))[0]
  const next = [...latest]
  let i = next.length - 1
  while (i >= 0 && next[i] === 'Z') next[i--] = 'A'
  if (i < 0) next.unshift('A')
  else next[i] = String.fromCharCode(next[i].charCodeAt(0) + 1)
  return next.join('')
}

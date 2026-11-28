export interface FilmStyle { styleNo?: string; name: string; inkColor: string }
export interface PlateSource { filmId: number; filmNo: string; versionId: number; styleNos?: string[]; styles: FilmStyle[] }
export interface ProcessStyle { versionId: number; styleNo: string; inkColor?: string }
export function readStyles(raw?: string | null): FilmStyle[] {
  if (!raw) return []
  const value = JSON.parse(raw)
  if (!Array.isArray(value)) throw new Error('版本样式格式异常')
  return value
}
export function readSources(raw?: string | null): PlateSource[] {
  if (!raw) return []
  const value = JSON.parse(raw)
  if (!Array.isArray(value)) throw new Error('网版来源格式异常')
  return value
}
/** 换版只保留身份完全相同的工序覆盖值；新增样式用来源色号。 */
export function selectPlateStyles(raw: string | undefined, sources: PlateSource[]) {
  const required = JSON.parse(raw || '{}')
  const previous: ProcessStyle[] = required.styles || []
  const unique = new Map<string, ProcessStyle>()
  sources.forEach(source => source.styles.forEach(style => {
    const old = previous.find(item => item.versionId === source.versionId && item.styleNo === style.styleNo)
    unique.set(`${source.versionId}:${style.styleNo}`, { versionId: source.versionId, styleNo: style.styleNo!, inkColor: old?.inkColor ?? style.inkColor })
  }))
  required.styles = [...unique.values()]
  if (!required.styles.length) delete required.styles
  return JSON.stringify(required)
}

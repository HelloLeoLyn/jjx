export type WorkSpecFontArea = 'title' | 'materials' | 'flows' | 'notes' | 'footer'
export type WorkSpecFontSettings = Record<WorkSpecFontArea, number>

export const workSpecFontAreas: { key: WorkSpecFontArea; label: string }[] = [
  { key: 'title', label: '标题' },
  { key: 'materials', label: '材料与刀模' },
  { key: 'flows', label: '作业流程' },
  { key: 'notes', label: '加工与变更' },
  { key: 'footer', label: '页脚' },
]

/** 各区域字号调节范围（相对默认的 px 增量，整数；0=默认）（2026-10-14 dev-20261014-014） */
export const WORK_SPEC_FONT_MIN = -3
export const WORK_SPEC_FONT_MAX = 6

export function defaultWorkSpecFontSettings(): WorkSpecFontSettings {
  return { title: 0, materials: 0, flows: 0, notes: 0, footer: 0 }
}

export function parseWorkSpecFontSettings(value?: string | null): WorkSpecFontSettings {
  const settings = defaultWorkSpecFontSettings()
  if (!value) return settings
  const parts = value.split(',').map(Number)
  if (parts.length !== workSpecFontAreas.length
      || parts.some(part => !Number.isInteger(part) || part < WORK_SPEC_FONT_MIN || part > WORK_SPEC_FONT_MAX)) return settings
  workSpecFontAreas.forEach((area, index) => { settings[area.key] = parts[index] })
  return settings
}

export function serializeWorkSpecFontSettings(settings: WorkSpecFontSettings): string {
  return workSpecFontAreas.map(area => settings[area.key]).join(',')
}

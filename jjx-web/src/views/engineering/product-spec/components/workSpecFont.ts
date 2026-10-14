export type WorkSpecFontArea = 'title' | 'materials' | 'flows' | 'notes' | 'footer'
export type WorkSpecFontSettings = Record<WorkSpecFontArea, number>

export const workSpecFontAreas: { key: WorkSpecFontArea; label: string }[] = [
  { key: 'title', label: '标题' },
  { key: 'materials', label: '材料与刀模' },
  { key: 'flows', label: '作业流程' },
  { key: 'notes', label: '加工与变更' },
  { key: 'footer', label: '页脚' },
]

export const workSpecFontOptions = [
  { label: '小一号', value: -1 },
  { label: '默认', value: 0 },
  { label: '大一号', value: 1 },
  { label: '大两号', value: 2 },
]

export function defaultWorkSpecFontSettings(): WorkSpecFontSettings {
  return { title: 0, materials: 0, flows: 0, notes: 0, footer: 0 }
}

export function parseWorkSpecFontSettings(value?: string | null): WorkSpecFontSettings {
  const settings = defaultWorkSpecFontSettings()
  if (!value) return settings
  const parts = value.split(',').map(Number)
  if (parts.length !== workSpecFontAreas.length || parts.some(part => !workSpecFontOptions.some(option => option.value === part))) return settings
  workSpecFontAreas.forEach((area, index) => { settings[area.key] = parts[index] })
  return settings
}

export function serializeWorkSpecFontSettings(settings: WorkSpecFontSettings): string {
  return workSpecFontAreas.map(area => settings[area.key]).join(',')
}

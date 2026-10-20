export const materialFontColumns = [
  { key: 'materialNumber', label: '序号', heading: '序号' },
  { key: 'materialProject', label: '项目', heading: '项目' },
  { key: 'materialName', label: '材料', heading: '材　料' },
  { key: 'materialSpec', label: '规格及模数', heading: '规格及模数' },
  { key: 'materialDie', label: '刀模位置', heading: '刀模位置' },
  { key: 'materialEmboss', label: '凹凸条件项目', heading: '凹凸条件' },
  { key: 'materialEmbossValue', label: '凹凸条件数值', heading: '' },
] as const
const flowFontColumns = [
  { key: 'flow0Number', label: '序号', group: '面板作业流程' },
  { key: 'flow0Operation', label: '作业流程', group: '面板作业流程' },
  { key: 'flow0Hours', label: '耗时', group: '面板作业流程' },
  { key: 'flow1Number', label: '序号', group: '上线作业流程' },
  { key: 'flow1Operation', label: '作业流程', group: '上线作业流程' },
  { key: 'flow1Hours', label: '耗时', group: '上线作业流程' },
  { key: 'flow2Number', label: '序号', group: '下线作业流程' },
  { key: 'flow2Operation', label: '作业流程', group: '下线作业流程' },
  { key: 'flow2Hours', label: '耗时', group: '下线作业流程' },
] as const
export type WorkSpecFontColumn = typeof materialFontColumns[number]['key'] | typeof flowFontColumns[number]['key']
export type WorkSpecFontArea = 'title' | 'notesTitle' | 'notes' | 'footer' | 'structureTitle' | 'structureCaption'
  | `${WorkSpecFontColumn}Title` | `${WorkSpecFontColumn}Content`
export type WorkSpecFontSettings = Record<WorkSpecFontArea, number>
interface FontArea { key: WorkSpecFontArea; label: string; group: string; defaultSize: number }
export const workSpecFontAreas: FontArea[] = [
  { key: 'title', label: '作业规范大标题', group: '常规', defaultSize: 27 },
  { key: 'notesTitle', label: '加工与变更 · 标题', group: '常规', defaultSize: 17 },
  { key: 'notes', label: '加工与变更 · 内容', group: '常规', defaultSize: 12 },
  { key: 'structureTitle', label: '结构图 · 标题', group: '常规', defaultSize: 15 },
  { key: 'structureCaption', label: '结构图 · 说明', group: '常规', defaultSize: 8 },
  { key: 'footer', label: '页脚', group: '常规', defaultSize: 13 },
  ...materialFontColumns.flatMap((column): FontArea[] => [
    { key: `${column.key}Title`, label: `${column.label} · 标题`, group: '材料表', defaultSize: 14 },
    { key: `${column.key}Content`, label: `${column.label} · 内容`, group: '材料表',
      defaultSize: ['materialNumber', 'materialProject', 'materialSpec'].includes(column.key) ? 13 : 12 },
  ]),
  ...flowFontColumns.flatMap((column): FontArea[] => [
    { key: `${column.key}Title`, label: `${column.label} · 标题`, group: column.group, defaultSize: 14 },
    { key: `${column.key}Content`, label: `${column.label} · 内容`, group: column.group,
      defaultSize: column.key.endsWith('Operation') ? 12 : 13 },
  ]),
]
export const workSpecFontGroups = ['常规', '材料表', '面板作业流程', '上线作业流程', '下线作业流程'].map(label => ({
  label, areas: workSpecFontAreas.filter(area => area.group === label && area.key !== 'materialEmbossValueTitle'),
}))
/** 实际字号，单位px；v3保存逐列设置，兼容旧五项增量和v2区域设置。 */
export const WORK_SPEC_FONT_MIN = 5
export const WORK_SPEC_FONT_MAX = 48
export function defaultWorkSpecFontSettings(): WorkSpecFontSettings {
  return Object.fromEntries(workSpecFontAreas.map(area => [area.key, area.defaultSize])) as WorkSpecFontSettings
}
export function parseWorkSpecFontSettings(value?: string | null): WorkSpecFontSettings {
  const settings = defaultWorkSpecFontSettings()
  if (!value) return settings
  const tokens = value.split(',')
  const version = tokens[0].startsWith('v') ? tokens.shift() : 'legacy'
  if (tokens.some(token => !token.trim())) return settings
  const parts = tokens.map(Number)
  if (version === 'v3') {
    if (parts.length !== workSpecFontAreas.length || !validSizes(parts)) return settings
    workSpecFontAreas.forEach((area, index) => { settings[area.key] = parts[index] })
    return settings
  }
  if (version === 'v2') {
    if (parts.length !== 8 || !validSizes(parts)) return settings
    const [title, materialTitle, materialContent, flowTitle, flowContent, notesTitle, notes, footer] = parts
    for (const area of workSpecFontAreas) {
      if (area.group === '材料表') settings[area.key] = area.key.endsWith('Title') ? materialTitle : materialContent
      if (area.group.endsWith('作业流程')) settings[area.key] = area.key.endsWith('Title') ? flowTitle : flowContent
    }
    Object.assign(settings, { title, notesTitle, notes, footer, structureTitle: flowTitle })
    return settings
  }
  if (version !== 'legacy' || parts.length !== 5 || parts.some(part => !Number.isInteger(part) || part < -3 || part > 6)) return settings
  const [title, materials, flows, notes, footer] = parts
  for (const area of workSpecFontAreas) {
    if (area.group === '材料表') settings[area.key] += materials
    if (area.group.endsWith('作业流程') || area.key.startsWith('structure')) settings[area.key] += flows
  }
  settings.title += title
  settings.notesTitle += notes
  settings.notes += notes
  settings.footer += footer
  return settings
}
function validSizes(parts: number[]): boolean {
  return parts.every(part => Number.isInteger(part) && part >= WORK_SPEC_FONT_MIN && part <= WORK_SPEC_FONT_MAX)
}
export function serializeWorkSpecFontSettings(settings: WorkSpecFontSettings): string {
  return 'v3,' + workSpecFontAreas.map(area => settings[area.key]).join(',')
}

export const materialFontColumns = [
  { key: 'materialNumber', label: '序号', heading: '序号' },
  { key: 'materialProject', label: '项目', heading: '项目' },
  { key: 'materialName', label: '材料', heading: '材　料' },
  { key: 'materialSpec', label: '规格及模数', heading: '规格及模数' },
  { key: 'materialDie', label: '刀模位置', heading: '刀模位置' },
  { key: 'materialEmboss', label: '凹凸条件项目', heading: '凹凸条件' },
  { key: 'materialEmbossValue', label: '凹凸条件数值', heading: '' },
] as const
export const materialFontColumnsB = [
  ...materialFontColumns.slice(0, 3),
  { key: 'materialSpec', label: '规格', heading: '规格' },
  { key: 'materialModule', label: '模数', heading: '模数' },
  { key: 'materialBase', label: '基数', heading: '基数' },
  materialFontColumns[4],
] as const
export type WorkSpecTemplate = 'A' | 'B'
export function normalizeWorkSpecTemplate(value?: unknown): WorkSpecTemplate {
  return value === 'B' ? 'B' : 'A'
}
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
  | 'materialModule' | 'materialBase'
export type WorkSpecFontArea = 'title' | 'notesTitle' | 'notes' | 'footer' | 'structureTitle' | 'structureCaption'
  | `${WorkSpecFontColumn}Title` | `${WorkSpecFontColumn}Content`
  | 'materialProjectIcon' | 'flow0Icon' | 'flow1Icon' | 'flow2Icon'
export type WorkSpecFontSettings = Record<WorkSpecFontArea, number>
interface FontArea { key: WorkSpecFontArea; label: string; group: string; defaultSize: number }
const legacyFontAreas: FontArea[] = [
  { key: 'title', label: '作业规范大标题', group: '常规', defaultSize: 27 },
  { key: 'notesTitle', label: '加工与变更 · 标题', group: '常规', defaultSize: 17 },
  { key: 'notes', label: '加工与变更 · 内容', group: '常规', defaultSize: 12 },
  { key: 'structureTitle', label: '结构图 · 标题', group: '常规', defaultSize: 15 },
  { key: 'structureCaption', label: '结构图 · 说明', group: '常规', defaultSize: 8 },
  { key: 'footer', label: '页脚', group: '常规', defaultSize: 13 },
  ...materialFontColumns.flatMap((column): FontArea[] => [
    { key: `${column.key}Title`, label: `${column.label} · 标题`, group: '材料表', defaultSize: 14 },
    { key: `${column.key}Content`, label: `${column.label} · 内容`, group: '材料表',
      // 项目内容默认 18（用户 2026-10-31 口径，dev-20261031-005）；序号/规格及模数维持 13，其余列 12
      defaultSize: column.key === 'materialProject' ? 18
        : ['materialNumber', 'materialSpec'].includes(column.key) ? 13 : 12 },
  ]),
  ...flowFontColumns.flatMap((column): FontArea[] => [
    { key: `${column.key}Title`, label: `${column.label} · 标题`, group: column.group, defaultSize: 14 },
    { key: `${column.key}Content`, label: `${column.label} · 内容`, group: column.group,
      defaultSize: column.key.endsWith('Operation') ? 12 : 13 },
  ]),
]
const moduleBaseFontAreas: FontArea[] = (['materialModule', 'materialBase'] as const).flatMap(
  (key): FontArea[] => [
    { key: `${key}Title`, label: `${key === 'materialModule' ? '模数' : '基数'} · 标题`, group: '材料表', defaultSize: 14 },
    { key: `${key}Content`, label: `${key === 'materialModule' ? '模数' : '基数'} · 内容`, group: '材料表', defaultSize: 13 },
  ]
)
/** 图标尺寸独立设置：默认18px，不随该格文字字号变化。 */
const iconFontAreas: FontArea[] = [
  { key: 'materialProjectIcon', label: '项目 · 图标', group: '材料表', defaultSize: 18 },
  { key: 'flow0Icon', label: '图标', group: '面板作业流程', defaultSize: 18 },
  { key: 'flow1Icon', label: '图标', group: '上线作业流程', defaultSize: 18 },
  { key: 'flow2Icon', label: '图标', group: '下线作业流程', defaultSize: 18 },
]
/** v4 序列化顺序（加入图标项之前），仅用于解析旧打印链接。 */
const v4FontAreas: FontArea[] = [...legacyFontAreas, ...moduleBaseFontAreas]
export const workSpecFontAreas: FontArea[] = [...v4FontAreas, ...iconFontAreas]
export const workSpecFontGroups = ['常规', '材料表', '面板作业流程', '上线作业流程', '下线作业流程'].map(label => ({
  label,
  areas: [...legacyFontAreas, ...iconFontAreas].filter(
    area => area.group === label && area.key !== 'materialEmbossValueTitle'
  ),
}))
export function workSpecFontGroupsForTemplate(template: WorkSpecTemplate) {
  if (template === 'A') return workSpecFontGroups
  const columns = materialFontColumnsB.map(column => column.key)
  const groups = workSpecFontGroups.map(group => ({
    label: group.label,
    areas: group.label === '材料表'
      ? workSpecFontAreas.filter(area =>
          area.key === 'materialProjectIcon'
          || columns.some(key => area.key === `${key}Title` || area.key === `${key}Content`))
          .map(area => ({ ...area, label: area.label.replace('规格及模数', '规格') }))
      : group.areas.map(area => ({ ...area, label: area.label.replace('加工与变更', '备注与变更') })),
  }))
  groups.splice(1, 0, {
    label: '顶部信息',
    areas: legacyFontAreas.filter(area => ['materialEmbossTitle', 'materialEmbossContent', 'materialEmbossValueContent'].includes(area.key))
      .map(area => ({ ...area, label: area.key === 'materialEmbossTitle' ? '凹凸条件 · 标题' : area.label })),
  })
  return groups
}
/** 实际字号，单位px；v4增加B模数/基数列，兼容v3、v2及旧增量参数。 */
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
  if (version === 'v5' || version === 'v4' || version === 'v3') {
    const areas = version === 'v5'
      ? workSpecFontAreas
      : version === 'v4' ? v4FontAreas : legacyFontAreas
    if (parts.length !== areas.length || !validSizes(parts)) return settings
    areas.forEach((area, index) => { settings[area.key] = parts[index] })
    return settings
  }
  if (version === 'v2') {
    if (parts.length !== 8 || !validSizes(parts)) return settings
    const [title, materialTitle, materialContent, flowTitle, flowContent, notesTitle, notes, footer] = parts
    for (const area of workSpecFontAreas) {
      if (area.key.endsWith('Icon')) continue
      if (area.group === '材料表') settings[area.key] = area.key.endsWith('Title') ? materialTitle : materialContent
      if (area.group.endsWith('作业流程')) settings[area.key] = area.key.endsWith('Title') ? flowTitle : flowContent
    }
    Object.assign(settings, { title, notesTitle, notes, footer, structureTitle: flowTitle })
    return settings
  }
  if (version !== 'legacy' || parts.length !== 5 || parts.some(part => !Number.isInteger(part) || part < -3 || part > 6)) return settings
  const [title, materials, flows, notes, footer] = parts
  for (const area of workSpecFontAreas) {
    if (area.key.endsWith('Icon')) continue
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
  return 'v5,' + workSpecFontAreas.map(area => settings[area.key]).join(',')
}

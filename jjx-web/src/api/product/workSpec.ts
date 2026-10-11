import request from '@/utils/request'

export interface WorkSpecChangeSource { id: number; date: string; text: string; label: string }
export interface PrintRemarkHistory {
  id: number
  operatorName: string
  changedAt: string
  fields: { field: string; label: string; before: string; after: string }[]
}
export interface WorkSpecChange {
  date?: string | null
  text: string
  color: string
  print: boolean
  reason: string
  sourceLogIds: number[]
  sources?: WorkSpecChangeSource[]
}
export interface ColorCheckItemValue { result: string; reason: string; by?: string; at?: string }
export interface ColorCheck {
  items: Record<string, ColorCheckItemValue>
  updatedBy?: string
  updatedAt?: string
}
export interface ProductWorkSpec {
  revision: string
  sourceRevision: string
  emboss: Record<string, string>
  engineeringRequirements: string
  requirementsColor: string
  dieLocation: string
  structureFileId: number | null
  changes: WorkSpecChange[]
  printRemarks: Record<string, string>
  colorCheck: ColorCheck
  issueUnit: string
  issueDate: string | null
  approved: boolean
  confirmedBy?: string
  confirmedAt?: string
}
export const embossFields = [
  { key: 'setupHeight', label: '凹凸调机高度', unit: 'mm' },
  { key: 'requiredHeight', label: '凹凸要求高度', unit: 'mm' },
  { key: 'upperTemperature', label: '凹凸上模温度', unit: '℃' },
  { key: 'lowerTemperature', label: '凹凸下模温度', unit: '℃' },
  { key: 'pressTime', label: '凹凸下压时间', unit: 's' },
  { key: 'holdTime', label: '凹凸保持时间', unit: 's' },
] as const
export const workSpecColors = [
  { value: '#252525', label: '黑色' }, { value: '#ed00df', label: '品红' },
  { value: '#e53935', label: '红色' }, { value: '#1565c0', label: '蓝色' },
]
export function normalizeColorCheck(value?: Partial<ColorCheck> | null): ColorCheck {
  const items: Record<string, ColorCheckItemValue> = {}
  for (const [key, item] of Object.entries(value?.items || {})) {
    if (!item) continue
    items[key] = { result: item.result || '', reason: item.reason || '', by: item.by, at: item.at }
  }
  return { items, updatedBy: value?.updatedBy, updatedAt: value?.updatedAt }
}
export function normalizeWorkSpec(value: Partial<ProductWorkSpec> = {}): ProductWorkSpec {
  return {
    revision: value.revision || '', sourceRevision: value.sourceRevision || '',
    emboss: Object.fromEntries(Object.entries(value.emboss || {}).map(([key, item]) => [key, item ?? ''])),
    engineeringRequirements: value.engineeringRequirements || '',
    requirementsColor: value.requirementsColor || '#ed00df', dieLocation: value.dieLocation || '',
    structureFileId: value.structureFileId ?? null,
    printRemarks: { ...(value.printRemarks || {}) },
    colorCheck: normalizeColorCheck(value.colorCheck),
    changes: (value.changes || []).map(row => ({ date: row.date || null, text: row.text || '', color: row.color || '#ed00df',
      print: row.print !== false, reason: row.reason || '', sourceLogIds: row.sourceLogIds || [], sources: row.sources || [] })),
    issueUnit: value.issueUnit ?? '工程部', issueDate: value.issueDate || null,
    approved: value.approved === true, confirmedBy: value.confirmedBy, confirmedAt: value.confirmedAt,
  }
}
export function workSpecContent(value: ProductWorkSpec) {
  const { revision, sourceRevision, approved, confirmedBy, confirmedAt, printRemarks, colorCheck, ...content } = value
  return {
    ...content, structureFileId: value.structureFileId || null, issueDate: value.issueDate || null,
    changes: value.changes.map(({ sources, ...change }) => ({ ...change, date: change.date || null })),
    emboss: Object.fromEntries(embossFields.map(field => [field.key, (value.emboss[field.key] || '').trim()]).filter(([, item]) => item)),
  }
}
export const productWorkSpecApi = {
  get: (id: number) => request.get('/product/' + id + '/work-spec'),
  save: (id: number, value: ProductWorkSpec) => request.put('/product/' + id + '/work-spec', { ...workSpecContent(value), revision: value.revision }),
  confirm: (id: number, revision: string, sourceRevision: string) => request.post('/product/' + id + '/work-spec/confirm', { revision, sourceRevision }),
  changeSources: (id: number, before?: number) => request.get('/product/' + id + '/work-spec/change-sources', { params: { before } }),
  savePrintRemarks: (id: number, revision: string, remarks: Record<string, string>) => request.put('/product/' + id + '/work-spec/print-remarks', { revision, remarks }),
  printRemarksHistory: (id: number, before?: number) => request.get('/product/' + id + '/work-spec/print-remarks/history', { params: { before } }),
  saveColorCheck: (id: number, revision: string, items: Record<string, ColorCheckItemValue>) => request.put('/product/' + id + '/work-spec/color-check', { revision, items }),
  // 发布版本（dev-20261011-008）
  publishVersion: (id: number, changeSummary?: string) => request.post('/product/' + id + '/work-spec/publish', null, { params: { changeSummary } }),
  listVersions: (id: number) => request.get('/product/' + id + '/work-spec/versions'),
  getVersion: (versionId: number) => request.get('/product/work-spec/versions/' + versionId),
}

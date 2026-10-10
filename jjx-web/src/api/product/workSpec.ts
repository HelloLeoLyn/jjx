import request from '@/utils/request'

export interface WorkSpecChange { date?: string | null; text: string; color: string }
export interface ProductWorkSpec {
  revision: string
  sourceRevision: string
  emboss: Record<string, string>
  engineeringRequirements: string
  requirementsColor: string
  dieLocation: string
  structureFileId: number | null
  changes: WorkSpecChange[]
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
export function normalizeWorkSpec(value: Partial<ProductWorkSpec> = {}): ProductWorkSpec {
  return {
    revision: value.revision || '', sourceRevision: value.sourceRevision || '',
    emboss: Object.fromEntries(Object.entries(value.emboss || {}).map(([key, item]) => [key, item ?? ''])),
    engineeringRequirements: value.engineeringRequirements || '',
    requirementsColor: value.requirementsColor || '#ed00df', dieLocation: value.dieLocation || '',
    structureFileId: value.structureFileId ?? null,
    changes: (value.changes || []).map(row => ({ date: row.date || null, text: row.text || '', color: row.color || '#ed00df' })),
    issueUnit: value.issueUnit ?? '工程部', issueDate: value.issueDate || null,
    approved: value.approved === true, confirmedBy: value.confirmedBy, confirmedAt: value.confirmedAt,
  }
}
export function workSpecContent(value: ProductWorkSpec) {
  const { revision, sourceRevision, approved, confirmedBy, confirmedAt, ...content } = value
  return {
    ...content, structureFileId: value.structureFileId || null, issueDate: value.issueDate || null,
    changes: value.changes.map(change => ({ ...change, date: change.date || null })),
    emboss: Object.fromEntries(embossFields.map(field => [field.key, (value.emboss[field.key] || '').trim()]).filter(([, item]) => item)),
  }
}
export const productWorkSpecApi = {
  get: (id: number) => request.get('/product/' + id + '/work-spec'),
  save: (id: number, value: ProductWorkSpec) => request.put('/product/' + id + '/work-spec', { ...workSpecContent(value), revision: value.revision }),
  confirm: (id: number, revision: string, sourceRevision: string) => request.post('/product/' + id + '/work-spec/confirm', { revision, sourceRevision }),
}

import request from '@/utils/request'
import { ElMessageBox } from 'element-plus'

export interface WorkSpecChangeSource {
  id: number
  date: string
  text: string
  label: string
}
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
export interface ColorCheckItemValue {
  result: string
  reason: string
  by?: string
  at?: string
}
export interface ColorCheck {
  items: Record<string, ColorCheckItemValue>
  updatedBy?: string
  updatedAt?: string
}
export type DieLocationMap = Record<string, string>
export interface WorkSpecAttachment {
  attachmentId: number
  documentType: string
  displayName?: string | null
  remark?: string | null
  print?: boolean
  sortOrder?: number
  sha256?: string | null
}
export interface WorkSpecDraftFile extends WorkSpecAttachment {
  refId: number
  fileName: string
  fileType?: string
  fileSize?: number
}
export interface ProjectMaterial {
  materialId?: number
  materialCode: string
  materialName: string
  processId?: number
  processName: string
  specification: string
  params?: string
  quantity: number
  unit: string
  dieLocation: string
  sortOrder: number
  moduleQty?: number
  baseQty?: number
  split?: string
  widthMm?: number
  lengthMm?: number
  icon?: string
  sourceBom?: Record<string, string>
}
export interface ProjectMaterialsCheck {
  errors: string[]
  warnings: string[]
  token?: string
  bomSources?: Record<string, string>[]
}
export interface ProductWorkSpec {
  projectMaterials?: ProjectMaterial[]
  projectMaterialsAcknowledgement?: string
  projectMaterialsCheck?: ProjectMaterialsCheck
  draftManaged?: boolean
  draftAttachments?: WorkSpecAttachment[]
  draftFiles?: WorkSpecDraftFile[]
  preparedById?: number
  preparedByName?: string
  basedOnVersionId?: number
  finalizedVersionId?: number
  finalizedFromRevision?: string
  revision: string
  sourceRevision: string
  emboss: Record<string, string>
  engineeringRequirements: string
  requirementsColor: string
  /** 刀模位置：key = 材料编码|层|工序（dev-20261011-039）。 */
  dieLocation: DieLocationMap
  /** 旧版多行文本（迁移前保留，保存时不丢）。 */
  dieLocationText?: string
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
  { value: '#252525', label: '黑色' },
  { value: '#ed00df', label: '品红' },
  { value: '#e53935', label: '红色' },
  { value: '#1565c0', label: '蓝色' },
]
export function normalizeColorCheck(value?: Partial<ColorCheck> | null): ColorCheck {
  const items: Record<string, ColorCheckItemValue> = {}
  for (const [key, item] of Object.entries(value?.items || {})) {
    if (!item) continue
    items[key] = { result: item.result || '', reason: item.reason || '', by: item.by, at: item.at }
  }
  return { items, updatedBy: value?.updatedBy, updatedAt: value?.updatedAt }
}
function dieLocationMap(value: unknown): DieLocationMap {
  if (!value || typeof value !== 'object') return {}
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([key, item]) => [
      key,
      item == null ? '' : String(item),
    ])
  )
}
export function normalizeWorkSpec(value: Partial<ProductWorkSpec> = {}): ProductWorkSpec {
  return {
    projectMaterials: value.projectMaterials?.map((row) => ({
      ...row,
      sourceBom: row.sourceBom && { ...row.sourceBom },
    })),
    projectMaterialsCheck: value.projectMaterialsCheck,
    projectMaterialsAcknowledgement: value.projectMaterialsAcknowledgement,
    draftManaged: value.draftManaged,
    draftAttachments: value.draftAttachments?.map((file) => ({ ...file })),
    draftFiles: value.draftFiles?.map((file) => ({ ...file })),
    preparedById: value.preparedById,
    preparedByName: value.preparedByName,
    basedOnVersionId: value.basedOnVersionId,
    finalizedVersionId: value.finalizedVersionId,
    finalizedFromRevision: value.finalizedFromRevision,
    revision: value.revision || '',
    sourceRevision: value.sourceRevision || '',
    emboss: Object.fromEntries(
      Object.entries(value.emboss || {}).map(([key, item]) => [key, item ?? ''])
    ),
    engineeringRequirements: value.engineeringRequirements || '',
    requirementsColor: value.requirementsColor || '#ed00df',
    dieLocation: dieLocationMap(value.dieLocation),
    dieLocationText: typeof value.dieLocation === 'string' ? value.dieLocation : '',
    structureFileId: value.structureFileId ?? null,
    printRemarks: { ...(value.printRemarks || {}) },
    colorCheck: normalizeColorCheck(value.colorCheck),
    changes: (value.changes || []).map((row) => ({
      date: row.date || null,
      text: row.text || '',
      color: row.color || '#ed00df',
      print: row.print !== false,
      reason: row.reason || '',
      sourceLogIds: row.sourceLogIds || [],
      sources: row.sources || [],
    })),
    issueUnit: value.issueUnit ?? '工程部',
    issueDate: value.issueDate || null,
    approved: value.approved === true,
    confirmedBy: value.confirmedBy,
    confirmedAt: value.confirmedAt,
  }
}
export function workSpecContent(value: ProductWorkSpec) {
  const {
    revision,
    sourceRevision,
    approved,
    confirmedBy,
    confirmedAt,
    printRemarks,
    colorCheck,
    dieLocationText,
    draftManaged,
    draftAttachments,
    draftFiles,
    preparedById,
    preparedByName,
    basedOnVersionId,
    finalizedVersionId,
    finalizedFromRevision,
    projectMaterialsCheck,
    projectMaterialsAcknowledgement,
    ...content
  } = value
  return {
    ...content,
    projectMaterials: content.projectMaterials?.map(({ icon, ...row }) => row),
    structureFileId: value.structureFileId || null,
    issueDate: value.issueDate || null,
    dieLocation: Object.keys(value.dieLocation || {}).length
      ? value.dieLocation
      : dieLocationText || {},
    changes: value.changes.map(({ sources, ...change }) => ({
      ...change,
      date: change.date || null,
    })),
    emboss: Object.fromEntries(
      embossFields
        .map((field) => [field.key, (value.emboss[field.key] || '').trim()])
        .filter(([, item]) => item)
    ),
  }
}
export async function checkProjectMaterialsBeforeSave(id: number, value: ProductWorkSpec) {
  if (!value.projectMaterials) return
  const response = await request.post('/product/' + id + '/work-spec/project-materials/check', {
    projectMaterials: value.projectMaterials,
  })
  const check = response.data as ProjectMaterialsCheck
  if (check.errors.length) throw new Error(check.errors.join('；'))
  value.projectMaterialsAcknowledgement = undefined
  if (check.warnings.length) {
    await ElMessageBox.confirm(check.warnings.join('；'), '项目材料差异提醒', {
      type: 'warning',
      confirmButtonText: '确认继续',
    })
    value.projectMaterialsAcknowledgement = check.token
  }
}
export async function acknowledgeProjectMaterials(
  id: number,
  value: ProductWorkSpec,
  action: '打印' | '定版'
) {
  const check = value.projectMaterialsCheck
  if (check?.errors.length) throw new Error(check.errors.join('；'))
  if (!check?.warnings.length) return value
  await ElMessageBox.confirm(check.warnings.join('；'), '项目材料差异提醒', {
    type: 'warning',
    confirmButtonText: '确认继续',
  })
  const response = await request.post(
    '/product/' + id + '/work-spec/project-materials/acknowledge',
    {
      revision: value.revision,
      token: check.token,
      action,
    }
  )
  return normalizeWorkSpec(response.data)
}
export const productWorkSpecApi = {
  saveDraft: (
    id: number,
    value: {
      revision: string
      preparedById?: number
      spec?: ReturnType<typeof workSpecContent>
      attachments: WorkSpecAttachment[]
    }
  ) => {
    return (async () => {
      if (!value.spec) {
        const current = normalizeWorkSpec((await productWorkSpecApi.get(id)).data)
        if (current.projectMaterials) value.spec = workSpecContent(current)
      }
      if (value.spec) await checkProjectMaterialsBeforeSave(id, value.spec as ProductWorkSpec)
      return request.put('/product/' + id + '/work-spec/draft', value)
    })()
  },
  upgradeDraft: (id: number, revision: string, baseVersionId: number) =>
    request.post('/product/' + id + '/work-spec/draft/from-version', { revision, baseVersionId }),
  responsibleUsers: (id: number) => request.get('/product/' + id + '/work-spec/responsible-users'),
  history: (id: number, before?: number) =>
    request.get('/product/' + id + '/work-spec/history', { params: { before } }),
  get: (id: number) => request.get('/product/' + id + '/work-spec'),
  save: async (id: number, value: ProductWorkSpec) => {
    await checkProjectMaterialsBeforeSave(id, value)
    return request.put('/product/' + id + '/work-spec', {
      ...workSpecContent(value),
      revision: value.revision,
      projectMaterialsAcknowledgement: value.projectMaterialsAcknowledgement,
    })
  },
  confirm: (id: number, revision: string, sourceRevision: string) =>
    request.post('/product/' + id + '/work-spec/confirm', { revision, sourceRevision }),
  changeSources: (id: number, before?: number) =>
    request.get('/product/' + id + '/work-spec/change-sources', { params: { before } }),
  savePrintRemarks: (id: number, revision: string, remarks: Record<string, string>) =>
    request.put('/product/' + id + '/work-spec/print-remarks', { revision, remarks }),
  printRemarksHistory: (id: number, before?: number) =>
    request.get('/product/' + id + '/work-spec/print-remarks/history', { params: { before } }),
  saveColorCheck: (id: number, revision: string, items: Record<string, ColorCheckItemValue>) =>
    request.put('/product/' + id + '/work-spec/color-check', { revision, items }),
  // 发布版本（dev-20261011-008）
  publishVersion: async (id: number, changeSummary?: string, revision?: string) => {
    const current = normalizeWorkSpec((await productWorkSpecApi.get(id)).data)
    if (revision && revision !== current.revision) throw new Error('草稿已变化，请重新加载后定版')
    const accepted = await acknowledgeProjectMaterials(id, current, '定版')
    return request.post('/product/' + id + '/work-spec/publish', null, {
      params: { changeSummary, revision: accepted.revision },
    })
  },
  listVersions: (id: number) => request.get('/product/' + id + '/work-spec/versions'),
  getVersion: (versionId: number) => request.get('/product/work-spec/versions/' + versionId),
  versionDiff: (versionId: number) =>
    request.get('/product/work-spec/versions/' + versionId + '/diff'),
  // 列表用：批量查最新发布版本 + 有无未发布改动（dev-20261011-027）
  batchStatus: (productIds: number[]) =>
    request.get('/product/work-spec/status', { params: { productIds: productIds.join(',') } }),
  /** 显式制定规范（dev-20261020-016/017）：无负责人时原子占用，返回最新工作态。 */
  claimWorkSpec: (id: number) => request.post('/product/' + id + '/work-spec/claim'),
  /** 转交/改派制定负责人（dev-20261020-016/018）：需当前 revision 与显式确认。 */
  transferWorkSpec: (
    id: number,
    body: { revision: string; preparedById: number; ownerTransferConfirmed: true }
  ) => request.post('/product/' + id + '/work-spec/transfer', body),
  // 任意两个已发布版本比较（dev-20261011-028）
  compareVersions: (versionA: number, versionB: number) =>
    request.get('/product/work-spec/compare', { params: { versionA, versionB } }),
  // 停用 / 恢复发布版本（不硬删；dev-20261011-033）
  retireVersion: (versionId: number) =>
    request.post('/product/work-spec/versions/' + versionId + '/retire'),
  restoreVersion: (versionId: number) =>
    request.post('/product/work-spec/versions/' + versionId + '/restore'),
}

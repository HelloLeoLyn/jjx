export type WorkSpecIssueTarget = 'structure' | 'attachments' | 'customer' | 'materials' | 'routing' | 'owner' | 'conflict' | 'general'

export interface WorkSpecIssueContext {
  productId: number
  productCode?: string
  productName?: string
  bom?: Record<string, any>
  routing?: Record<string, any>
  files?: { attachmentId?: number; id?: number; fileName?: string; displayName?: string | null; version?: string | null }[]
}

export interface WorkSpecIssue {
  key: string
  object: string
  reason: string
  next: string
  target: WorkSpecIssueTarget
  count: number
}

export function workSpecIssueMessage(cause: unknown): string {
  if (typeof cause === 'string') return cause
  const error = cause as { message?: string; response?: { status?: number } } | null
  const httpCode = error?.response?.status
  if (httpCode) return `资料读取失败（HTTP ${httpCode}）：${error?.message || '请核对查看权限或联系资料负责人'}`
  return error?.message || '资料检查失败，请重新加载；仍失败时联系管理员'
}

function targetOf(reason: string): WorkSpecIssueTarget {
  if (/revision|冲突|草稿已变化|其他入口修改|读取期间发生变化/i.test(reason)) return 'conflict'
  if (/负责人|制定规范|编制负责人|编辑权限/.test(reason)) return 'owner'
  if (/结构图/.test(reason)) return 'structure'
  if (/客供/.test(reason)) return 'customer'
  if (/工艺路线|路线|工序/.test(reason)) return 'routing'
  if (/BOM|项目材料|物料|材料项目/i.test(reason)) return 'materials'
  if (/附件|文件|资料类型|工程资料|工程图|图集/.test(reason)) return 'attachments'
  return 'general'
}

const guidance: Record<WorkSpecIssueTarget, string> = {
  structure: '在作业规范的结构图区核对选定文件；由制定负责人重新选择有效结构图并保存后再定版。',
  attachments: '在工程图集核对文件名称、版本和资料类型；由制定负责人修正草稿，保存后重试。',
  customer: '在客供资料核对原文件及引用；无查看权限时请联系资料负责人。',
  materials: '在作业规范的项目材料区核对来源BOM、版本及检查结果；确认来源后再由负责人处理。',
  routing: '在作业规范的工艺流程区核对来源路线与版本；由有权限人员处理来源问题。',
  owner: '查看当前制定负责人；由负责人继续制定，或由有权限人员在原入口转交，不会自动接管。',
  conflict: '保留当前改动，使用现有“比较并重新应用”或“放弃并重读”处理冲突后再定版。',
  general: '先核对资料和权限；保存当前修改后重新加载检查，仍失败时联系管理员。',
}

/** 仅合并同一对象的相同原始原因；不根据错误文字里的ID切换产品或推定新来源。 */
export function buildWorkSpecIssues(messages: string[], context: WorkSpecIssueContext): WorkSpecIssue[] {
  const product = [context.productCode, context.productName].filter(Boolean).join(' · ') || `产品ID ${context.productId}`
  const grouped = new Map<string, WorkSpecIssue>()
  for (const message of messages) {
    const reason = message.trim().replace(/\s+/g, ' ')
    if (!reason) continue
    const target = targetOf(reason)
    const source = target === 'materials' ? context.bom : target === 'routing' ? context.routing : undefined
    const sourceName = target === 'materials' ? source?.bomName : source?.routingName
    const sourceVersion = target === 'materials' ? source?.bomVersion : source?.routingVersion
    const fileId = ['structure', 'attachments', 'customer'].includes(target)
      ? reason.match(/(?:\bID|附件ID)\s*[=:：]\s*(\d+)/i)?.[1] : undefined
    const matched = ['structure', 'attachments', 'customer'].includes(target)
      ? (context.files || []).filter((file) => fileId
        ? String(file.attachmentId || file.id) === fileId
        : !!(file.displayName || file.fileName) && reason.includes(file.displayName || file.fileName || '')) : []
    const file = matched.length === 1 ? matched[0] : undefined
    const object = [product, sourceName, sourceVersion, file?.displayName || file?.fileName, file?.version].filter(Boolean).join(' · ')
    const key = JSON.stringify([context.productId, target, object, reason])
    const existing = grouped.get(key)
    if (existing) existing.count++
    else grouped.set(key, { key, object, reason, target, next: guidance[target], count: 1 })
  }
  return [...grouped.values()]
}

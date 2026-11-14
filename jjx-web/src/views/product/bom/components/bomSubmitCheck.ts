import { BomStatusEnum } from '@/enums/product'
import type { BomCheckIssue } from '@/types/product/bom'

/**
 * BOM 提交预检里与下层制造方案有关的提示码。
 * 后端只返回中文 message（含错误码与 bomId），前端在这里解析并按方案去重展示。
 */
export type SubBomIssueCode = 'SUB_BOM_NOT_APPROVED' | 'SUB_BOM_NOT_FOUND'

export interface ParsedSubBomIssue {
  code: SubBomIssueCode
  bomId: number
}

/** 只解析两类“子方案”提示；解析不到就按其他校验错误原样保留。 */
export function parseSubBomIssue(issue: BomCheckIssue | null | undefined): ParsedSubBomIssue | null {
  const message = issue?.message
  if (!message) return null
  const codeMatch = /^(SUB_BOM_NOT_APPROVED|SUB_BOM_NOT_FOUND)\s*[：:]/.exec(message.trim())
  if (!codeMatch) return null
  const idMatch = /bomId\s*=\s*(\d+)/.exec(message)
  if (!idMatch) return null
  const bomId = Number(idMatch[1])
  if (!Number.isFinite(bomId) || bomId <= 0) return null
  return { code: codeMatch[1] as SubBomIssueCode, bomId }
}

export interface MergedSubBomIssue {
  kind: 'sub-bom'
  key: string
  code: SubBomIssueCode
  bomId: number
  /** 同一方案、同一提示码在预检里出现的次数（行检查/全图检查/主方案检查会重复）。 */
  count: number
}

export interface MergedOtherIssue {
  kind: 'other'
  key: string
  issue: BomCheckIssue
  count: number
}

export type MergedSubmitIssue = MergedSubBomIssue | MergedOtherIssue

/**
 * 相同子方案 + 相同阻断原因合并为一项；不同方案、不同原因分别保留。
 * 其他校验错误按原顺序完整保留，避免去重掩盖真实问题。
 */
export function mergeSubmitIssues(issues: BomCheckIssue[] | null | undefined): MergedSubmitIssue[] {
  const merged: MergedSubmitIssue[] = []
  const schemeIndex = new Map<string, MergedSubBomIssue>()
  ;(issues || []).forEach((issue, index) => {
    const parsed = parseSubBomIssue(issue)
    if (parsed) {
      const key = `${parsed.code}:${parsed.bomId}`
      const existing = schemeIndex.get(key)
      if (existing) {
        existing.count += 1
        return
      }
      const entry: MergedSubBomIssue = {
        kind: 'sub-bom',
        key,
        code: parsed.code,
        bomId: parsed.bomId,
        count: 1,
      }
      schemeIndex.set(key, entry)
      merged.push(entry)
      return
    }
    merged.push({ kind: 'other', key: `other:${index}`, issue, count: 1 })
  })
  return merged
}

export interface SchemeDetail {
  bomId: number
  name: string
  code: string
  version: string
  status?: number
  outputType?: string
  outputId?: number
}

export type SchemeDetailResult =
  | { ok: true; detail: SchemeDetail }
  | { ok: false; bomId: number; error: string }

export type SchemeDetailFetcher = (bomId: number) => Promise<Record<string, any> | null | undefined>

const text = (value: unknown): string => (value == null ? '' : String(value).trim())

/** 把只读详情归一化成展示字段；版本/状态缺失时如实保留为空，不伪造。 */
export function normalizeSchemeDetail(
  bomId: number,
  raw: Record<string, any> | null | undefined
): SchemeDetail {
  if (!raw) throw new Error('方案不存在或无权查看')
  const name = text(raw.bomName) || text(raw.productName) || text(raw.materialName) || text(raw.bomCode)
  const statusValue = Number(raw.approveStatus)
  const outputId = Number(raw.outputId ?? raw.productId)
  return {
    bomId,
    name: name || '未命名制造方案',
    code: text(raw.bomCode),
    version: text(raw.bomVersion) || '版本未知',
    status: Number.isFinite(statusValue) ? statusValue : undefined,
    outputType: text(raw.outputType) || undefined,
    outputId: Number.isFinite(outputId) && outputId > 0 ? outputId : undefined,
  }
}

function errorMessage(error: unknown): string {
  const message = (error as { message?: unknown } | null | undefined)?.message
  return typeof message === 'string' && message.trim() ? message.trim() : '读取失败'
}

/** 每个不同 bomId 只请求一次；单个失败只影响该项，不吞掉其他项。 */
export async function loadSchemeDetails(
  bomIds: number[],
  fetchDetail: SchemeDetailFetcher
): Promise<Map<number, SchemeDetailResult>> {
  const unique = Array.from(new Set((bomIds || []).filter((id) => Number.isFinite(id) && id > 0)))
  const results = new Map<number, SchemeDetailResult>()
  await Promise.all(
    unique.map(async (bomId) => {
      try {
        const raw = await fetchDetail(bomId)
        results.set(bomId, { ok: true, detail: normalizeSchemeDetail(bomId, raw) })
      } catch (error) {
        results.set(bomId, { ok: false, bomId, error: errorMessage(error) })
      }
    })
  )
  return results
}

export interface SchemeNavigateTarget {
  bomId: number
  outputType: string
  outputId: number
  name: string
  version: string
}

export interface SchemeBlock {
  key: string
  code: SubBomIssueCode
  bomId: number
  title: string
  message: string
  statusLabel?: string
  canNavigate: boolean
  target?: SchemeNavigateTarget
  /** 读取失败时保留原因，供“重新读取”与测试核对。 */
  error?: string
  /** 详情尚未返回时的本轮占位项：不含任何上一轮方案信息，也不允许导航。 */
  pending?: boolean
}

export interface OtherBlock {
  key: string
  location: string
  message: string
}

export interface SubmitCheckView {
  schemeItems: SchemeBlock[]
  otherItems: OtherBlock[]
}

const MANUFACTURING_SUFFIX = '制造方案'

/** “离型纸 RP-W195-L80制造方案”在提示里显示为“离型纸 RP-W195-L80”。 */
export function schemeDisplayName(name: string): string {
  const trimmed = text(name)
  return trimmed.endsWith(MANUFACTURING_SUFFIX)
    ? trimmed.slice(0, trimmed.length - MANUFACTURING_SUFFIX.length)
    : trimmed
}

function buildSchemeBlock(entry: MergedSubBomIssue, result: SchemeDetailResult | undefined): SchemeBlock {
  const base = { key: entry.key, code: entry.code, bomId: entry.bomId }

  if (entry.code === 'SUB_BOM_NOT_FOUND') {
    if (result?.ok) {
      const detail = result.detail
      const displayName = schemeDisplayName(detail.name)
      const statusLabel = detail.status == null ? '状态未知' : BomStatusEnum.getLabel(detail.status)
      const target = buildTarget(detail)
      return {
        ...base,
        title: `请先检查${displayName}${MANUFACTURING_SUFFIX}`,
        message: `提交预检未找到该制造方案，但当前仍能读取到『${displayName} · ${detail.version}』，状态为「${statusLabel}」。请打开版本维护确认该方案是否仍然有效，再回来提交当前 BOM。`,
        statusLabel,
        canNavigate: Boolean(target),
        target,
      }
    }
    return {
      ...base,
      title: '引用的制造方案已不存在',
      message:
        '当前 BOM 行引用的制造方案已不存在或无权查看，无法确认它的批准状态，因此暂时不能提交审核。请先修正该 BOM 行引用的制造方案。',
      canNavigate: false,
      error: result && !result.ok ? result.error : undefined,
    }
  }

  if (!result) {
    return {
      ...base,
      title: '无法确认制造方案状态',
      message:
        '没有取得该制造方案的只读详情，无法确认它是否已批准，因此暂时不能提交审核。请刷新后重试。',
      canNavigate: false,
      error: '未取得方案详情',
    }
  }

  if (!result.ok) {
    return {
      ...base,
      title: '制造方案信息读取失败',
      message: `无法读取该制造方案的名称、版本和状态（${result.error}），无法确认它是否已批准，因此暂时不能提交审核。请刷新后重试；仍失败请联系管理员。`,
      canNavigate: false,
      error: result.error,
    }
  }

  const detail = result.detail
  const displayName = schemeDisplayName(detail.name)
  const statusLabel = detail.status == null ? '状态未知' : BomStatusEnum.getLabel(detail.status)
  const target = buildTarget(detail)
  const label = `${displayName} · ${detail.version}`
  const message =
    detail.status === BomStatusEnum.APPROVED.value
      ? `当前 BOM 使用的『${label}』在预检时仍被判定为未批准（系统当前显示为「${statusLabel}」），请刷新后重新提交；若仍被拦截，请联系管理员核对。`
      : `当前 BOM 使用的『${label}』当前状态为「${statusLabel}」，尚未批准，因此暂时不能提交审核。请先完成该制造方案的提交审核和批准，再回来提交当前 BOM。`
  return {
    ...base,
    title: `请先审核${displayName}${MANUFACTURING_SUFFIX}`,
    message,
    statusLabel,
    canNavigate: Boolean(target),
    target,
    error: target ? undefined : '方案产出对象信息缺失，无法定位版本维护入口',
  }
}

function buildTarget(detail: SchemeDetail): SchemeNavigateTarget | undefined {
  if (!detail.outputType || !detail.outputId) return undefined
  return {
    bomId: detail.bomId,
    outputType: detail.outputType,
    outputId: detail.outputId,
    name: detail.name,
    version: detail.version,
  }
}

const FIELD_LABELS: Record<string, string> = {
  bomId: 'BOM',
  outputId: '产出对象',
  items: '明细',
  materialId: '物料关联',
  materialName: '材料名称',
  quantity: '用量',
  unit: '单位',
  lossRate: '损耗率',
  sourceType: '来源类型',
  subBomId: '下层方案',
  moduleQty: '模数',
  baseQty: '基数',
  unitFactor: '单位换算',
  processId: '项目',
  parentMaterialId: '父子关系',
  reviewStatus: '行审核状态',
  removeConfirmed: '待移除确认',
  isCurrent: '当前版本',
  schemeChoices: '产品主方案',
}

/** 其他校验错误的定位：物料编码/名称优先；后端只给明细 ID 时才用 ID。 */
export function describeIssueLocation(issue: BomCheckIssue | null | undefined): string {
  if (!issue) return ''
  const parts: string[] = []
  if (issue.materialCode) parts.push(issue.materialCode)
  if (issue.materialName) parts.push(issue.materialName)
  if (!parts.length && issue.itemId != null) parts.push(`明细 #${issue.itemId}`)
  if (issue.field) parts.push(FIELD_LABELS[issue.field] || issue.field)
  return parts.join(' · ')
}

function buildOtherBlock(entry: MergedOtherIssue): OtherBlock {
  const issue = entry.issue || {}
  const message = text(issue.message) || '预检返回了未描述的问题，请刷新后重试。'
  return { key: entry.key, location: describeIssueLocation(issue), message }
}

const isSubBomEntry = (entry: MergedSubmitIssue): entry is MergedSubBomIssue =>
  entry.kind === 'sub-bom'

/** 详情返回前的占位项：只有“正在读取”，没有名称/版本/导航目标，避免闪出上一轮方案。 */
function buildPendingSchemeBlock(entry: MergedSubBomIssue): SchemeBlock {
  return {
    key: entry.key,
    code: entry.code,
    bomId: entry.bomId,
    title: '正在读取制造方案信息',
    message: '正在读取该制造方案的名称、版本和审批状态，读取完成前不能跳转。',
    canNavigate: false,
    pending: true,
  }
}

/**
 * 新一轮预检的即时视图：方案项只给本轮占位，其他校验错误立刻完整展示。
 * 详情较慢时用户不会看到上一轮方案，也不会因为等详情而看不到其他问题。
 */
export function buildPendingCheckView(merged: MergedSubmitIssue[]): SubmitCheckView {
  const schemeItems: SchemeBlock[] = []
  const otherItems: OtherBlock[] = []
  for (const entry of merged) {
    if (isSubBomEntry(entry)) schemeItems.push(buildPendingSchemeBlock(entry))
    else otherItems.push(buildOtherBlock(entry))
  }
  return { schemeItems, otherItems }
}

export function buildSubmitCheckView(
  merged: MergedSubmitIssue[],
  details: Map<number, SchemeDetailResult>
): SubmitCheckView {
  const schemeItems: SchemeBlock[] = []
  const otherItems: OtherBlock[] = []
  for (const entry of merged) {
    if (isSubBomEntry(entry)) schemeItems.push(buildSchemeBlock(entry, details.get(entry.bomId)))
    else otherItems.push(buildOtherBlock(entry))
  }
  return { schemeItems, otherItems }
}

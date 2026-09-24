/**
 * 来料检验（IQC）行级规则 —— 页面与「检测项目」弹窗共用，避免两处逻辑漂移
 * dev-20260916-008（2026-09-16）：批量合格 / 复制上一行 / 行内联动
 *
 * 口径（用户拍板）：
 * - 全检：合格数量 + 不良数量 = 收货数量，接收数量 = 合格数量
 * - 批量合格：每项 结论=合格、实测记录留空、CR/MA/MI=0；合格数量=收货数量、
 *   不良=0、判定=合格、处置与不合格原因清空、接收数量=收货数量
 * - 复制上一行：只带 检验标准/方法/设备/结论，不带实测值与缺陷数（避免误判）
 */
import { InspectionResultEnum } from '@/enums/inventory/InboundEnum'
import { InspectionResult } from '@/enums/quality/InspectionEnum'

/** 处置方式联动接收数量：全检口径下接收数量恒等于合格数量 */
export function syncIqcDisposition(row: any) {
  if (!row) return
  row.acceptedQuantity = maxAcceptedIqcQuantity(row)
}

/** 全检口径的接收数量：等于合格数量 */
export function maxAcceptedIqcQuantity(row: any): number {
  if (!row) return 0
  return Math.max(0, Number(row.qualifiedQuantity || 0))
}

/** 由检验项缺陷数反推行级数量与判定（弹窗保存/批量后调用） */
export function syncIqcRowFromChecks(row: any) {
  if (!row) return
  const items: any[] = row.inspectionItems || []
  const sum = (key: string) => items.reduce((total, check) => total + Number(check?.[key] || 0), 0)
  const total = sum('crQuantity') + sum('maQuantity') + sum('miQuantity')

  const quantity = Number(row.isReinspection ? row.reinspectionQuantity : row.quantity || 0)
  row.rejectedQuantity = Math.min(quantity, total)
  row.qualifiedQuantity = Math.max(0, quantity - row.rejectedQuantity)

  if (sum('crQuantity') > 0 || row.rejectedQuantity > 0) {
    row.inspectionResult = InspectionResultEnum.FAIL.value
  } else if (row.qualifiedQuantity > 0) {
    row.inspectionResult = InspectionResultEnum.PASS.value
  } else {
    row.inspectionResult = ''
  }
  if (row.inspectionResult === InspectionResultEnum.PASS.value) {
    row.disposition = undefined
    row.acceptedQuantity = row.qualifiedQuantity
  } else {
    syncIqcDisposition(row)
  }
}

/**
 * 行级校验（列表提交 与 录入弹窗保存 共用；唯一出处，避免两处漂移）
 * dev-20260924-017：从"发现一个问题就 return"改为"返回全部问题"，
 * 供页面一次性提示 + 行级红标定位。
 */
export function iqcRowProblems(row: any): string[] {
  if (!row) return []
  const problems: string[] = []
  const quantity = Number(row.isReinspection ? row.reinspectionQuantity : row.quantity || 0)
  if (Number(row.qualifiedQuantity || 0) + Number(row.rejectedQuantity || 0) !== quantity) {
    problems.push('合格数量与不良数量之和必须等于收货数量')
  }
  if (row.inspectionResult === InspectionResultEnum.FAIL.value) {
    if (!row.disposition) problems.push('整批判定不合格时必须选择处置方式')
    if (!String(row.rejectReason || '').trim()) problems.push('不合格必须填写不合格原因')
  }
  // dev-20260916-009：不良品不得计入允收入库（否则隔离数量=0，不良品当良品入库）
  const accepted = Number(row.acceptedQuantity || 0)
  if (accepted > Number(row.quantity || 0)) problems.push('接收数量不能超过收货数量')
  if (
    row.inspectionResult === InspectionResultEnum.FAIL.value &&
    accepted > Number(row.qualifiedQuantity || 0)
  ) {
    problems.push('接收数量不能超过良品数量（不良品请走隔离处置）')
  }
  // dev-20260916-008：实测记录允许留空，仅要求逐项给出合格/不合格结论
  const undecided = (row.inspectionItems || []).find(
    (check: any) =>
      ![InspectionResult.PASS, InspectionResult.FAIL].includes(check?.result)
  )
  if (undecided) {
    problems.push(`请判定检测项目「${undecided.checkItem}」合格或不合格（实测记录可留空）`)
  }
  return problems
}

/** 批量合格：整批判合格、实测记录留空、缺陷数归零、合格/接收=收货数 */
export function batchPassIqcRow(row: any) {
  if (!row) return
  const quantity = Number(row.isReinspection ? row.reinspectionQuantity : row.quantity || 0)
  ;(row.inspectionItems || []).forEach((check: any) => {
    check.actualValue = ''
    check.crQuantity = 0
    check.maQuantity = 0
    check.miQuantity = 0
    check.result = InspectionResult.PASS
  })
  row.qualifiedQuantity = quantity
  row.rejectedQuantity = 0
  row.inspectionResult = InspectionResultEnum.PASS.value
  row.disposition = undefined
  row.rejectReason = ''
  row.acceptedQuantity = quantity
}

/** 复制上一行的检验项：只带 标准/方法/设备/结论 + 行级联动重算 */
export function copyIqcChecks(from: any, to: any) {
  const source: any[] = from?.inspectionItems || []
  const target: any[] = to?.inspectionItems || []
  if (!source.length || !target.length) return
  target.forEach((check: any, index: number) => {
    const origin =
      source[index] || source.find((candidate: any) => candidate.checkItem === check.checkItem)
    if (!origin) return
    check.standard = origin.standard
    check.inspectionMethod = origin.inspectionMethod
    check.equipment = origin.equipment
    if (origin.result === InspectionResult.PASS || origin.result === InspectionResult.FAIL) {
      check.result = origin.result
    }
  })
  syncIqcRowFromChecks(to)
}

/** 行内是否已录：实测值 / 缺陷数 / 备注 任一有值 */
export function iqcCheckProgress(row: any): number {
  return (row?.inspectionItems || []).filter(
    (check: any) =>
      String(check.actualValue || '').trim() ||
      Number(check.crQuantity || 0) > 0 ||
      Number(check.maQuantity || 0) > 0 ||
      Number(check.miQuantity || 0) > 0 ||
      String(check.remark || '').trim()
  ).length
}

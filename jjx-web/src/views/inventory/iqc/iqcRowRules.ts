/**
 * 来料检验（IQC）行级规则 —— 页面与「检测项目」弹窗共用，避免两处逻辑漂移
 * dev-20260916-008（2026-09-16）：批量合格 / 复制上一行 / 行内联动
 *
 * 口径（用户拍板）：
 * - 批量合格：每项 结论=合格、实测记录留空、CR/MA/MI=0；抽检数量=收货数量、合格数量=收货数量、
 *   不良=0、判定=合格、处置与不合格原因清空、接收数量=收货数量
 * - 复制上一行：只带 检验标准/方法/设备/结论，不带实测值与缺陷数（避免误判）
 */
import { InspectionResultEnum, IqcDispositionEnum } from '@/enums/inventory/InboundEnum'
import { InspectionResult } from '@/enums/quality/InspectionEnum'

/** 处置方式联动接收数量（让步接收/部分接收 → 整批接收） */
export function syncIqcDisposition(row: any) {
  if (!row) return
  if (
    row.disposition === IqcDispositionEnum.CONCESSION.value ||
    row.disposition === IqcDispositionEnum.PARTIAL_ACCEPT.value
  ) {
    row.acceptedQuantity = Number(row.quantity || 0)
  } else {
    row.acceptedQuantity = 0
  }
}

/** 由检验项缺陷数反推行级数量与判定（弹窗保存/批量后调用） */
export function syncIqcRowFromChecks(row: any) {
  if (!row) return
  const items: any[] = row.inspectionItems || []
  const sum = (key: string) => items.reduce((total, check) => total + Number(check?.[key] || 0), 0)
  const total = sum('crQuantity') + sum('maQuantity') + sum('miQuantity')

  let sampled = Number(row.sampledQuantity || 0)
  if (sampled === 0 && total > 0) sampled = total
  row.sampledQuantity = sampled
  row.rejectedQuantity = Math.min(sampled, total)
  row.qualifiedQuantity = Math.max(0, sampled - row.rejectedQuantity)

  if (sum('crQuantity') > 0 || row.rejectedQuantity > 0) {
    row.inspectionResult = InspectionResultEnum.FAIL.value
  } else if (row.qualifiedQuantity > 0) {
    row.inspectionResult = InspectionResultEnum.PASS.value
  } else {
    row.inspectionResult = ''
  }
  if (row.inspectionResult === InspectionResultEnum.PASS.value) {
    row.disposition = undefined
    row.acceptedQuantity = Number(row.quantity || 0)
  } else {
    syncIqcDisposition(row)
  }
}

/** 批量合格：整批判合格、实测记录留空、缺陷数归零、抽检=收货数、接收=收货数 */
export function batchPassIqcRow(row: any) {
  if (!row) return
  const quantity = Number(row.quantity || 0)
  ;(row.inspectionItems || []).forEach((check: any) => {
    check.actualValue = ''
    check.crQuantity = 0
    check.maQuantity = 0
    check.miQuantity = 0
    check.result = InspectionResult.PASS
  })
  row.sampledQuantity = quantity
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

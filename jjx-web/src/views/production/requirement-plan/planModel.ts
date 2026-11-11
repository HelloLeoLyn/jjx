import type { RequirementLine, RequirementSummary } from '@/types/production/requirementPlan'

export interface PlanTotals {
  lineCount: number
  gapLineCount: number
  gapQty: number
  productionSupply: number
  purchaseSupply: number
  plannedPurchaseSupply: number
}

export const num = (value?: number | null): number =>
  typeof value === 'number' && Number.isFinite(value) ? value : 0

/** 展示用：去掉无意义的尾随 0，空值显示 —（只做展示，不参与计算）。 */
export function formatQty(value?: number | null): string {
  if (value === null || value === undefined) return '—'
  if (!Number.isFinite(value)) return '—'
  return String(Number(value.toFixed(4)))
}

/** 缺口行：缺口 > 0 才算缺料；0 或负值不作为采购/领料依据。 */
export function hasGap(line: RequirementLine): boolean {
  return num(line.gapQtyApprox) > 0
}

/** 汇总只按行累加，不在前端重算净需求（净需求口径唯一真源在服务端）。 */
export function summarizePlan(
  lines: RequirementLine[] = [],
  plannedPurchaseSupply?: number | null
): PlanTotals {
  const totals: PlanTotals = {
    lineCount: lines.length,
    gapLineCount: 0,
    gapQty: 0,
    productionSupply: 0,
    purchaseSupply: 0,
    plannedPurchaseSupply: num(plannedPurchaseSupply),
  }
  for (const line of lines) {
    totals.productionSupply += num(line.productionSupply)
    totals.purchaseSupply += num(line.purchaseSupply)
    if (hasGap(line)) {
      totals.gapLineCount += 1
      totals.gapQty += num(line.gapQtyApprox)
    }
  }
  return totals
}

/** 只读提示文案：任何入口都不能把试算说成已确认/已占用。 */
export function readOnlyNotice(summary?: RequirementSummary | null): string {
  if (!summary) return '试算结果只读：不写库存、不预留、不生成工单或采购单。'
  if (summary.confirmSupported) return summary.note || '试算结果只读。'
  return summary.note || '确认与持久分配暂未开放：本页仅展示缺口与可得供给，不占用库存。'
}

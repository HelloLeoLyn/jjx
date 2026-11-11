import { describe, expect, it } from 'vitest'
import { formatQty, hasGap, readOnlyNotice, summarizePlan } from '../planModel'
import type { RequirementLine } from '@/types/production/requirementPlan'

const line = (patch: Partial<RequirementLine>): RequirementLine => ({
  materialId: 1,
  materialCode: 'M1',
  materialName: '材料1',
  ...patch,
})

describe('需求计划展示口径（dev-20261111-007）', () => {
  it('只统计缺口行，0/负值不算缺料', () => {
    const totals = summarizePlan([
      line({ gapQtyApprox: 100, productionSupply: 30, purchaseSupply: 20 }),
      line({ materialId: 2, gapQtyApprox: 0 }),
      line({ materialId: 3, gapQtyApprox: -5 }),
    ], 60)
    expect(totals.lineCount).toBe(3)
    expect(totals.gapLineCount).toBe(1)
    expect(totals.gapQty).toBe(100)
    expect(totals.productionSupply).toBe(30)
    expect(totals.purchaseSupply).toBe(20)
    expect(totals.plannedPurchaseSupply).toBe(60)
  })

  it('空值与 NaN 按 0 处理，不让 NaN 污染合计', () => {
    const totals = summarizePlan([line({ gapQtyApprox: null, productionSupply: Number.NaN })])
    expect(totals.gapQty).toBe(0)
    expect(totals.productionSupply).toBe(0)
    expect(hasGap(line({ gapQtyApprox: null }))).toBe(false)
  })

  it('展示格式去掉尾随 0，空值显示破折号', () => {
    expect(formatQty(100)).toBe('100')
    expect(formatQty(2.5)).toBe('2.5')
    expect(formatQty(0.33333)).toBe('0.3333')
    expect(formatQty(null)).toBe('—')
  })

  it('服务端未支持 confirm 时提示只读，不能暗示已占用', () => {
    const notice = readOnlyNotice({ confirmSupported: false, note: '' } as any)
    expect(notice).toContain('不占用库存')
    expect(readOnlyNotice(null)).toContain('只读')
  })
})

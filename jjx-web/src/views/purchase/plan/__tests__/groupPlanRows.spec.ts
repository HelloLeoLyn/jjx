import { describe, expect, it } from 'vitest'
import { groupPlanRows, groupQtyLabel } from '../groupPlanRows'
import type { PlanRow } from '@/api/purchase/planEntry'

const row = (patch: Partial<PlanRow>): PlanRow => ({
  key: String(Math.random()),
  materialId: 1,
  materialCode: 'M1',
  materialName: '物料',
  unit: 'PCS',
  purchaseUnit: 'PCS',
  priceUnit: 'PCS',
  purchaseQuantity: 1,
  quantity: 1,
  unitPrice: 0,
  taxRate: 0,
  amount: 0,
  reason: '测试',
  priority: 'normal',
  sourceAlertIds: [],
  ...patch,
} as PlanRow)

describe('采购计划按供应商分组（dev-20261113-010）', () => {
  it('2 个供应商 3 行 → 2 组，交期取最早、urgent 组内任一', () => {
    const groups = groupPlanRows([
      row({ supplierId: 1, supplierName: '甲', needDate: '2026-11-20' }),
      row({ supplierId: 1, supplierName: '甲', needDate: '2026-11-18', priority: 'urgent' }),
      row({ supplierId: 2, supplierName: '乙', needDate: '2026-11-25' }),
    ])
    expect(groups).toHaveLength(2)
    expect(groups[0].rows).toHaveLength(2)
    expect(groups[0].earliestNeedDate).toBe('2026-11-18')
    expect(groups[0].urgent).toBe(true)
    expect(groups[1].rows).toHaveLength(1)
  })

  it('无供应商单独成组并标记', () => {
    const groups = groupPlanRows([
      row({ supplierId: undefined, supplierName: '' }),
      row({ supplierId: 3, supplierName: '丙' }),
    ])
    expect(groups).toHaveLength(2)
    const missing = groups.find((g) => g.missingSupplier)
    expect(missing?.supplierId).toBeNull()
    expect(missing?.supplierName).toContain('未配置')
  })

  it('合计按单位分开，不跨单位相加', () => {
    const group = groupPlanRows([
      row({ supplierId: 1, quantity: 2, unit: 'PCS' }),
      row({ supplierId: 1, quantity: 1.5, unit: 'KG' }),
    ])[0]
    expect(groupQtyLabel(group)).toContain('2 PCS')
    expect(groupQtyLabel(group)).toContain('1.5 KG')
  })
})

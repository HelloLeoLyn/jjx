import { describe, expect, it } from 'vitest'
import { cardMaterialCount, countPlanMaterials } from '../planCounts'

describe('成品方案带出统计：材料条数由前端计算（dev-20261102-006）', () => {
  it('卡片材料 + 未挂材料 = 总条数（YL001MWOL：2 + 11 = 13）', () => {
    const rows = [
      { materials: JSON.stringify([{ name: 'F200' }]) },
      { materials: JSON.stringify([{ name: 'PET-125' }]) },
    ]
    const unlinked = Array.from({ length: 11 }, (_, i) => ({ name: `未挂${i + 1}` }))
    expect(countPlanMaterials(rows, unlinked)).toBe(13)
  })

  it('空卡片/坏 JSON/空清单都不抛错，按 0 计', () => {
    expect(cardMaterialCount({ materials: null })).toBe(0)
    expect(cardMaterialCount({ materials: '' })).toBe(0)
    expect(cardMaterialCount({ materials: '{坏数据' })).toBe(0)
    expect(cardMaterialCount({ materials: '{"a":1}' })).toBe(0)
    expect(countPlanMaterials([], [])).toBe(0)
    expect(countPlanMaterials(undefined, undefined)).toBe(0)
    expect(countPlanMaterials([{ materials: undefined }, { materials: '[]' }], [{ name: 'x' }])).toBe(1)
  })
})

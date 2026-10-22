import { describe, expect, it } from 'vitest'
import { bomGroupRows } from '../bomGroups'
import { bomPrintRows, paginateBomRows } from '@/components/print/bomPrintLayout'
import { groupBomMaterials, projectMaterialPrintRows, type DocsetData } from '@/views/engineering/product-spec/components/docset'

describe('BOM各入口共用组合归属', () => {
  it('草稿仅连续同项目成组，空项目独立且不修改原数据', () => {
    const items = [{ processId: 7 }, { processId: 7 }, {}, {}, { processId: 7 }, { processId: 8 }]
    const before = JSON.stringify(items)
    const rows = bomGroupRows(items)
    expect(rows.map(row => row.number)).toEqual([1, 1, 2, 3, 4, 5])
    expect(rows.map(row => row.span)).toEqual([2, 0, 1, 1, 1, 1])
    expect(rows[0].label).toContain('组合1')
    expect(rows[2].label).toContain('独立材料')
    expect(JSON.stringify(items)).toBe(before)
  })
  it('已保存显式归属优先，同项目的相邻独立行不会被吸入组合', () => {
    const rows = bomGroupRows([
      { itemId: 10, processId: 7 }, { itemId: 11, processId: 7, parentMaterialId: 10 },
      { itemId: 12, processId: 7 }, { itemId: 13, processId: 7 },
    ], true)
    expect(rows.map(row => row.number)).toEqual([1, 1, 2, 3])
    expect(rows.every(row => !row.legacy)).toBe(true)
  })
  it('无归属历史数据明确标注推定；ID优先，不按同名合并不同ID', () => {
    const rows = bomGroupRows([{ processId: 1, processName: '模切' }, { processId: 2, processName: '模切' },
      { processName: ' 模切 ' }, { processName: '模切' }, {}, {}], true)
    expect(rows.map(row => row.number)).toEqual([1, 2, 3, 3, 4, 5])
    expect(rows.every(row => row.label.includes('历史推定'))).toBe(true)
  })
  it('缺首行和循环引用标记异常，不按项目猜归属', () => {
    const rows = bomGroupRows([{ itemId: 1, parentMaterialId: 99 },
      { itemId: 2, parentMaterialId: 3 }, { itemId: 3, parentMaterialId: 2 }], true)
    expect(rows.every(row => row.invalid && row.size === 1 && row.label.includes('归属异常'))).toBe(true)
  })
  it('详情公共模型、五列打印、BOM回退材料区保持同一行序和归属，跨页只续号不重复', () => {
    const items = [{ itemId: 1, processName: '模切' }, { itemId: 2, processName: '模切', parentMaterialId: 1 },
      { itemId: 3 }, { itemId: 4 }]
    const groups = bomGroupRows(items, true)
    const printed = bomPrintRows(items)
    expect(printed.map(row => row.number)).toEqual(groups.map(row => row.number))
    expect(groupBomMaterials(items).map(row => row.projectGroup)).toEqual(groups.map(row => row.number))
    const pages = paginateBomRows(printed, [30, 30, 30, 30], 40)
    expect(pages.flat().map(row => row.key)).toEqual([0, 1, 2, 3])
    expect(pages.flat().map(row => row.number)).toEqual([1, 1, 2, 3])
    expect(pages.flat().map(row => row.span)).toEqual([1, 1, 1, 1])
  })
  it('独立作业规范包括显式空材料数组不被BOM回退覆盖；发布内容不随新BOM规则改组', () => {
    const data = { bom: { items: [{ itemId: 1 }, { itemId: 2 }] }, workSpec: { projectMaterials: [] } } as unknown as DocsetData
    expect(projectMaterialPrintRows(data)).toEqual([])
    data.workSpec.projectMaterials = [{ groupNo: 9, groupKey: 'own', projectName: '独立项目', materials: [{ materialName: '独立料' }] }] as any
    expect(projectMaterialPrintRows(data)[0].materialName).toBe('独立料')
    expect(projectMaterialPrintRows(data)[0].projectGroup).toBe(9)
    delete data.workSpec.projectMaterials
    expect(projectMaterialPrintRows(data).map(row => row.projectGroup)).toEqual([1, 2])
    data.published = true
    expect(projectMaterialPrintRows(data).every(row => row.bomGroupLabel === undefined)).toBe(true)
  })
})

import { describe, expect, it } from 'vitest'
import { bomMaterialSpec, bomPrintRows, paginateBomRows } from '../bomPrintLayout'

describe('BOM 五列打印数据', () => {
  it('规格沿用宽长、跳距、模数口径，并安全处理缺失字段', () => {
    expect(bomMaterialSpec({ widthMm: 120, lengthMm: 80, split: '50', moduleQty: 12 })).toBe(
      '120*80mm跳50=12PCS'
    )
    expect(bomMaterialSpec({ specification: '120*80mm', split: '50mm', moduleQty: 12 })).toBe(
      '120*80mm跳50mm=12PCS'
    )
    expect(bomMaterialSpec({ specification: 'PET 0.1mm' })).toBe('PET 0.1mm')
    expect(bomMaterialSpec({ specification: 'PET', moduleQty: 0 })).toBe('PET')
    expect(bomMaterialSpec({ specification: 'PET', moduleQty: NaN })).toBe('PET')
    expect(bomMaterialSpec({ moduleQty: 12 })).toBe('-')
    expect(bomMaterialSpec({})).toBe('-')
    // 计件回退（dev-20261022-020）：基数1、模数为空或 0<模数<1 → 1个/PCS
    expect(bomMaterialSpec({ baseQty: 1, unit: 'PCS' })).toBe('1个/PCS')
    expect(bomMaterialSpec({ baseQty: 1, unit: '个', moduleQty: 0.5 })).toBe('1个/PCS')
    // 计件且模数=1：与作业规范/定版统一为 1个/PCS（dev-20261026-009，原为 '-'）
    expect(bomMaterialSpec({ baseQty: 1, unit: 'PCS', moduleQty: 1 })).toBe('1个/PCS')
    expect(bomMaterialSpec({ baseQty: 2, unit: 'PCS' })).toBe('-')
    // 规格纯零值按空处理（dev-20261023-010）
    expect(bomMaterialSpec({ specification: '0', moduleQty: 1 })).toBe('-')
    expect(bomMaterialSpec({ specification: '0.0', moduleQty: 2 })).toBe('-')
  })
  it('连续项目只编一个序号，分隔后再次出现则为新组', () => {
    const rows = bomPrintRows([
      { processId: 1 },
      { processId: 1 },
      { processId: 1 },
      { processId: 2 },
      { processId: 1 },
    ])
    const [page] = paginateBomRows(rows, [22, 22, 22, 22, 22], 200)
    expect(page.map((row) => row.number)).toEqual([1, 1, 1, 2, 3])
    expect(page.map((row) => row.span)).toEqual([3, 0, 0, 1, 1])
  })
  it('按实际行高分页，跨页重建合并单元格并保持序号', () => {
    const rows = bomPrintRows(Array.from({ length: 5 }, () => ({ processId: 1 })))
    const pages = paginateBomRows(rows, [22, 60, 22, 22, 22], 90)
    expect(pages.map((page) => page.map((row) => row.key))).toEqual([
      [0, 1],
      [2, 3, 4],
    ])
    expect(pages.map((page) => page.map((row) => row.span))).toEqual([
      [2, 0],
      [3, 0, 0],
    ])
    expect(pages.flat().every((row) => row.number === 1)).toBe(true)
  })
  it('同名不同工序不合并，无ID按名称分组，子材料不遗漏', () => {
    const rows = bomPrintRows([
      { processId: 1, processName: '冲切', children: [{ processId: 2, processName: '冲切' }] },
      { processName: '导光', processCategory: 'PANEL' },
      { processName: '导光', processCategory: 'PANEL' },
      { processName: '导光', processCategory: 'UP_LINE' },
    ])
    expect(rows.map((row) => row.number)).toEqual([1, 2, 3, 3, 3])
  })
  it('空表和超高单行仍保留内容，不生成空白前页', () => {
    expect(paginateBomRows([], [], 90)).toEqual([[]])
    const rows = bomPrintRows([{ specification: '超长文本' }, {}])
    expect(paginateBomRows(rows, [200, 22], 90).map((page) => page.map((row) => row.key))).toEqual([
      [0],
      [1],
    ])
  })
})

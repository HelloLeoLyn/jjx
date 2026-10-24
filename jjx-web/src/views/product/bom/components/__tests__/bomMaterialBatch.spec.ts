import { describe, expect, it } from 'vitest'
import {
  batchStatusLabel,
  buildBatchLinks,
  buildBatchRows,
  selectCandidate,
  selectedCandidate,
  validateBatchSelection,
  type BatchRow,
} from '../bomMaterialBatch'

const items: any[] = [
  { itemId: 1, materialName: 'PET', specification: '0.1' },
  { itemId: 2, materialName: 'PC', specification: '' },
  { itemId: 3, materialName: 'PE', specification: '1' },
]
const results: any[] = [
  {
    index: 0,
    status: 'MATCHED',
    matchCount: 1,
    candidates: [{ materialId: 11, materialName: 'PET', materialCode: 'M11', specification: '0.1', unit: 'KG' }],
  },
  {
    index: 1,
    status: 'AMBIGUOUS',
    matchCount: 2,
    candidates: [
      { materialId: 21, materialName: 'PC', materialCode: 'M21' },
      { materialId: 22, materialName: 'PC', materialCode: 'M22' },
    ],
  },
  { index: 2, status: 'NOT_FOUND', matchCount: 0, candidates: [] },
]

describe('bomMaterialBatch（dev-20261024-020）', () => {
  it('按 index 对齐行，保留原文与候选', () => {
    const rows = buildBatchRows(items, results)
    expect(rows).toHaveLength(3)
    expect(rows[0]).toMatchObject({ itemId: 1, originalName: 'PET', status: 'MATCHED' })
    expect(rows[1].candidates).toHaveLength(2)
    expect(rows[2].status).toBe('NOT_FOUND')
  })

  it('唯一候选也不自动选中：必须人工确认', () => {
    const rows = buildBatchRows(items, results)
    expect(rows[0].selectedMaterialId).toBeUndefined()
    expect(buildBatchLinks(rows)).toEqual([])
  })

  it('人工选择后才进入提交载荷', () => {
    const rows = buildBatchRows(items, results)
    selectCandidate(rows[0], 11)
    selectCandidate(rows[1], 22)
    expect(selectedCandidate(rows[1])?.materialCode).toBe('M22')
    expect(buildBatchLinks(rows)).toEqual([
      { itemId: 1, materialId: 11 },
      { itemId: 2, materialId: 22 },
    ])
  })

  it('一行都没选时校验报错', () => {
    const rows = buildBatchRows(items, results)
    expect(validateBatchSelection(rows)).toContain('至少')
  })

  it('选中后校验通过', () => {
    const rows = buildBatchRows(items, results)
    selectCandidate(rows[0], 11)
    expect(validateBatchSelection(rows)).toBeNull()
  })

  it('状态文案：唯一/多候选/未匹配', () => {
    expect(batchStatusLabel('MATCHED')).toBe('唯一候选')
    expect(batchStatusLabel('AMBIGUOUS')).toBe('多候选')
    expect(batchStatusLabel('NOT_FOUND')).toBe('未匹配')
  })

  it('取消（未确认）不产生载荷', () => {
    const rows: BatchRow[] = buildBatchRows(items, results)
    expect(buildBatchLinks(rows)).toEqual([])
  })
})

import { describe, expect, it } from 'vitest'
import { autoSpecOf } from '../bomSpecMode'

describe('BOM档案规格对照', () => {
  it('保留数据原值，读取不改写行规格', () => {
    const row = { specification: '旧规格', materialSpecification: '新规格' }
    expect(autoSpecOf(row)).toBe('新规格')
    expect(row.specification).toBe('旧规格')
  })
  it('source 内空规格也不回退到旧值', () => {
    expect(autoSpecOf({ materialSpecification: '旧值', materialSpecificationSource: { specification: '', attributes: { coating: '单面加硬' } } })).toBe('')
    expect(autoSpecOf(null)).toBe('')
  })
})

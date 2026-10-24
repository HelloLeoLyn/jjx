import { describe, expect, it } from 'vitest'
import { autoSpecOf, currentSpecOf, isManualSpec, specFollowValue } from '../bomSpecMode'

describe('bomSpecMode（dev-20261024-023）', () => {
  it('autoSpecOf / currentSpecOf 会 trim，空值返回空串', () => {
    expect(autoSpecOf({ materialSpecification: '  0.175mm  ' })).toBe('0.175mm')
    expect(autoSpecOf({})).toBe('')
    expect(currentSpecOf({ specification: '  A  ' })).toBe('A')
  })

  it('未建档行（materialId=0/空）不判定为手改', () => {
    expect(isManualSpec({ materialId: 0, specification: 'X', materialSpecification: 'Y' })).toBe(false)
    expect(isManualSpec({ materialId: null, specification: 'X', materialSpecification: 'Y' })).toBe(false)
  })

  it('与系统值一致 = 系统模式，不标已改写', () => {
    expect(isManualSpec({ materialId: 1, specification: '0.175mm', materialSpecification: '0.175mm' })).toBe(false)
  })

  it('与系统值不同 = 人工改写', () => {
    expect(isManualSpec({ materialId: 1, specification: '手填值', materialSpecification: '0.175mm' })).toBe(true)
  })

  it('物料无系统规格时：不判定手改，也不用空串覆盖', () => {
    const row = { materialId: 1, specification: '手填值', materialSpecification: '' }
    expect(isManualSpec(row)).toBe(false)
    expect(specFollowValue(row)).toBeNull()
  })

  it('规格为空：自动跟随系统值', () => {
    expect(specFollowValue({ materialId: 1, specification: '', materialSpecification: '0.175mm' })).toBe('0.175mm')
  })

  it('已有手改值：不跟随（不覆盖）', () => {
    expect(specFollowValue({ materialId: 1, specification: '手填值', materialSpecification: '0.175mm' })).toBeNull()
  })

  it('与系统值一致：无需改动', () => {
    expect(specFollowValue({ materialId: 1, specification: '0.175mm', materialSpecification: '0.175mm' })).toBeNull()
  })
})

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

  it('保存日志证明旧值曾为系统值：属性变化后自动跟随', () => {
    const row = { materialId: 1, specification: '旧系统值', materialSpecification: '新系统值', specificationBaseline: '旧系统值' }
    expect(isManualSpec(row)).toBe(false)
    expect(specFollowValue(row)).toBe('新系统值')
  })

  it('人工清空保留；手填后再次等于系统值即恢复自动', () => {
    expect(specFollowValue({ materialId: 1, specification: '', materialSpecification: '系统值', specificationManual: true })).toBeNull()
    expect(isManualSpec({ materialId: 1, specification: '系统值', materialSpecification: '系统值', specificationManual: true })).toBe(false)
  })
})

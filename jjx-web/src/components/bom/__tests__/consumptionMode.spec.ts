import { describe, expect, it } from 'vitest'
import type { EngineeringBomItem } from '@/types/product/bom'
import { hasPitchValues, hasRatioValues, consumptionInputError, unitMismatch } from '../consumptionMode'
const row = (patch: Partial<EngineeringBomItem> = {}) => ({ materialId: 1, unit: 'M2', pcsPerPitch: 1, ...patch } as EngineeringBomItem)
describe('BOM原始参数互斥', () => {
  it('默认每跳1片不占用跳距组；两组空允许保存，split不作来源', () => {
    const input = row({ split: '80mm' })
    expect(hasPitchValues(input)).toBe(false)
    expect(hasRatioValues(input)).toBe(false)
    expect(consumptionInputError(input)).toBe('')
  })
  it('跳距组与模数组即时互斥，最后一个值清空后解锁', () => {
    const input = row({ pitchMm: 80 })
    expect(hasPitchValues(input)).toBe(true)
    input.pitchMm = null
    expect(hasPitchValues(input)).toBe(false)
    input.baseQty = 1
    expect(hasRatioValues(input)).toBe(true)
    input.baseQty = undefined
    expect(hasRatioValues(input)).toBe(false)
    input.pcsPerPitch = 2
    expect(hasPitchValues(input)).toBe(true)
  })
  it('同时填两组保存被拦，不抹除原始值', () => {
    const input = row({ pitchMm: 80, moduleQty: 6, baseQty: 1 })
    expect(consumptionInputError(input)).toContain('二选一')
    expect(input).toMatchObject({ pitchMm: 80, moduleQty: 6, baseQty: 1 })
  })
  it('仅模数/基数历史行不受旧来源方式或split影响', () => {
    const input = row({ moduleQty: 6, baseQty: 1, split: '80mm', consumptionMode: 'roll_cut' })
    expect(hasPitchValues(input)).toBe(false)
    expect(consumptionInputError(input)).toBe('')
  })
  it('单位异名可等价，不一致需人工确认；数值精度与范围受检', () => {
    expect(unitMismatch(row({ unit: '㎡', archiveUnit: 'M2' }))).toBe(false)
    expect(consumptionInputError(row({ unit: 'PCS', archiveUnit: 'M2' }))).toContain('人工确认')
    expect(consumptionInputError(row({ pitchMm: 0 }))).toContain('必须大于0')
    expect(consumptionInputError(row({ pitchMm: 80, pcsPerPitch: 0 }))).toContain('正整数')
    expect(consumptionInputError(row({ moduleQty: 6 }))).toContain('同时填写')
    expect(consumptionInputError(row({ baseQty: 1, moduleQty: 6.12345 }))).toContain('4位小数')
  })
})

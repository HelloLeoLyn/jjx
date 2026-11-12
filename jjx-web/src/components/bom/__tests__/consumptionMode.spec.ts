import { describe, expect, it } from 'vitest'
import {
  inferConsumptionMode,
  showModuleFields,
  showPitchFields,
  showUnitConsumptionField,
} from '../consumptionMode'

describe('材料来源方式（dev-20261112-008）', () => {
  it('已有跳距优先判为卷材裁切', () => {
    expect(inferConsumptionMode({ unit: 'PCS', widthMm: 450, lengthMm: 225 }, '80mm')).toBe('roll_cut')
  })

  it('M/M2 且有卷宽判为卷材裁切', () => {
    expect(inferConsumptionMode({ unit: 'M2', widthMm: 195, lengthMm: null })).toBe('roll_cut')
    expect(inferConsumptionMode({ unit: 'm', widthMm: 195 })).toBe('roll_cut')
  })

  it('宽长齐全判为片材冲型', () => {
    expect(inferConsumptionMode({ unit: 'PCS', widthMm: 450, lengthMm: 225 })).toBe('sheet_nest')
  })

  it('无尺寸判为直接领用', () => {
    expect(inferConsumptionMode({ unit: 'PCS' })).toBe('direct')
    expect(inferConsumptionMode(null)).toBe('direct')
  })

  it('显隐口径：卷材只看跳距、片材只看模数基数、连续只看单耗', () => {
    expect(showPitchFields('roll_cut')).toBe(true)
    expect(showModuleFields('roll_cut')).toBe(false)
    expect(showModuleFields('sheet_nest')).toBe(true)
    expect(showPitchFields('sheet_nest')).toBe(false)
    expect(showUnitConsumptionField('continuous')).toBe(true)
    expect(showPitchFields('direct')).toBe(false)
    expect(showModuleFields('direct')).toBe(false)
    expect(showModuleFields(undefined)).toBe(true)
  })
})

import { describe, expect, it } from 'vitest'
import { composeMaterialSize, mergeLegacyThickness, normalizeMaterialSize, parseMaterialSize, parseMaterialThickness, renderMaterialDimensions, validateMaterialDimensions, emptyMaterialSize } from '../materialSize'

describe('统一尺寸值', () => {
  it('卷材按厚μm/宽mm/卷长m显示，与Java缺项和四舍五入口径一致', () => {
    const value = { unit: 'ROLL', materialForm: 'FILM', widthMm: 315, lengthMm: 1000000, thicknessMm: 0.254, sizeUnit: 'mm' }
    expect(renderMaterialDimensions(value)).toBe('254μm*315mm*1000m')
    expect(renderMaterialDimensions({ ...value, widthMm: null, lengthMm: null })).toBe('254μm')
    expect(renderMaterialDimensions({ ...value, thicknessMm: null, lengthMm: null })).toBe('315mm')
    expect(renderMaterialDimensions({ ...value, lengthMm: null }, true)).toBe('254μm*315mm')
    expect(renderMaterialDimensions({ ...value, widthMm: null })).toBe('254μm*1000m')
    expect(renderMaterialDimensions({ ...value, widthMm: null, lengthMm: null, thicknessMm: null })).toBe('')
    expect(renderMaterialDimensions({ ...value, thicknessMm: 0.2541, lengthMm: 1005 })).toBe('254.1μm*315mm*1.01m')
    for (const sizeUnit of ['mm', 'cm', 'm', '', null]) {
      expect(renderMaterialDimensions({ ...value, sizeUnit })).toBe('254μm*315mm*1000m')
      for (const unit of ['M', 'PCS', 'PIN', '', null]) {
        expect(renderMaterialDimensions({ ...value, unit, sizeUnit })).toBe(`315*1000000*0.254${sizeUnit || 'mm'}`)
        expect(renderMaterialDimensions({ ...value, unit, sizeUnit, lengthMm: null })).toBe(`315**0.254${sizeUnit || 'mm'}`)
      }
    }
  })
  it('μm厚度仅在输入边界换算，存储与size_unit仍用原有契约', () => {
    expect(parseMaterialThickness('254μm')).toBe(0.254)
    expect(parseMaterialThickness(' 254.1 μm ')).toBe(0.2541)
    expect(parseMaterialThickness('254µm')).toBe(0.254)
    expect(parseMaterialThickness('0.254')).toBe(0.254)
    expect(parseMaterialThickness('')).toBeNull()
    expect(parseMaterialThickness('254mm')).toBeNaN()
    expect(() => validateMaterialDimensions({ ...emptyMaterialSize(), thicknessMm: parseMaterialThickness('254μm') })).not.toThrow()
    expect(() => validateMaterialDimensions({ ...emptyMaterialSize(), sizeUnit: 'μm' })).toThrow()
  })
  it('原尺寸可拆分，已有单位不重复追加，未提供的单位不擅自补入', () => {
    expect(composeMaterialSize(parseMaterialSize('30*90mm')!)).toBe('30*90mm')
    expect(composeMaterialSize(parseMaterialSize('30*90')!)).toBe('30*90')
    expect(parseMaterialSize('300×305×0.175mm')).toMatchObject({ width: '300', length: '305', thickness: '0.175' })
  })
  it('缺项位置与圆形尺寸可逆，不把厚度变成长度', () => {
    for (const value of ['914**0.175mm', '**0.175mm', '*90mm', 'Φ8*0.05mm']) {
      expect(composeMaterialSize(parseMaterialSize(value)!)).toBe(value)
    }
  })
  it('安全合并旧厚度，已有厚度优先，未知原文与克重不猜测', () => {
    expect(mergeLegacyThickness('300×305mm', 0.175)).toBe('300*305*0.175mm')
    expect(mergeLegacyThickness('300*305*0.2mm', 0.175)).toBe('300*305*0.2mm')
    expect(mergeLegacyThickness('按图纸', 0.175)).toBe('按图纸')
    expect(mergeLegacyThickness('300', '280g')).toBe('300')
    expect(mergeLegacyThickness('', 0.175)).toBe('**0.175mm')
  })
  it('错误格式保持为不可拆分，负数或零不能写回', () => {
    for (const value of ['30*90mmmm', '0*90mm', '-1mm', '1*2*3*4', '按图纸']) expect(parseMaterialSize(value)).toBeNull()
    expect(() => composeMaterialSize({ round: false, width: '0', length: '', thickness: '', unit: 'mm' })).toThrow()
  })
  it('回显统一半角*，识别×/x/X/全角＊，无法识别的原文不改写', () => {
    for (const value of ['300x305', '300X305', '300×305', '300＊305', '300 × 305']) {
      expect(normalizeMaterialSize(value)).toBe('300*305')
    }
    expect(normalizeMaterialSize('Φ8×0.05mm')).toBe('Φ8*0.05mm')
    expect(normalizeMaterialSize('914××0.175mm')).toBe('914**0.175mm')
    expect(normalizeMaterialSize('300*305mm')).toBe('300*305mm')
    expect(normalizeMaterialSize('按图纸尺寸')).toBe('按图纸尺寸')
    expect(normalizeMaterialSize('30*90mmmm')).toBe('30*90mmmm')
  })
})

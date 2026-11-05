import { describe, expect, it } from 'vitest'
import { displayDimensionOf, composeMaterialSize, mergeLegacyThickness, normalizeMaterialSize, parseMaterialSize, parseMaterialDimension, renderMaterialDimensions, validateMaterialDimensions, emptyMaterialSize } from '../materialSize'

describe('统一尺寸值', () => {
  it('字段单位决定显示，与物料计量单位无关，未配置仍逐字回落', () => {
    const value = { widthMm: 315, lengthMm: 1000000, thicknessMm: 0.254, sizeUnit: 'mm' }
    const fields = [{ key: 'thickness_mm', sep: '*', unit: 'μm' as const }, { key: 'width', sep: '*' }, { key: 'length', sep: '*', unit: 'm' as const }]
    expect(renderMaterialDimensions(value, false, fields)).toBe('254μm*315mm*1000m')
    expect(renderMaterialDimensions({ ...value, widthMm: null, lengthMm: null }, false, fields)).toBe('254μm')
    expect(renderMaterialDimensions({ ...value, widthMm: null, lengthMm: null, thicknessMm: null }, false, fields)).toBe('')
    expect(renderMaterialDimensions({ ...value, thicknessMm: 0.2541, lengthMm: 1005 }, false, fields)).toBe('254.1μm*315mm*1.01m')
    for (const unit of ['ROLL', 'M', 'PCS', 'PIN']) {
      const row = { ...value, unit }
      expect(renderMaterialDimensions(row, false, fields)).toBe('254μm*315mm*1000m')
      expect(renderMaterialDimensions({ ...row, sizeUnit: 'cm' })).toBe('315*1000000*0.254mm')
    }
    expect(renderMaterialDimensions({ widthMm: 300, thicknessMm: 0.175 })).toBe('300**0.175mm')
    expect(renderMaterialDimensions({ thicknessMm: 0.175 })).toBe('0.175mm')
    expect(displayDimensionOf(315, 'cm')).toBe('31.5')
    expect(displayDimensionOf(0.175, 'mm')).toBe('0.175')
    expect(displayDimensionOf(0.175, 'mm', 2)).toBe('0.18')
    expect(displayDimensionOf(0.2541, 'μm', 0)).toBe('254')
  })
  it('三个尺寸按各自单位折算mm，显式后缀优先', () => {
    expect(parseMaterialDimension('254μm')).toBe(0.254)
    expect(parseMaterialDimension(' 254.1 μm ')).toBe(0.2541)
    expect(parseMaterialDimension('254µm')).toBe(0.254)
    expect(parseMaterialDimension('0.254')).toBe(0.254)
    expect(parseMaterialDimension('')).toBeNull()
    expect(parseMaterialDimension('254um')).toBe(0.254)
    expect(parseMaterialDimension('0.254mm')).toBe(0.254)
    expect(parseMaterialDimension('0.0254cm')).toBe(0.254)
    expect(parseMaterialDimension('0.000254m')).toBe(0.254)
    expect(() => validateMaterialDimensions({ ...emptyMaterialSize(), thicknessMm: parseMaterialDimension('254μm') })).not.toThrow()
    expect(parseMaterialDimension('254', 'μm')).toBe(0.254)
    expect(parseMaterialDimension('1000', 'm')).toBe(1000000)
    expect(parseMaterialDimension('31.5', 'cm')).toBe(315)
    expect(parseMaterialDimension('254μm', 'cm')).toBe(0.254)
    expect(parseMaterialDimension('错误', 'm')).toBeNaN()
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

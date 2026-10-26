import { describe, expect, it } from 'vitest'
import { composeMaterialSpec } from '../materialSpec'

/**
 * 材料「规格及模数」唯一口径（dev-20261026-009）：
 * 作业规范正文/打印、BOM 打印、定版固化共用同一判定顺序，任一处改动都要过这组样例。
 */
describe('材料规格及模数口径', () => {
  it('计件材料带规格也用 N个/PCS，不再打成 =1PCS', () => {
    const led = { specification: '0.6T 020 蓝灯', unit: 'PCS', moduleQty: 1, baseQty: 1 }
    expect(composeMaterialSpec(led)).toBe('0.6T 020 蓝灯1个/PCS')
    expect(composeMaterialSpec({ ...led, baseQty: 2 })).toBe('0.6T 020 蓝灯2个/PCS')
    expect(composeMaterialSpec({ ...led, baseQty: undefined })).toBe('0.6T 020 蓝灯1个/PCS')
    expect(composeMaterialSpec({ ...led, unit: '个' })).toBe('0.6T 020 蓝灯1个/PCS')
    expect(composeMaterialSpec({ ...led, specification: '<b>蓝灯</b>' })).toBe('蓝灯1个/PCS')
  })

  it('拼版/卷料口径不变：尺寸、跳距在前，模数在后', () => {
    expect(composeMaterialSpec({ widthMm: 290, lengthMm: 135, moduleQty: 30 })).toBe(
      '290*135mm=30PCS'
    )
    expect(composeMaterialSpec({ specification: '110', split: '50mm', moduleQty: 5 })).toBe(
      '110跳50mm=5PCS'
    )
    expect(composeMaterialSpec({ specification: '110', split: ' 50mm ' })).toBe('110跳50mm')
    expect(composeMaterialSpec({ specification: '110', split: '' })).toBe('110')
  })

  it('没有 PCS/个 单位的行不按计件处理，仍用 =模数PCS', () => {
    expect(composeMaterialSpec({ specification: '110', moduleQty: 1 })).toBe('110=1PCS')
    expect(composeMaterialSpec({ specification: '0.6T 020 蓝灯', moduleQty: 1 })).toBe(
      '0.6T 020 蓝灯=1PCS'
    )
  })

  it('模数>1 的计件行仍走 =模数PCS（拼版口径优先）', () => {
    expect(composeMaterialSpec({ specification: '0.6T 020 蓝灯', unit: 'PCS', moduleQty: 3 })).toBe(
      '0.6T 020 蓝灯=3PCS'
    )
  })

  it('模数为空或零：有规格只输出规格，不加后缀', () => {
    expect(composeMaterialSpec({ specification: 'PET 0.1mm', unit: 'PCS', moduleQty: 0 })).toBe(
      'PET 0.1mm'
    )
    expect(composeMaterialSpec({ specification: 'PET', unit: 'PCS', moduleQty: null })).toBe('PET')
    expect(composeMaterialSpec({ specification: 'PET', moduleQty: NaN })).toBe('PET')
  })

  it('无规格的计件兜底保持 1个/PCS（dev-20261022-020）', () => {
    expect(composeMaterialSpec({ baseQty: 1, unit: 'PCS' })).toBe('1个/PCS')
    expect(composeMaterialSpec({ baseQty: 1, unit: '个', moduleQty: 0.5 })).toBe('1个/PCS')
    expect(composeMaterialSpec({ baseQty: 1, unit: 'PCS', moduleQty: 1 })).toBe('1个/PCS')
    expect(composeMaterialSpec({ specification: '0', unit: 'PCS', moduleQty: 1 })).toBe('1个/PCS')
  })

  it('没有可打印内容时返回空串，由打印侧渲染占位', () => {
    expect(composeMaterialSpec({ baseQty: 2, unit: 'PCS' })).toBe('')
    expect(composeMaterialSpec({ moduleQty: 12 })).toBe('')
    expect(composeMaterialSpec({})).toBe('')
    expect(composeMaterialSpec(null)).toBe('')
  })
})

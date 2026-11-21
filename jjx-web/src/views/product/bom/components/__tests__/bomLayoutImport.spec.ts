import { describe, expect, it } from 'vitest'
import { consumptionInputError } from '@/components/bom/consumptionMode'
import { flattenBomLayoutDrafts, hasBomLayoutHeader, parseBomLayoutRows } from '../bomLayoutImport'

const sample = [
  ['序号', '项目', '材料', '规格及模数', '刀模位置'],
  ['(1)', '', '加硬浅茶0.8PC(11008)', '450*225mm=6PCS', ''],
  ['(2)', '', '3M9080+离型纸', '195宽跳80mm=1PCS', ''],
  ['(3)', '', '020# PE低粘保护膜', '110宽跳215mm=3PCS', ''],
  ['(4)', '', '透明易撕贴', '1个/PCS', ''],
  ['(5)', '', 'T4901PET保护膜', '205宽跳440mm=6PCS', ''],
  ['(6)', '', '', '', ''],
]

describe('039 快速模式排版导入', () => {
  it('真实五行样例生成五个父草稿、六条材料明细，保留原序', () => {
    const before = JSON.stringify(sample)
    const drafts = parseBomLayoutRows(sample)
    expect(drafts).toHaveLength(5)
    expect(drafts[1].materials.map((row) => row.materialName)).toEqual(['3M9080', '离型纸'])
    const items = flattenBomLayoutDrafts(drafts)
    expect(items).toHaveLength(6)
    expect(items.map((row) => row.materialName)).toEqual([
      '加硬浅茶0.8PC(11008)',
      '3M9080',
      '离型纸',
      '020# PE低粘保护膜',
      '透明易撕贴',
      'T4901PET保护膜',
    ])
    expect(items.map((row) => row.sortOrder)).toEqual([1, 2, 3, 4, 5, 6])
    expect(JSON.stringify(sample)).toBe(before)
  })

  it.each([
    ['205宽跳440mm=6PCS', '205mm*440mm', 205, 440, 6],
    ['195宽跳110mm', '195mm*110mm', 195, 110, 1],
    ['195mm宽跳80mm=1PCS', '195mm*80mm', 195, 80, 1],
  ])('%s 拆为父行规格和比例、子行宽度跳距，未计算数量', (raw, spec, width, pitch, module) => {
    const drafts = parseBomLayoutRows([
      ['材料', '规格及模数'],
      ['材料原文', raw],
    ])
    expect(drafts[0]).toMatchObject({ specification: spec, moduleQty: module, baseQty: 1 })
    expect(drafts[0].materials[0]).toMatchObject({ widthMm: width, pitchMm: pitch, pcsPerPitch: 1 })
    expect(flattenBomLayoutDrafts(drafts)[0]).toMatchObject({
      widthMm: width,
      lengthMm: pitch,
      split: `${pitch}mm`,
      pitchMm: null,
      moduleQty: module,
      baseQty: 1,
      pcsPerPitch: 1,
      quantity: 0,
    })
  })

  it('N个/PCS 使用基数N、模数1，不生成猜测的宽度或跳距', () => {
    const drafts = parseBomLayoutRows([['规格及模数'], ['6个/PCS']])
    expect(drafts[0]).toMatchObject({ specification: '', baseQty: 6, moduleQty: 1 })
    expect(drafts[0].materials[0].pitchMm).toBeUndefined()
  })

  it('板材宽长仅作尺寸，不把长度误判成耗用跳距', () => {
    const drafts = parseBomLayoutRows([
      ['材料', '规格及模数'],
      ['PC', '450*225mm=6PCS'],
    ])
    expect(drafts[0].materials[0]).toMatchObject({ widthMm: 450, lengthMm: 225 })
    expect(drafts[0].materials[0].pitchMm).toBeUndefined()
    expect(flattenBomLayoutDrafts(drafts)[0]).toMatchObject({
      specification: '450*225mm',
      split: '',
      pitchMm: null,
    })
  })

  it('物料名称、物料ID可空，输出仍是允许保存的现有DTO', () => {
    const items = flattenBomLayoutDrafts(
      parseBomLayoutRows([['规格及模数'], ['205宽跳440mm=6PCS']])
    )
    expect(items[0]).toMatchObject({ materialId: 0, materialName: '', moduleQty: 6, baseQty: 1 })
    expect(consumptionInputError(items[0])).toBe('')
  })

  it('普通表头不冒充排版导入；支持标题行、列换序和表头别名', () => {
    expect(hasBomLayoutHeader([['材料', '规格', '模数']])).toBe(false)
    const drafts = parseBomLayoutRows([
      ['客户领料单'],
      ['规格及模数（必填）', '单位', '项目名称', '原料品名'],
      ['1个/PCS', 'PCS', '贴膜', '易撕贴'],
    ])
    expect(drafts[0]).toMatchObject({ sourceRow: 3, processName: '贴膜' })
    expect(drafts[0].materials[0].materialName).toBe('易撕贴')
  })

  it('拍平复制父行比例到子明细，只输出六条DTO，不带父行、嵌套材料或children', () => {
    const drafts = parseBomLayoutRows(sample)
    const before = JSON.stringify(drafts)
    const items = flattenBomLayoutDrafts(drafts)
    expect(
      items.every((row) => !('materials' in row) && !('children' in row) && !('sourceRow' in row))
    ).toBe(true)
    expect(
      items.every((row) => row.parentMaterialId === null && consumptionInputError(row) === '')
    ).toBe(true)
    expect(JSON.stringify(drafts)).toBe(before)
  })

  it('草稿修改保留父比例与各子行独立宽度/跳距，名称可清空', () => {
    const drafts = parseBomLayoutRows(sample)
    drafts[1].baseQty = 2
    drafts[1].moduleQty = 3
    drafts[1].materials[0].widthMm = 200
    drafts[1].materials[1].pitchMm = 90
    drafts[1].materials[1].materialName = ''
    const items = flattenBomLayoutDrafts(drafts)
    expect(items[1]).toMatchObject({ baseQty: 2, moduleQty: 3, widthMm: 200, lengthMm: 80 })
    expect(items[2]).toMatchObject({
      baseQty: 2,
      moduleQty: 3,
      widthMm: 195,
      lengthMm: 90,
      materialName: '',
    })
  })

  it.each(['无法解析', '205宽跳0mm=6PCS', '205宽跳440mm=0PCS', '0个/PCS', '3M=0PCS'])(
    '%s 报明源行，不静默丢行',
    (raw) => {
      expect(() =>
        parseBomLayoutRows([
          ['材料', '规格及模数'],
          ['材料', raw],
        ])
      ).toThrow('Excel第2行')
    }
  )

  it('草稿非法比例、宽度及非1的排版每跳片数均阻止加入', () => {
    const drafts = parseBomLayoutRows(sample)
    drafts[0].moduleQty = 0
    expect(() => flattenBomLayoutDrafts(drafts)).toThrow('模数')
    drafts[0].moduleQty = 6
    drafts[0].materials[0].widthMm = -1
    expect(() => flattenBomLayoutDrafts(drafts)).toThrow('宽度')
    drafts[0].materials[0].widthMm = 450
    Object.assign(drafts[0].materials[0], { pcsPerPitch: 2 })
    expect(() => flattenBomLayoutDrafts(drafts)).toThrow('每跳片数')
  })
})

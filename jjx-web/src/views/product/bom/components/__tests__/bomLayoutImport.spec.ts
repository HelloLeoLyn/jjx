import { describe, expect, it } from 'vitest'
import { consumptionInputError } from '@/components/bom/consumptionMode'
import {
  productBomItemsFromLayout,
  manufacturingItemsFromLayout,
  hasBomLayoutHeader,
  parseBomLayoutRows,
} from '../bomLayoutImport'

const sample = [
  ['序号', '项目', '材料', '规格及模数', '刀模位置'],
  ['(1)', '', '加硬浅茶0.8PC(11008)', '450*225mm=6PCS', ''],
  ['(2)', '', '3M9080+离型纸', '195宽跳80mm=1PCS', ''],
  ['(3)', '', '020# PE低粘保护膜', '110宽跳215mm=3PCS', ''],
  ['(4)', '', '透明易撕贴', '1个/PCS', ''],
  ['(5)', '', 'T4901PET保护膜', '205宽跳440mm=6PCS', ''],
  ['(6)', '', '', '', ''],
]

describe('040 方案B：保留解析，父行保存与子料草稿分离', () => {
  it('真实五行样例只生成五条产品父行，六条子料仅保留在草稿', () => {
    const before = JSON.stringify(sample)
    const drafts = parseBomLayoutRows(sample)
    expect(drafts).toHaveLength(5)
    expect(drafts[1].materials.map((row) => row.materialName)).toEqual(['3M9080', '离型纸'])
    const items = productBomItemsFromLayout(drafts)
    expect(items).toHaveLength(5)
    expect(items.map((row) => row.materialName)).toEqual([
      '加硬浅茶0.8PC(11008)',
      '3M9080+离型纸',
      '020# PE低粘保护膜',
      '透明易撕贴',
      'T4901PET保护膜',
    ])
    expect(items.map((row) => row.sortOrder)).toEqual([1, 2, 3, 4, 5])
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
    expect(productBomItemsFromLayout(drafts)[0]).toMatchObject({
      split: '',
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
    expect(productBomItemsFromLayout(drafts)[0]).toMatchObject({
      specification: '450*225mm',
      split: '',
      pitchMm: null,
    })
  })

  it('物料名称、物料ID可空，输出仍是允许保存的现有DTO', () => {
    const items = productBomItemsFromLayout(
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

  it('产品请求只带父行比例，不带子料、嵌套材料或children', () => {
    const drafts = parseBomLayoutRows(sample)
    const before = JSON.stringify(drafts)
    const items = productBomItemsFromLayout(drafts)
    expect(
      items.every((row) => !('materials' in row) && !('children' in row) && !('sourceRow' in row))
    ).toBe(true)
    expect(
      items.every((row) => row.parentMaterialId === null && consumptionInputError(row) === '')
    ).toBe(true)
    expect(JSON.stringify(drafts)).toBe(before)
  })

  it('修改子料不影响产品父行；另存时单独保留子料身份、宽度、跳距', () => {
    const drafts = parseBomLayoutRows(sample)
    drafts[4].materials[0].materialName = '原材料卷'
    drafts[4].materials[0].materialId = 900
    drafts[4].materials[0].widthMm = 210
    drafts[4].materials[0].pitchMm = 450
    const parent = productBomItemsFromLayout(drafts)[4]
    expect(parent).toMatchObject({
      materialId: 0,
      materialName: 'T4901PET保护膜',
      moduleQty: 6,
      baseQty: 1,
    })
    expect(parent.widthMm).toBeUndefined()
    expect(parent.pitchMm).toBeNull()
    const child = manufacturingItemsFromLayout(drafts[4].materials)[0]
    expect(child).toMatchObject({
      materialId: 900,
      materialName: '原材料卷',
      widthMm: 210,
      pitchMm: 450,
      pcsPerPitch: 1,
    })
    expect(child.moduleQty).toBeUndefined()
    expect(child.baseQty).toBeUndefined()
    expect(consumptionInputError(child)).toBe('')
  })

  it('0条子料仍可保存父行；多条草稿保留但本次不能另存制造BOM', () => {
    const drafts = parseBomLayoutRows(sample)
    drafts[0].materials = []
    expect(productBomItemsFromLayout(drafts)).toHaveLength(5)
    expect(manufacturingItemsFromLayout([])).toEqual([])
    expect(() => manufacturingItemsFromLayout(drafts[1].materials)).toThrow('0或1条')
    expect(drafts[1].materials).toHaveLength(2)
  })

  it('子料未确认的档案搜索仅阻止另存，不阻止保存父行', () => {
    const drafts = parseBomLayoutRows(sample)
    drafts[0].materials[0].searchPending = true
    expect(productBomItemsFromLayout(drafts)).toHaveLength(5)
    expect(() => manufacturingItemsFromLayout(drafts[0].materials)).toThrow('搜索尚未确认')
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
    expect(() => productBomItemsFromLayout(drafts)).toThrow('模数')
    drafts[0].moduleQty = 6
    drafts[0].materials[0].widthMm = -1
    expect(() => manufacturingItemsFromLayout(drafts[0].materials)).toThrow('宽度')
    drafts[0].materials[0].widthMm = 450
    Object.assign(drafts[0].materials[0], { pcsPerPitch: 2 })
    expect(() => manufacturingItemsFromLayout(drafts[0].materials)).toThrow('每跳片数')
  })
})

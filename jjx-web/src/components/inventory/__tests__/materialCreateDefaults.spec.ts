import { describe, expect, it } from 'vitest'
import {
  MATERIAL_DEFAULT_UNIT,
  materialCreatePresetFromImport,
  materialSaveFromImport,
} from '../materialCreateDefaults'

describe('物料建档共享默认值（dev-20261021-026）', () => {
  it('导入行预填名称/规格/供应商，单位来自共享常量，不默认归类', () => {
    const preset = materialCreatePresetFromImport({
      materialName: 'PET125#',
      specification: '100*200mm',
      supplierName: '3M',
    })
    expect(preset).toEqual({
      materialName: 'PET125#',
      specification: '100*200mm',
      supplierName: '3M',
      unit: MATERIAL_DEFAULT_UNIT,
    })
    expect(MATERIAL_DEFAULT_UNIT).toBe('PCS')
  })

  it('批量建档载荷与弹窗预填使用同一默认单位，不下发旧类型', () => {
    const payload = materialSaveFromImport(
      { materialName: '离型纸', specification: '120g', supplierName: '供应商A' },
      'M20261021001'
    )
    expect(payload).toEqual({
      materialCode: 'M20261021001',
      materialName: '离型纸',
      specification: '120g',
      unit: MATERIAL_DEFAULT_UNIT,
      supplierName: '供应商A',
    })
  })

  it('缺字段时归一为空字符串，不下发 undefined', () => {
    const payload = materialSaveFromImport({}, 'M1')
    expect(payload.materialName).toBe('')
    expect(payload.specification).toBe('')
    expect(payload.supplierName).toBe('')
    expect(payload).not.toHaveProperty('materialType')
    expect(payload.unit).toBe('PCS')
  })
})

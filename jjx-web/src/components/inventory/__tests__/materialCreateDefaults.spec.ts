import { describe, expect, it } from 'vitest'
import {
  MATERIAL_DEFAULT_TYPE,
  MATERIAL_DEFAULT_UNIT,
  materialCreatePresetFromImport,
  materialSaveFromImport,
} from '../materialCreateDefaults'

describe('物料建档共享默认值（dev-20261021-026）', () => {
  it('导入行预填名称/规格/供应商，类型与单位来自共享常量', () => {
    const preset = materialCreatePresetFromImport({
      materialName: 'PET125#',
      specification: '100*200mm',
      supplierName: '3M',
    })
    expect(preset).toEqual({
      materialName: 'PET125#',
      specification: '100*200mm',
      supplierName: '3M',
      materialType: MATERIAL_DEFAULT_TYPE,
      unit: MATERIAL_DEFAULT_UNIT,
    })
    expect(MATERIAL_DEFAULT_TYPE).toBe('R')
    expect(MATERIAL_DEFAULT_UNIT).toBe('PCS')
  })

  it('批量建档载荷与弹窗预填使用同一默认类型/单位', () => {
    const payload = materialSaveFromImport(
      { materialName: '离型纸', specification: '120g', supplierName: '供应商A' },
      'M20261021001'
    )
    expect(payload).toEqual({
      materialCode: 'M20261021001',
      materialName: '离型纸',
      materialType: MATERIAL_DEFAULT_TYPE,
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
    expect(payload.materialType).toBe('R')
    expect(payload.unit).toBe('PCS')
  })
})

import { describe, expect, it } from 'vitest'
import { applyMaterialSelection, clearMaterialSelection } from '../inboundMaterial'

describe('inboundMaterial（dev-20261024-019 wave B）', () => {
  it('选中档案：回填 ID/编码/名称/规格/单位', () => {
    const row: any = {}
    const ok = applyMaterialSelection(row, { materialId: 9, materialCode: 'M9', materialName: 'PET', specification: '0.1', unit: 'KG' })
    expect(ok).toBe(true)
    expect(row).toMatchObject({ materialId: '9', materialCode: 'M9', materialName: 'PET', specification: '0.1', unit: 'KG' })
  })

  it('只有文字没有档案：清空旧 ID，不用文字冒充', () => {
    const row: any = { materialId: '9', materialCode: 'M9', materialName: 'PET', specification: '0.1', unit: 'KG' }
    const ok = applyMaterialSelection(row, null)
    expect(ok).toBe(false)
    expect(row.materialId).toBe('')
    expect(row.materialName).toBe('')
  })

  it('清空：ID/编码/名称/规格/单位一起清', () => {
    const row: any = { materialId: '9', materialCode: 'M9', materialName: 'PET', specification: '0.1', unit: 'KG' }
    clearMaterialSelection(row)
    expect(row).toMatchObject({ materialId: '', materialCode: '', materialName: '', specification: '', unit: '' })
  })
})

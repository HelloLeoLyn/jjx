import { beforeEach, describe, expect, it, vi } from 'vitest'
const http = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: http }))
import { previewBomConsumption, previewPitchRequirement } from '@/api/production/bomConsumption'
beforeEach(() => { vi.clearAllMocks() })
describe('BOM实时试算接口契约', () => {
  it('原始模数基数和试算量提交只读POST，不发送舍入后的每件值', () => {
    const body = { outputQuantity: 300, outputUnit: 'PCS', rows: [{ key: '-1', materialId: 109, unit: 'PCS', baseQty: 1, moduleQty: 6, lossRate: 0, minIssueQty: 0 }] }
    previewBomConsumption(body)
    expect(http.post).toHaveBeenCalledWith('/production/bom/consumption/preview', body)
    expect(body.rows[0]).not.toHaveProperty('quantity')
  })
  it('跳距复用004接口，80.5不截成80', () => {
    const params = { widthMm: 195, pitchMm: 80.5, pcsPerJump: 1, inputUnit: 'M2', outputPcs: 100 }
    previewPitchRequirement(params)
    expect(http.get).toHaveBeenCalledWith('/production/bom/pitch-requirement/preview', { params })
  })
})

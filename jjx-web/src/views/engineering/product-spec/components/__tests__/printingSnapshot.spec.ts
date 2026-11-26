import { describe, it, expect } from 'vitest'
import { printingIdentity, printingPlate, printingRequirements } from '../workSpecProcessRows'

describe('发布的印刷要求展示', () => {
  it('历史快照优先于当前版本与网框，建议版明确区别于实际用版', () => {
    const row = { artworkVersionId: 99, defaultPlateNo: '当前版', printingSnapshot: {
      artworkVersionId: 2, artworkNo: 'A1', artworkVersionNo: 'V2', defaultPlateId: 3,
      defaultPlateNo: '旧版面', defaultFrameNo: '旧框', requirements: { position: '面板', meshMin: 100, tensionMax: 20 },
    } }
    expect(printingIdentity(row)).toBe('A1 / V2')
    expect(printingPlate(row)).toBe('旧版面 / 旧框')
    expect(printingRequirements(row)).toContain('目数 100—不限')
    expect(printingRequirements(row)).toContain('20 N/cm')
    row.artworkVersionId = 100
    expect(printingIdentity(row)).toBe('A1 / V2')
  })
  it('缺失历史身份明确未知，不凭旧框号补造关联', () => {
    const row = { customProcessParams: '{"screenNo":"旧框"}' }
    expect(printingIdentity(row)).toBe('图案版本未记录')
    expect(printingPlate(row)).toBe('历史记录：版号未记录 / 旧框（未核验）')
    expect(printingRequirements(row)).toBe('要求未记录')
  })
})

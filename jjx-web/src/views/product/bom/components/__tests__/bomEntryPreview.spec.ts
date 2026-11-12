import { beforeAll, describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const directory = resolve(process.cwd(), 'public/demos/bom-entry-review')
const data = JSON.parse(readFileSync(resolve(directory, 'review-data.json'), 'utf8'))
let preview: any
beforeAll(async () => {
  const moduleUrl = pathToFileURL(resolve(directory, 'entry-preview.mjs')).href
  preview = await import(/* @vite-ignore */ moduleUrl)
})
const row = (patch: Record<string, unknown> = {}) => ({
  unit: 'PCS', base: 1, module: 6, loss: 0, minimum: 0, ...patch,
})

describe('007评审稿使用现有Java计算结果，不在浏览器复制耗用算法', () => {
  it('6模1基数乘300展示50，不用0.1667放大取51', () => {
    expect(preview.getQuote(row(), 300, data)).toMatchObject({ each: '0.1667', total: '50', exact: '50/1' })
    expect(preview.getQuote(row({ module: 3 }), 300, data).total).toBe('100')
  })
  it('每件净用量与含损耗/最低投料的总需求分别展示', () => {
    expect(preview.getQuote(row({ loss: 5 }), 300, data)).toMatchObject({ each: '0.1667', total: '53' })
    expect(preview.getQuote(row({ minimum: 10 }), 1, data).total).toBe('10')
  })
  it('非法与未收录输入不给旧值或0冒充结果', () => {
    for (const patch of [{ module: 0 }, { base: '' }, { loss: 101 }, { loss: '0.001' }, { base: '0.00001' }]) {
      expect(preview.getQuote(row(patch), 100, data)).toBeNull()
    }
    expect(preview.getQuote(row({ module: 7 }), 100, data)).toBeNull()
    expect(preview.getQuote(row(), 12.5, data)).toBeNull()
  })
  it('跳距变动不覆盖人工定额，明确采用后才回填', () => {
    const draft = row({ unit: 'M2', width: 195, pitch: 80, perJump: 1, module: 1 })
    expect(preview.getPitchQuote(draft, 100, data).displayConsumption).toBe(1.56)
    expect(draft.base).toBe(1)
    draft.pitch = 100
    expect(preview.getPitchQuote(draft, 100, data).displayConsumption).toBe(1.95)
    expect(draft.base).toBe(1)
    expect(preview.adoptPitch(draft, 100, data)).toBe(true)
    expect(draft).toMatchObject({ base: 0.0195, module: 1 })
  })
  it('80.5mm按㎡计算不能精确保存时禁止采用，不自动舍入', () => {
    const draft = row({ unit: 'M2', width: 195, pitch: 80.5, perJump: 1, module: 1 })
    expect(preview.getPitchQuote(draft, 100, data).baseQtyStorableInBom).toBe(false)
    expect(preview.adoptPitch(draft, 100, data)).toBe(false)
    expect(draft.base).toBe(1)
  })
  it('净长80与跳距100区分，折算米与卷数只读参考', () => {
    const draft = row({ unit: 'M2', width: 195, pitch: 100, perJump: 1, module: 1 })
    preview.adoptPitch(draft, 100, data)
    const result = preview.getQuote(draft, 100, data)
    expect(result.total).toBe('1.95')
    expect(result.lengths['195']).toMatchObject({ metres: '10', rolls: { '50': '1' } })
    expect(preview.getQuote(row(), 300, data).lengths).toEqual({})
  })
  it('使用本地真实物料与方案快照，原始单位差异和78mm跳距仍保留', () => {
    expect(data.materials.find((m: any) => m.code === 'AUX000347').unit).toBe('PCS')
    expect(data.boms.find((b: any) => b.id === 6).rows[0].pitch).toBe('78mm')
    const oldRow = data.boms.find((b: any) => b.id === 2).rows.find((r: any) => r.materialId === 112)
    expect(oldRow.bomUnit).toBe('PCS')
    expect(data.materials.find((m: any) => m.id === 112).unit).toBe('M')
  })
})

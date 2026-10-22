import { describe, expect, it } from 'vitest'
import { parseSpecModule } from '../bomSpecImport'

describe('BOM 导入：规格及模数单列反推（dev-20261022-016）', () => {
  it('A*Bmm=nPCS：规格整段进 specification，模数 n，基数缺省 1', () => {
    expect(parseSpecModule('340*305mm=8PCS')).toEqual({
      specification: '340*305mm',
      split: '',
      moduleQty: 8,
      baseQty: 1,
    })
    expect(parseSpecModule('20*7mm=1PCS')).toEqual({
      specification: '20*7mm',
      split: '',
      moduleQty: 1,
      baseQty: 1,
    })
  })

  it('规格跳跳距=nPCS：跳距单独拆出并保留 mm', () => {
    expect(parseSpecModule('315宽跳75mm=2PCS')).toEqual({
      specification: '315宽',
      split: '75mm',
      moduleQty: 2,
      baseQty: 1,
    })
  })

  it('N个/PCS：模数 1、基数 N', () => {
    expect(parseSpecModule('6个/PCS')).toEqual({
      specification: '',
      split: '',
      moduleQty: 1,
      baseQty: 6,
    })
    expect(parseSpecModule('2个/PCS')?.baseQty).toBe(2)
  })

  it('「贴」不是跳距标记，整段作为规格', () => {
    expect(parseSpecModule('295宽贴315=8PCS')).toEqual({
      specification: '295宽贴315',
      split: '',
      moduleQty: 8,
      baseQty: 1,
    })
  })

  it('无法解析或空值返回 null', () => {
    expect(parseSpecModule('')).toBeNull()
    expect(parseSpecModule('随便一段文字')).toBeNull()
    expect(parseSpecModule('=0PCS')).toBeNull()
  })
})

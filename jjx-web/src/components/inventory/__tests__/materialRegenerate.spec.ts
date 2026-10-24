import { describe, expect, it } from 'vitest'
import {
  buildRegeneration,
  hasRegenerationChanges,
  parseOriginalName,
  resolveFinalName,
} from '../materialRegenerate'

const current = { materialName: '人工名称', specification: '旧规格' }
const generated = { name: 'PC11008透明（3M）', specification: '厚度:0.175mm；表面处理:细砂' }

describe('迁移期【原名称】解析', () => {
  it('取【原名称】与【原规格】之间的文本', () => {
    expect(parseOriginalName('【原名称】PC膜旧名【原规格】0.38mm 幼砂')).toBe('PC膜旧名')
  })

  it('忽略前面的原供应商等前缀标记', () => {
    expect(
      parseOriginalName('【原物料ID】12【原供应商】3M【原名称】PET膜125【原规格】雾面')
    ).toBe('PET膜125')
  })

  it('遇到【重生成】留痕时截断', () => {
    expect(parseOriginalName('【原名称】胶带5604（3M）\n【重生成】2026-10-23 18:45 大黄')).toBe(
      '胶带5604（3M）'
    )
  })

  it('无标记返回空串', () => {
    expect(parseOriginalName('普通备注')).toBe('')
    expect(parseOriginalName('')).toBe('')
    expect(parseOriginalName(undefined)).toBe('')
  })
})

describe('重算名称与规格', () => {
  it('对比当前值与生成值，标出变化', () => {
    const result = buildRegeneration(current, generated, ['厚度：0.175mm'])
    expect(result.currentName).toBe('人工名称')
    expect(result.generatedName).toBe('PC11008透明（3M）')
    expect(result.nameChanged).toBe(true)
    expect(result.specChanged).toBe(true)
    expect(result.evidence).toEqual(['厚度：0.175mm'])
  })

  it('一致时不算变化', () => {
    const result = buildRegeneration(
      { materialName: generated.name, specification: generated.specification },
      generated,
      []
    )
    expect(result.nameChanged).toBe(false)
    expect(result.specChanged).toBe(false)
    expect(hasRegenerationChanges(result, result.currentName, result.currentSpec)).toBe(false)
  })

  it('默认保留当前人工名称，勾选后才采用生成名称', () => {
    const result = buildRegeneration(current, generated, [])
    expect(resolveFinalName(result, false)).toBe('人工名称')
    expect(resolveFinalName(result, true)).toBe('PC11008透明（3M）')
  })

  it('当前名称为空时回落到生成名称', () => {
    const result = buildRegeneration({ materialName: '', specification: '' }, generated, [])
    expect(resolveFinalName(result, false)).toBe('PC11008透明（3M）')
  })

  it('最终值不同于当前值才算需要保存', () => {
    const result = buildRegeneration(current, generated, [])
    expect(hasRegenerationChanges(result, '人工名称', '旧规格')).toBe(false)
    expect(hasRegenerationChanges(result, '人工名称', generated.specification)).toBe(true)
    expect(hasRegenerationChanges(result, generated.name, generated.specification)).toBe(true)
  })
})

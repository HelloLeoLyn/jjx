import { describe, expect, it } from 'vitest'
import { MaterialClassStatusEnum, resolveClassStatus, suggestRuleKeyword } from '../MaterialClassEnum'

describe('物料归类三态', () => {
  it('按 categoryId 与来源/确认标记推导状态', () => {
    expect(resolveClassStatus({ categoryId: null })).toBe('unclassified')
    expect(resolveClassStatus({ categoryId: 18, classSource: 'RULE', classConfirmed: false })).toBe(
      'rule_unconfirmed'
    )
    expect(resolveClassStatus({ categoryId: 18, classSource: 'MANUAL', classConfirmed: true })).toBe(
      'confirmed'
    )
    expect(resolveClassStatus({ categoryId: 18, classSource: 'MANUAL', classConfirmed: false })).toBe(
      'confirmed'
    )
  })

  it('标签与枚举一致', () => {
    expect(MaterialClassStatusEnum.getLabel('unclassified')).toBe('未归类')
    expect(MaterialClassStatusEnum.getLabel('rule_unconfirmed')).toBe('规则命中未确认')
    expect(MaterialClassStatusEnum.getLabel('confirmed')).toBe('人工确认')
  })
})

describe('规则关键词建议', () => {
  it('取第一个非空名称，去括号与空白后截 6 字', () => {
    expect(suggestRuleKeyword(['', 'PC膜11008（3M）'])).toBe('PC膜110')
    expect(suggestRuleKeyword([null, '  PET保护膜 WG008 '])).toBe('PET保护膜')
  })
})

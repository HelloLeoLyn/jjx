import { describe, expect, it } from 'vitest'
import {
  formatAmount,
  formatCny,
  formatOriginal,
  moneyPair,
  orderMoneyView,
} from '../../composables/useOrderForm'

/**
 * 展示口径（dev-20261019-020）：
 * 系统金额字段按人民币存储；USD 等外币按 1 外币 = N 人民币折合展示，避免把人民币数值直接标成 USD。
 */
describe('销售订单金额展示（列表/详情一致）', () => {
  it('USD 单：人民币为主值，折合原币跟在同一行', () => {
    expect(moneyPair(2450, 'USD', 6.73)).toBe('¥2,450.00（USD 364.04）')
    expect(moneyPair(2480, 'USD', 6.73)).toBe('¥2,480.00（USD 368.50）')
  })

  it('含税总额（折前）与实际应收是两个不同金额，不互相顶替', () => {
    expect(moneyPair(2480, 'USD', 6.73)).not.toBe(moneyPair(2450, 'USD', 6.73))
    expect(formatCny(orderMoneyView(2480, 'USD', 6.73))).toBe('¥2,480.00')
    expect(formatCny(orderMoneyView(2450, 'USD', 6.73))).toBe('¥2,450.00')
  })

  it('CNY 单只显示人民币，不附加多余外币值', () => {
    expect(moneyPair(2000, 'CNY', 1)).toBe('¥2,000.00')
    expect(moneyPair(2000, undefined, undefined)).toBe('¥2,000.00')
    expect(formatOriginal(orderMoneyView(2000, 'CNY', 6.73))).toBe('¥2,000.00')
  })

  it('金额为 0 时仍完整显示 0.00，不被当成缺失', () => {
    expect(moneyPair(0, 'USD', 6.73)).toBe('¥0.00（USD 0.00）')
    expect(formatAmount(0)).toBe('0.00')
  })

  it('汇率缺失/无效：原币明确提示，不按 1 冒充成同数值', () => {
    const missing = orderMoneyView(2450, 'USD', undefined)
    expect(missing.rateValid).toBe(false)
    expect(missing.original).toBeNull()
    expect(formatCny(missing)).toBe('¥2,450.00')
    expect(formatOriginal(missing)).toBe('汇率缺失，无法折算原币')
    expect(moneyPair(2450, 'USD', 0)).toBe('¥2,450.00（汇率缺失，无法折算原币）')
    expect(moneyPair(2450, 'USD', -1)).toContain('汇率缺失')
  })

  it('空值与非法数值显示占位符，不显示 NaN', () => {
    expect(formatAmount(null)).toBe('—')
    expect(formatAmount(undefined)).toBe('—')
    expect(formatAmount(Number.NaN)).toBe('—')
  })

  it('汇率变化只影响折合原币，不改变人民币金额', () => {
    const before = orderMoneyView(2450, 'USD', 6.73)
    const after = orderMoneyView(2450, 'USD', 7)
    expect(before.cny).toBe(after.cny)
    expect(before.original).toBe(364.04)
    expect(after.original).toBe(350)
  })
})

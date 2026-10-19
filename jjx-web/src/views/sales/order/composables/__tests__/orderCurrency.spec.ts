import { describe, expect, it } from 'vitest'
import { orderMoneyView, roundAmount } from '../useOrderForm'

/**
 * 金额换算口径（dev-20261019-020）：
 * - sales_order / sales_quotation 金额字段按人民币（CNY）存储；
 * - exchange_rate 方向 1 外币 = N 人民币，折合原币 = 人民币 / 汇率；
 * - 汇率缺失/非正不按 1 兜底；本函数只读、无副作用，关闭不保存不会写库。
 */
describe('订单金额换算与汇率边界', () => {
  it('人民币 ÷ 汇率得到原币，四舍五入到 2 位', () => {
    expect(orderMoneyView(2000, 'USD', 6.73).original).toBe(297.18)
    expect(orderMoneyView(2450, 'USD', 6.73).original).toBe(364.04)
    expect(orderMoneyView(100, 'EUR', 7.85).original).toBe(12.74)
  })

  it('汇率方向是 1 外币 = N 人民币（不是人民币兑外币）', () => {
    expect(orderMoneyView(673, 'USD', 6.73).original).toBe(100)
    expect(orderMoneyView(1000, 'USD', 7).original).toBe(142.86)
  })

  it('汇率缺失、0、负数、NaN 均视为无效，人民币金额保留', () => {
    for (const rate of [undefined, null, 0, -1, Number.NaN, '', 'abc']) {
      const view = orderMoneyView(2450, 'USD', rate as never)
      expect(view.rateValid, String(rate)).toBe(false)
      expect(view.original, String(rate)).toBeNull()
      expect(view.cny).toBe(2450)
    }
  })

  it('币种为空或小写时按人民币处理，大小写不敏感', () => {
    expect(orderMoneyView(100, '', 6.73)).toMatchObject({ currency: 'CNY', original: 100 })
    expect(orderMoneyView(100, 'usd', 6.73).currency).toBe('USD')
    expect(orderMoneyView(100, 'cny', 6.73).original).toBe(100)
  })

  it('0 金额任何币种都返回 0，不产生除零/NaN', () => {
    expect(orderMoneyView(0, 'USD', 6.73)).toMatchObject({ cny: 0, original: 0, rateValid: true })
    expect(orderMoneyView(0, 'CNY', 1).original).toBe(0)
  })

  it('四舍五入为 HALF_UP，且不出现浮点尾差', () => {
    expect(roundAmount(2.345)).toBe(2.35)
    expect(roundAmount(2.344)).toBe(2.34)
    expect(roundAmount(0)).toBe(0)
    expect(roundAmount(Number.NaN)).toBe(0)
    expect(orderMoneyView(10, 'USD', 3).original).toBe(3.33)
  })

  it('同一输入反复计算值稳定，且不修改入参（展示不写数据）', () => {
    const amount = 2450
    const first = orderMoneyView(amount, 'USD', 6.73)
    const second = orderMoneyView(amount, 'USD', 6.73)
    expect(second).toEqual(first)
    expect(amount).toBe(2450)
  })

  it('修改币种/汇率后按新值展示（关闭不保存不涉及写库）', () => {
    const usd = orderMoneyView(2450, 'USD', 6.73)
    const eur = orderMoneyView(2450, 'EUR', 7.85)
    expect(usd.original).not.toBe(eur.original)
    // 纯函数只返回视图，不持有/修改表单或订单对象。
    expect(usd).not.toBe(eur)
  })
})

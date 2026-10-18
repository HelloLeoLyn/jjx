import { describe, expect, it } from 'vitest'
import { duplicateOrderProduct, sameOrderProduct } from '../../composables/orderProductRules'
describe('order product uniqueness', () => {
  it('uses archived identity and normalizes draft codes', () => {
    expect(sameOrderProduct({ productId: 1 }, { productId: 1 })).toBe(true)
    expect(sameOrderProduct({ productId: 1, productCode: 'A' }, { productId: 2, productCode: 'A' })).toBe(false)
    expect(sameOrderProduct({ productCode: ' a ' }, { productCode: 'A' })).toBe(true)
    expect(sameOrderProduct({}, {})).toBe(false)
  })
  it('does not count the edited row and catches final repeated rows', () => {
    const row = { productId: 1, productCode: 'A' }
    expect(duplicateOrderProduct([row], row, row)).toBeUndefined()
    expect(duplicateOrderProduct([row], { productCode: ' a ' })).toBe(row)
    expect(duplicateOrderProduct([row, { productId: 2, productCode: 'B' }])).toBeUndefined()
    expect(duplicateOrderProduct([row, { productId: 1, productCode: 'A' }])).toBeDefined()
  })
})

import { describe, expect, it } from 'vitest'
import { clearStockSearch, stockSearchPayload, type StockSearchModel } from '../stockSearch'
import { StockSearchStateEnum } from '@/enums/inventory/StockSearchEnum'
describe('即时库存搜索条件', () => {
  it('父类/子类精确选择、属性AND及零库存false保持，交互字段不发给后端', () => {
    const payload = stockSearchPayload({ keyword: '离型纸 120g', pageNum: 2, parentCategoryId: 10, subCategoryId: 11, stockState: StockSearchStateEnum.LOW.value,
      onlyHasStock: false, attr__adhesive: false, attr__empty: '', createTimeRange: ['2026-11-01', '2026-11-20'] })
    expect(payload).toMatchObject({ pageNum: 2, keyword: '离型纸 120g', categoryId: 11, lowStock: true, onlyHasStock: false, createTimeStart: '2026-11-01', createTimeEnd: '2026-11-20' })
    expect(JSON.parse(payload.attributeFilters!)).toEqual([{ key: 'adhesive', value: 'false' }])
    expect(payload).not.toHaveProperty('parentCategoryId'); expect(payload).not.toHaveProperty('attr__adhesive')
  })
  it('清除子类回到父类，重置清空高级条件并回第一页、保留每页数量', () => {
    const model: StockSearchModel = { pageNum: 5, pageSize: 50, parentCategoryId: 10, warehouseId: 1, onlyHasStock: false, attr__color: 'red', stockState: StockSearchStateEnum.OBSOLETE.value }
    expect(stockSearchPayload(model).categoryId).toBe(10)
    clearStockSearch(model)
    expect(model).toEqual({ pageNum: 1, pageSize: 50, keyword: '' })
    expect(stockSearchPayload(model)).toMatchObject({ pageNum: 1, pageSize: 50 })
    expect(stockSearchPayload(model).attributeFilters).toBeUndefined()
  })
})

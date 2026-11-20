import type { StockQueryParams } from '@/types/inventory/stock'
import { StockSearchStateEnum } from '@/enums/inventory/StockSearchEnum'
export interface StockSearchModel extends StockQueryParams {
  parentCategoryId?: number
  subCategoryId?: number
  stockState?: string
  createTimeRange?: string[]
  [key: string]: unknown
}
export function stockSearchPayload(model: StockSearchModel): StockQueryParams {
  const { parentCategoryId, subCategoryId, stockState, createTimeRange, ...params } = model
  for (const key of Object.keys(params)) if (key.startsWith('attr__')) delete params[key]
  const attributes = Object.entries(model).filter(([key, value]) => key.startsWith('attr__') && value != null && value !== '')
    .map(([key, value]) => ({ key: key.slice(6), value: String(value) }))
  return { ...params, categoryId: subCategoryId ?? parentCategoryId,
    attributeFilters: attributes.length ? JSON.stringify(attributes) : undefined,
    lowStock: stockState === StockSearchStateEnum.LOW.value ? true : undefined,
    expiring: stockState === StockSearchStateEnum.EXPIRING.value ? true : undefined,
    obsolete: stockState === StockSearchStateEnum.OBSOLETE.value ? true : undefined,
    createTimeStart: createTimeRange?.[0] || undefined, createTimeEnd: createTimeRange?.[1] || undefined }
}
export function clearStockSearch(model: StockSearchModel) {
  const size = model.pageSize
  for (const key of Object.keys(model)) delete model[key]
  Object.assign(model, { pageNum: 1, pageSize: size || 10, keyword: '' })
}

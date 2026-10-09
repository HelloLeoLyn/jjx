import request from '@/utils/request'
import type { PageResult, R } from '@/types'

export interface ProductPrice {
  productId: number
  productCode: string
  productName: string
  customerName?: string
  unit?: string
  basePrice?: number | null
  costPrice?: number | null
}

export interface ProductPriceUpdate {
  basePrice: number
  costPrice: number
  expectedBasePrice: number | null
  expectedCostPrice: number | null
}

export const productPriceApi = {
  page(params: { productCode?: string; productName?: string; pageNum: number; pageSize: number }) {
    return request.get<R<PageResult<ProductPrice>>>('/product/prices', { params })
  },
  get(productId: number) {
    return request.get<R<ProductPrice>>(`/product/prices/${productId}`)
  },
  update(productId: number, data: ProductPriceUpdate) {
    return request.put<R<{ success: boolean; detailMessage?: string }>>(`/product/prices/${productId}`, data)
  },
}

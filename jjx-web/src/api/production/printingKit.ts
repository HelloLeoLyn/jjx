import request from '@/utils/request'
import type { R } from '@/types'

/** 齐套核对项（dev-20261126-002） */
export interface KitCheckItem {
  resourceType: string
  routingItemId?: number | null
  processName?: string | null
  resourceKey?: string | null
  resourceCode?: string | null
  resourceName?: string | null
  need?: number
  available?: number
  gap?: number
  unit?: string | null
}

export interface KitResult {
  orderId?: number
  orderNo?: string
  productId?: number
  checks?: KitCheckItem[]
  items?: KitCheckItem[]
  gaps?: KitCheckItem[]
  verified?: boolean
  verificationMessage?: string | null
  warnings?: string[]
  materialsWarning?: string
  ok?: boolean
  materials?: Record<string, unknown> | null
}

export interface KitShortage {
  id: number
  scope: string
  orderId?: number
  orderNo?: string
  productId?: number
  routingItemId?: number
  processName?: string
  resourceType: string
  resourceKey?: string
  resourceCode?: string
  resourceName?: string
  needQty?: number
  availableQty?: number
  gapQty?: number
  unit?: string
  status: number
  firstFoundAt?: string
  lastCheckAt?: string
  closedReason?: string
}

export const printingKitApi = {
  productPrecheck(productId: number) {
    return request.get<R<KitResult>>(`/production/printing/kit/product/${productId}`)
  },
  orderKit(orderId: number) {
    return request.get<R<KitResult>>(`/production/printing/kit/order/${orderId}`)
  },
  recheck(orderId: number) {
    return request.post<R<KitResult>>(`/production/printing/kit/order/${orderId}/recheck`)
  },
  shortages(params: { orderId?: number; status?: number }) {
    return request.get<R<KitShortage[]>>('/production/printing/kit/shortages', { params })
  },
  close(id: number, reason?: string) {
    return request.post<R<void>>(`/production/printing/kit/shortages/${id}/close`, null, {
      params: { reason },
    })
  },
}

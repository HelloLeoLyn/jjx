import request from '@/utils/request'
import type { PurchaseEntryItem, PurchaseUnitOption } from './orderEntry'
export interface PlanRow extends PurchaseEntryItem {
  key: string
  supplierId?: number
  supplierName?: string
  demandQuantity?: number | null
  currentStock?: number | null
  availableStock?: number | null
  inTransit?: number | null
  contextError?: string
  contextLoading?: boolean
  reason: string
  priority: string
  sourceAlertIds: number[]
  needDate?: string
  linkedOrderNo?: string
}
export interface PlanOrderSeed {
  supplierId: number
  supplierName: string
  expectedDeliveryDate?: string
  urgentFlag: boolean
  items: PurchaseEntryItem[]
}
export interface SavedPurchaseOrder {
  orderNo: string
  items: PurchaseEntryItem[]
}
export const getPlanUnits = () =>
  request.get<{ data: PurchaseUnitOption[] }>('/purchase/plan/unit-options')
export const getPlanMaterial = (id: number) =>
  request.get<{ data: Partial<PlanRow> }>(`/purchase/plan/material/${id}`)
export const previewPlanQuantity = (item: Partial<PurchaseEntryItem>) =>
  request.post<{ data: PurchaseEntryItem }>('/purchase/plan/quantity-preview', item)

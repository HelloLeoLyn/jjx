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
  /** 上次采购价（最近一张已批准订单该物料单价，dev-20261113-010） */
  lastPrice?: number | null
  /** 物料档案标准价（dev-20261113-010） */
  standardPrice?: number | null
  procurement?: PlanProcurementScheme
}
export type PlanPurchaseItem = Omit<PlanRow, 'procurement'>
export interface PlanProcurementScheme {
  purchase: PlanPurchaseItem
  expectedDeliveryDate?: string
  status: string
  coveredQuantity: number | null
  remainingQuantity: number | null
  message: string
}
export interface PlanSchemePreview {
  item: PurchaseEntryItem
  supplierName?: string
  status: string
  coveredQuantity: number | null
  remainingQuantity: number | null
  message: string
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
/** 参考价（上次采购价 + 物料标准价），dev-20261113-010 */
export const getReferencePrices = (materialIds: number[]) =>
  request.get<{
    data: Record<string, { lastPrice?: number | null; lastPriceUnit?: string; lastPriceTaxInclusive?: boolean; lastTaxRate?: number | null; standardPrice?: number | null }>
  }>('/purchase/order/reference-price', { params: { materialIds: materialIds.join(',') } })
export const previewPlanQuantity = (item: Partial<PurchaseEntryItem>) =>
  request.post<{ data: PurchaseEntryItem }>('/purchase/plan/quantity-preview', item)
export const previewPlanScheme = (data: {
  sourceMaterialId: number
  demandQuantity?: number | null
  supplierId?: number
  needDate?: string
  expectedDeliveryDate?: string
  item: Partial<PurchaseEntryItem>
}) => request.post<{ data: PlanSchemePreview }>('/purchase/plan/scheme-preview', data)

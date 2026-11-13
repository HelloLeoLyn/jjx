import request from '@/utils/request'

export interface PurchaseUnitOption { value: string; label: string }
export interface PurchaseMaterialDefault {
  configured: boolean
  unit: string
  purchaseUnit: string
  priceUnit: string
  rollWidthMm?: number | null
  rollLengthM?: number | null
  pieceWidthMm?: number | null
  pieceLengthMm?: number | null
  allowedUnits?: string[]
}
export interface PurchaseEntryItem {
  itemId?: string | number
  materialId?: number
  materialCode: string
  materialName: string
  supplierMaterialName?: string
  materialSpec?: string
  unit: string
  purchaseUnit?: string
  priceUnit?: string
  purchaseQuantity?: number | null
  priceQuantity?: number | null
  rollCount?: number | null
  rollLengthM?: number | null
  rollWidthMm?: number | null
  pieceWidthMm?: number | null
  pieceLengthMm?: number | null
  equivalentUnitPrice?: number | null
  pricingError?: string
  pricingLoading?: boolean
  defaultLoading?: boolean
  defaultError?: boolean
  archiveWidthMm?: number | null
  archiveLengthMm?: number | null
  defaultConfigured?: boolean
  allowedUnits?: string[]
  quantity: number
  unitPrice: number
  taxRate?: number | null
  taxAmount?: number
  amount: number
  purchaseRemark?: string
  inspectionRemark?: string
}
export const getPurchaseUnitOptions = () => request.get<{ data: PurchaseUnitOption[] }>('/purchase/order/unit-options')
export const getPurchaseMaterialDefault = (supplierId: number, materialId: number) =>
  request.get<{ data: PurchaseMaterialDefault }>('/purchase/order/material-default', { params: { supplierId, materialId } })
export const savePurchaseMaterialDefault = (data: { supplierId: number; materialId: number; purchaseUnit: string; priceUnit: string }) =>
  request.put('/purchase/order/material-default', data)

/** 仅格式化后端结果，不计算计价数量、税或金额。 */
export function entryNumber(value: unknown, maximumFractionDigits = 4): string {
  if (value == null || value === '') return '—'
  const n = Number(value)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN', { maximumFractionDigits }) : '—'
}
export const entryPrice = (value: unknown) => value == null ? '—' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })

export function entrySpecification(item: Partial<PurchaseEntryItem>): string {
  if ((item.purchaseUnit === 'ROLL' || item.priceUnit === 'ROLL') && Number(item.rollWidthMm) > 0 && Number(item.rollLengthM) > 0)
    return `${entryNumber(item.rollWidthMm)}mm×${entryNumber(item.rollLengthM)}m/卷`
  return item.materialSpec || '—'
}

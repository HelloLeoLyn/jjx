import request from '@/utils/request'
import { PurchaseProcessingOperationEnum, PurchaseProcessingDeliveryEnum } from '@/enums/purchase/processing'
export type ProcessingDelivery = typeof PurchaseProcessingDeliveryEnum.INBOUND.value | typeof PurchaseProcessingDeliveryEnum.SCRAP.value

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
  processingPlan?: string | null
  priceTaxInclusive?: boolean
  quotedAmount?: number | null
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

export interface ProcessingOutput {
  outputId: string; nodePath?: string; materialId?: number; materialCode?: string; materialName?: string; materialSpec?: string
  unit: 'PCS' | 'ROLL'; quantity: number; widthMm?: number; lengthMm?: number; rollLengthM?: number; remark?: string
  delivery?: ProcessingDelivery; disposalReason?: string | null
}
export interface ProcessingGroup {
  groupId: string; name: string; motherRolls: number
  slits: { widthMm: number; rolls: number }[]; outputs: ProcessingOutput[]
}
export interface ProcessingTreeOperation {
  type: typeof PurchaseProcessingOperationEnum.SLIT.value | typeof PurchaseProcessingOperationEnum.CUT.value
  useQuantity: number; slits?: { widthMm: number; rolls: number }[]
  pieceWidthMm?: number; pieceLengthMm?: number; rotated?: boolean; piecesPerUnit?: number | null
}
export interface ProcessingTreeNode {
  nodeId: string; role: string; unit: string; widthMm: number; lengthMm: number; quantity: number
  materialId?: number | null; materialCode?: string | null; materialName?: string | null; bindingMessage?: string | null; remark?: string
  operation?: ProcessingTreeOperation | null; children: ProcessingTreeNode[]
  delivery?: ProcessingDelivery | null; disposalReason?: string | null
}
export interface ProcessingPlan { groups: ProcessingGroup[]; tree?: ProcessingTreeNode }
export interface ProcessingReceiptGroup {
  groupId: string; lossArea?: number; lossReason?: string
  outputs: { outputId: string; quantity: number; rollLengthM?: number }[]
}
export const previewProcessingPlan = (item: Partial<PurchaseEntryItem>) => request.post<{data: PurchaseEntryItem}>('/purchase/processing/preview', item)
export const getProcessingHistory = (itemId: number) => request.get<{data: {processingPlan: string; receipts: {groupId: string; outputId: string; quantity: number; rollLengthM?: number; settledArea: number}[]}}>(`/purchase/processing/item/${itemId}`)
export const previewProcessingReceipt = (itemId: number, groups: ProcessingReceiptGroup[]) => request.post<{data: {receivedQuantity: number; items: {materialCode: string; materialName: string; specification: string; quantity: number; unit: string; amount: number; remark: string}[]; disposals?: {outputId:string;quantity:number;scrapArea:number;lossReason:string;netCost:number}[]}}>('/purchase/processing/receipt-preview', {itemId,groups})
export function readProcessingPlan(value?: string | null): ProcessingPlan | undefined {
  if (!value) return undefined
  try { const plan = JSON.parse(value); if (Array.isArray(plan?.groups) || (plan?.tree && typeof plan.tree === 'object')) return plan } catch { /* 显示无效提示，不能把损坏快照当空方案。 */ }
  return undefined
}

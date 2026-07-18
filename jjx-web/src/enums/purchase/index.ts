// src/enums/purchase/index.ts
import { PurchaseOrderTypeEnum, UrgentFlagEnum } from './order'
import { SupplierTypeEnum, SupplierStatusEnum } from './supplier'
import { InvoiceTypeEnum, InvoiceStatusEnum } from './invoice'
import { ReceiptStatusEnum, InspectionResultEnum } from './receipt'
import { DocumentTypeEnum } from './document'
import { PaymentStatusEnum, PaymentMethodEnum, CurrencyEnum, ApprovalStatusEnum } from './payment'
import { InquiryStatusEnum } from './inquiry'
import { DeliveryMethodEnum } from './delivery'

// 重新导出统一对象
export {
  PurchaseOrderTypeEnum,
  UrgentFlagEnum,
  SupplierTypeEnum,
  SupplierStatusEnum,
  InvoiceTypeEnum,
  InvoiceStatusEnum,
  ReceiptStatusEnum,
  InspectionResultEnum,
  DocumentTypeEnum,
  PaymentStatusEnum,
  PaymentMethodEnum,
  CurrencyEnum,
  ApprovalStatusEnum,
  InquiryStatusEnum,
  DeliveryMethodEnum,
}

/**
 * 采购模块所有枚举的统一导出对象
 */
export const PurchaseEnum = {
  orderType: PurchaseOrderTypeEnum,
  urgentFlag: UrgentFlagEnum,
  approvalStatus: ApprovalStatusEnum,
  receiptStatus: ReceiptStatusEnum,
  paymentStatus: PaymentStatusEnum,
  supplierType: SupplierTypeEnum,
  supplierStatus: SupplierStatusEnum,
  invoiceType: InvoiceTypeEnum,
  invoiceStatus: InvoiceStatusEnum,
  inspectionResult: InspectionResultEnum,
  documentType: DocumentTypeEnum,
  paymentMethod: PaymentMethodEnum,
  currency: CurrencyEnum,
  inquiryStatus: InquiryStatusEnum,
  deliveryMethod: DeliveryMethodEnum,
}

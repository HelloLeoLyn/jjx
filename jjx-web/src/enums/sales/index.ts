// src/enums/sales/index.ts
import { OrderEnum } from './OrderEnum'
import { QuotationStatusEnum } from './QuotationEnum'
import { SampleOrderStatusEnum } from './SampleEnum'
import { InquiryStatusEnum } from './InquiryEnum'

// 重新导出所有内容
export * from './OrderEnum'
export * from './QuotationEnum'
export * from './SampleEnum'
export * from './InquiryEnum'
export * from './FinanceDocumentEnum'

// 重新导出统一对象
export { OrderEnum, QuotationStatusEnum, SampleOrderStatusEnum, InquiryStatusEnum }

/**
 * 销售模块所有枚举的统一导出对象
 */
export const SalesEnum = {
  order: OrderEnum,
  quotation: QuotationStatusEnum,
  sample: SampleOrderStatusEnum,
  inquiry: InquiryStatusEnum,
}

// src/views/sales/inquiry/components/types.ts
export type InquiryDisplayMode = 'dialog' | 'inline'

/** 询价单表单数据 */
export interface InquiryFormData {
  inquiryId?: number
  customerId?: number
  customerName: string
  contactPerson: string
  contactPhone: string
  inquiryDate: string
  expectedQuantity?: number
  unitPrice?: number
  productId?: number
  productCode: string
  productName: string
  customerShortName: string
  productDescription: string
  hasDrawing: number
  inquiryStatus: number
  inquiryType: 1 | 2
  remark: string
  salesPersonId?: number
  salesPersonName: string
}

/** 组件可注入的 API */
export interface InquiryApiLike {
  getInfo: (id: number) => Promise<any>
  add: (payload: any) => Promise<any>
  edit: (payload: any) => Promise<any>
}

/** 空表单工厂 */
export function createEmptyInquiryForm(): InquiryFormData {
  return {
    inquiryId: undefined,
    customerId: undefined,
    customerName: '',
    contactPerson: '',
    contactPhone: '',
    inquiryDate: '',
    expectedQuantity: undefined,
    unitPrice: undefined,
    productId: undefined,
    productCode: '',
    productName: '',
    customerShortName: '',
    productDescription: '',
    hasDrawing: 0,
    inquiryStatus: 0,
    inquiryType: 1,
    remark: '',
    salesPersonId: undefined,
    salesPersonName: '',
  }
}

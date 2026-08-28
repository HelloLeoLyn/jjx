import request from '@/utils/request'

export interface SalesReceipt {
  receiptId: number; receiptNo: string; invoiceId?: number; orderId?: number; customerName?: string
  receiptDate?: string; paymentMethod?: number; receiptAmount?: number; actualAmount?: number
  currency?: string; status: number; remark?: string
}
export interface SalesReceiptQuery { pageNum: number; pageSize: number; receiptNo?: string; customerName?: string; startDate?: string; endDate?: string; status?: number }

export const salesReceiptApi = {
  page: (params: SalesReceiptQuery) => request.get('/sales/receipt/page', { params }),
  detail: (id: number) => request.get(`/sales/receipt/${id}`),
  printLog: (id: number) => request.post(`/sales/receipt/${id}/print-log`),
}

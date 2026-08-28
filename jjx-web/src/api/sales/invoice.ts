import request from '@/utils/request'

export interface SalesInvoice {
  invoiceId: number; invoiceNo: string; orderId?: number; customerName?: string; invoiceDate?: string
  taxpayerId?: string; address?: string; phone?: string; bankName?: string; bankAccount?: string
  invoiceAmount?: number; taxAmount?: number; totalAmount?: number; status: number; remark?: string
}
export interface SalesInvoiceQuery { pageNum: number; pageSize: number; invoiceNo?: string; customerName?: string; startDate?: string; endDate?: string; status?: number }

export const salesInvoiceApi = {
  page: (params: SalesInvoiceQuery) => request.get('/sales/invoice/page', { params }),
  detail: (id: number) => request.get(`/sales/invoice/${id}`),
  printLog: (id: number) => request.post(`/sales/invoice/${id}/print-log`),
}

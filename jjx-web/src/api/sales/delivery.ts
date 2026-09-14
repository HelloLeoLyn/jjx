import request from '@/utils/request'
import type { R } from '@/types'

export interface SalesDeliveryVO {
  deliveryId: number
  deliveryNo: string
  orderId: number
  customerId: number
  customerName: string
  deliveryDate: string
  deliveryAddress: string
  contactPerson: string
  contactPhone: string
  deliveryMethod: string
  trackingNo: string
  carrier: string
  deliveryStatus: number
  deliveryStatusDesc: string
  totalQuantity: number
  totalWeight: number
  freightAmount: number
  totalAmount: number
  remark: string
  deliveryPersonName: string
  receiverName: string
  receiverPhone: string
  receiveTime: string
  receiveRemark: string
  /** 打印次数（口径 D3：来自 quality_template_print_log 聚合） */
  printCount?: number
  /** 最近打印时间 */
  lastPrintTime?: string
  /** 最近打印人 */
  lastPrintBy?: string
}

export interface SalesDeliveryQueryDTO {
  orderId?: number
  deliveryNo?: string
  customerName?: string
  deliveryStatus?: number
  deliveryDateStart?: string
  deliveryDateEnd?: string
  pageNum?: number
  pageSize?: number
}

export interface SalesDeliveryReceiveDTO {
  receiverName?: string
  receiverPhone?: string
  receiveRemark?: string
}

export interface SalesDeliveryCreateDTO {
  deliveryMethod?: string
  contactPerson?: string
  contactPhone?: string
  deliveryAddress?: string
  carrier?: string
  trackingNo?: string
  remark?: string
  deliveryDate?: string
}

export const deliveryApi = {
  /** 分页查询发货单 */
  list(params: SalesDeliveryQueryDTO) {
    return request.get<R<{ records: SalesDeliveryVO[]; total: number }>>('/sales/deliveries', { params })
  },

  /** 查询发货单详情 */
  getById(deliveryId: number) {
    return request.get<R<SalesDeliveryVO>>(`/sales/deliveries/${deliveryId}`)
  },

  /** 根据销售订单ID查询发货单 */
  listByOrderId(orderId: number) {
    return request.get<R<SalesDeliveryVO[]>>(`/sales/deliveries/by-order/${orderId}`)
  },

  /** 签收发货单 */
  receive(deliveryId: number, data: SalesDeliveryReceiveDTO) {
    return request.put<R<void>>(`/sales/deliveries/${deliveryId}/receive`, data)
  },

  /** 记录送货单打印留痕（口径 D3：biz_type=sales_delivery + biz_id=deliveryId） */
  printLog(deliveryId: number) {
    return request.post<R<void>>(`/sales/deliveries/${deliveryId}/print-log`)
  },

}

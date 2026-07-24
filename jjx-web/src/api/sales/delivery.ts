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
}

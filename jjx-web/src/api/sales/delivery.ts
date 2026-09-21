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
  insuranceAmount?: number
  otherCharges?: number
  totalAmount: number
  remark: string
  deliveryPersonName: string
  receiverName: string
  receiverPhone: string
  receiveTime: string
  /** 客户签收日期（纸面，口径 D1） */
  customerReceiveDate?: string
  receiveRemark: string
  /** 打印次数（口径 D3：来自 quality_template_print_log 聚合） */
  printCount?: number
  /** 最近打印时间 */
  lastPrintTime?: string
  /** 最近打印人 */
  lastPrintBy?: string
  /** 本次发货明细（分批发货） */
  items?: SalesDeliveryItem[]
  /** 拒收原因 */
  rejectReason?: string
  /** 拒收登记时间 */
  rejectTime?: string
  /** 拒收登记人 */
  rejectName?: string
}

/** 发货明细（分批发货，2026-09-21 dev-20260921-039） */
export interface SalesDeliveryItem {
  itemId?: number
  deliveryId?: number
  /** 销售订单明细ID（sales_order_product.id） */
  orderProductId?: number
  productId?: number
  productCode?: string
  productName?: string
  specification?: string
  unit?: string
  /** 本次发货数量 */
  quantity?: number
  unitPrice?: number
  amount?: number
  remark?: string
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
  /** 客户签收日期（纸面，口径 D1；与系统登记时间区分） */
  customerReceiveDate?: string
  receiveRemark?: string
}

export interface SalesDeliveryCreateDTO {
  /** 运费（快递/物流填，2026-09-21） */
  freightAmount?: number
  /** 保价费 */
  insuranceAmount?: number
  /** 其他费用 */
  otherCharges?: number
  /** 本次发货明细（不传=按未发数量全发，向后兼容） */
  items?: SalesDeliveryItem[]
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

  /** 客户拒收登记（自动回冲库存、订单可重发，2026-09-21 dev-20260921-039） */
  reject(deliveryId: number, reason: string) {
    return request.post<R<void>>(`/sales/deliveries/${deliveryId}/reject`, { reason })
  },

  /** 记录送货单打印留痕（口径 D3：biz_type=sales_delivery + biz_id=deliveryId） */
  printLog(deliveryId: number) {
    return request.post<R<void>>(`/sales/deliveries/${deliveryId}/print-log`)
  },

}

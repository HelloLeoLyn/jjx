import request from '@/utils/request'

/** 分页查询退货单 */
export function pageSalesReturn(params?: Record<string, unknown>) {
  return request({
    url: '/sales/returns/page',
    method: 'get',
    params,
  })
}

/** 退货单详情 */
export function getSalesReturn(returnId: number) {
  return request({
    url: `/sales/returns/${returnId}`,
    method: 'get',
  })
}

/** 创建退货单 */
export function createSalesReturn(data: Record<string, unknown>) {
  return request({
    url: '/sales/returns',
    method: 'post',
    data,
  })
}

/** 审核通过 */
export function approveSalesReturn(returnId: number, approverName?: string, approveRemark?: string) {
  return request({
    url: `/sales/returns/${returnId}/approve`,
    method: 'put',
    params: { approverName, approveRemark },
  })
}

/** 审核驳回 */
export function rejectSalesReturn(returnId: number, approverName?: string, approveRemark?: string) {
  return request({
    url: `/sales/returns/${returnId}/reject`,
    method: 'put',
    params: { approverName, approveRemark },
  })
}

/** 收货确认（联动退货入库） */
export function receiveSalesReturn(returnId: number, receiverName?: string, remark?: string) {
  return request({
    url: `/sales/returns/${returnId}/receive`,
    method: 'put',
    params: { receiverName, remark },
  })
}

/** 退款（回写订单付款状态） */
export function refundSalesReturn(returnId: number, refundAmount?: number, refundName?: string) {
  return request({
    url: `/sales/returns/${returnId}/refund`,
    method: 'put',
    params: { refundAmount, refundName },
  })
}

/** 退货单明细列表 */
export function getSalesReturnItems(returnId: number) {
  return request({
    url: `/sales/returns/${returnId}/items`,
    method: 'get',
  })
}

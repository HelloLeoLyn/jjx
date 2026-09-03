import request from '@/utils/request'

/** 对账汇总（客户+期间：送货明细 + 回款合计） */
export function getReconciliation(params: { customerId: number; startDate?: string; endDate?: string }) {
  return request({
    url: '/sales/reconciliation',
    method: 'get',
    params,
  })
}

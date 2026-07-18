import request from '@/utils/request'

export interface OrderItem {
  itemNo: number // 项次
  materialName: string // 品名
  specification: string // 规格
  unit: string // 单位
  quantity: number // 数量
  unitPrice: number // 单价
  amount: number // 金额
  remark: string // 备注
}

export interface OrderData {
  orderNo: string // 订单号码
  orderDate: string // 订货时间
  deliveryDate: string // 交货时间
  supplierName: string // 厂商名称
  supplierContact: string // 联系人
  supplierTel: string // 电话
  tradeType: 'RMB' | 'monthly' // 交易方式
  items: OrderItem[] // 订单明细
  totalAmount: number // 合计金额
}

export default {
  // 导出Excel
  exportExcel(data: OrderData) {
    return request.post('/order/export', data, {
      responseType: 'blob',
    })
  },
}

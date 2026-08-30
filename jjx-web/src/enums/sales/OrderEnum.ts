// src/enums/sales/OrderEnum.ts
import { createEnum } from '../base'

/**
 * 销售订单状态枚举
 * 对应后端 SalesOrderStatusEnum
 */
export const SalesOrderStatusEnum = createEnum<number>({
  items: [
    { value: 1, label: '草稿', tagProps: { type: 'info' } },
    { value: 2, label: '待审核', tagProps: { type: 'warning' } },
    { value: 3, label: '审核中', tagProps: { type: 'warning' } },
    { value: 4, label: '已审核', tagProps: { type: 'primary' } },
    { value: 5, label: '已驳回', tagProps: { type: 'danger' } },
    { value: 6, label: '已确认', tagProps: { type: 'success' } },
    { value: 7, label: '生产中', tagProps: { type: 'warning' } },
    { value: 8, label: '已发货', tagProps: { type: 'success' } },
    { value: 9, label: '已完成', tagProps: { type: 'success' } },
    { value: 10, label: '已取消', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})
/**
 * 订单类型枚举
 * 对应后端 SalesOrderTypeEnum
 */
export const OrderTypeEnum = createEnum<number>({
  items: [
    { value: 1, label: '标准订单', tagProps: { type: 'primary' } },
    { value: 2, label: '样品订单', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 生产状态枚举
 * 对应后端 ProdStatusEnum
 */
export const ProdStatusEnum = createEnum<number>({
  items: [
    { value: 1, label: '无生产', tagProps: { type: 'info' } },
    { value: 2, label: '部分生产中', tagProps: { type: 'warning' } },
    { value: 3, label: '全部生产中', tagProps: { type: 'warning' } },
    { value: 4, label: '生产完成', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 支付状态枚举
 * 对应后端 SalesPaymentStatusEnum
 */
export const PaymentStatusEnum = createEnum<number>({
  items: [
    { value: 1, label: '未支付', tagProps: { type: 'warning' } },
    { value: 2, label: '支付中', tagProps: { type: 'info' } },
    { value: 3, label: '已支付', tagProps: { type: 'success' } },
    { value: 4, label: '部分支付', tagProps: { type: 'warning' } },
    { value: 5, label: '已退款', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 是否枚举
 * 对应后端 YesNoEnum
 */
export const YesNoEnum = createEnum<number>({
  items: [
    { value: 0, label: '否', tagProps: { type: 'info' } },
    { value: 1, label: '是', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 销售订单相关枚举统一导出
 */
export const OrderEnum = {
  status: SalesOrderStatusEnum,
  type: OrderTypeEnum,
  prodStatus: ProdStatusEnum,
  paymentStatus: PaymentStatusEnum,
  yesNo: YesNoEnum,
}

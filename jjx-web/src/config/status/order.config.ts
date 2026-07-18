export enum OrderStatus {
  PENDING_PAYMENT = 'pending_payment',
  PAID = 'paid',
  SHIPPED = 'shipped',
  DELIVERED = 'delivered',
  COMPLETED = 'completed',
  CANCELLED = 'cancelled',
}

export const orderStatusConfig = {
  [OrderStatus.PENDING_PAYMENT]: {
    label: '待付款',
    type: 'warning',
  },
  [OrderStatus.PAID]: {
    label: '已付款',
    type: 'success',
  },
  [OrderStatus.SHIPPED]: {
    label: '已发货',
    type: 'info',
  },
  [OrderStatus.DELIVERED]: {
    label: '已送达',
    type: 'success',
  },
  [OrderStatus.COMPLETED]: {
    label: '已完成',
    type: 'success',
  },
  [OrderStatus.CANCELLED]: {
    label: '已取消',
    type: 'danger',
  },
}

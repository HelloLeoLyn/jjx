import { createNamedEnum } from '../base'

/** 销售发货状态，对应后端 sales_delivery.delivery_status。 */
export const DeliveryStatusEnum = createNamedEnum(
  {
    PENDING: { value: 1, label: '待发货', tagProps: { type: 'warning' } },
    SHIPPED: { value: 2, label: '已发货', tagProps: { type: 'primary' } },
    IN_TRANSIT: { value: 3, label: '运输中', tagProps: { type: 'warning' } },
    RECEIVED: { value: 4, label: '已签收', tagProps: { type: 'success' } },
    REJECTED: { value: 5, label: '已拒收', tagProps: { type: 'danger' } },
  },
  { type: 'info' }
)

import { createNamedEnum } from '../base'

/** 销售发货状态，对应后端 sales_delivery.delivery_status。
 *
 * ⚠️ 2026-09-21（dev-20260921-039）：状态 3「运输中」已退场——
 * 全链路无写入点（无物流轨迹数据源），且无历史数据；对账/工作台原先按 (2,3) 统计已收敛为 (2)。
 * 数值 4/5 保持不变（历史数据不可重编号）。
 */
export const DeliveryStatusEnum = createNamedEnum(
  {
    PENDING: { value: 1, label: '待发货', tagProps: { type: 'warning' } },
    SHIPPED: { value: 2, label: '已发货', tagProps: { type: 'primary' } },
    RECEIVED: { value: 4, label: '已签收', tagProps: { type: 'success' } },
    REJECTED: { value: 5, label: '已拒收', tagProps: { type: 'danger' } },
  },
  { type: 'info' }
)

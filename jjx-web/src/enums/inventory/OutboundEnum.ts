// src/enums/inventory/OutboundEnum.ts
import { createEnum, createNamedEnum } from '../base'
import { ApproveStatusEnum } from './InboundEnum'

/**
 * 出库类型枚举
 */
export const OutboundTypeEnum = createEnum({
  items: [
    { value: 'production', label: '生产领料', tagProps: { type: 'primary' } },
    { value: 'sample', label: '打样领料', tagProps: { type: 'primary' } },
    { value: 'sales', label: '销售出库', tagProps: { type: 'success' } },
    { value: 'SALES_SHIP', label: '销售发货出库', tagProps: { type: 'success' } },
    { value: 'return', label: '退货出库', tagProps: { type: 'warning' } },
    { value: 'scrap', label: '报废出库', tagProps: { type: 'danger' } },
    { value: 'transfer', label: '调拨出库', tagProps: { type: 'info' } },
    { value: 'adjust', label: '盘亏出库', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 出库单状态枚举
 */
export const OutboundOrderStatusEnum = createNamedEnum(
  {
    DRAFT: { value: 0, label: '草稿', tagProps: { type: 'info' } },
    PENDING: { value: 1, label: '待审核', tagProps: { type: 'warning' } },
    APPROVED: { value: 2, label: '已审核', tagProps: { type: 'success' } },
    REJECTED: { value: 3, label: '已驳回', tagProps: { type: 'danger' } },
    PROCESSING: { value: 4, label: '处理中', tagProps: { type: 'warning' } },
    CONFIRMED: { value: 5, label: '已确认', tagProps: { type: 'primary' } },
    OUT_CONFIRM: { value: 6, label: '已出库', tagProps: { type: 'success' } },
    IN_CONFIRM: { value: 7, label: '已入库', tagProps: { type: 'success' } },
    CLOSED: { value: 8, label: '已关闭', tagProps: { type: 'info' } },
    CANCELLED: { value: 9, label: '已取消', tagProps: { type: 'danger' } },
    COMPLETED: { value: 10, label: '已完成', tagProps: { type: 'success' } },
    PROCESSED: { value: 11, label: '已处理', tagProps: { type: 'success' } },
    IN_PROGRESS: { value: 12, label: '调拨中', tagProps: { type: 'warning' } },
  },
  { type: 'info' }
)

/**
 * 出库相关枚举统一导出
 */
export const OutboundEnum = {
  type: OutboundTypeEnum,
  orderStatus: OutboundOrderStatusEnum,
  approveStatus: ApproveStatusEnum,
}

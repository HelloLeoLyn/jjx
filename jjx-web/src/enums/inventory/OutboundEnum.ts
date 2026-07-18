// src/enums/inventory/OutboundEnum.ts
import { createEnum } from '../base'
import { ApproveStatusEnum } from './InboundEnum'

/**
 * 出库类型枚举
 */
export const OutboundTypeEnum = createEnum({
  items: [
    { value: 'production', label: '生产领料', tagProps: { type: 'primary' } },
    { value: 'sales', label: '销售出库', tagProps: { type: 'success' } },
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
export const OutboundOrderStatusEnum = createEnum({
  items: [
    { value: 'draft', label: '草稿', tagProps: { type: 'info' } },
    { value: 'confirmed', label: '已确认', tagProps: { type: 'primary' } },
    { value: 'closed', label: '已关闭', tagProps: { type: 'success' } },
    { value: 'cancelled', label: '已取消', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 出库相关枚举统一导出
 */
export const OutboundEnum = {
  type: OutboundTypeEnum,
  orderStatus: OutboundOrderStatusEnum,
  approveStatus: ApproveStatusEnum,
}

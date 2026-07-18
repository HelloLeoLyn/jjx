// src/enums/inventory/TransferEnum.ts
import { createEnum } from '../base'
import { ApproveStatusEnum } from './InboundEnum'

/**
 * 调拨类型枚举
 */
export const TransferTypeEnum = createEnum({
  items: [
    { value: 'normal', label: '普通调拨', tagProps: { type: 'primary' } },
    { value: 'urgent', label: '紧急调拨', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 调拨单状态枚举
 */
export const TransferOrderStatusEnum = createEnum({
  items: [
    { value: 'draft', label: '草稿', tagProps: { type: 'info' } },
    { value: 'approved', label: '已批准', tagProps: { type: 'primary' } },
    { value: 'out_confirm', label: '已出库', tagProps: { type: 'warning' } },
    { value: 'in_confirm', label: '已入库', tagProps: { type: 'success' } },
    { value: 'closed', label: '已关闭', tagProps: { type: 'success' } },
    { value: 'cancelled', label: '已取消', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 调拨明细状态枚举
 */
export const TransferItemStatusEnum = createEnum({
  items: [
    { value: 'pending', label: '待处理', tagProps: { type: 'warning' } },
    { value: 'partial', label: '部分完成', tagProps: { type: 'info' } },
    { value: 'completed', label: '已完成', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 调拨相关枚举统一导出
 */
export const TransferEnum = {
  type: TransferTypeEnum,
  orderStatus: TransferOrderStatusEnum,
  approveStatus: ApproveStatusEnum,
  itemStatus: TransferItemStatusEnum,
}

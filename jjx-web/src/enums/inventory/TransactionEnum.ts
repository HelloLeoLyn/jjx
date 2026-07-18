// src/enums/inventory/TransactionEnum.ts
import { createEnum } from '../base'

/**
 * 交易类型枚举
 */
export const TransactionTypeEnum = createEnum({
  items: [
    { value: 'inbound', label: '入库', tagProps: { type: 'success' } },
    { value: 'outbound', label: '出库', tagProps: { type: 'warning' } },
    { value: 'transfer_in', label: '调拨入库', tagProps: { type: 'info' } },
    { value: 'transfer_out', label: '调拨出库', tagProps: { type: 'info' } },
    { value: 'adjust', label: '盘盈盘亏', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 来源类型枚举
 */
export const SourceTypeEnum = createEnum({
  items: [
    { value: 'purchase_order', label: '采购订单', tagProps: { type: 'primary' } },
    { value: 'work_order', label: '生产工单', tagProps: { type: 'warning' } },
    { value: 'sales_order', label: '销售订单', tagProps: { type: 'success' } },
    { value: 'stocktake', label: '盘点单', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 交易相关枚举统一导出
 */
export const TransactionEnum = {
  type: TransactionTypeEnum,
  sourceType: SourceTypeEnum,
}

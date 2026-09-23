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
    { value: 'purchase', label: '采购入库', tagProps: { type: 'primary' } },
    { value: 'production', label: '生产工单', tagProps: { type: 'warning' } },
    { value: 'sales', label: '销售订单', tagProps: { type: 'success' } },
    { value: 'stocktake', label: '盘点单', tagProps: { type: 'info' } },
    { value: 'transfer', label: '调拨单', tagProps: { type: 'info' } },
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

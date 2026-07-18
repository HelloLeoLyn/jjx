// src/enums/inventory/StockItemEnum.ts
import { createEnum } from '../base'

/**
 * 库存批次明细状态枚举
 * 0=未生效，1=生效
 */
export const StockItemStatusEnum = createEnum({
  items: [
    { value: 0, label: '未生效', tagProps: { type: 'info' } },
    { value: 1, label: '生效', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 库存批次明细相关枚举统一导出
 */
export const StockItemEnum = {
  status: StockItemStatusEnum,
}

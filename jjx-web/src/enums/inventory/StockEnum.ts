// src/enums/inventory/StockEnum.ts
import { createEnum } from '../base'

/**
 * 库存状态枚举
 */
export const StockStatusEnum = createEnum({
  items: [
    { value: 'active', label: '正常', tagProps: { type: 'success' } },
    { value: 'frozen', label: '冻结', tagProps: { type: 'warning' } },
    { value: 'expired', label: '过期', tagProps: { type: 'danger' } },
    { value: 'scrap', label: '报废', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 库存相关枚举统一导出
 */
export const StockEnum = {
  status: StockStatusEnum,
}

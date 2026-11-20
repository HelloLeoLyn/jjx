// src/enums/inventory/StocktakeEnum.ts
import { createEnum, createNamedEnum } from '../base'
import { ApproveStatusEnum } from './InboundEnum'

/**
 * 盘点类型枚举
 */
export const StocktakeTypeEnum = createEnum({
  items: [
    { value: 'full', label: '全盘', tagProps: { type: 'primary' } },
    { value: 'partial', label: '抽盘', tagProps: { type: 'warning' } },
    { value: 'cycle', label: '循环盘点', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 盘点单状态枚举
 */
export const StocktakeOrderStatusEnum = createEnum({
  items: [
    { value: 'draft', label: '草稿', tagProps: { type: 'info' } },
    { value: 'processing', label: '盘点中', tagProps: { type: 'warning' } },
    { value: 'closed', label: '已关闭', tagProps: { type: 'success' } },
    { value: 'cancelled', label: '已取消', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 调整状态枚举
 */
export const AdjustStatusEnum = createEnum({
  items: [
    { value: 'pending', label: '待处理', tagProps: { type: 'warning' } },
    { value: 'processed', label: '已处理', tagProps: { type: 'success' } },
    { value: 'skipped', label: '已跳过', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 盘点相关枚举统一导出
 */
export const StocktakeEnum = {
  type: StocktakeTypeEnum,
  orderStatus: StocktakeOrderStatusEnum,
  approveStatus: ApproveStatusEnum,
  adjustStatus: AdjustStatusEnum,
}

/** 快捷盘点复用后端InventoryOrderStatusEnum数值，不影响普通盘点旧枚举。 */
export const QuickStocktakeStatusEnum = createNamedEnum({
  CONFIRMED: { value: 5, label: '待确认过账', tagProps: { type: 'warning' } },
  CANCELLED: { value: 9, label: '已作废', tagProps: { type: 'info' } },
  PROCESSED: { value: 11, label: '已生效', tagProps: { type: 'success' } },
}, { type: 'info' })

// src/enums/inventory/AlertEnum.ts
import { createEnum } from '../base'

/**
 * 预警类型枚举
 */
export const AlertTypeEnum = createEnum({
  items: [
    { value: 'safe_stock', label: '安全库存', tagProps: { type: 'warning' } },
    { value: 'max_stock', label: '最高库存', tagProps: { type: 'warning' } },
    { value: 'expiry', label: '保质期', tagProps: { type: 'danger' } },
    { value: 'obsolete', label: '呆滞料', tagProps: { type: 'info' } },
    { value: 'order_shortage', label: '订单缺料', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 预警级别枚举
 */
export const AlertLevelEnum = createEnum({
  items: [
    { value: 'info', label: '提示', tagProps: { type: 'info' } },
    { value: 'warning', label: '警告', tagProps: { type: 'warning' } },
    { value: 'urgent', label: '紧急', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 预警处理状态枚举
 */
export const AlertStatusEnum = createEnum({
  items: [
    { value: 'new', label: '新预警', tagProps: { type: 'warning' } },
    { value: 'read', label: '已读', tagProps: { type: 'info' } },
    { value: 'processed', label: '已处理', tagProps: { type: 'success' } },
    { value: 'ignored', label: '已忽略', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 预警相关枚举统一导出
 */
export const AlertEnum = {
  type: AlertTypeEnum,
  level: AlertLevelEnum,
  status: AlertStatusEnum,
}

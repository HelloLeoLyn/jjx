// src/enums/inventory/LocationEnum.ts
import { createEnum } from '../base'

/**
 * 库位类型枚举
 */
export const LocationTypeEnum = createEnum({
  items: [
    { value: 'normal', label: '普通库位', tagProps: { type: 'primary' } },
    { value: 'frozen', label: '冷冻库位', tagProps: { type: 'info' } },
    { value: 'flammable', label: '易燃库位', tagProps: { type: 'danger' } },
    { value: 'valuable', label: '贵重库位', tagProps: { type: 'warning' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 库位状态枚举
 */
export const LocationStatusEnum = createEnum({
  items: [
    { value: '0', label: '正常', tagProps: { type: 'success' } },
    { value: '1', label: '停用', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 库位相关枚举统一导出
 */
export const LocationEnum = {
  type: LocationTypeEnum,
  status: LocationStatusEnum,
}

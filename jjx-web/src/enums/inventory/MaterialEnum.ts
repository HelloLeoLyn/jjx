// src/enums/inventory/MaterialEnum.ts
import { createEnum } from '../base'

/**
 * 物料类型枚举
 * 对应后端 MaterialEnums.MaterialType
 */
export const MaterialTypeEnum = createEnum({
  items: [
    { value: 'R', label: '原材料', tagProps: { type: 'danger' } },
    { value: 'S', label: '半成品', tagProps: { type: 'warning' } },
    { value: 'F', label: '成品', tagProps: { type: 'success' } },
    { value: 'A', label: '辅助材料', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 物料状态枚举
 * 对应后端 MaterialEnums.MaterialStatus
 */
export const MaterialStatusEnum = createEnum({
  items: [
    { value: 1, label: '启用', tagProps: { type: 'success' } },
    { value: 0, label: '停用', tagProps: { type: 'danger' } },
    { value: 2, label: '废弃', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 批次管理枚举
 * 对应后端 MaterialEnums.BatchControl
 */
export const BatchControlEnum = createEnum({
  items: [
    { value: 1, label: '启用', tagProps: { type: 'success' } },
    { value: 0, label: '禁用', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 物料相关枚举统一导出
 */
export const MaterialEnum = {
  type: MaterialTypeEnum,
  status: MaterialStatusEnum,
  batchControl: BatchControlEnum,
}

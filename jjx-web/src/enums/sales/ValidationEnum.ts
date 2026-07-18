// src/enums/sales/ValidationEnum.ts
import { createEnum } from '../base'

/**
 * 验证结果级别枚举
 * 对应后端 ValidationLevelEnum
 */
export const ValidationLevelEnum = createEnum<number>({
  items: [
    { value: 1, label: '错误', tagProps: { type: 'danger' } },
    { value: 2, label: '警告', tagProps: { type: 'warning' } },
    { value: 3, label: '提示', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 验证类型枚举
 * 对应后端 ValidationTypeEnum
 */
export const ValidationTypeEnum = createEnum<number>({
  items: [
    { value: 1, label: '产品验证', tagProps: { type: 'primary' } },
    { value: 2, label: 'BOM验证', tagProps: { type: 'success' } },
    { value: 3, label: '工艺路线验证', tagProps: { type: 'warning' } },
    { value: 4, label: '生产能力验证', tagProps: { type: 'info' } },
    { value: 5, label: '成本验证', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 验证状态枚举
 * 对应后端 ValidationStatusEnum
 */
export const ValidationStatusEnum = createEnum<number>({
  items: [
    { value: 1, label: '未验证', tagProps: { type: 'info' } },
    { value: 2, label: '验证中', tagProps: { type: 'warning' } },
    { value: 3, label: '验证通过', tagProps: { type: 'success' } },
    { value: 4, label: '验证失败', tagProps: { type: 'danger' } },
    { value: 5, label: '部分通过', tagProps: { type: 'warning' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 修复状态枚举
 * 对应后端 FixStatusEnum
 */
export const FixStatusEnum = createEnum<number>({
  items: [
    { value: 1, label: '未修复', tagProps: { type: 'info' } },
    { value: 2, label: '修复中', tagProps: { type: 'warning' } },
    { value: 3, label: '已修复', tagProps: { type: 'success' } },
    { value: 4, label: '无法修复', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 验证相关枚举统一导出
 */
export const ValidationEnum = {
  level: ValidationLevelEnum,
  type: ValidationTypeEnum,
  status: ValidationStatusEnum,
  fixStatus: FixStatusEnum,
}

// src/enums/system/RoleEnum.ts
import { createEnum } from '../base'

/**
 * 角色状态枚举
 */
export const RoleStatusEnum = createEnum({
  items: [
    { value: '0', label: '启用', tagProps: { type: 'success' } },
    { value: '1', label: '停用', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 角色类型枚举
 */
export const RoleTypeEnum = createEnum({
  items: [
    { value: 'system', label: '系统角色', tagProps: { type: 'primary' } },
    { value: 'custom', label: '自定义角色', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 角色相关枚举统一导出
 */
export const RoleEnum = {
  status: RoleStatusEnum,
  type: RoleTypeEnum,
}

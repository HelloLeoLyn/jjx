// src/enums/system/UserEnum.ts
import { createEnum } from '../base'

/**
 * 用户状态枚举
 */
export const UserStatusEnum = createEnum({
  items: [
    { value: '0', label: '启用', tagProps: { type: 'success' } },
    { value: '1', label: '停用', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 用户性别枚举
 */
export const UserGenderEnum = createEnum({
  items: [
    { value: '0', label: '男', tagProps: { type: 'primary' } },
    { value: '1', label: '女', tagProps: { type: 'danger' } },
    { value: '2', label: '未知', tagProps: { type: 'info' } },
    { value: '3', label: '保密', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 用户类型枚举
 */
export const UserTypeEnum = createEnum({
  items: [
    { value: 'admin', label: '管理员', tagProps: { type: 'danger' } },
    { value: 'user', label: '普通用户', tagProps: { type: 'primary' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 用户相关枚举统一导出
 */
export const UserEnum = {
  status: UserStatusEnum,
  gender: UserGenderEnum,
  type: UserTypeEnum,
}

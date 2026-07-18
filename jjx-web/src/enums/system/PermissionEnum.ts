// src/enums/system/PermissionEnum.ts
import { createEnum } from '../base'

/**
 * 权限类型枚举
 */
export const PermissionTypeEnum = createEnum({
  items: [
    { value: 'menu', label: '菜单', tagProps: { type: 'primary' } },
    { value: 'button', label: '按钮', tagProps: { type: 'success' } },
    { value: 'api', label: '接口', tagProps: { type: 'info' } },
    { value: 'data', label: '数据', tagProps: { type: 'warning' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 权限状态枚举
 */
export const PermissionStatusEnum = createEnum({
  items: [
    { value: '0', label: '启用', tagProps: { type: 'success' } },
    { value: '1', label: '停用', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 菜单类型枚举
 */
export const MenuTypeEnum = createEnum({
  items: [
    { value: 'M', label: '目录', tagProps: { type: 'primary' } },
    { value: 'C', label: '菜单', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 显示状态枚举
 */
export const VisibleEnum = createEnum({
  items: [
    { value: '0', label: '显示', tagProps: { type: 'success' } },
    { value: '1', label: '隐藏', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 权限相关枚举统一导出
 */
export const PermissionEnum = {
  type: PermissionTypeEnum,
  status: PermissionStatusEnum,
  menuType: MenuTypeEnum,
  visible: VisibleEnum,
}

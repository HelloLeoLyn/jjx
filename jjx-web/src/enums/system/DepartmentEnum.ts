// src/enums/system/DepartmentEnum.ts
import { createEnum } from '../base'

/**
 * 部门类型枚举
 */
export const DepartmentTypeEnum = createEnum({
  items: [
    { value: 'company', label: '公司', tagProps: { type: 'primary' } },
    { value: 'branch', label: '分公司', tagProps: { type: 'success' } },
    { value: 'department', label: '部门', tagProps: { type: 'info' } },
    { value: 'team', label: '小组', tagProps: { type: 'warning' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 部门状态枚举
 */
export const DepartmentStatusEnum = createEnum({
  items: [
    { value: '0', label: '启用', tagProps: { type: 'success' } },
    { value: '1', label: '停用', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 组织层级枚举
 */
export const OrgLevelEnum = createEnum({
  items: [
    { value: 'company', label: '公司级', tagProps: { type: 'primary' } },
    { value: 'department', label: '部门级', tagProps: { type: 'success' } },
    { value: 'position', label: '岗位级', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 部门相关枚举统一导出
 */
export const DepartmentEnum = {
  type: DepartmentTypeEnum,
  status: DepartmentStatusEnum,
  level: OrgLevelEnum,
}

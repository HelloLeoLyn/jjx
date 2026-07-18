// src/enums/system/index.ts
import { UserEnum } from './UserEnum'
import { RoleEnum } from './RoleEnum'
import { DepartmentEnum } from './DepartmentEnum'
import { PermissionEnum } from './PermissionEnum'
import { LogEnum } from './LogEnum'
import { NoticeEnum } from './NoticeEnum'
import { FileEnum } from './FileEnum'

// 重新导出所有内容
export * from './UserEnum'
export * from './RoleEnum'
export * from './DepartmentEnum'
export * from './PermissionEnum'
export * from './LogEnum'
export * from './NoticeEnum'
export * from './FileEnum'

// 重新导出统一对象
export {
  UserEnum,
  RoleEnum,
  DepartmentEnum,
  PermissionEnum,
  LogEnum,
  NoticeEnum,
  FileEnum,
}

/**
 * 系统模块所有枚举的统一导出对象
 */
export const SystemEnum = {
  user: UserEnum,
  role: RoleEnum,
  department: DepartmentEnum,
  permission: PermissionEnum,
  log: LogEnum,
  notice: NoticeEnum,
  file: FileEnum,
}

/** 员工档案查询参数（人事管理 P0） */
export interface HrEmployeeQuery {
  pageNum?: number
  pageSize?: number
  /** 姓名 / 工号 / 手机号 */
  keyword?: string
  deptId?: number
  /** 岗位（字典 hr_position 的 item_key） */
  position?: string
  /** 1试用 2正式 3停薪留职 9离职 */
  employmentStatus?: number
  onlyLinked?: boolean
}

/** 员工档案展示对象 */
export interface HrEmployeeVO {
  empId: number
  empNo: string
  name: string
  /** 1男 2女 */
  sex?: number
  deptId?: number
  deptName?: string
  /** 岗位（字典 hr_position 的 item_key） */
  position?: string
  phone?: string
  email?: string
  hireDate?: string
  leaveDate?: string
  employmentStatus?: number
  /** 无 hr:employee:sensitive 权限时返回脱敏值（含 ****） */
  idCardNo?: string
  idCardAddress?: string
  currentAddress?: string
  education?: string
  major?: string
  resume?: string
  userId?: number
  userName?: string
  remark?: string
  /** 当前用户是否可看敏感字段明文 */
  sensitiveVisible?: boolean
  createTime?: string
  updateTime?: string
}

/** 员工档案表单 */
export interface HrEmployeeForm {
  empId?: number
  empNo?: string
  name: string
  sex?: number
  deptId?: number
  position?: string
  phone?: string
  email?: string
  hireDate?: string
  leaveDate?: string
  employmentStatus?: number
  idCardNo?: string
  idCardAddress?: string
  currentAddress?: string
  education?: string
  major?: string
  resume?: string
  userId?: number
  remark?: string
}

/** 导入结果 */
export interface HrImportResult {
  total: number
  successCount: number
  failCount: number
  errors: { rowNum: number; message: string }[]
}

/** 由员工档案生成账号的表单 */
export interface HrCreateUserForm {
  userName?: string
  password?: string
  roleIds?: number[]
  remark?: string
}

/** 生成的账号信息 */
export interface HrAccount {
  userId: number
  userName: string
  nickName?: string
  deptId?: number
  deptName?: string
  /** 未同步项提示（手机号/邮箱被占用、未分配角色等） */
  warnings?: string[]
  roleBound?: boolean
}

/** 可分配角色选项 */
export interface HrRoleOption {
  roleId: number
  roleName: string
  roleKey?: string
}

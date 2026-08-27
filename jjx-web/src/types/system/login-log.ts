/**
 * 登录日志表 sys_login_log（对齐后端 SysLoginLog 实体）
 */
export interface SysLoginLog {
  id?: number
  userId?: number
  /** 用户账号 */
  username?: string
  tenantId?: number
  /** 登录类型（如 PASSWORD / SMS 等） */
  loginType?: string
  /** 登录IP */
  loginIp?: string
  /** 登录地点 */
  loginLocation?: string
  userAgent?: string
  /** 登录时间 */
  loginTime?: string
  /** 登录状态 0失败 1成功(YesNoEnum) */
  status?: number
  /** 失败原因/提示消息 */
  failReason?: string
}

/** 登录日志查询参数 */
export interface SysLoginLogQuery {
  pageNum?: number
  pageSize?: number
  username?: string
  loginType?: string
  status?: number
}

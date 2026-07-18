/**
 * 系统登录日志表 sys_login_log
 */
export interface SysLoginLog {
  /**
   * 访问ID
   */
  loginId?: number

  /**
   * 用户账号
   */
  userName?: string

  /**
   * 用户ID
   */
  userId?: number

  /**
   * 登录IP地址
   */
  ipAddress?: string

  /**
   * 登录地点
   */
  loginLocation?: string

  /**
   * 浏览器类型
   */
  browser?: string

  /**
   * 操作系统
   */
  os?: string

  /**
   * 登录状态（0成功 1失败）
   */
  status?: number

  /**
   * 提示消息
   */
  msg?: string

  /**
   * 登录时间
   */
  loginTime?: string

  /**
   * 创建时间
   */
  createTime?: string

  /**
   * 更新时间
   */
  updateTime?: string

  /**
   * 删除标志（0代表存在 2代表删除）
   */
  deleted?: number
}

/**
 * 登录日志查询参数
 */
export interface SysLoginLogQuery {
  pageNum?: number
  pageSize?: number
  userName?: string
  ipAddress?: string
  status?: number
  startTime?: string
  endTime?: string
}

/**
 * 系统异常日志表 sys_exception_log
 */
export interface SysExceptionLog {
  /**
   * 异常ID
   */
  exceptionId?: number

  /**
   * 异常名称
   */
  exceptionName?: string

  /**
   * 异常信息
   */
  exceptionMessage?: string

  /**
   * 堆栈信息
   */
  stackTrace?: string

  /**
   * 请求URL
   */
  requestUrl?: string

  /**
   * 请求方法
   */
  requestMethod?: string

  /**
   * 请求参数
   */
  requestParams?: string

  /**
   * IP地址
   */
  ipAddress?: string

  /**
   * 用户ID
   */
  userId?: number

  /**
   * 用户名
   */
  userName?: string

  /**
   * 异常时间
   */
  exceptionTime?: string

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
 * 异常日志查询参数
 */
export interface SysExceptionLogQuery {
  pageNum?: number
  pageSize?: number
  exceptionName?: string
  userName?: string
  ipAddress?: string
  startTime?: string
  endTime?: string
}

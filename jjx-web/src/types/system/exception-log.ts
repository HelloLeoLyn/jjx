/**
 * 异常日志表 sys_error_log（对齐后端 SysErrorLog 实体）
 */
export interface SysErrorLog {
  id?: number
  traceId?: string
  userId?: number
  /** 用户名 */
  username?: string
  /** 异常名称 */
  exceptionName?: string
  /** 异常信息 */
  exceptionMsg?: string
  /** 请求URL */
  requestUrl?: string
  /** 请求方法 */
  requestMethod?: string
  /** 请求参数 */
  requestParams?: string
  /** 客户端IP */
  clientIp?: string
  /** 异常触发时间 */
  triggerTime?: string
  /** 处理状态 */
  handleStatus?: number
  /** 处理备注 */
  handleRemark?: string
  /** 处理时间 */
  handleTime?: string
  /** 处理人 */
  handleBy?: string
}

/** 异常日志查询参数 */
export interface SysExceptionLogQuery {
  pageNum?: number
  pageSize?: number
  exceptionName?: string
  requestUrl?: string
  handleStatus?: number
}

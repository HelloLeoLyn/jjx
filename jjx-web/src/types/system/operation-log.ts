/**
 * 操作日志表 sys_oper_log（对齐后端 SysOperLog 实体）
 */
export interface SysOperLog {
  id?: number
  userId?: number
  username?: string
  realName?: string
  tenantId?: number
  /** 模块名称 */
  module?: string
  /** 操作动作中文文案 */
  action?: string
  /** 业务类型 0其它 1新增 2修改 3删除 4授权 5导出 6导入 7强退 8生成代码 9清空数据 */
  businessType?: number
  /** 请求URL */
  operUrl?: string
  /** 操作IP */
  operIp?: string
  /** 请求参数 */
  operParam?: string
  /** 业务类型标识 */
  bizType?: string
  /** 业务ID */
  bizId?: string
  /** 追踪ID */
  traceId?: string
  /** 详情 */
  detail?: string
  /** 耗时(ms) */
  costTime?: number
  /** 操作状态 0失败 1成功(YesNoEnum) */
  status?: number
  /** 错误信息 */
  errorMsg?: string
  userAgent?: string
  /** 创建时间 */
  createTime?: string
}

/** 操作日志查询参数 */
export interface SysOperLogQuery {
  pageNum?: number
  pageSize?: number
  module?: string
  bizType?: string
  traceId?: string
  status?: number
}

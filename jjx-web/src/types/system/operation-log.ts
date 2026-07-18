import type { st } from 'vue-router/dist/router-CWoNjPRp.mjs'

/**
 * 操作日志记录表 sys_operation_log
 */
export interface SysOperationLog {
  /**
   * 日志主键
   */
  logId?: number

  /**
   * 日志标题
   */
  title?: string

  /**
   * 业务类型（0其它 1新增 2修改 3删除 4授权 5导出 6导入 7强退 8生成代码 9清空数据）
   */
  businessType?: string

  /**
   * 方法名称
   */
  method?: string

  /**
   * 请求方式
   */
  requestMethod?: string

  /**
   * 操作类别（0其它 1后台用户 2手机端用户）
   */
  operatorType?: number

  /**
   * 操作人员ID
   */
  operatorId?: number

  /**
   * 操作人员名称
   */
  operatorName?: string

  /**
   * 部门名称
   */
  deptName?: string

  /**
   * 请求URL
   */
  requestUrl?: string

  /**
   * 主机地址
   */
  requestIp?: string

  /**
   * 操作地点
   */
  requestLocation?: string

  /**
   * 请求参数
   */
  requestParam?: string

  /**
   * 返回参数
   */
  jsonResult?: string

  /**
   * 操作状态（0正常 1异常）
   */
  status?: number

  /**
   * 错误消息
   */
  errorMsg?: string

  /**
   * 操作时间
   */
  operationTime?: string

  /**
   * 耗时（毫秒）
   */
  costTime?: number

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
 * 操作日志查询参数
 */
export interface SysOperationLogQuery {
  pageNum?: number
  pageSize?: number
  module?: string
  businessType?: string
  operatorName?: string
  status?: number
  time: string[]
}

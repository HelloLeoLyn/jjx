/**
 * 状态变更日志表 sys_status_change_log
 */
export interface SysStatusChangeLog {
  /**
   * 变更ID
   */
  changeId?: number

  /**
   * 业务类型
   */
  businessType?: string

  /**
   * 业务ID
   */
  businessId?: number

  /**
   * 业务编码
   */
  businessCode?: string

  /**
   * 业务名称
   */
  businessName?: string

  /**
   * 状态字段名
   */
  statusField?: string

  /**
   * 原状态值
   */
  oldStatus?: string

  /**
   * 原状态标签
   */
  oldStatusLabel?: string

  /**
   * 新状态值
   */
  newStatus?: string

  /**
   * 新状态标签
   */
  newStatusLabel?: string

  /**
   * 变更原因
   */
  changeReason?: string

  /**
   * 变更备注
   */
  changeRemark?: string

  /**
   * 操作人ID
   */
  operatorId?: number

  /**
   * 操作人姓名
   */
  operatorName?: string

  /**
   * 操作人类型（0系统 1用户）
   */
  operatorType?: number

  /**
   * 部门名称
   */
  deptName?: string

  /**
   * IP地址
   */
  ipAddress?: string

  /**
   * 请求URL
   */
  requestUrl?: string

  /**
   * 请求方法
   */
  requestMethod?: string

  /**
   * 变更时间
   */
  changeTime?: string

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
 * 状态变更日志查询参数
 */
export interface SysStatusChangeLogQuery {
  pageNum?: number
  pageSize?: number
  businessType?: string
  businessName?: string
  operatorName?: string
  startTime?: string
  endTime?: string
}

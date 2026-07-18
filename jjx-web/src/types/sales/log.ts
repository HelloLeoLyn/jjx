/**
 * 销售订单操作日志类型定义
 */

/**
 * 操作日志VO
 */
export interface SalesLogVO {
  /** 日志ID */
  logId: number
  /** 订单ID */
  orderId: number
  /** 订单号 */
  orderNo?: string
  /** 操作类型代码 */
  operationType: string
  /** 操作类型名称 */
  operationTypeName: string
  /** 操作描述 */
  operationDescription: string
  /** 操作人ID */
  operatorId: number
  /** 操作人姓名 */
  operatorName: string
  /** 操作时间 */
  operationTime: string
  /** 操作结果代码 */
  operationResult: string
  /** 操作结果名称 */
  operationResultName: string
  /** 备注 */
  remark?: string
}

/**
 * 操作日志查询DTO
 */
export interface SalesLogQueryDTO {
  /** 订单ID */
  orderId?: number
  /** 订单号 */
  orderNo?: string
  /** 操作类型 */
  operationType?: string
  /** 操作人ID */
  operatorId?: number
  /** 操作人姓名 */
  operatorName?: string
  /** 操作结果 */
  operationResult?: string
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  /** 页码 */
  pageNum?: number
  /** 每页大小 */
  pageSize?: number
  /** 排序字段 */
  orderByColumn?: string
  /** 排序方式 */
  isAsc?: 'asc' | 'desc'
}

/**
 * 操作类型统计
 */
export interface OperationTypeStats {
  /** 操作类型代码 */
  operationType: string
  /** 操作类型名称 */
  operationTypeName: string
  /** 数量 */
  count: number
}

/**
 * 操作人统计
 */
export interface OperatorStats {
  /** 操作人ID */
  operatorId: number
  /** 操作人姓名 */
  operatorName: string
  /** 数量 */
  count: number
}

/**
 * 枚举项
 */
export interface EnumItem {
  /** 枚举代码 */
  code: string
  /** 枚举名称 */
  name: string
}

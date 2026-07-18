// 生产工序执行相关类型定义

// 执行状态枚举
export enum ExecutionStatus {
  PENDING = 'PENDING', // 待执行
  IN_PROGRESS = 'IN_PROGRESS', // 进行中
  PAUSED = 'PAUSED', // 已暂停
  COMPLETED = 'COMPLETED', // 已完成
  CANCELLED = 'CANCELLED', // 已取消
}

// 执行状态映射
export const ExecutionStatusMap: Record<string, string> = {
  [ExecutionStatus.PENDING]: '待执行',
  [ExecutionStatus.IN_PROGRESS]: '进行中',
  [ExecutionStatus.PAUSED]: '已暂停',
  [ExecutionStatus.COMPLETED]: '已完成',
  [ExecutionStatus.CANCELLED]: '已取消',
}

// 工序执行 VO（与后端 ProductionOperationExecutionVO 对齐）
export interface OperationExecutionVO {
  executionId?: number
  orderId?: number
  orderNo?: string
  processId?: number
  processOrder?: number
  executionStatus?: string
  executionStatusDesc?: string
  plannedStartTime?: string
  plannedEndTime?: string
  actualStartTime?: string
  actualEndTime?: string
  operatorId?: number
  operatorName?: string
  equipmentId?: number
  equipmentCode?: string
  equipmentName?: string
  inputQuantity?: number
  outputQuantity?: number
  qualifiedQuantity?: number
  defectiveQuantity?: number
  defectiveReason?: string
  actualProcessParams?: string
  qualityCheckResult?: string
  actualLaborHours?: number
  actualMachineHours?: number
  createTime?: string
  updateTime?: string

  // 计算字段
  hasStarted?: boolean
  hasEnded?: boolean
  isOverdue?: boolean
  isPending?: boolean
  isProcessing?: boolean
  isCompleted?: boolean
  isSkipped?: boolean
  plannedHours?: number
  actualHours?: number
  qualifiedRate?: number
  defectiveRate?: number
  canStart?: boolean
  canComplete?: boolean
  totalActualHours?: number

  // 关联工单信息
  productionOrder?: ProductionOrderVO
}

// 生产工单 VO（简化版）
export interface ProductionOrderVO {
  orderId?: number
  orderNo?: string
  productId?: number
  productCode?: string
  productName?: string
  productSpec?: string
  productUnit?: string
  plannedQuantity?: number
  completedQuantity?: number
  orderStatus?: string
}

// 工序执行查询参数
export interface OperationExecutionQuery {
  orderId?: number
  orderNo?: string
  processId?: number
  processCode?: string
  processName?: string
  equipmentId?: number
  equipmentCode?: string
  equipmentName?: string
  operatorId?: number
  operatorName?: string
  executionStatus?: string
  qualityStatus?: string
  planStartTimeFrom?: string
  planStartTimeTo?: string
  planEndTimeFrom?: string
  planEndTimeTo?: string
  actualStartTimeFrom?: string
  actualStartTimeTo?: string
  actualEndTimeFrom?: string
  actualEndTimeTo?: string
  hasException?: boolean
  pageNum?: number
  pageSize?: number
  orderBy?: string
  orderDirection?: string
}

// 工序执行创建 DTO
export interface OperationExecutionCreateDTO {
  orderId: number
  processId: number
  processOrder: number
  planStartTime?: string
  planEndTime?: string
  operatorId?: number
  operatorName?: string
  equipmentId?: number
  equipmentCode?: string
  equipmentName?: string
  plannedQuantity?: number
}

// 工序执行更新 DTO
export interface OperationExecutionUpdateDTO {
  executionId: number
  actualStartTime?: string
  actualEndTime?: string
  actualLaborHours?: number
  actualMachineHours?: number
  actualSetupTime?: number
  actualCleanupTime?: number
  actualCompletedQuantity?: number
  actualQualifiedQuantity?: number
  actualDefectiveQuantity?: number
  actualQualifiedRate?: number
  equipmentId?: number
  equipmentCode?: string
  equipmentName?: string
  operatorId?: number
  operatorName?: string
  executionStatus?: string
  qualityStatus?: string
  qualityInspectorId?: number
  qualityInspectorName?: string
  qualityInspectionTime?: string
  qualityRemark?: string
  exceptionCode?: string
  exceptionDescription?: string
  exceptionHandlerId?: number
  exceptionHandlerName?: string
  exceptionHandleTime?: string
  exceptionHandleResult?: string
  remark?: string
}

// 工序执行统计
export interface OperationExecutionStats {
  totalCount: number
  pendingCount: number
  inProgressCount: number
  completedCount: number
  cancelledCount: number
}

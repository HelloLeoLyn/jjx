// 生产工序执行相关类型定义

// 工序执行 VO（与后端 ProductionOperationExecutionVO 对齐）
export interface OperationExecutionVO {
  executionId?: number
  orderId?: number
  orderNo?: string
  processId?: number
  majorCategory?: string
  processName?: string
  customProcessParams?: string
  processOrder?: number
  executionStatus?: number
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
  /** PENDING 报工数量（合格+不良），不计入累计产出 */
  pendingApprovalQuantity?: number
  /** 待完成 = 任务数量 - 已完成（下限0） */
  remainingQuantity?: number
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
  executionStatus?: number | ''
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
  /** 不良原因（P0-03：正确映射 defective_reason，不再借道 remark） */
  defectiveReason?: string
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

/**
 * 工单级收口状态（2026-09-10）：一级负责人对整张工单一次收口。
 * 仅做轻量投影（是否可点击）；逐工序前置在点击收口时由服务端聚合校验。
 */
export interface OrderCompletionStatusVO {
  orderId: number
  orderNo?: string
  /** 待完工工序数（未终态） */
  pendingExecutionCount: number
  /** 当前用户是否有权收口（该工单全部工序根负责人本人，或超管） */
  authorized: boolean
  /** 是否可点击收口（有权 + 存在待完工工序） */
  canComplete: boolean
  /** 完工阶段（派生，dev-20260918-015；PENDING_SUPPLEMENT 见 dev-20260923-028）：NOT_STARTED/IN_PRODUCTION/PENDING_FQC/PENDING_DISPOSITION/PENDING_SUPPLEMENT/READY_TO_COMPLETE/PENDING_INBOUND/COMPLETED/PAUSED/CANCELLED/UNKNOWN */
  stage?: string
  /** 阶段中文名 */
  stageLabel?: string
  /** 下一步该谁做什么 */
  nextAction?: string
  /** 工序总数（不含已取消） */
  executionTotal?: number
  /** 已完成工序数 */
  executionDone?: number
  /** 待检完工检验批张数 */
  fqcPendingCount?: number
  /** 未处置不良合计 */
  undisposedFailQuantity?: number
  /** 成品检验合格累计 */
  qualifiedQuantity?: number
  /** 计划数量 */
  plannedQuantity?: number
  /** 已登记报废合计（有效批 SCRAP DONE）—— dev-20260923-028 */
  scrappedQuantity?: number
  /** Per-operation planned input and approved output, in route order. */
  operationQuantities?: Array<{
    processOrder?: number
    processName?: string
    plannedInputQuantity?: number
    approvedOutputQuantity?: number
    finalOperation?: boolean
  }>
  finalOperationOutputQuantity?: number
  /** Approved supplement output, counted once per source outbound despite multiple route operations. */
  supplementReportedQuantity?: number
  /** 缺口 = max(0, 计划 − 合格累计)；阶段=待补产时即「还需补产多少件」—— dev-20260923-028 */
  shortfallQuantity?: number
}

/**
 * 返工链投影（只读）—— dev-20260923-031。
 * 把「不良单 → 返工工序 → 报工 → 复检 → 回收」投影成一条，供工序执行页与不良台账页展示进度。
 */
export interface ReworkTraceVO {
  ncrId?: number
  ncrNo?: string
  defectQuantity?: number
  disposedQuantity?: number
  actionId?: number
  /** PROCESSING 返工在制 / DONE 返工闭环完成 */
  actionStatus?: string
  /** 本次返工件数 */
  reworkQuantity?: number
  executionId?: number
  processName?: string
  taskId?: number
  taskNo?: string
  taskStatus?: string
  taskAssigneeName?: string
  hasWorkerTasks?: boolean
  executionStatus?: number
  reworkRequirement?: string
  reportedQuantity?: number
  reinspectionLotId?: number
  reinspectionLotNo?: string
  reinspectionStatus?: string
  reinspectionResult?: string
  recoveredQuantity?: number
  /** 一句人话：现在到哪一步、下一步做什么 */
  statusText?: string
}

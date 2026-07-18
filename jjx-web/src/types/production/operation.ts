// 生产操作相关类型定义

// 操作任务状态枚举
export enum OperationStatus {
  PENDING = 'pending', // 待分配
  IN_PROGRESS = 'in_progress', // 进行中
  COMPLETED = 'completed', // 已完成
  CANCELLED = 'cancelled', // 已取消
}

// 操作任务优先级枚举
export enum OperationPriority {
  LOW = 'low', // 低
  MEDIUM = 'medium', // 中
  HIGH = 'high', // 高
  URGENT = 'urgent', // 紧急
}

// 操作任务质量结果枚举
export enum QualityResult {
  PASS = 'pass', // 合格
  FAIL = 'fail', // 不合格
  REWORK = 'rework', // 返工
  SCRAP = 'scrap', // 报废
}

// 操作任务基础接口
export interface OperationBase {
  operationId: string
  operationCode: string
  workOrderId: string
  workOrderNo: string
  productId: string
  productName: string
  productSpec: string
  stepId: string
  stepName: string
  stepSequence: number
  operatorId: string
  operatorName: string
  equipmentId: string
  equipmentCode: string
  equipmentName: string
  plannedQuantity: number
  completedQuantity: number
  operationStatus: OperationStatus
  priority: OperationPriority
  startTime: string
  endTime: string
  parameters: string
  qualityResult: QualityResult | null
  remark: string
  createTime: string
  updateTime: string
}

// 操作任务列表项
export interface OperationItem extends OperationBase {
  // 扩展字段
  productCode: string
  stepCode: string
  operatorCode: string
  equipmentStatus: string
  qualityCheckTime: string | null
}

// 操作任务详情
export interface OperationDetail extends OperationBase {
  // 详情扩展字段
  workOrderDetail: {
    workOrderId: string
    workOrderNo: string
    productName: string
    productSpec: string
    plannedQuantity: number
    completedQuantity: number
    workOrderStatus: string
    priority: string
    createTime: string
  }
  stepDetail: {
    stepId: string
    stepName: string
    stepCode: string
    standardTime: number
    standardOutput: number
    qualityRequirements: string
    safetyRequirements: string
  }
  operatorDetail: {
    userId: string
    userName: string
    userCode: string
    departmentName: string
    position: string
    skillLevel: string
  }
  equipmentDetail: {
    equipmentId: string
    equipmentCode: string
    equipmentName: string
    equipmentType: string
    equipmentStatus: string
    maintenanceStatus: string
    lastMaintenanceTime: string | null
  }
  qualityRecords: Array<{
    recordId: string
    checkTime: string
    checkItem: string
    checkStandard: string
    actualValue: string
    checkResult: QualityResult
    checkerName: string
    remark: string
  }>
  operationRecords: Array<{
    recordId: string
    recordTime: string
    recordType: string
    recordContent: string
    operatorName: string
    parameters: string
  }>
}

// 操作任务查询参数
export interface OperationQueryParams {
  workOrderNo?: string
  operationCode?: string
  productName?: string
  stepName?: string
  operatorName?: string
  equipmentCode?: string
  operationStatus?: OperationStatus | ''
  priority?: OperationPriority | ''
  startTimeStart?: string
  startTimeEnd?: string
  endTimeStart?: string
  endTimeEnd?: string
  pageNum: number
  pageSize: number
}

// 操作任务表单数据
export interface OperationFormData {
  operationId?: string
  workOrderId: string
  stepId: string
  operatorId: string
  equipmentId: string
  plannedQuantity: number
  priority: OperationPriority
  parameters: string
  remark: string
}

// 操作任务分配数据
export interface OperationAssignData {
  operationIds: string[]
  operatorId: string
  equipmentId: string
  startTime?: string
  remark?: string
}

// 操作任务开始数据
export interface OperationStartData {
  operationId: string
  actualStartTime: string
  parameters?: Record<string, any>
  remark?: string
}

// 操作任务完成数据
export interface OperationCompleteData {
  operationId: string
  actualEndTime: string
  completedQuantity: number
  qualityResult: QualityResult
  qualityRemark?: string
  parameters?: Record<string, any>
  remark?: string
}

// 操作任务取消数据
export interface OperationCancelData {
  operationId: string
  cancelReason: string
  remark?: string
}

// 操作任务参数记录
export interface OperationParameterRecord {
  operationId: string
  recordTime: string
  parameters: Record<string, any>
  operatorId: string
  remark?: string
}

// 操作任务质量记录
export interface OperationQualityRecord {
  operationId: string
  checkTime: string
  checkItem: string
  checkStandard: string
  actualValue: string
  checkResult: QualityResult
  checkerId: string
  checkerName: string
  remark?: string
}

// 操作统计数据
export interface OperationStats {
  totalCount: number
  pendingCount: number
  inProgressCount: number
  completedCount: number
  cancelledCount: number
  todayCount: number
  weekCount: number
  monthCount: number
  qualityPassRate: number
  onTimeCompletionRate: number
  avgCompletionTime: number
  topOperators: Array<{
    operatorId: string
    operatorName: string
    completedCount: number
    qualityPassRate: number
    avgCompletionTime: number
  }>
  topEquipment: Array<{
    equipmentId: string
    equipmentCode: string
    equipmentName: string
    operationCount: number
    utilizationRate: number
    avgCompletionTime: number
  }>
}

// 操作任务选项数据
export interface OperationOptions {
  workOrderOptions: Array<{
    workOrderId: string
    workOrderNo: string
    productName: string
    productSpec: string
    plannedQuantity: number
    remainingQuantity: number
    workOrderStatus: string
  }>
  stepOptions: Array<{
    stepId: string
    stepCode: string
    stepName: string
    stepSequence: number
    standardTime: number
    standardOutput: number
  }>
  operatorOptions: Array<{
    userId: string
    userCode: string
    userName: string
    departmentName: string
    position: string
    skillLevel: string
    currentWorkload: number
    available: boolean
  }>
  equipmentOptions: Array<{
    equipmentId: string
    equipmentCode: string
    equipmentName: string
    equipmentType: string
    equipmentStatus: string
    maintenanceStatus: string
    currentWorkload: number
    available: boolean
  }>
}

// 操作任务导出数据
export interface OperationExportData {
  operationCode: string
  workOrderNo: string
  productName: string
  stepName: string
  operatorName: string
  equipmentCode: string
  plannedQuantity: number
  completedQuantity: number
  operationStatus: string
  startTime: string
  endTime: string
  qualityResult: string
  remark: string
}

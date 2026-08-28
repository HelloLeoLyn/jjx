// 统一的生产订单类型定义
import { ExecutionStatusEnum } from '@/enums/production'

// 订单类型枚举
export enum OrderType {
  PLAN = 'plan', // 生产计划
  WORK_ORDER = 'work_order', // 生产工单
}

// 订单状态枚举（统一状态机）
export enum OrderStatus {
  // 计划状态
  DRAFT = 0, // 草稿
  PENDING_APPROVAL = 1, // 待审批
  APPROVED = 2, // 已批准

  // 工单状态
  SCHEDULED = 4, // 已排程(已计划)
  IN_PROGRESS = 6, // 进行中
  COMPLETED = 8, // 已完成

  // 通用状态
  CANCELLED = 9, // 已取消
}

// 审批状态枚举（计划特有）
export enum ApprovalStatus {
  PENDING = 1, // 待审批
  APPROVED = 2, // 已批准
  REJECTED = 3, // 已拒绝
  CANCELLED = 4, // 已取消
}

// 执行状态类型来自统一枚举；不在 types 中重复定义状态值。
export type ExecutionStatus = (typeof ExecutionStatusEnum.items)[number]['value']

// 优先级枚举
export enum Priority {
  LOW = 'low', // 低
  MEDIUM = 'medium', // 中
  HIGH = 'high', // 高
  URGENT = 'urgent', // 紧急
}

// 计划类型枚举
export enum PlanType {
  MONTHLY = 'monthly', // 月计划
  WEEKLY = 'weekly', // 周计划
  DAILY = 'daily', // 日计划
  SPECIAL = 'special', // 专项计划
}

// 统一的生产订单基础接口
export interface ProductionOrderBase {
  // 标识信息
  orderId: string
  orderNo: string
  traceId?: string
  orderType: OrderType

  // 关联信息
  parentOrderId?: string // 父订单ID（计划→工单）
  salesOrderId?: string // 销售订单ID
  salesOrderNo?: string // 销售订单编号
  instanceId?: string // 产品实例ID
  instanceCode?: string // 产品实例编码

  // 产品信息
  productId: string
  productCode: string
  productName: string
  productSpec: string
  productUnit: string

  // 数量信息
  plannedQuantity: number // 计划数量
  completedQuantity: number // 已完成数量
  remainingQuantity: number // 剩余数量

  // 时间信息
  planStartDate: string // 计划开始日期
  planEndDate: string // 计划结束日期
  actualStartTime?: string // 实际开始时间
  actualEndTime?: string // 实际结束时间
  createTime: string // 创建时间
  updateTime: string // 更新时间

  // 状态信息
  orderStatus: OrderStatus // 统一订单状态
  approvalStatus?: ApprovalStatus // 审批状态（计划特有）
  executionStatus?: ExecutionStatus // 执行状态（工单特有）
  reworkFlag?: number // 返工标记：1=返工中

  // 优先级
  priority: Priority

  // 备注
  remark?: string
}

// 生产计划接口（扩展）
export interface ProductionPlan extends ProductionOrderBase {
  orderType: OrderType.PLAN

  // 计划特有字段
  planType: PlanType // 计划类型
  approverId?: string // 审批人ID
  approverName?: string // 审批人姓名
  approvalTime?: string // 审批时间
  approvalRemark?: string // 审批备注

  // 关联工单信息
  workOrderCount: number // 关联工单数量
  completedWorkOrderCount: number // 已完成工单数量

  // 统计信息
  totalPlannedQuantity: number // 总计划数量
  totalCompletedQuantity: number // 总完成数量
  completionRate: number // 完成率
}

// 生产工单接口（扩展）
export interface WorkOrder extends ProductionOrderBase {
  orderType: OrderType.WORK_ORDER

  // 工单特有字段
  planId?: string // 关联计划ID
  planNo?: string // 关联计划编号
  operatorId?: string // 操作员ID
  operatorName?: string // 操作员姓名
  equipmentId?: string // 设备ID
  equipmentCode?: string // 设备编号
  equipmentName?: string // 设备名称

  // 工艺信息
  stepId?: string // 工序ID
  stepCode?: string // 工序编码
  stepName?: string // 工序名称
  stepSequence?: number // 工序顺序

  // 质量信息
  qualityResult?: string // 质量结果
  qualityCheckTime?: string // 质检时间

  // 成本信息
  materialCost?: number // 材料成本
  laborCost?: number // 人工成本
  totalCost?: number // 总成本
}

// 统一的生产订单视图接口（用于列表展示）
export interface ProductionOrderVO extends ProductionOrderBase {
  // 扩展字段（用于列表展示）
  canConvertToWorkOrder: boolean // 是否可以转为工单
  canStart: boolean // 是否可以开始
  canComplete: boolean // 是否可以完成
  canCancel: boolean // 是否可以取消
  canEdit: boolean // 是否可以编辑

  // 显示字段
  statusLabel: string // 状态标签
  statusType: string // 状态类型（用于标签颜色）
  priorityLabel: string // 优先级标签
  materialStatus?: number // 领料状态：0未领料/1待发料/2已领料
  materialStatusLabel?: string // 领料状态标签

  // 时间显示
  planDateRange: string // 计划日期范围显示
  actualTimeRange?: string // 实际时间范围显示

  // 进度信息
  progress: number // 进度百分比
  progressLabel: string // 进度标签
  remainingQuantity: number // 剩余数量
}

// 生产订单查询参数
export interface ProductionOrderQuery {
  // 基本查询
  orderNo?: string
  productName?: string
  productCode?: string

  // 类型筛选
  orderType?: OrderType | 'all' // 订单类型
  planType?: PlanType // 计划类型（计划特有）

  // 状态筛选
  orderStatus?: OrderStatus | '' // 统一状态
  approvalStatus?: ApprovalStatus | '' // 审批状态
  executionStatus?: ExecutionStatus | '' // 执行状态

  // 时间筛选
  planDateStart?: string // 计划开始日期
  planDateEnd?: string // 计划结束日期
  createTimeStart?: string // 创建时间开始
  createTimeEnd?: string // 创建时间结束

  // 关联筛选
  salesOrderId?: number // 销售订单ID
  salesOrderNo?: string // 销售订单编号
  instanceCode?: string // 产品实例编码

  // 分页参数
  pageNum: number
  pageSize: number

  // 排序参数
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

// 生产订单创建参数
export interface ProductionOrderCreateDTO {
  orderType: OrderType // 订单类型

  // 通用字段
  productId: string
  productCode: string
  productName: string
  plannedQuantity: number
  planStartDate: string
  planEndDate: string
  priority: Priority
  remark?: string

  // 计划特有字段
  planType?: PlanType
  approverId?: string

  // 工单特有字段
  operatorId?: string
  equipmentId?: string
  stepId?: string

  // 关联信息
  salesOrderId?: string
  instanceId?: string
  parentOrderId?: string
}

// 生产订单更新参数
export interface ProductionOrderUpdateDTO {
  orderId: string

  // 可更新字段
  plannedQuantity?: number
  planStartDate?: string
  planEndDate?: string
  priority?: Priority
  remark?: string

  // 计划特有字段
  planType?: PlanType
  approverId?: string

  // 工单特有字段
  operatorId?: string
  equipmentId?: string
  stepId?: string
}

// 状态更新参数
export interface OrderStatusUpdateDTO {
  orderId: string
  orderStatus: OrderStatus
  remark?: string

  // 计划特有
  approvalStatus?: ApprovalStatus
  approvalRemark?: string

  // 工单特有
  executionStatus?: ExecutionStatus
  completedQuantity?: number
  qualityResult?: string
}

// 计划转工单参数
export interface ConvertPlanToWorkOrdersDTO {
  planId: string
  workOrders: Array<{
    productId: string
    productCode: string
    productName: string
    plannedQuantity: number
    operatorId?: string
    equipmentId?: string
    stepId?: string
    planStartDate: string
    planEndDate: string
    priority: Priority
    remark?: string
  }>
  batchConvert?: boolean // 是否批量转换
}

// 生产订单统计信息
export interface ProductionOrderStats {
  // 总数统计
  totalCount: number
  planCount: number
  workOrderCount: number

  // 状态统计
  draftCount: number
  pendingApprovalCount: number
  approvedCount: number
  scheduledCount: number
  inProgressCount: number
  completedCount: number
  cancelledCount: number

  // 进度统计
  totalPlannedQuantity: number
  totalCompletedQuantity: number
  overallCompletionRate: number

  // 时间统计
  todayCount: number
  weekCount: number
  monthCount: number
  overdueCount: number

  // 质量统计
  qualityPassRate: number
  onTimeCompletionRate: number

  // 成本统计
  totalMaterialCost: number
  totalLaborCost: number
  totalCost: number
  avgCostPerUnit: number
}

// 生产订单导出数据
export interface ProductionOrderExportData {
  orderNo: string
  orderType: string
  productName: string
  productSpec: string
  plannedQuantity: number
  completedQuantity: number
  orderStatus: string
  planStartDate: string
  planEndDate: string
  actualStartTime?: string
  actualEndTime?: string
  priority: string
  remark?: string
  createTime: string
  updateTime: string
}

// 状态流转规则
export const STATUS_FLOW_RULES: Record<OrderStatus, OrderStatus[]> = {
  [OrderStatus.DRAFT]: [OrderStatus.PENDING_APPROVAL, OrderStatus.CANCELLED],
  [OrderStatus.PENDING_APPROVAL]: [OrderStatus.APPROVED, OrderStatus.CANCELLED],
  [OrderStatus.APPROVED]: [OrderStatus.SCHEDULED, OrderStatus.CANCELLED],
  [OrderStatus.SCHEDULED]: [OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED],
  [OrderStatus.IN_PROGRESS]: [OrderStatus.COMPLETED, OrderStatus.CANCELLED],
  [OrderStatus.COMPLETED]: [],
  [OrderStatus.CANCELLED]: [],
}

// 状态标签映射
export const STATUS_LABELS: Record<OrderStatus, string> = {
  [OrderStatus.DRAFT]: '草稿',
  [OrderStatus.PENDING_APPROVAL]: '待审批',
  [OrderStatus.APPROVED]: '已批准',
  [OrderStatus.SCHEDULED]: '已排程',
  [OrderStatus.IN_PROGRESS]: '进行中',
  [OrderStatus.COMPLETED]: '已完成',
  [OrderStatus.CANCELLED]: '已取消',
}

// 状态类型映射（用于标签颜色）
export const STATUS_TYPES: Record<OrderStatus, 'info' | 'warning' | 'success' | 'danger'> = {
  [OrderStatus.DRAFT]: 'info',
  [OrderStatus.PENDING_APPROVAL]: 'warning',
  [OrderStatus.APPROVED]: 'success',
  [OrderStatus.SCHEDULED]: 'info',
  [OrderStatus.IN_PROGRESS]: 'warning',
  [OrderStatus.COMPLETED]: 'success',
  [OrderStatus.CANCELLED]: 'danger',
}

// 优先级标签映射
export const PRIORITY_LABELS: Record<Priority, string> = {
  [Priority.LOW]: '低',
  [Priority.MEDIUM]: '中',
  [Priority.HIGH]: '高',
  [Priority.URGENT]: '紧急',
}

// 计划类型标签映射
export const PLAN_TYPE_LABELS: Record<PlanType, string> = {
  [PlanType.MONTHLY]: '月计划',
  [PlanType.WEEKLY]: '周计划',
  [PlanType.DAILY]: '日计划',
  [PlanType.SPECIAL]: '专项计划',
}

// 审批状态标签映射
export const APPROVAL_STATUS_LABELS: Record<ApprovalStatus, string> = {
  [ApprovalStatus.PENDING]: '待审批',
  [ApprovalStatus.APPROVED]: '已批准',
  [ApprovalStatus.REJECTED]: '已拒绝',
  [ApprovalStatus.CANCELLED]: '已取消',
}

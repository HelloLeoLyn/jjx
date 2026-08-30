// src/enums/production/WorkOrderEnum.ts
import { createEnum, createNamedEnum } from '../base'

/**
 * 生产工单状态枚举
 * 对应后端 production ProductionOrderStatusEnum
 */
export const ProductionOrderStatusEnum = createNamedEnum(
  {
    DRAFT: { value: 0, label: '草稿', tagProps: { type: 'info' } },
    PENDING_APPROVAL: { value: 1, label: '待审核', tagProps: { type: 'warning' } },
    APPROVED: { value: 2, label: '已审核', tagProps: { type: 'primary' } },
    REJECTED: { value: 3, label: '已驳回', tagProps: { type: 'danger' } },
    PLANNED: { value: 4, label: '已计划', tagProps: { type: 'warning' } },
    PENDING_START: { value: 5, label: '待开始', tagProps: { type: 'info' } },
    IN_PROGRESS: { value: 6, label: '进行中', tagProps: { type: 'warning' } },
    PAUSED: { value: 7, label: '已暂停', tagProps: { type: 'warning' } },
    COMPLETED: { value: 8, label: '已完成', tagProps: { type: 'success' } },
    CANCELLED: { value: 9, label: '已取消', tagProps: { type: 'danger' } },
    CLOSED: { value: 10, label: '已关闭', tagProps: { type: 'info' } },
    OVERDUE: { value: 11, label: '已超期', tagProps: { type: 'danger' } },
  },
  { type: 'info' }
)

/**
 * 工序执行状态枚举
 * 对应后端 ExecutionStatusEnum
 */
export const ExecutionStatusEnum = createNamedEnum(
  {
    PENDING: { value: 0, label: '待执行', tagProps: { type: 'info' } },
    PREPARING: { value: 1, label: '准备中', tagProps: { type: 'warning' } },
    EXECUTING: { value: 2, label: '执行中', tagProps: { type: 'primary' } },
    PAUSED: { value: 3, label: '已暂停', tagProps: { type: 'warning' } },
    COMPLETED: { value: 4, label: '已完成', tagProps: { type: 'success' } },
    SKIPPED: { value: 5, label: '已跳过', tagProps: { type: 'info' } },
    CANCELLED: { value: 6, label: '已取消', tagProps: { type: 'danger' } },
    OVERDUE: { value: 7, label: '已超期', tagProps: { type: 'danger' } },
    ABNORMAL: { value: 8, label: '异常中', tagProps: { type: 'danger' } },
    PENDING_CONFIRMATION: { value: 9, label: '待确认', tagProps: { type: 'warning' } },
  },
  { type: 'info' }
)

/**
 * 生产记录类型枚举
 * 对应后端 RecordTypeEnum
 */
export const RecordTypeEnum = createEnum<string>({
  items: [
    { value: 'START', label: '开始记录', tagProps: { type: 'primary' } },
    { value: 'PAUSE', label: '暂停记录', tagProps: { type: 'warning' } },
    { value: 'RESUME', label: '恢复记录', tagProps: { type: 'primary' } },
    { value: 'COMPLETE', label: '完成记录', tagProps: { type: 'success' } },
    { value: 'QUALITY', label: '质量记录', tagProps: { type: 'warning' } },
    { value: 'ISSUE', label: '问题记录', tagProps: { type: 'danger' } },
    { value: 'PARAMETER', label: '参数记录', tagProps: { type: 'info' } },
    { value: 'STATUS', label: '状态记录', tagProps: { type: 'info' } },
    { value: 'OPERATION', label: '操作记录', tagProps: { type: 'info' } },
    { value: 'DATA', label: '数据记录', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/** 兼容旧引用：占位符对象保留导出 */
export const WorkOrderEnum = {
  orderStatus: ProductionOrderStatusEnum,
  executionStatus: ExecutionStatusEnum,
  recordType: RecordTypeEnum,
}

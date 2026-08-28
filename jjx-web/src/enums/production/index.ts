// src/enums/production/index.ts
import { WorkOrderEnum, ProductionOrderStatusEnum, ExecutionStatusEnum, RecordTypeEnum } from './WorkOrderEnum'
import { ToolingTypeEnum, ToolingStatusEnum } from './ToolingEnum'

// 重新导出所有内容
export * from './WorkOrderEnum'
export * from './ToolingEnum'
export * from './QualityTemplateEnum'

// 重新导出统一对象
export { WorkOrderEnum, ProductionOrderStatusEnum, ExecutionStatusEnum, RecordTypeEnum, ToolingTypeEnum, ToolingStatusEnum }

/**
 * 生产模块所有枚举的统一导出对象
 */
export const ProductionModuleEnum = {
  workOrder: WorkOrderEnum,
  orderStatus: ProductionOrderStatusEnum,
  executionStatus: ExecutionStatusEnum,
  recordType: RecordTypeEnum,
  toolingType: ToolingTypeEnum,
  toolingStatus: ToolingStatusEnum,
}

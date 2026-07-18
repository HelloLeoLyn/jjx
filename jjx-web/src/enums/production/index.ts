// src/enums/production/index.ts
import { WorkOrderEnum } from './WorkOrderEnum'

// 重新导出所有内容
export * from './WorkOrderEnum'

// 重新导出统一对象
export { WorkOrderEnum }

/**
 * 生产模块所有枚举的统一导出对象
 */
export const ProductionModuleEnum = {
  workOrder: WorkOrderEnum,
}

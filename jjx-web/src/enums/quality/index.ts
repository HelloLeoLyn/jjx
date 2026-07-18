// src/enums/quality/index.ts
import { InspectionEnum } from './InspectionEnum'

// 重新导出所有内容
export * from './InspectionEnum'

// 重新导出统一对象
export { InspectionEnum }

/**
 * 质量模块所有枚举的统一导出对象
 */
export const QualityModuleEnum = {
  inspection: InspectionEnum,
}

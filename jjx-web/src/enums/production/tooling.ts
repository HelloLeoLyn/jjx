// src/enums/production/ToolingEnum.ts 统一导出
import { ToolingTypeEnum, ToolingStatusEnum } from './ToolingEnum'

export * from './ToolingEnum'

export const ToolingModuleEnum = {
  type: ToolingTypeEnum,
  status: ToolingStatusEnum,
}

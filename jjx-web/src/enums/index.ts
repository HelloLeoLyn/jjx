// src/enums/index.ts

// 导出基础工具
export * from './base'

// 导出各模块（使用显式导出避免命名冲突）
export * from './system'
export * from './product'
export * from './sales'
export * from './production'
export * from './quality'
export * from './finance'
export * from './report'
export * from './mold'

// 导入各模块的统一导出对象
import { SystemEnum } from './system'
import { InventoryEnum } from './inventory'
import { SalesEnum } from './sales'
import { ProductionModuleEnum } from './production'
import { QualityModuleEnum } from './quality'
import { FinanceModuleEnum } from './finance'
import { ReportModuleEnum } from './report'
import { MoldModuleEnum } from './mold'
import { PurchaseEnum } from './purchase'
/**
 * 所有模块枚举的统一导出对象
 * 使用方式：import { AppEnum } from '@/enums'
 */
export const AppEnum = {
  system: SystemEnum,
  inventory: InventoryEnum,
  sales: SalesEnum,
  production: ProductionModuleEnum,
  quality: QualityModuleEnum,
  finance: FinanceModuleEnum,
  report: ReportModuleEnum,
  mold: MoldModuleEnum,
  purchase: PurchaseEnum,
}

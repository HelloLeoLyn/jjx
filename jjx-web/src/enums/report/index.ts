// src/enums/report/index.ts
import { ReportEnum } from './ReportEnum'

// 重新导出所有内容
export * from './ReportEnum'

// 重新导出统一对象
export { ReportEnum }

/**
 * 报表模块所有枚举的统一导出对象
 */
export const ReportModuleEnum = {
  report: ReportEnum,
}

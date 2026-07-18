// src/enums/finance/index.ts
import { AccountEnum } from './AccountEnum'

// 重新导出所有内容
export * from './AccountEnum'

// 重新导出统一对象
export { AccountEnum }

/**
 * 财务模块所有枚举的统一导出对象
 */
export const FinanceModuleEnum = {
  account: AccountEnum,
}

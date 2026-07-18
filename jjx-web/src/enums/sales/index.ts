// src/enums/sales/index.ts
import { OrderEnum } from './OrderEnum'

// 重新导出所有内容
export * from './OrderEnum'

// 重新导出统一对象
export { OrderEnum }

/**
 * 销售模块所有枚举的统一导出对象
 */
export const SalesEnum = {
  order: OrderEnum,
}

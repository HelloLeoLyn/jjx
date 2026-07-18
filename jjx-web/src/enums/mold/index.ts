// src/enums/mold/index.ts
import { MoldEnum } from './MoldEnum'

// 重新导出所有内容
export * from './MoldEnum'

// 重新导出统一对象
export { MoldEnum }

/**
 * 模具模块所有枚举的统一导出对象
 */
export const MoldModuleEnum = {
  mold: MoldEnum,
}

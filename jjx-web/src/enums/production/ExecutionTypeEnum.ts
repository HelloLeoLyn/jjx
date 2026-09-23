// src/enums/production/ExecutionTypeEnum.ts
// dev-20260923-041：工序执行类型（正常 / 返工）枚举 —— 页面不再写 `executionType === 'REWORK'` 这种字面量。
import { createEnum } from '../base'

/** production_operation_execution.execution_type —— 工序执行类型 */
export const ExecutionBizType = {
  NORMAL: 'NORMAL',
  REWORK: 'REWORK',
} as const

export const ExecutionBizTypeEnum = createEnum<string>({
  items: [
    { value: ExecutionBizType.NORMAL, label: '正常工序', tagProps: { type: 'info' } },
    { value: ExecutionBizType.REWORK, label: '返工工序', tagProps: { type: 'warning' } },
  ],
  defaultTag: { type: 'info' },
})

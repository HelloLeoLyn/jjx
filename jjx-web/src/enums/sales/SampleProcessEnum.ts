// src/enums/sales/SampleProcessEnum.ts
import { createNamedEnum } from '../base'

/**
 * 打样工序执行状态枚举（样品单工序计划项 SalesSampleProcess.status）
 * 对应后端 SampleOrderServiceImpl：0=待开始（默认），1=进行中，2=已完成（status 校验 0~2）
 * 2026-09-05（任务1426）：替代 sample-workbench 内散落的魔法值比较
 */
export const SampleProcessStatusEnum = createNamedEnum(
  {
    TODO: { value: 0, label: '待开始', tagProps: { type: 'info' } },
    DOING: { value: 1, label: '进行中', tagProps: { type: 'warning' } },
    DONE: { value: 2, label: '已完成', tagProps: { type: 'success' } },
  },
  { type: 'info' }
)

import { createNamedEnum } from '../base'

/** 业务需求单状态，对应后端 RequirementStatusEnum。 */
export const RequirementStatusEnum = createNamedEnum(
  {
    DRAFT: { value: 1, label: '草稿', tagProps: { type: 'info' } },
    REVIEWING: { value: 2, label: '评审中', tagProps: { type: 'warning' } },
    APPROVED: { value: 3, label: '已通过', tagProps: { type: 'success' } },
    EXECUTING: { value: 4, label: '执行中', tagProps: { type: 'primary' } },
    CLOSED: { value: 5, label: '已关闭', tagProps: { type: 'info' } },
    REJECTED: { value: 6, label: '已驳回', tagProps: { type: 'danger' } },
  },
  { type: 'info' }
)

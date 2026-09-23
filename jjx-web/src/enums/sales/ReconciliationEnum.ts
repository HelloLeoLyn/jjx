import { createNamedEnum } from '../base'

export const ReconciliationDueStatusEnum = createNamedEnum(
  {
    PREPAID: { value: 'PREPAID', label: '已预付', tagProps: { type: 'info' } },
    NOT_DUE: { value: 'NOT_DUE', label: '未到期', tagProps: { type: 'success' } },
    DUE_TODAY: { value: 'DUE_TODAY', label: '今日到期', tagProps: { type: 'warning' } },
    OVERDUE: { value: 'OVERDUE', label: '已逾期', tagProps: { type: 'danger' } },
  },
  { type: 'info' }
)

import { createEnum } from '../base'

export const WorkReportStatus = {
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  CANCELLED: 'CANCELLED',
} as const

export const WorkReportStatusEnum = createEnum<string>({
  items: [
    { value: WorkReportStatus.PENDING, label: '待审批', tagProps: { type: 'info' } },
    { value: WorkReportStatus.APPROVED, label: '已通过', tagProps: { type: 'success' } },
    { value: WorkReportStatus.REJECTED, label: '已驳回', tagProps: { type: 'warning' } },
    { value: WorkReportStatus.CANCELLED, label: '已撤销', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

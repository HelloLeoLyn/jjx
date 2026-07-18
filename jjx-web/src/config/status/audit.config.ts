export enum AuditStatus {
  DRAFT = 'draft',
  SUBMITTED = 'submitted',
  REVIEWING = 'reviewing',
  APPROVED = 'approved',
  REJECTED = 'rejected',
}

export const auditStatusConfig = {
  [AuditStatus.DRAFT]: {
    label: '草稿',
    type: 'info',
  },
  [AuditStatus.SUBMITTED]: {
    label: '待审核',
    type: 'warning',
  },
  [AuditStatus.REVIEWING]: {
    label: '审核中',
    type: 'warning',
  },
  [AuditStatus.APPROVED]: {
    label: '已通过',
    type: 'success',
  },
  [AuditStatus.REJECTED]: {
    label: '已驳回',
    type: 'danger',
  },
}

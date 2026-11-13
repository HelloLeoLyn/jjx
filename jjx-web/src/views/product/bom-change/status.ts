import { BomChangeStatusEnum } from '@/enums/product/BomChangeStatusEnum'

/** BOM 变更单状态展示与动作可见性（dev-20261112-009）。 */
export const statusLabel = (status?: string | null): string => {
  if (!status) return '—'
  const found = Object.values(BomChangeStatusEnum).find((item) => item.value === status)
  return found ? found.label : status
}

export const statusType = (
  status?: string | null,
): undefined | 'success' | 'warning' | 'info' => {
  if (status === BomChangeStatusEnum.EFFECTIVE.value) return 'success'
  if (status === BomChangeStatusEnum.PENDING.value || status === BomChangeStatusEnum.APPROVED.value) return 'warning'
  if (status === BomChangeStatusEnum.CANCELLED.value) return 'info'
  return undefined
}

export const canSubmit = (status?: string | null) => status === BomChangeStatusEnum.DRAFT.value
export const canApprove = (status?: string | null) => status === BomChangeStatusEnum.PENDING.value
export const canActivate = (status?: string | null) => status === BomChangeStatusEnum.APPROVED.value
export const canCancel = (status?: string | null) =>
  status !== BomChangeStatusEnum.EFFECTIVE.value && status !== BomChangeStatusEnum.CANCELLED.value

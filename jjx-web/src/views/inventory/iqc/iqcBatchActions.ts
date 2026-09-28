import type { IqcPendingVO } from '@/types/inventory/inbound'
import { InboundOrderStatusEnum } from '@/enums/inventory/InboundEnum'

export type IqcBatchAction = 'inspect' | 'reinspect' | 'review' | 'dispose' | 'scrap' | 'rework' | 'inbound'

/** 一个批次可同时有多项待办；详情入口不参与此列表。 */
export function iqcBatchActions(row: IqcPendingVO) {
  const actions: { key: IqcBatchAction; label: string; permission: string }[] = []
  if (Number(row.pendingInspectionCount) > 0)
    actions.push({ key: 'inspect', label: '检验录入', permission: 'quality:lot:inspect' })
  if (Number(row.pendingReinspectionCount) > 0)
    actions.push({ key: 'reinspect', label: '复检录入', permission: 'quality:lot:inspect' })
  if (Number(row.pendingReviewCount) > 0)
    actions.push({ key: 'review', label: '审核', permission: 'quality:lot:judge' })
  if (Number(row.remainingDispositionQuantity) > 0)
    actions.push({ key: 'dispose', label: '不良处置', permission: 'quality:ncr:dispose' })
  if (Number(row.pendingScrapCount) > 0)
    actions.push({ key: 'scrap', label: '报废审批', permission: 'quality:ncr:dispose' })
  if (Number(row.pendingReworkCount) > 0)
    actions.push({ key: 'rework', label: '完成返工', permission: 'quality:ncr:dispose' })
  if (row.orderStatus === InboundOrderStatusEnum.APPROVED.value)
    actions.push({ key: 'inbound', label: '去确认入库', permission: 'inventory:inbound:confirm' })
  return actions
}

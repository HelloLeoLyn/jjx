import type { IqcPendingVO } from '@/types/inventory/inbound'
import { InboundOrderStatusEnum } from '@/enums/inventory/InboundEnum'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'

export type IqcBatchAction = 'inspect' | 'reinspect' | 'review' | 'dispose' | 'scrap' | 'rework' | 'inbound'

/** 复用统一操作列配置；一个批次可同时有多项待办，详情入口独立。 */
export const iqcBatchActionColumns = [
  { key: 'inspect', label: '检验录入', permission: 'quality:lot:inspect',
    visible: ({ row }) => Number(row.pendingInspectionCount) > 0 },
  { key: 'reinspect', label: '复检录入', permission: 'quality:lot:inspect',
    visible: ({ row }) => Number(row.pendingReinspectionCount) > 0 },
  { key: 'review', label: '审核', permission: 'quality:lot:judge',
    visible: ({ row }) => Number(row.pendingReviewCount) > 0 },
  { key: 'dispose', label: '不良处置', permission: 'quality:ncr:dispose',
    visible: ({ row }) => Number(row.remainingDispositionQuantity) > 0 },
  { key: 'scrap', label: '报废审批', permission: 'quality:ncr:dispose',
    visible: ({ row }) => Number(row.pendingScrapCount) > 0 },
  { key: 'rework', label: '完成返工', permission: 'quality:ncr:dispose',
    visible: ({ row }) => Number(row.pendingReworkCount) > 0 },
  { key: 'inbound', label: '去确认入库', permission: 'inventory:inbound:confirm',
    visible: ({ row }) => row.orderStatus === InboundOrderStatusEnum.APPROVED.value },
] satisfies (TableAction<IqcPendingVO> & { key: IqcBatchAction })[]

export const iqcBatchActions = (row: IqcPendingVO) =>
  iqcBatchActionColumns.filter(action => action.visible({ row, index: 0 }))

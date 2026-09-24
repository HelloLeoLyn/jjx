// src/enums/quality/NcrEnum.ts
// dev-20260923-041：不良台账（quality_ncr / quality_ncr_action）状态与动作类型枚举。
// 目的：页面不再写字符串状态字面量（`row.status === 'VOID'` 这类），门禁 check-status-enums 可当场拦下新增。
import { createEnum } from '../base'

/** quality_ncr.status —— 不良单状态 */
export const QualityNcrStatus = {
  PENDING: 'PENDING',
  DISPOSING: 'DISPOSING',
  CLOSED: 'CLOSED',
  VOID: 'VOID',
} as const

export const QualityNcrStatusEnum = createEnum<string>({
  items: [
    { value: QualityNcrStatus.PENDING, label: '待处置', tagProps: { type: 'danger' } },
    { value: QualityNcrStatus.DISPOSING, label: '处置中', tagProps: { type: 'warning' } },
    { value: QualityNcrStatus.CLOSED, label: '已结', tagProps: { type: 'success' } },
    { value: QualityNcrStatus.VOID, label: '已作废（随批/撤销）', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/** quality_ncr_action.action_type —— 处置动作类型 */
export const NcrActionType = {
  REWORK: 'REWORK',
  CONCESSION: 'CONCESSION',
  SCRAP: 'SCRAP',
  RETURN: 'RETURN',
} as const

export const NcrActionTypeEnum = createEnum<string>({
  items: [
    { value: NcrActionType.REWORK, label: '返工', tagProps: { type: 'primary' } },
    { value: NcrActionType.CONCESSION, label: '让步接收（特采）', tagProps: { type: 'warning' } },
    { value: NcrActionType.SCRAP, label: '报废', tagProps: { type: 'danger' } },
    { value: NcrActionType.RETURN, label: '退货', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/** quality_ncr_action.status —— 处置执行状态 */
export const NcrActionStatus = {
  PENDING: 'PENDING',
  PROCESSING: 'PROCESSING',
  /** dev-20260924-005：超阈值的报废待品质主管审批（未计入台账，件级也不动） */
  PENDING_APPROVAL: 'PENDING_APPROVAL',
  DONE: 'DONE',
  VOID: 'VOID',
} as const

export const NcrActionStatusEnum = createEnum<string>({
  items: [
    { value: NcrActionStatus.PENDING, label: '待执行', tagProps: { type: 'info' } },
    { value: NcrActionStatus.PROCESSING, label: '执行中', tagProps: { type: 'warning' } },
    { value: NcrActionStatus.PENDING_APPROVAL, label: '待审批', tagProps: { type: 'warning' } },
    { value: NcrActionStatus.DONE, label: '已完成', tagProps: { type: 'success' } },
    { value: NcrActionStatus.VOID, label: '已作废', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

export const NcrEnum = {
  status: QualityNcrStatusEnum,
  actionType: NcrActionTypeEnum,
  actionStatus: NcrActionStatusEnum,
}

import { createEnum, createNamedEnum } from '../base'

export const IqcBatchStatus = {
  SOURCE: 'SOURCE',
  PENDING_REINSPECTION: 'PENDING_REINSPECTION',
  REWORKED: 'REWORKED',
  QUALIFIED: 'QUALIFIED',
  FAILED: 'FAILED',
  SCRAPPED: 'SCRAPPED',
  RETURNED: 'RETURNED',
  RELEASED: 'RELEASED',
} as const

export const IqcBatchStatusEnum = createEnum({
  items: [
    { value: IqcBatchStatus.SOURCE, label: '原始批次', tagProps: { type: 'info' } },
    { value: IqcBatchStatus.PENDING_REINSPECTION, label: '待复检', tagProps: { type: 'warning' } },
    { value: IqcBatchStatus.REWORKED, label: '已返工', tagProps: { type: 'primary' } },
    { value: IqcBatchStatus.QUALIFIED, label: '合格', tagProps: { type: 'success' } },
    { value: IqcBatchStatus.FAILED, label: '不合格', tagProps: { type: 'danger' } },
    { value: IqcBatchStatus.SCRAPPED, label: '已报废', tagProps: { type: 'danger' } },
    { value: IqcBatchStatus.RETURNED, label: '已退货', tagProps: { type: 'warning' } },
    { value: IqcBatchStatus.RELEASED, label: '已放行', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * IQC 批次类型（inventory_iqc_batch.batch_type 实际取值，2026-09-21 补枚举，
 * 此前页面直接显示英文码 ORIGINAL/REWORK/REINSPECTION）。
 */
export const IqcBatchTypeEnum = createNamedEnum(
  {
    ORIGINAL: { value: 'ORIGINAL', label: '原始批次', tagProps: { type: 'info' } },
    REWORK: { value: 'REWORK', label: '返工子批次', tagProps: { type: 'warning' } },
    REINSPECTION: { value: 'REINSPECTION', label: '复检子批次', tagProps: { type: 'primary' } },
  },
  { type: 'info' }
)

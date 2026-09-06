import { createEnum } from '../base'

export const IqcQuarantineAction = {
  RELEASE: 'RELEASE',
  RETURN: 'RETURN',
  REWORK: 'REWORK',
  SCRAP: 'SCRAP',
} as const
export const IqcQuarantineActionEnum = createEnum({
  items: [
    { value: IqcQuarantineAction.RELEASE, label: '释放入库', tagProps: { type: 'success' } },
    { value: IqcQuarantineAction.RETURN, label: '退货', tagProps: { type: 'warning' } },
    { value: IqcQuarantineAction.REWORK, label: '返工', tagProps: { type: 'primary' } },
    { value: IqcQuarantineAction.SCRAP, label: '报废', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})
export const IqcQuarantineStatus = {
  PENDING: 'PENDING',
  RELEASED: 'RELEASED',
  RETURNED: 'RETURNED',
  REWORKED: 'REWORKED',
  SCRAPPED: 'SCRAPPED',
} as const

export const IqcQuarantineStatusEnum = createEnum({
  items: [
    { value: 'PENDING', label: '待处置', tagProps: { type: 'warning' } },
    { value: IqcQuarantineStatus.RELEASED, label: '已释放', tagProps: { type: 'success' } },
    { value: IqcQuarantineStatus.RETURNED, label: '已退货', tagProps: { type: 'info' } },
    { value: IqcQuarantineStatus.REWORKED, label: '已返工', tagProps: { type: 'primary' } },
    { value: IqcQuarantineStatus.SCRAPPED, label: '已报废', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

import { createEnum, createNamedEnum } from '../base'

export const IqcQuarantineAction = {
  RELEASE: 'RELEASE',
  RETURN: 'RETURN',
  REWORK: 'REWORK',
  SCRAP: 'SCRAP',
} as const
export const IqcQuarantineActionEnum = createNamedEnum(
  {
    RELEASE: {
      value: IqcQuarantineAction.RELEASE,
      label: '让步接收（特采）',
      tagProps: { type: 'success' },
    },
    RETURN: { value: IqcQuarantineAction.RETURN, label: '退货', tagProps: { type: 'warning' } },
    REWORK: { value: IqcQuarantineAction.REWORK, label: '返工', tagProps: { type: 'primary' } },
    SCRAP: { value: IqcQuarantineAction.SCRAP, label: '报废', tagProps: { type: 'danger' } },
  },
  { type: 'info' }
)
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
    { value: IqcQuarantineStatus.RELEASED, label: '已让步接收', tagProps: { type: 'success' } },
    { value: IqcQuarantineStatus.RETURNED, label: '已退货', tagProps: { type: 'info' } },
    { value: IqcQuarantineStatus.REWORKED, label: '已返工', tagProps: { type: 'primary' } },
    { value: IqcQuarantineStatus.SCRAPPED, label: '已报废', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 处置方式对应的业务后果说明（界面提示用，与后端 handleQuarantine 的实际行为一致）。
 * 集中放这里而不是各页面自己写文案，避免两页面对同一动作的说法不一致。
 */
export const IqcQuarantineActionEffect: Record<string, string> = {
  [IqcQuarantineAction.RELEASE]:
    '影响库存：是。把这部分来料隔离品计入可用库存并保留原检验结论；来料场景不要求客户确认',
  [IqcQuarantineAction.RETURN]: '影响库存：否。生成退货单，货物按退货处理，不进可用库存',
  [IqcQuarantineAction.REWORK]:
    '影响库存：暂不入库。生成供应商返工单与复检子批次，返工完成后需到「来料检验」复检',
  [IqcQuarantineAction.SCRAP]: '影响库存：否。生成报废单，只记台账（不良品未进可用库存）',
}

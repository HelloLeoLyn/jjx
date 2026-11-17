import { createNamedEnum } from '@/enums/base'

/** 加工操作类型，几何结果由后端计算。 */
export const PurchaseProcessingOperationEnum = createNamedEnum(
  {
    SLIT: { value: 'SLIT', label: '分切', tagProps: { type: 'info' } },
    CUT: { value: 'CUT', label: '切片', tagProps: { type: 'info' } },
  },
  { type: 'info' }
)

export const PurchaseProcessingDeliveryEnum = createNamedEnum(
  {
    INBOUND: { value: 'INBOUND', label: '交货入库', tagProps: { type: 'success' } },
    SCRAP: { value: 'SCRAP', label: '报废不入库', tagProps: { type: 'danger' } },
  },
  { type: 'info' }
)

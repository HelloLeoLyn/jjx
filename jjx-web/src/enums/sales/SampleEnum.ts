// src/enums/sales/SampleEnum.ts
import { createNamedEnum } from '../base'

/**
 * 样品单状态枚举
 * 对应后端 SampleOrderStatusEnum
 */
export const SampleOrderStatus = createNamedEnum(
  {
    CREATED: { value: 1, label: '草稿', tagProps: { type: 'info' } },
    REQUEST: { value: 2, label: '待打样', tagProps: { type: 'warning' } },
    ENGINEERING: { value: 3, label: '工程打样中', tagProps: { type: 'warning' } },
    SAMPLE_READY: { value: 4, label: '样品待送样', tagProps: { type: 'primary' } },
    SAMPLE_SENT: { value: 5, label: '已送样待确认', tagProps: { type: 'warning' } },
    CONFIRMED: { value: 6, label: '样品确认', tagProps: { type: 'success' } },
    TRANSFERRED: { value: 7, label: '已转量产', tagProps: { type: 'success' } },
    CLOSED: { value: 8, label: '已关闭', tagProps: { type: 'info' } },
    REJECTED: { value: 9, label: '客户退回', tagProps: { type: 'danger' } },
    CANCELLED: { value: 10, label: '已取消', tagProps: { type: 'danger' } },
  },
  { type: 'info' }
)

/** 兼容现有展示层调用，新业务判断使用 SampleOrderStatus.具名成员。 */
export const SampleOrderStatusEnum = SampleOrderStatus

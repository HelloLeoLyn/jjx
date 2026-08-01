// src/enums/sales/SampleEnum.ts
import { createEnum } from '../base'

/**
 * 样品单状态枚举
 * 对应后端 SampleOrderStatusEnum
 */
export const SampleOrderStatusEnum = createEnum<number>({
  items: [
    { value: 1, label: '样品需求已创建', tagProps: { type: 'info' } },
    { value: 2, label: '待审核', tagProps: { type: 'warning' } },
    { value: 3, label: '工程打样中', tagProps: { type: 'warning' } },
    { value: 4, label: '样品待送样', tagProps: { type: 'primary' } },
    { value: 5, label: '已送样待确认', tagProps: { type: 'warning' } },
    { value: 6, label: '样品确认', tagProps: { type: 'success' } },
    { value: 7, label: '已转量产', tagProps: { type: 'success' } },
    { value: 8, label: '已关闭', tagProps: { type: 'info' } },
    { value: 9, label: '客户退回', tagProps: { type: 'danger' } },
    { value: 10, label: '已取消', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

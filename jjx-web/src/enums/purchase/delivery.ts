import { createEnum } from '@/enums/base'

/**
 * 交货方式枚举
 */
export const DeliveryMethodEnum = createEnum({
  items: [
    { value: 'self_pickup', label: '自提', tagProps: { type: 'info' } },
    { value: 'supplier_delivery', label: '供应商送货', tagProps: { type: 'primary' } },
    { value: 'logistics', label: '物流配送', tagProps: { type: 'success' } },
    { value: 'express', label: '快递', tagProps: { type: 'warning' } },
    { value: 'air', label: '空运', tagProps: { type: 'danger' } },
    { value: 'sea', label: '海运', tagProps: { type: 'info' } },
    { value: 'rail', label: '铁路', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

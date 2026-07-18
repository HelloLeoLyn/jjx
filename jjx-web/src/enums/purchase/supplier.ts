import { createEnum } from '@/enums/base'

/**
 * 供应商类型枚举
 */
export const SupplierTypeEnum = createEnum({
  items: [
    { value: 'M', label: '原材料供应商', tagProps: { type: 'primary' } },
    { value: 'E', label: '设备供应商', tagProps: { type: 'success' } },
    { value: 'O', label: '其他供应商', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 供应商状态枚举
 */
export const SupplierStatusEnum = createEnum({
  items: [
    { value: 1, label: '正常', tagProps: { type: 'success' } },
    { value: 0, label: '停用', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

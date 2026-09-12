import { createEnum } from '@/enums/base'

/**
 * 供应商类型枚举
 */
export const SupplierTypeEnum = createEnum({
  items: [
    { value: 'R', label: '原材料', tagProps: { type: 'primary' } },
    { value: 'A', label: '辅助材料', tagProps: { type: 'warning' } },
    { value: 'I', label: '油墨', tagProps: { type: 'danger' } },
    { value: 'F', label: '成品', tagProps: { type: 'success' } },
    { value: 'E', label: '设备', tagProps: { type: 'info' } },
    { value: 'O', label: '其他', tagProps: { type: 'info' } },
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

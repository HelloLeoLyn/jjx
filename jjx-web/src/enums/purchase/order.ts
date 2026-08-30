import { createEnum } from '@/enums/base'

/**
 * 采购订单类型枚举
 * normal=正常, urgent=紧急, reorder=补单, return=退货, sample=样品
 */
export const PurchaseOrderTypeEnum = createEnum({
  items: [
    { value: 'normal', label: '正常', tagProps: { type: 'primary' } },
    { value: 'urgent', label: '紧急', tagProps: { type: 'danger' } },
    { value: 'reorder', label: '补单', tagProps: { type: 'warning' } },
    { value: 'return', label: '退货单', tagProps: { type: 'info' } },
    { value: 'sample', label: '样品单', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 紧急标志枚举
 */
export const UrgentFlagEnum = createEnum({
  items: [
    { value: false, label: '正常', tagProps: { type: 'info' } },
    { value: true, label: '紧急', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})
/**
 * 审批状态枚举
 * 1=草稿, 2=待审批, 3=已批准, 4=已拒绝, 5=已取消
 */

export const ApprovalStatusEnum = createEnum({
  items: [
    { value: 1, label: '草稿', tagProps: { type: 'info' } },
    { value: 2, label: '待审批', tagProps: { type: 'warning' } },
    { value: 3, label: '已批准', tagProps: { type: 'success' } },
    { value: 4, label: '已拒绝', tagProps: { type: 'danger' } },
    { value: 5, label: '已取消', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 采购订单状态枚举
 * 对应后端 PurchaseOrderStatusEnum
 */
export const PurchaseOrderStatusEnum = createEnum({
  items: [
    { value: 0, label: '草稿', tagProps: { type: 'info' } },
    { value: 1, label: '询价中', tagProps: { type: 'warning' } },
    { value: 2, label: '比价中', tagProps: { type: 'warning' } },
    { value: 3, label: '已提交', tagProps: { type: 'primary' } },
    { value: 4, label: '已批准', tagProps: { type: 'success' } },
    { value: 5, label: '执行中', tagProps: { type: 'primary' } },
    { value: 6, label: '已完成', tagProps: { type: 'success' } },
    { value: 7, label: '已关闭', tagProps: { type: 'info' } },
    { value: 8, label: '已取消', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

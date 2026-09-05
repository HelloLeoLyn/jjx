// src/enums/inventory/InboundEnum.ts
import { createEnum, createNamedEnum } from '../base'

/**
 * 入库类型枚举
 */
export const InboundTypeEnum = createEnum({
  items: [
    { value: 'purchase', label: '采购入库', tagProps: { type: 'primary' } },
    { value: 'production', label: '生产入库', tagProps: { type: 'success' } },
    { value: 'return', label: '退货入库', tagProps: { type: 'warning' } },
    { value: 'transfer', label: '调拨入库', tagProps: { type: 'info' } },
    { value: 'adjust', label: '盘盈入库', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 检验结果枚举
 */
export const InspectionResultEnum = createNamedEnum(
  {
    PASS: { value: 'PASS', label: '合格', tagProps: { type: 'success' } },
    FAIL: { value: 'FAIL', label: '不合格', tagProps: { type: 'danger' } },
    OTHER: { value: 'OTHER', label: '其它', tagProps: { type: 'warning' } },
  },
  { type: 'info' },
)

/**
 * 入库单状态枚举
 */
export const InboundOrderStatusEnum = createNamedEnum(
  {
    DRAFT: { value: 0, label: '草稿', tagProps: { type: 'info' } },
    PENDING: { value: 1, label: '待审批', tagProps: { type: 'warning' } },
    APPROVED: { value: 2, label: '已批准', tagProps: { type: 'success' } },
    REJECTED: { value: 3, label: '已驳回', tagProps: { type: 'danger' } },
    PROCESSING: { value: 4, label: '处理中', tagProps: { type: 'warning' } },
    CONFIRMED: { value: 5, label: '已确认', tagProps: { type: 'success' } },
    OUT_CONFIRM: { value: 6, label: '已出库', tagProps: { type: 'success' } },
    IN_CONFIRM: { value: 7, label: '已入库', tagProps: { type: 'success' } },
    CLOSED: { value: 8, label: '已关闭', tagProps: { type: 'info' } },
    CANCELLED: { value: 9, label: '已取消', tagProps: { type: 'danger' } },
    COMPLETED: { value: 10, label: '已完成', tagProps: { type: 'success' } },
    PROCESSED: { value: 11, label: '已处理', tagProps: { type: 'success' } },
    IN_PROGRESS: { value: 12, label: '调拨中', tagProps: { type: 'warning' } },
  },
  { type: 'info' },
)

/**
 * 审批状态枚举
 */
export const ApproveStatusEnum = createEnum({
  items: [
    { value: 'pending', label: '待审批', tagProps: { type: 'warning' } },
    { value: 'approved', label: '已批准', tagProps: { type: 'success' } },
    { value: 'rejected', label: '已驳回', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

export const InboundSourceTypeEnum = createEnum({
  items: [
    { value: 'purchase_order', label: '采购订单', tagProps: { type: 'primary' } },
    { value: 'work_order', label: '生产工单', tagProps: { type: 'primary' } },
    { value: 'sales_return', label: '销售退货', tagProps: { type: 'primary' } },
    { value: 'transfer_order', label: '调拨单', tagProps: { type: 'primary' } },
    { value: 'other', label: '其他', tagProps: { type: 'primary' } },
  ],
  defaultTag: { type: 'primary' },
})

/**
 * 入库相关枚举统一导出
 */
export const InboundEnum = {
  type: InboundTypeEnum,
  inspectionResult: InspectionResultEnum,
  orderStatus: InboundOrderStatusEnum,
  approveStatus: ApproveStatusEnum,
  sourceType: InboundSourceTypeEnum,
}

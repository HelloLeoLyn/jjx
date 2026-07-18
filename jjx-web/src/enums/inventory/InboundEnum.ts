// src/enums/inventory/InboundEnum.ts
import { createEnum } from '../base'

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
export const InspectionResultEnum = createEnum({
  items: [
    { value: 'pass', label: '合格', tagProps: { type: 'success' } },
    { value: 'fail', label: '不合格', tagProps: { type: 'danger' } },
    { value: 'partial', label: '部分合格', tagProps: { type: 'warning' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 入库单状态枚举
 */
export const InboundOrderStatusEnum = createEnum({
  items: [
    { value: 'draft', label: '草稿', tagProps: { type: 'info' } },
    { value: 'confirmed', label: '已确认', tagProps: { type: 'primary' } },
    { value: 'closed', label: '已关闭', tagProps: { type: 'success' } },
    { value: 'cancelled', label: '已取消', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

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

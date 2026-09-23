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
  { type: 'info' }
)

export const IqcDispositionEnum = createNamedEnum(
  {
    RETURN: { value: 'RETURN', label: '退货', tagProps: { type: 'danger' } },
    SUPPLIER_REWORK: {
      value: 'SUPPLIER_REWORK',
      label: '供应商来厂重工',
      tagProps: { type: 'warning' },
    },
    INTERNAL_SORT: {
      value: 'INTERNAL_SORT',
      label: '内部挑选/返工',
      tagProps: { type: 'warning' },
    },
    PARTIAL_ACCEPT: {
      value: 'PARTIAL_ACCEPT',
      label: '部分接收',
      tagProps: { type: 'warning' },
    },
    CONCESSION: { value: 'CONCESSION', label: '让步接收', tagProps: { type: 'success' } },
    SCRAP: { value: 'SCRAP', label: '报废', tagProps: { type: 'danger' } },
    REINSPECT: { value: 'REINSPECT', label: '待复检', tagProps: { type: 'info' } },
    HOLD: { value: 'HOLD', label: '待定/隔离', tagProps: { type: 'info' } },
  },
  { type: 'info' }
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
  { type: 'info' }
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
 * 入库类型取值归一（dev-20260923-012）
 * 库里的 inbound_type 是大写历史值（PURCHASE / PRODUCTION_FINISH），
 * 枚举定义是小写（purchase / production），这里做唯一映射，页面不再各写一份。
 */
const INBOUND_TYPE_ALIASES: Record<string, string> = {
  PRODUCTION_FINISH: 'production',
  PURCHASE_ORDER: 'purchase',
}

/** 入库单文案上下文：来源与后端下发的名称（仅兜底用） */
export interface InboundLabelContext {
  sourceType?: string | null
  inboundType?: string | null
  /** 后端 statusName / inboundTypeName；枚举判定不出时才使用 */
  fallbackName?: string | null
}

/** 生产来源入库单：source_type=PRODUCTION 或 inbound_type=production/production_finish（dev-20260923-012） */
export function isProductionInbound(
  sourceType?: string | null,
  inboundType?: string | null
): boolean {
  const source = (sourceType || '').trim().toUpperCase()
  const type = (inboundType || '').trim().toUpperCase()
  return (
    source === 'PRODUCTION' ||
    source === 'PRODUCTION_FINISH' ||
    type === 'PRODUCTION' ||
    type === 'PRODUCTION_FINISH'
  )
}

/**
 * 红冲单：复检换代生成的冲销单 —— 单号后缀 -R 或数量为负（dev-20260923-012）
 */
export function isReverseInbound(
  inboundNo?: string | null,
  totalQuantity?: number | string | null
): boolean {
  if (String(inboundNo || '').trim().endsWith('-R')) {
    return true
  }
  const quantity = Number(totalQuantity)
  return Number.isFinite(quantity) && quantity < 0
}

/**
 * 入库单状态文案（dev-20260923-012）
 * 生产来源的 PENDING 语义是「等仓库确认入库」，不是通用审批的「待审批」；
 * 采购侧维持「待审批」。
 */
export function inboundStatusLabel(
  status?: number | null,
  ctx: InboundLabelContext = {}
): string {
  if (status === undefined || status === null) {
    return ctx.fallbackName || '-'
  }
  if (
    isProductionInbound(ctx.sourceType, ctx.inboundType) &&
    status === InboundOrderStatusEnum.PENDING.value
  ) {
    return '待确认入库'
  }
  if (InboundOrderStatusEnum.canDo(status)) {
    return InboundOrderStatusEnum.getLabel(status)
  }
  return ctx.fallbackName || String(status)
}

/**
 * 入库类型文案（dev-20260923-012）
 * 红冲单在类型列标注「生产入库（红冲）」，不再只靠单号后缀与红色标签识别。
 */
export function inboundTypeLabel(
  inboundType?: string | null,
  ctx: InboundLabelContext & { reverse?: boolean } = {}
): string {
  const raw = (inboundType || '').trim()
  const normalized = INBOUND_TYPE_ALIASES[raw.toUpperCase()] || raw.toLowerCase()
  const base = InboundTypeEnum.canDo(normalized)
    ? InboundTypeEnum.getLabel(normalized)
    : ctx.fallbackName || raw || '-'
  return ctx.reverse && base !== '-' ? `${base}（红冲）` : base
}

/**
 * 入库相关枚举统一导出
 */
export const InboundEnum = {
  type: InboundTypeEnum,
  inspectionResult: InspectionResultEnum,
  iqcDisposition: IqcDispositionEnum,
  orderStatus: InboundOrderStatusEnum,
  approveStatus: ApproveStatusEnum,
  sourceType: InboundSourceTypeEnum,
}

// src/enums/system/AttachmentEnum.ts
import { createEnum } from '../base'

/**
 * 附件生命周期状态（与后端 AttachmentUploadState 一一对应）
 * LEGACY 历史行保持人工复核；TEMP 临时上传（7 天）；BOUND 已建立引用；ORPHAN 最后一条引用解除（30 天）
 */
export const AttachmentUploadStateEnum = createEnum({
  items: [
    { value: 'LEGACY', label: '历史附件', tagProps: { type: 'info' } },
    { value: 'TEMP', label: '待绑定', tagProps: { type: 'warning' } },
    { value: 'BOUND', label: '已绑定', tagProps: { type: 'success' } },
    { value: 'ORPHAN', label: '已解绑', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 附件来源类型（与后端 AttachmentSourcePolicy 白名单一致；含旧别名归一）
 */
export const AttachmentSourceTypeEnum = createEnum({
  items: [
    { value: 'product', label: '产品', tagProps: { type: 'primary' } },
    { value: 'inquiry', label: '询价单', tagProps: { type: 'primary' } },
    { value: 'quotation', label: '报价单', tagProps: { type: 'primary' } },
    { value: 'sales_order', label: '销售订单', tagProps: { type: 'primary' } },
    { value: 'sample', label: '样品单', tagProps: { type: 'primary' } },
    { value: 'purchase_order', label: '采购订单', tagProps: { type: 'info' } },
    { value: 'production_order', label: '生产工单', tagProps: { type: 'info' } },
    { value: 'material', label: '物料', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 来源类型 → 后端权限码前缀映射（唯一真源是后端 AttachmentSourcePolicy；
 * 前端仅用于提前禁用按钮，后端始终是最终裁决方）
 */
export const ATTACHMENT_SOURCE_PERMISSION: Record<string, string> = {
  product: 'product',
  inquiry: 'sales:inquiry',
  quotation: 'sales:quotation',
  sales_order: 'sales:order',
  sample: 'sales:sample',
  purchase_order: 'purchase:order',
  production_order: 'production:order',
  material: 'inventory:material',
}

/** 旧别名 → 规范来源（与后端 AttachmentSourcePolicy.canonical 同表） */
export const ATTACHMENT_SOURCE_ALIAS: Record<string, string> = {
  order: 'sales_order',
  sample_order: 'sample',
  purchase: 'purchase_order',
  production: 'production_order',
}

/**
 * 来源类型 → 单号前缀编码（用于附件展示名拼装）。
 * 真源：`jjx-docs/design/doc-no-rules-dev-20260922-023.md` §4「前缀登记表」——直接复用单号前缀，不另造码。
 * 注：非 status 值，故意不用 createEnum（避免状态魔法值门禁误伤）。
 */
export const ATTACHMENT_SOURCE_CODE: Record<string, string> = {
  product: 'PROD',
  inquiry: 'INQ',
  quotation: 'QT',
  sales_order: 'SO',
  sample: 'SP',
  purchase_order: 'PO',
  production_order: 'WO',
  material: 'MTR',
}

/** 取来源类型编码；未知来源回退大写原值（不静默为空） */
export function attachmentSourceCode(source?: string | null): string {
  if (!source) return ''
  return ATTACHMENT_SOURCE_CODE[source] || source.toUpperCase()
}

/** 归一来源类型；未知来源原样返回（由后端 fail-closed 拒绝） */
export function canonicalAttachmentSource(source?: string | null): string {
  if (!source) return ''
  return ATTACHMENT_SOURCE_ALIAS[source] || source
}

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

/**
 * 一级资料分类编码 → 短码（仅供展示名拼装；**真源仍是字典** `attachment_category.item_key`，
 * 这里只是一张"编码 → 短码"对照表，用来把名字写短）。未知编码回退原键，不静默为空。
 */
export const ATTACHMENT_CATEGORY_CODE: Record<string, string> = {
  BUSINESS_DOC: 'BIZ',
  CUSTOMER_DOC: 'CUS',
  ENGINEERING_DOC: 'ENG',
  GENERAL: 'GEN',
  QUALITY_DOC: 'QUA',
}

/** 二级资料类型编码 → 短码（同上；真源是字典 attachment_document_type.item_key） */
export const ATTACHMENT_DOCUMENT_TYPE_CODE: Record<string, string> = {
  CONTRACT: 'CT',
  ORDER_CONFIRMATION: 'OC',
  SALES_INQUIRY_ATTACHMENT: 'IA',
  SALES_QUOTATION_ATTACHMENT: 'QA',
  CUSTOMER_APPROVAL: 'CA',
  CUSTOMER_DRAWING: 'CD',
  CUSTOMER_REQUIREMENT: 'CR',
  CUSTOMER_SAMPLE: 'CS',
  PRODUCT_IMAGE: 'PI',
  PRODUCT_SPECIFICATION: 'PS',
  BOM: 'BOM',
  DIE_CUTTING_PARAMETER: 'DP',
  DIE_DRAWING: 'DD',
  ENGINEERING_DRAWING: 'ED',
  INK_MIXING_RECORD: 'IM',
  PRINTING_PROCESS: 'PP',
  PROCESS_ROUTE: 'PR',
  WORK_INSTRUCTION: 'WI',
  OTHER: 'OT',
  ABNORMAL_REPORT: 'AR',
  CERTIFICATE_OF_CONFORMITY: 'CC',
  DEFECT_PHOTO: 'DF',
  INSPECTION_REPORT: 'IR',
  TEST_RECORD: 'TR',
  // 2026-10-13 M1（dev-20261013-038）：工程技术资料下新增 8 个二级（对应原 19 类别的图种/生产资料）
  STRUCTURE_DRAWING: 'SD',
  OUTLINE_DRAWING: 'OD',
  PANEL_DRAWING: 'PD',
  CIRCUIT_DRAWING: 'CI',
  FILM: 'FL',
  CONFIRM_DRAWING: 'CF',
  PRINT_GUIDE: 'PG',
  COLOR_CHECK: 'CK',
}

/** 一级编码 → 短码；未知回退原键 */
export function attachmentCategoryCode(itemKey?: string | null): string {
  if (!itemKey) return ''
  return ATTACHMENT_CATEGORY_CODE[itemKey] || itemKey
}

/** 二级编码 → 短码；未知回退原键 */
export function attachmentDocumentTypeCode(itemKey?: string | null): string {
  if (!itemKey) return ''
  return ATTACHMENT_DOCUMENT_TYPE_CODE[itemKey] || itemKey
}

/** 归一来源类型；未知来源原样返回（由后端 fail-closed 拒绝） */
export function canonicalAttachmentSource(source?: string | null): string {
  if (!source) return ''
  return ATTACHMENT_SOURCE_ALIAS[source] || source
}

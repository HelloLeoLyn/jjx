import type { InventoryMaterial } from './material'
import type { SysTag } from '@/types/system/tag'

export type AttributeValue = string | number | boolean | string[] | null
export interface DictionaryOption {
  code: string
  label: string
}
export interface MaterialAttributeField {
  key: string
  label: string
  type: 'enum' | 'multienum' | 'string' | 'integer' | 'decimal' | 'boolean'
  required: boolean
  unit?: string | null
  options?: DictionaryOption[] | null
  min?: number | null
  max?: number | null
  precision?: number | null
  default?: AttributeValue
  visible_when?: Record<string, string> | null
}
export interface MaterialAttributeSchema {
  version: number
  meta_options: string[]
  meta_required?: boolean
  default_meta?: string | null
  default_form?: string | null
  default_unit?: string | null
  fields: MaterialAttributeField[]
}
export interface MaterialMasterCategory {
  categoryId: number
  categoryCode: string
  categoryName: string
  parentId: number | null
  categoryLevel: number
  materialType: string
  pathName: string
  selectable: boolean
  sortOrder?: number
  status?: number
}
export interface MaterialMasterConfiguration {
  category: MaterialMasterCategory
  path: MaterialMasterCategory[]
  template: { templateId: number; templateCode: string; templateName: string; status: number }
  schema: MaterialAttributeSchema
}
export interface MaterialMasterForm {
  readonly templateId?: number | null
  readonly thickness?: string | null
  readonly widthSize?: string | null
  materialId?: number
  materialCode?: string
  categoryId: number | null
  /** 正式名称：默认按 材料+型号+颜色（品牌）生成，可人工改（dev-20261024-004） */
  materialName: string
  /** 前端标记：用户手改过名称后停止自动同步，不提交给后端 */
  nameManual?: boolean
  materialMeta: string
  materialForm: string
  model: string
  color: string
  brand: string
  size: string
  unit: string
  attributeJson: Record<string, AttributeValue>
  isCustomerSupplied: boolean
  isSample: boolean
  searchKeywords: string
  remark: string
  supplierId?: number | null
  supplierName: string
  defaultWarehouseId?: number | null
  defaultLocationId?: number | null
  safeStock?: number | null
  maxStock?: number | null
  reorderPoint?: number | null
  standardPrice?: number | null
  leadTime?: number | null
  batchControl: boolean
  shelfLife?: number | null
  expiryAlertDays?: number | null
  tagIds: number[]
}
export type MaterialMasterRecord = InventoryMaterial & {
  materialMeta?: string | null
  materialForm?: string | null
  attributeJson?: string | null
  isCustomerSupplied?: boolean
  isSample?: boolean
}
export interface MaterialMasterDetail {
  material: MaterialMasterRecord
  attributes: Record<string, AttributeValue>
  configuration: MaterialMasterConfiguration | null
  tags: SysTag[]
  source: { originalName: string | null; originalSpecification: string | null }
}
export type MaterialDictionaries = Record<string, DictionaryOption[]>
export interface MaterialImportResult {
  successCount: number
  errors: string[]
}

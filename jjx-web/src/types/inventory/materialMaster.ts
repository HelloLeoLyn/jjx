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
  common?: boolean | null
  in_spec?: boolean | null
  spec_order?: number | null
  true_label?: string | null
  false_label?: string | null
}
export interface MaterialAttributeSchema {
  version: number
  meta_options: string[]
  meta_required?: boolean
  default_meta?: string | null
  default_form?: string | null
  default_unit?: string | null
  fields: MaterialAttributeField[]
  /** 命名规则：勾选参与命名的字段与顺序（品类维度配置，dev-20261028-005） */
  name_pattern?: MaterialNamingPattern | null
}
export interface MaterialNamingPattern {
  /** 字段编码：公共字段 brand/model/color/meta/form/unit + 本品类属性字段 */
  fields: string[]
  separator: string
  /** 缺值处理：skip 跳过 / placeholder 占位 — */
  empty: 'skip' | 'placeholder'
}
export interface MaterialNamingPreview {
  samples: { materialId: number; materialCode: string; oldName: string; newName: string | null }[]
  message: string
}
export interface MaterialRenameChange {
  materialId: number
  materialCode: string
  oldName: string
  newName: string | null
  /** null 表示会改名；blank=规则生成空名、same=与当前名称一致 */
  skipReason: string | null
}
export interface MaterialRenameReport {
  total: number
  changeable: number
  samples: MaterialRenameChange[]
  message: string
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
  widthMm?: number | null
  lengthMm?: number | null
  thicknessMm?: number | null
  sizeUnit?: string | null
  readonly specBasis?: string | null
  /** 克重 g/m²：与厚度二选一，填了即克重口径 */
  grammage?: number | null
  /** 厚度公差 ±mm */
  thicknessTolerance?: number | null
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
  /** 后端拼装的只读展示值；不提交。 */
  readonly size?: string
  unit: string
  attributeJson: Record<string, AttributeValue>
  isCustomerSupplied: boolean
  isSample: boolean
  searchKeywords: string
  remark: string
  supplierId?: number | null
  supplierName: string
  /** 供应商侧品名/叫法 */
  supplierMaterialName?: string
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
  specificationGeneration?: { specification: string | null; generated: boolean; message: string }
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

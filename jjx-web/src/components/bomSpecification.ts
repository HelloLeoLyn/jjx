import type { AttributeValue, MaterialAttributeField, MaterialAttributeSchema, MaterialMasterDetail } from '@/types/inventory/materialMaster'

export interface BomSpecificationSource {
  specification?: string | null
  size?: string | null
  attributes?: Record<string, AttributeValue>
  schema?: MaterialAttributeSchema | null
}

export function specificationSource(detail: MaterialMasterDetail): BomSpecificationSource {
  return { specification: detail.material.specification, size: detail.material.size, attributes: detail.attributes, schema: detail.configuration?.schema }
}

export function participatesInSpecification(field: MaterialAttributeField): boolean {
  return field.key !== 'thickness_mm' && (field.in_spec ?? field.key !== 'component_type')
}

/** BOM 规格只读取物料档案，不在前端拼装属性或尺寸。 */
export function generateBomSpecification(source: BomSpecificationSource): string {
  return source.specification ?? ''
}

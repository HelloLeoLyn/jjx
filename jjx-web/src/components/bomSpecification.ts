import type { AttributeValue, MaterialAttributeField, MaterialAttributeSchema, MaterialMasterDetail } from '@/types/inventory/materialMaster'

export interface BomSpecificationSource {
  size?: string | null
  attributes?: Record<string, AttributeValue>
  schema?: MaterialAttributeSchema | null
}

export function specificationSource(detail: MaterialMasterDetail): BomSpecificationSource {
  return { size: detail.material.size, attributes: detail.attributes, schema: detail.configuration?.schema }
}

export function participatesInSpecification(field: MaterialAttributeField): boolean {
  return field.key !== 'thickness_mm' && (field.in_spec ?? field.key !== 'component_type')
}

/** 两项均为中文时连写；相邻任一项含英文或数字时以单空格分隔。 */
export function joinSpecificationValues(values: string[]): string {
  const parts = values.map(value => value.trim()).filter(Boolean)
  return parts.map((part, index) => (index && /[A-Za-z0-9]/.test(parts[index - 1] + part) ? ' ' : '') + part).join('')
}

/** BOM 唯一默认规格组合器。后端只提供原始尺寸、属性和模板。 */
export function generateBomSpecification(source: BomSpecificationSource): string {
  const size = source.size?.trim()
  if (size) return size
  const values = source.attributes || {}
  const fields = (source.schema?.fields || []).map((field, index) => ({ field, index }))
    .filter(({ field }) => participatesInSpecification(field))
    .sort((a, b) => (a.field.spec_order ?? a.index) - (b.field.spec_order ?? b.index) || a.index - b.index)
  const parts = fields.flatMap(({ field }) => {
    if (field.visible_when && !Object.entries(field.visible_when).every(([key, value]) => String(values[key]) === value)) return []
    const value = values[field.key]
    if (value == null || value === '' || Array.isArray(value) && !value.length) return []
    const label = (code: string) => field.options?.find(option => option.code === code)?.label || code
    const display = typeof value === 'boolean' ? value ? field.true_label || '是' : field.false_label || '否'
      : Array.isArray(value) ? joinSpecificationValues(value.map(label))
        : field.type === 'enum' ? label(String(value)) : String(value).trim()
    return display ? [display + (field.unit && !display.endsWith(field.unit) ? field.unit : '')] : []
  })
  const result = joinSpecificationValues(parts)
  if (result.length > 500) throw new Error('生成规格超过500字，请减少参与规格的属性')
  return result
}

import type {
  AttributeValue,
  MaterialAttributeField,
  MaterialAttributeSchema,
  MaterialMasterConfiguration,
  MaterialMasterForm,
} from '@/types/inventory/materialMaster'

export function emptyMasterForm(): MaterialMasterForm {
  return {
    categoryId: null,
    originalName: '',
    originalSpecification: '',
    materialMeta: '',
    materialForm: '',
    model: '',
    color: '',
    brand: '',
    size: '',
    unit: '',
    attributeJson: {},
    isCustomerSupplied: false,
    isSample: false,
    searchKeywords: '',
    specSupplement: '',
    remark: '',
    supplierName: '',
    batchControl: false,
    tagIds: [],
    safeStock: 0,
    maxStock: 0,
    reorderPoint: 0,
    expiryAlertDays: 30,
  }
}

export function attributeVisible(
  field: MaterialAttributeField,
  values: Record<string, AttributeValue>
): boolean {
  return (
    !field.visible_when ||
    Object.entries(field.visible_when).every(([key, value]) => String(values[key]) === value)
  )
}

export function attributeDefaults(schema: MaterialAttributeSchema): Record<string, AttributeValue> {
  return Object.fromEntries(
    schema.fields.filter((f) => f.default != null).map((f) => [f.key, f.default!])
  )
}

export function changeMasterCategory(
  form: MaterialMasterForm,
  config: MaterialMasterConfiguration
): void {
  form.categoryId = config.category.categoryId
  form.materialMeta =
    config.schema.default_meta ||
    (config.schema.meta_options.length === 1 ? config.schema.meta_options[0] : '')
  form.materialForm = config.schema.default_form || ''
  form.unit = config.schema.default_unit || ''
  form.attributeJson = attributeDefaults(config.schema)
  form.specSupplement = ''
}

export function pruneAttributes(
  schema: MaterialAttributeSchema,
  values: Record<string, AttributeValue>
): Record<string, AttributeValue> {
  return Object.fromEntries(
    schema.fields
      .filter((f) => attributeVisible(f, values) && values[f.key] != null && values[f.key] !== '')
      .map((f) => [f.key, values[f.key]])
  )
}

export function previewMasterSpecification(
  schema: MaterialAttributeSchema | undefined,
  values: Record<string, AttributeValue>,
  supplement: string
): string {
  const parts = (schema?.fields || []).flatMap((f) => {
    const value = values[f.key]
    if (
      !attributeVisible(f, values) ||
      value == null ||
      value === '' ||
      (Array.isArray(value) && !value.length)
    )
      return []
    const label = (code: string) => f.options?.find((o) => o.code === code)?.label || code
    const display =
      typeof value === 'boolean'
        ? value
          ? '是'
          : '否'
        : Array.isArray(value)
          ? value.map(label).join('、')
          : f.type === 'enum'
            ? label(String(value))
            : String(value)
    return [`${f.label}:${display}${f.unit || ''}`]
  })
  if (supplement.trim()) parts.push(supplement.trim())
  return parts.join('；')
}

export function downloadMaterialBlob(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  URL.revokeObjectURL(url)
}

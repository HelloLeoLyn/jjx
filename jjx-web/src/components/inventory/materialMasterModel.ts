import type {
  AttributeValue,
  DictionaryOption,
  MaterialAttributeField,
  MaterialAttributeSchema,
  MaterialMasterConfiguration,
  MaterialMasterForm,
} from '@/types/inventory/materialMaster'
import { composeMaterialName } from './materialMasterFields'

/** 属性分组口径：常用属性默认展开，其余放进「低频属性」折叠（dev-20261024-004）。 */
export const COMMON_ATTRIBUTE_KEYS = [
  'thickness_mm',
  'gram_weight_gsm',
  'surface_treatment',
  'adhesive_type',
  'adhesiveness',
  'release_force_class',
  'component_type',
  'foil_type',
  'optical_type',
  'ink_type',
]

const labelOf = (options: DictionaryOption[] | undefined, code?: string | null) =>
  code ? options?.find((o) => o.code === code)?.label || code : ''

export function emptyMasterForm(): MaterialMasterForm {
  return {
    categoryId: null,
    materialName: '',
    nameManual: false,
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

/**
 * 名称口径：材料 + 型号 + 颜色（品牌），无空格、缺项跳过、品牌全角括号。
 * 材料取材质字典的中文标签（PC/PET…），与前端 materialMasterFields.composeMaterialName 同源。
 */
export function previewMasterName(
  form: Pick<MaterialMasterForm, 'materialMeta' | 'model' | 'color' | 'brand'>,
  dictionaries: Record<string, DictionaryOption[]>
): string {
  return composeMaterialName({
    materialGrade: labelOf(dictionaries.material_meta, form.materialMeta),
    model: form.model,
    color: labelOf(dictionaries.color, form.color),
    brand: labelOf(dictionaries.brand, form.brand),
  })
}

/** 最终名称：人工改过就用人工值，否则用规则生成值。 */
export function effectiveMasterName(
  form: MaterialMasterForm,
  dictionaries: Record<string, DictionaryOption[]>
): string {
  const generated = previewMasterName(form, dictionaries)
  return form.nameManual ? form.materialName.trim() || generated : generated
}

/** 属性依据（重算时展示取自哪些属性）。 */
export function attributeEvidence(
  schema: MaterialAttributeSchema | undefined,
  values: Record<string, AttributeValue>
): string[] {
  return (schema?.fields || []).flatMap((field) => {
    const value = values[field.key]
    if (!attributeVisible(field, values) || value == null || value === '' || (Array.isArray(value) && !value.length)) {
      return []
    }
    const label = (code: string) => field.options?.find((o) => o.code === code)?.label || code
    const display = typeof value === 'boolean'
      ? value ? '是' : '否'
      : Array.isArray(value)
        ? value.map(label).join('、')
        : field.type === 'enum' ? label(String(value)) : String(value)
    return [`${field.label}：${display}${field.unit || ''}`]
  })
}

export function previewMasterSpecification(
  schema: MaterialAttributeSchema | undefined,
  values: Record<string, AttributeValue>,
  dimensions: { thickness?: string | null; widthSize?: string | null } | '' = {}
): string {
  if (!schema?.fields.length) return ''
  // 属性详情旧调用仅传空串；不再接受任意规格补充文本。
  if (dimensions === '') dimensions = {}
  values = { ...values }
  const thicknessField = schema.fields.find((f) => ['thickness_mm', 'gram_weight_gsm'].includes(f.key))
  if (thicknessField && values[thicknessField.key] == null && dimensions.thickness?.trim()) {
    values[thicknessField.key] = dimensions.thickness
  }
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
  if (dimensions.widthSize?.trim()) parts.push(`尺寸:${dimensions.widthSize.trim()}mm`)
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

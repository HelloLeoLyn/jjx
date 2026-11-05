/**
 * 单条「重算名称与规格」纯逻辑（dev-20261024-004）。
 * 只负责对比与最终取值；名称与规格生成规则由后端提供。
 */
import { dimensionField, displayDimensionOf } from './materialSize'
import type {
  AttributeValue,
  MaterialAttributeSchema,
  MaterialDictionaries,
  MaterialMasterRecord,
} from '@/types/inventory/materialMaster'

export interface CharacterDiffPart {
  text: string
  kind: 'equal' | 'removed' | 'added'
}

/** 最长公共子序列按 Unicode 字符比对，只展示差异，不参与生成或保存。 */
export function diffCharacters(current: string, generated: string) {
  const before = Array.from(current)
  const after = Array.from(generated)
  const lengths = Array.from({ length: before.length + 1 }, () => new Uint16Array(after.length + 1))
  for (let i = before.length - 1; i >= 0; i--) {
    for (let j = after.length - 1; j >= 0; j--) {
      lengths[i][j] =
        before[i] === after[j]
          ? lengths[i + 1][j + 1] + 1
          : Math.max(lengths[i + 1][j], lengths[i][j + 1])
    }
  }
  const oldParts: CharacterDiffPart[] = []
  const newParts: CharacterDiffPart[] = []
  const append = (parts: CharacterDiffPart[], text: string, kind: CharacterDiffPart['kind']) => {
    const last = parts.at(-1)
    if (last?.kind === kind) last.text += text
    else parts.push({ text, kind })
  }
  let i = 0
  let j = 0
  while (i < before.length || j < after.length) {
    if (i < before.length && j < after.length && before[i] === after[j]) {
      append(oldParts, before[i++], 'equal')
      append(newParts, after[j++], 'equal')
    } else if (
      i < before.length &&
      (j === after.length || lengths[i + 1][j] >= lengths[i][j + 1])
    ) {
      append(oldParts, before[i++], 'removed')
    } else append(newParts, after[j++], 'added')
  }
  return { current: oldParts, generated: newParts, changed: current !== generated }
}

const fieldLabels: Record<string, string> = {
  category: '品类',
  brand: '品牌',
  meta: '材质',
  form: '形态',
  model: '型号',
  color: '颜色',
  width: '宽',
  length: '长',
  thickness_mm: '厚',
  grammage: '克重',
  supplier: '供应商',
  customersupplied: '客供',
  sampleonly: '样品',
  unit: '单位',
}

/** 仅将配置原文转为可读表达式；不在前端重算名称或规格。 */
export function describeRegenerationRules(schema?: MaterialAttributeSchema | null) {
  const label = (key: string) =>
    fieldLabels[key] || schema?.fields.find((f) => f.key === key)?.label || key
  const name = schema?.name_pattern
  const spec = schema?.spec_pattern
  const nameSeparator = name?.separator || ' '
  return {
    name: name?.fields.length
      ? name.fields
          .map(
            (key, index) =>
              `${index ? (name.separators?.[key] ?? nameSeparator) : ''}${label(key)}`
          )
          .join('')
      : '该品类未配置命名规则',
    nameDetails: name?.fields.length
      ? `字段顺序：${name.fields.map(label).join(' → ')}；全局分隔符：${JSON.stringify(name.separator || ' ')}；` +
        `逐字段前置分隔符：${
          name.fields
            .slice(1)
            .map(
              (key) =>
                `${label(key)}=${JSON.stringify(name.separators?.[key] ?? nameSeparator)}`
            )
            .join('、') || '无'
        }（空串表示紧贴）；缺值：${name.empty === 'placeholder' ? '占位 —' : '跳过'}`
      : '',
    spec: spec?.fields.length
      ? spec.fields
          .map((field, index) => {
            const settings = [field.unit, field.scale == null ? null : `${field.scale}位`].filter(
              (v) => v != null
            )
            return `${index ? field.sep || '；' : ''}${label(field.key)}${settings.length ? `(${settings.join(',')})` : ''}`
          })
          .join('')
      : '该品类未配置规格模板',
    specDetails: spec?.fields.length
      ? `字段顺序：${spec.fields.map((f) => label(f.key)).join(' → ')}；逐字段前置分隔符：${
          spec.fields
            .slice(1)
            .map((f) => `${label(f.key)}=${JSON.stringify(f.sep || '；')}`)
            .join('、') || '无'
        }；未单配的单位/小数位按后端默认规则展示；字段标签：${spec.show_label ? '显示' : '不显示'}；单位位置：${{ each: '每项', end: '末尾', none: '不显示' }[spec.unit_position || (spec.with_unit ? 'each' : 'none')]}`
      : '',
  }
}

export interface RegenerationValue {
  key: string
  label: string
  value: string
  empty: boolean
}

export function regenerationValues(
  material: MaterialMasterRecord,
  attributes: Record<string, AttributeValue>,
  schema: MaterialAttributeSchema | null | undefined,
  dictionaries: MaterialDictionaries
): RegenerationValue[] {
  const missing = (value: AttributeValue | undefined) =>
    value == null ||
    (typeof value === 'string' && !value.trim()) ||
    (Array.isArray(value) && !value.length)
  const dictionaryLabel = (key: string, value: string) =>
    dictionaries[key]?.find((o) => o.code === value)?.label || value
  const base: [string, string, AttributeValue | undefined, string?][] = [
    ['meta', '材质', material.materialMeta, 'material_meta'],
    ['model', '型号', material.model],
    ['color', '颜色', material.color, 'color'],
    ['brand', '品牌', material.brand, 'brand'],
    ...(['thicknessMm', 'widthMm', 'lengthMm'] as const).map(key => {
      const field = dimensionField(schema?.spec_pattern?.fields || [], key)
      const unit = field?.unit || 'mm'
      const label = { thicknessMm: '厚度', widthMm: '宽', lengthMm: '长' }[key]
      const fieldKey = { thicknessMm: 'thickness_mm', widthMm: 'width', lengthMm: 'length' }[key]
      const value = material[key]
      return [fieldKey, `${label}（${unit}）`, value == null ? null : `${displayDimensionOf(value, unit, field?.scale)}${unit}`] as [string, string, AttributeValue]
    }),
  ]
  return [
    ...base.map(([key, label, value, dictionary]) => ({
      key,
      label,
      empty: missing(value),
      value: missing(value)
        ? '空'
        : dictionary
          ? dictionaryLabel(dictionary, String(value))
          : String(value),
    })),
    ...(schema?.fields || [])
      .filter((f) => f.required)
      .map((field) => {
        const value = attributes[field.key]
        const option = (code: string) => field.options?.find((o) => o.code === code)?.label || code
        const display =
          typeof value === 'boolean'
            ? value
              ? field.true_label || '是'
              : field.false_label || '否'
            : Array.isArray(value)
              ? value.map(option).join('、')
              : field.type === 'enum'
                ? option(String(value))
                : String(value)
        return {
          key: `attribute:${field.key}`,
          label: `${field.label}（必填属性）`,
          empty: missing(value),
          value: missing(value) ? '空' : `${display}${field.unit || ''}`,
        }
      }),
  ]
}

/** 原因取后端预览事实与配置，不以客户端重新生成来猜测。 */
export function specificationUnavailableReason(
  categoryId: number | null | undefined,
  schema: MaterialAttributeSchema | null | undefined,
  message: string,
  configurationError = ''
): string {
  if (!categoryId) return '未生成：未归类，保留原规格'
  if (message.includes('字段当前均无值')) return '未生成：模板字段全空，规格留空'
  if (configurationError) return `未生成：配置读取失败，暂无法核实原因（${configurationError}）`
  if (!schema) return '未生成：该品类无启用模板，保留原规格'
  if (!schema.fields.length && !schema.spec_pattern?.fields.length)
    return '未生成：该品类模板未配置规格字段，保留原规格'
  return `未生成：${message}`
}

export interface RegenerationSnapshot {
  materialName?: string | null
  specification?: string | null
}

export interface RegenerationResult {
  remark?: string
  currentName: string
  currentSpec: string
  generatedName: string
  generatedSpec: string
  specGenerated: boolean
  nameChanged: boolean
  specChanged: boolean
  evidence: string[]
}

export function buildRegeneration(
  current: RegenerationSnapshot,
  generated: { name: string; specification: string | null; specGenerated?: boolean },
  evidence: string[],
  remark?: string
): RegenerationResult {
  const currentName = (current.materialName || '').trim()
  const currentSpec = (current.specification || '').trim()
  const generatedName = (generated.name || '').trim()
  const specGenerated = generated.specGenerated !== false
  // generated=false 也可能表示模板无值、保存时会清空，最终规格必须服从后端返回值。
  const generatedSpec = (generated.specification || '').trim()
  return {
    remark,
    currentName,
    currentSpec,
    generatedName,
    generatedSpec,
    specGenerated,
    nameChanged: currentName !== generatedName,
    specChanged: currentSpec !== generatedSpec,
    evidence,
  }
}

/** 名称默认保留当前值；只有显式勾选「采用生成名称」才替换（人工确认不自动覆盖）。 */
export function resolveFinalName(result: RegenerationResult, adoptGenerated: boolean): string {
  if (adoptGenerated) return result.generatedName
  return result.currentName || result.generatedName
}

/** 完全无变化时不重复保存。 */
export function hasRegenerationChanges(result: RegenerationResult, finalName: string): boolean {
  return finalName.trim() !== result.currentName || result.specChanged
}

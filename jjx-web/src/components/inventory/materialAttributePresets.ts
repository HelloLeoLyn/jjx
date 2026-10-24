import type { MaterialAttributeField, MaterialAttributeSchema } from '@/types/inventory/materialMaster'

const field = (key: string, label: string, type: MaterialAttributeField['type'], extra: Partial<MaterialAttributeField> = {}): MaterialAttributeField =>
  ({ key, label, type, required: false, common: true, in_spec: true, ...extra })

/** 仅补入编辑草稿；由用户检查并点击保存后才成为品类模板。 */
export function recommendedAttributes(categoryCode: string): MaterialAttributeField[] {
  if (/ELECTRONIC/.test(categoryCode)) return [
    field('force_g', '操作力', 'decimal', { unit: 'gf', visible_when: { component_type: 'SPRING' }, spec_order: 10 }),
    field('has_dimple', '是否带点', 'boolean', { true_label: '带点', false_label: '无点', visible_when: { component_type: 'SPRING' }, spec_order: 20 }),
    field('plating', '镀层', 'string', { spec_order: 30 }),
    field('life_cycles', '寿命', 'integer', { unit: '次', common: false, in_spec: false, visible_when: { component_type: 'SPRING' } }),
    field('stroke_mm', '行程', 'decimal', { unit: 'mm', common: false, in_spec: false, visible_when: { component_type: 'SPRING' } }),
  ]
  if (/RELEASE/.test(categoryCode)) return [
    field('gram_weight_gsm', '克重', 'decimal', { unit: 'g/m²' }),
    field('release_force_g', '离型力', 'decimal', { unit: 'g' }),
    field('release_side', '离型面', 'enum', { options: [{ code: 'SINGLE', label: '单面' }, { code: 'DOUBLE', label: '双面' }] }),
  ]
  if (/TAPE/.test(categoryCode)) return [
    field('adhesive_sides', '胶面', 'enum', { options: [{ code: 'SINGLE', label: '单面胶' }, { code: 'DOUBLE', label: '双面胶' }] }),
    field('conductive', '是否导电', 'boolean', { true_label: '导电', false_label: '不导电' }),
    field('temperature_c', '耐温', 'decimal', { unit: '℃' }),
  ]
  if (/INK/.test(categoryCode)) return [field('color_code', '色号', 'string'), field('curing_method', '固化方式', 'string')]
  if (/RAW_BASE|PROTECT|FOAM|OPTICAL/.test(categoryCode)) return [
    field('flame_rating', '阻燃等级', 'string'),
    field('temperature_c', '耐温', 'decimal', { unit: '℃' }),
    field('surface_finish', '表面处理说明', 'string', { common: false }),
  ]
  return []
}

export function addRecommendedAttributes(schema: MaterialAttributeSchema, categoryCode: string): number {
  let added = 0
  for (const preset of recommendedAttributes(categoryCode)) {
    if (preset.visible_when && Object.keys(preset.visible_when).some(key => !schema.fields.some(field => field.key === key))) continue
    const current = schema.fields.find(field => field.key === preset.key)
    if (current) {
      current.common ??= preset.common
      current.in_spec ??= preset.in_spec
      current.spec_order ??= preset.spec_order
      current.true_label ??= preset.true_label
      current.false_label ??= preset.false_label
    } else {
      schema.fields.push(preset)
      added++
    }
  }
  return added
}

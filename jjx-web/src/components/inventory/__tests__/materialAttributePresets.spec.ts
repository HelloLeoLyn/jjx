import { describe, expect, it } from 'vitest'
import { addRecommendedAttributes, recommendedAttributes } from '../materialAttributePresets'
import { attributeVisible, isCommonAttribute } from '../materialMasterModel'
import type { MaterialAttributeSchema } from '@/types/inventory/materialMaster'

describe('按品类补充可选属性', () => {
  it('弹片补带点/力值/镀层，保留已有属性语义和用户配置，不生成新厚度属性', () => {
    const schema: MaterialAttributeSchema = { version: 1, meta_options: [], fields: [
      { key: 'component_type', label: '元件类型', type: 'enum', required: true, options: [{ code: 'SPRING', label: '弹片' }] },
      { key: 'force_g', label: '力值', type: 'decimal', required: false, unit: 'gf', common: false, in_spec: false },
    ] }
    const added = addRecommendedAttributes(schema, 'RAW_AUX_ELECTRONIC')
    expect(added).toBeGreaterThan(0)
    expect(schema.fields.find(field => field.key === 'force_g')).toMatchObject({ label: '力值', unit: 'gf', common: false, in_spec: false })
    expect(addRecommendedAttributes(schema, 'RAW_AUX_ELECTRONIC')).toBe(0)
    const dimple = schema.fields.find(field => field.key === 'has_dimple')!
    expect(dimple).toMatchObject({ required: false, true_label: '带点', false_label: '无点' })
    expect(attributeVisible(dimple, { component_type: 'SPRING' })).toBe(true)
    expect(attributeVisible(dimple, { component_type: 'LED' })).toBe(false)
    expect(schema.fields.some(field => field.key === 'thickness_mm')).toBe(false)
    expect(schema.fields.every(field => field.default == null)).toBe(true)
  })
  it('常用配置覆盖旧的固定分组，低频属性可改为常用', () => {
    expect(isCommonAttribute({ key: 'plating', label: '镀层', type: 'string', required: false, common: true })).toBe(true)
    expect(isCommonAttribute({ key: 'surface_treatment', label: '表面', type: 'string', required: false, common: false })).toBe(false)
    expect(recommendedAttributes('RAW_BASE_PC').some(field => field.key === 'temperature_c')).toBe(true)
  })
})

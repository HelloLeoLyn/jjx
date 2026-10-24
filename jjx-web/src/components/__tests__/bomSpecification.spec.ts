import { describe, expect, it } from 'vitest'
import { generateBomSpecification, joinSpecificationValues } from '../bomSpecification'
import type { MaterialAttributeSchema } from '@/types/inventory/materialMaster'

const schema: MaterialAttributeSchema = { version: 1, meta_options: [], fields: [
  { key: 'component_type', label: '元件类型', type: 'enum', required: true, options: [{ code: 'SPRING', label: '弹片' }] },
  { key: 'force_g', label: '操作力', type: 'decimal', required: false, unit: 'gf', spec_order: 10 },
  { key: 'has_dimple', label: '是否带点', type: 'boolean', required: false, true_label: '带点', false_label: '无点', spec_order: 20, visible_when: { component_type: 'SPRING' } },
  { key: 'plating', label: '镀层', type: 'enum', required: false, options: [{ code: 'NI', label: '镀镍' }], spec_order: 30 },
  { key: 'remark', label: '备注', type: 'string', required: false, in_spec: false },
] }
describe('BOM前端唯一规格组合器', () => {
  it('尺寸原文优先，不加标题、不追加单位、不拼其他属性', () => {
    for (const size of ['30*90mm', '30*90', 'Φ8*0.05mm']) {
      expect(generateBomSpecification({ size, schema, attributes: { force_g: 280 } })).toBe(size)
    }
  })
  it('空尺寸按模板顺序和值生成弹片规格，不输出标题或备注', () => {
    expect(generateBomSpecification({ size: '', schema, attributes: { component_type: 'SPRING', force_g: 280, has_dimple: true, plating: 'NI', remark: '不要拼入' } }))
      .toBe('280gf 带点镀镍')
  })
  it('中文连写，英文数字只在相邻属性边界加单空格', () => {
    expect(joinSpecificationValues(['带点', '镀镍'])).toBe('带点镀镍')
    expect(joinSpecificationValues(['SMD', '0603'])).toBe('SMD 0603')
    expect(joinSpecificationValues(['细砂', 'V0'])).toBe('细砂 V0')
    expect(joinSpecificationValues(['双面胶', '高粘', '导电'])).toBe('双面胶高粘导电')
  })
  it('明确无点保留，隐藏属性跳过，全部缺失保持空', () => {
    expect(generateBomSpecification({ schema, attributes: { component_type: 'SPRING', has_dimple: false, plating: 'NI' } })).toBe('无点镀镍')
    expect(generateBomSpecification({ schema, attributes: { component_type: 'LED', has_dimple: true } })).toBe('')
    expect(generateBomSpecification({ attributes: {} })).toBe('')
  })
  it('属性单位不重复拼接，规格参与设置可包含元件类型', () => {
    expect(generateBomSpecification({ schema, attributes: { force_g: '280gf' } })).toBe('280gf')
    const configured = { ...schema, fields: schema.fields.map(field => ({ ...field, in_spec: field.key === 'component_type' })) }
    expect(generateBomSpecification({ schema: configured, attributes: { component_type: 'SPRING', force_g: 280 } })).toBe('弹片')
  })
})

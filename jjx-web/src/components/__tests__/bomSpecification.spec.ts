import { describe, expect, it } from 'vitest'
import { generateBomSpecification, joinSpecificationValues, specificationSource } from '../bomSpecification'
import type { MaterialAttributeSchema, MaterialMasterDetail } from '@/types/inventory/materialMaster'

const schema: MaterialAttributeSchema = { version: 1, meta_options: [], fields: [
  { key: 'component_type', label: '元件类型', type: 'enum', required: true, options: [{ code: 'SPRING', label: '弹片' }] },
  { key: 'force_g', label: '操作力', type: 'decimal', required: false, unit: 'gf', spec_order: 10 },
  { key: 'has_dimple', label: '是否带点', type: 'boolean', required: false, true_label: '带点', false_label: '无点', spec_order: 20, visible_when: { component_type: 'SPRING' } },
  { key: 'plating', label: '镀层', type: 'enum', required: false, options: [{ code: 'NI', label: '镀镍' }], spec_order: 30 },
  { key: 'remark', label: '备注', type: 'string', required: false, in_spec: false },
] }
describe('BOM前端唯一规格组合器', () => {
  it('档案规格优先于尺寸和属性，不加标题、不追加单位', () => {
    for (const size of ['30*90mm', '30*90', 'Φ8*0.05mm']) {
      expect(generateBomSpecification({ specification: '  档案规格  ', size, schema, attributes: { force_g: 280 } })).toBe('档案规格')
    }
  })
  it('档案规格为空时忽略尺寸，按模板顺序和值生成弹片规格', () => {
    expect(generateBomSpecification({ specification: ' ', size: '30*90mm', schema, attributes: { component_type: 'SPRING', force_g: 280, has_dimple: true, plating: 'NI', remark: '不要拼入' } }))
      .toBe('280gf 带点镀镍')
  })
  it('只有尺寸而无档案规格和可用属性时保持空串', () => {
    for (const specification of [undefined, null, '', '  ']) {
      expect(generateBomSpecification({ specification, size: '450×225×0.8' })).toBe('')
    }
  })
  it('物料详情提取档案已存规格，不采用尺寸或预览重算值', () => {
    const detail = {
      material: { specification: '450*225mm', size: '450×225×0.8' },
      attributes: { force_g: 280 }, configuration: { schema },
      specificationGeneration: { specification: '预览值', generated: true, message: '' },
    } as MaterialMasterDetail
    expect(generateBomSpecification(specificationSource(detail))).toBe('450*225mm')
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

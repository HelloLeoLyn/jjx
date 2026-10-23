import { describe, expect, it } from 'vitest'
import {
  changeMasterCategory,
  emptyMasterForm,
  previewMasterSpecification,
  pruneAttributes,
} from '../materialMasterModel'
import type {
  MaterialAttributeSchema,
  MaterialMasterConfiguration,
} from '@/types/inventory/materialMaster'

const schema: MaterialAttributeSchema = {
  version: 1,
  meta_options: [],
  fields: [
    {
      key: 'component_type',
      label: '元件类型',
      type: 'enum',
      required: true,
      options: [
        { code: 'LED', label: '灯' },
        { code: 'CONNECTOR', label: '连接器' },
      ],
    },
    {
      key: 'pin_count',
      label: '针数',
      type: 'integer',
      required: false,
      visible_when: { component_type: 'CONNECTOR' },
    },
    { key: 'conductive', label: '导电', type: 'boolean', required: false },
  ],
}

describe('新模型物料表单', () => {
  it('换品类清理旧属性和材质，保留来源资料', () => {
    const form = emptyMasterForm()
    form.originalName = '真实名称'
    form.originalSpecification = '供应商原规格'
    form.materialMeta = 'PC'
    form.attributeJson = { thickness_mm: 0.175 }
    const config = {
      category: { categoryId: 99 },
      schema: { ...schema, default_form: 'COMPONENT', default_unit: 'PCS' },
    } as MaterialMasterConfiguration
    changeMasterCategory(form, config)
    expect(form.attributeJson).toEqual({})
    expect(form.materialMeta).toBe('')
    expect(form.unit).toBe('PCS')
    expect(form.originalName).toBe('真实名称')
    expect(form.originalSpecification).toBe('供应商原规格')
  })

  it('未知布尔保持空，明确false保留，条件不适用的针数清理', () => {
    expect(emptyMasterForm().attributeJson).toEqual({})
    expect(
      pruneAttributes(schema, {
        component_type: 'LED',
        pin_count: 4,
        conductive: false,
        old_key: 1,
      })
    ).toEqual({ component_type: 'LED', conductive: false })
    expect(pruneAttributes(schema, { component_type: 'CONNECTOR', pin_count: 4 })).toEqual({
      component_type: 'CONNECTOR',
      pin_count: 4,
    })
  })

  it('显示选项名称及固定单位，不用代码拼成不可读规格', () => {
    const film: MaterialAttributeSchema = {
      version: 1,
      meta_options: ['PC'],
      fields: [
        { key: 'thickness_mm', label: '厚度', type: 'decimal', unit: 'mm', required: false },
        {
          key: 'surface',
          label: '表面',
          type: 'multienum',
          required: false,
          options: [
            { code: 'SAND', label: '中砂' },
            { code: 'HARD', label: '加硬' },
          ],
        },
      ],
    }
    expect(
      previewMasterSpecification(film, { thickness_mm: 0.175, surface: ['SAND', 'HARD'] }, '补充')
    ).toBe('厚度:0.175mm；表面:中砂、加硬；补充')
    expect(
      previewMasterSpecification(
        schema,
        { component_type: 'LED', pin_count: 4, conductive: false },
        ''
      )
    ).toBe('元件类型:灯；导电:否')
  })
})

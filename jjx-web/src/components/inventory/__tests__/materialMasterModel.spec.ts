import { describe, expect, it } from 'vitest'
import {
  attributeEvidence,
  changeMasterCategory,
  effectiveMasterName,
  emptyMasterForm,
  previewMasterName,
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
  it('换品类清理旧属性和材质，保留人工名称', () => {
    const form = emptyMasterForm()
    form.materialName = '真实名称'
    form.nameManual = true
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
    expect(form.materialName).toBe('真实名称')
  })

  it('名称按 材料+型号+颜色（品牌）生成，手改后不被覆盖', () => {
    const dictionaries = {
      material_meta: [{ code: 'PC', label: 'PC' }],
      color: [{ code: 'CLEAR', label: '透明' }],
      brand: [{ code: '3M', label: '3M' }],
    }
    const form = emptyMasterForm()
    form.materialMeta = 'PC'
    form.model = '11008'
    form.color = 'CLEAR'
    expect(previewMasterName(form, dictionaries)).toBe('PC11008透明')
    form.brand = '3M'
    expect(previewMasterName(form, dictionaries)).toBe('PC11008透明（3M）')
    expect(effectiveMasterName(form, dictionaries)).toBe('PC11008透明（3M）')
    form.nameManual = true
    form.materialName = '人工名称'
    expect(effectiveMasterName(form, dictionaries)).toBe('人工名称')
  })

  it('属性依据可追溯到字段与单位', () => {
    expect(
      attributeEvidence(schema, { component_type: 'LED', conductive: false, pin_count: 4 })
    ).toEqual(['元件类型：灯', '导电：否'])
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
    expect(previewMasterSpecification(film, {}, { widthSize: '30*90mm' })).toBe('尺寸:30*90mm')
    expect(
      previewMasterSpecification(film, { thickness_mm: 0.175, surface: ['SAND', 'HARD'] }, { widthSize: '300×305' })
    ).toBe('厚度:0.175mm；表面:中砂、加硬；尺寸:300*305mm')
    expect(
      previewMasterSpecification(
        schema,
        { component_type: 'LED', pin_count: 4, conductive: false }
      )
    ).toBe('元件类型:灯；导电:否')
  })
})

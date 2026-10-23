import { describe, expect, it } from 'vitest'
import {
  MATERIAL_SPEC_SUPPLEMENT_SEPARATOR,
  composeSpecification,
  findMissingSpecAttrs,
  guessSpecValues,
  specValueToInput,
  stripSpecUnit,
  type MaterialSpecInputValue,
} from '../materialMasterFields'
import type { MaterialSpecAttrDef } from '@/types/inventory/material'

/**
 * 规格属性组合（dev-20261023-022）。
 * 与服务端 MaterialSpecComposer 同一规则：示例文本必须两边一致，改规则要同时改两处测试。
 */
const def = (
  attrCode: string,
  attrName: string,
  inputType: MaterialSpecAttrDef['inputType'],
  unit: string | null,
  options: string[],
  required: boolean,
  sortOrder: number
): MaterialSpecAttrDef => ({
  categoryCode: 'RM-FILM',
  attrCode,
  attrName,
  inputType,
  unit,
  options,
  required,
  sortOrder,
})

const FILM_DEFS: MaterialSpecAttrDef[] = [
  def('thickness', '厚度', 'number', 'mm', [], true, 10),
  def('surface', '表面处理', 'select', null, ['光面', '雾面', '幼砂', '加砂', '加硬', '幼砂加硬'], false, 20),
  def('adhesive_side', '胶面', 'select', null, ['无胶', '单面', '双面'], false, 30),
]

describe('规格属性组合 composeSpecification', () => {
  it('片材：厚度 + 表面处理 → 0.38mm 幼砂加硬', () => {
    expect(
      composeSpecification(FILM_DEFS, { thickness: { value: '0.38' }, surface: { value: '幼砂加硬' } })
    ).toBe('0.38mm 幼砂加硬')
  })

  it('按属性排序组合，与填写顺序无关；数值自带单位不重复', () => {
    expect(
      composeSpecification(FILM_DEFS, {
        adhesive_side: { value: '双面' },
        thickness: { value: '0.25MM' },
      })
    ).toBe('0.25mm 双面')
  })

  it('纸材：克重 + 离型力区间 → 120g 10-15g', () => {
    const paperDefs: MaterialSpecAttrDef[] = [
      def('gram', '克重', 'number', 'g', [], true, 10),
      def('release_force', '离型力', 'range', 'g', [], false, 20),
    ]
    expect(
      composeSpecification(paperDefs, {
        gram: { value: '120' },
        release_force: { value: '10', value2: '15' },
      })
    ).toBe('120g 10-15g')
  })

  it('补充说明用全角分号接在生成文本后', () => {
    expect(
      composeSpecification(
        FILM_DEFS,
        { thickness: { value: '0.38' }, surface: { value: '幼砂' } },
        '客供'
      )
    ).toBe(`0.38mm 幼砂${MATERIAL_SPEC_SUPPLEMENT_SEPARATOR}客供`)
  })

  it('只有补充说明时直接用补充说明', () => {
    expect(composeSpecification(FILM_DEFS, {}, '按图加工')).toBe('按图加工')
  })

  it('反例：下拉值不在选项内、数值非法，预览都不出现该段', () => {
    expect(
      composeSpecification(FILM_DEFS, { thickness: { value: '0' }, surface: { value: '磨砂' } })
    ).toBe('')
    expect(composeSpecification(FILM_DEFS, { thickness: { value: '厚' } })).toBe('')
  })

  it('反例：没有属性定义（OTHER/耗材工具）时组合结果为空，规格走自由填写', () => {
    expect(composeSpecification([], {}, '')).toBe('')
  })
})

describe('规格属性必填预检 findMissingSpecAttrs', () => {
  it('新增缺厚度时提示，编辑不阻塞由调用方控制', () => {
    const missing = findMissingSpecAttrs(FILM_DEFS, { surface: { value: '光面' } })
    expect(missing.map((item) => item.attrCode)).toEqual(['thickness'])
    expect(findMissingSpecAttrs(FILM_DEFS, { thickness: { value: '0.38' } })).toEqual([])
  })
})

describe('存量反推 guessSpecValues', () => {
  const paperDefs: MaterialSpecAttrDef[] = [
    def('gram', '克重', 'number', 'g', [], true, 10),
    def('release_force', '离型力', 'range', 'g', [], false, 20),
  ]

  it('从名称/型号认厚度与克重', () => {
    expect(guessSpecValues(FILM_DEFS, { model: '0.05mm' })).toEqual({ thickness: { value: '0.05' } })
    expect(guessSpecValues(paperDefs, { materialName: '离型纸120g黄色' })).toEqual({
      gram: { value: '120' },
    })
    expect(guessSpecValues(paperDefs, { materialName: '离型纸 离型力10-15g' })).toEqual({
      release_force: { value: '10', value2: '15' },
    })
  })

  it('下拉词命中名称（幼砂/双面）', () => {
    expect(guessSpecValues(FILM_DEFS, { materialName: 'PC膜0.38mm幼砂加硬' })).toMatchObject({
      surface: { value: '幼砂加硬' },
    })
    expect(guessSpecValues(FILM_DEFS, { materialName: 'PET膜双面胶' })).toMatchObject({
      adhesive_side: { value: '双面' },
    })
  })

  it('反例：尺寸与料号不进规格（90×265 / DB6842 不产生任何属性值）', () => {
    expect(guessSpecValues(FILM_DEFS, { model: 'DB6842', size: '90×265' })).toEqual({})
  })
})

describe('stripSpecUnit / specValueToInput', () => {
  it('剥单位', () => {
    expect(stripSpecUnit('0.38mm', 'mm')).toBe('0.38')
    expect(stripSpecUnit('0.38 MM', 'mm')).toBe('0.38')
    expect(stripSpecUnit('0.38', 'mm')).toBe('0.38')
    expect(stripSpecUnit('', 'mm')).toBe('')
  })

  it('已存值反显', () => {
    expect(specValueToInput(FILM_DEFS[0], { attrCode: 'thickness', valueNum: 0.38 })).toEqual({
      value: '0.38',
    })
    expect(
      specValueToInput(paperRangeDef, { attrCode: 'release_force', valueNum: 10, valueNum2: 15 })
    ).toEqual({ value: '10', value2: '15' })
    expect(specValueToInput(FILM_DEFS[1], { attrCode: 'surface', valueText: '幼砂' })).toEqual({
      value: '幼砂',
    })
    expect(specValueToInput(FILM_DEFS[0], null)).toEqual({ value: '' })
  })
})

const paperRangeDef: MaterialSpecAttrDef = def('release_force', '离型力', 'range', 'g', [], false, 20)

/** 组合结果类型自检：values 传 Record<string, MaterialSpecInputValue> */
const sample: Record<string, MaterialSpecInputValue> = { thickness: { value: '0.38' } }
void sample

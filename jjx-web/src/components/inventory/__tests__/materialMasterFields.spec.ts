import { describe, expect, it } from 'vitest'
import {
  MATERIAL_CATEGORY_SEED,
  MATERIAL_COLOR_OPTIONS,
  MATERIAL_GRADE_OPTIONS,
  composeMaterialName,
  deriveMaterialType,
  findMissingRequiredFields,
  requiredMaterialFields,
  splitLegacyMaterialName,
} from '../materialMasterFields'

describe('materialMasterFields 词表', () => {
  it('材料是 23 个受控复合词', () => {
    expect(MATERIAL_GRADE_OPTIONS).toHaveLength(23)
    expect(MATERIAL_GRADE_OPTIONS).toContain('PE保护膜')
    expect(MATERIAL_GRADE_OPTIONS).toContain('EVA泡棉')
    expect(MATERIAL_GRADE_OPTIONS).toContain('标签纸')
  })

  it('颜色词表覆盖常用色', () => {
    expect(MATERIAL_COLOR_OPTIONS).toContain('透明')
    expect(MATERIAL_COLOR_OPTIONS).toContain('咖啡色')
  })

  it('品类兜底列表带品类编码与大类', () => {
    const film = MATERIAL_CATEGORY_SEED.find((item) => item.categoryCode === 'RM-FILM')
    expect(film?.materialType).toBe('R')
    expect(MATERIAL_CATEGORY_SEED).toHaveLength(11)
  })
})

describe('composeMaterialName', () => {
  it('材料+型号+颜色无空格拼接', () => {
    expect(composeMaterialName({ materialGrade: 'PET膜', model: 'PET125' })).toBe('PET膜PET125')
  })

  it('颜色参与拼接', () => {
    expect(
      composeMaterialName({ materialGrade: 'PC膜', model: '11008', color: '透明' })
    ).toBe('PC膜11008透明')
  })

  it('品牌走全角括号', () => {
    expect(composeMaterialName({ materialGrade: '胶带', model: '5604', brand: '3M' })).toBe(
      '胶带5604（3M）'
    )
  })

  it('缺项跳过、全空返回空串', () => {
    expect(composeMaterialName({ color: '黑色' })).toBe('黑色')
    expect(composeMaterialName({})).toBe('')
  })
})

describe('splitLegacyMaterialName', () => {
  it('旧名称解析出材料与型号', () => {
    expect(splitLegacyMaterialName('PET膜PET125')).toEqual({
      materialGrade: 'PET膜',
      model: 'PET125',
    })
  })

  it('优先匹配最长复合词（PET保护膜 而不是 PE）', () => {
    expect(splitLegacyMaterialName('PET保护膜WG008')).toEqual({
      materialGrade: 'PET保护膜',
      model: 'WG008',
    })
  })

  it('末尾括号进品牌', () => {
    expect(splitLegacyMaterialName('PE保护膜WG008（3M）')).toEqual({
      materialGrade: 'PE保护膜',
      model: 'WG008',
      brand: '3M',
    })
  })

  it('解析不出结构化字段时整体留在型号，名称不丢', () => {
    expect(splitLegacyMaterialName('其他F200')).toEqual({ materialGrade: '其他', model: 'F200' })
    expect(splitLegacyMaterialName('定制件')).toEqual({ model: '定制件' })
  })
})

describe('deriveMaterialType / requiredMaterialFields', () => {
  it('大类由品类编码带出', () => {
    expect(deriveMaterialType('RM-FILM')).toBe('R')
    expect(deriveMaterialType('AUX-ADHESIVE')).toBe('A')
    expect(deriveMaterialType('OTHER-A')).toBe('A')
    expect(deriveMaterialType()).toBe('')
  })

  it('必填矩阵按品类取值', () => {
    expect(requiredMaterialFields('AUX-ADHESIVE')).toEqual(['model', 'specification', 'brand'])
    expect(requiredMaterialFields('AUX-ELEC')).toEqual(['model', 'color'])
    expect(requiredMaterialFields('AUX-TOOL')).toEqual([])
  })
})

describe('findMissingRequiredFields', () => {
  const values = { materialGrade: 'PET膜', model: '', color: '', specification: '' }

  it('新增按矩阵报缺失', () => {
    expect(findMissingRequiredFields(values, 'RM-FILM')).toEqual(['color', 'specification'])
  })

  it('编辑历史缺失不阻塞', () => {
    const originals = { materialGrade: 'PET膜', model: '', color: '', specification: '' }
    expect(findMissingRequiredFields(values, 'RM-FILM', originals)).toEqual([])
  })

  it('编辑清空原有必填值要报错', () => {
    const originals = { materialGrade: 'PET膜', model: '', color: '透明', specification: '' }
    expect(findMissingRequiredFields(values, 'RM-FILM', originals)).toEqual(['color'])
  })

  it('品类变更时按新品类全量校验', () => {
    expect(findMissingRequiredFields(values, 'AUX-ADHESIVE', {}, true)).toEqual([
      'model',
      'specification',
      'brand',
    ])
  })
})

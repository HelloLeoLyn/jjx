import { describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
vi.mock('@/utils/request', () => ({ default: { get: vi.fn() } }))
import {
  loadMaterialCategoryOptions,
  materialImportSaveWithCategory,
  readImportedCategoryFields,
  MATERIAL_COLOR_OPTIONS,
  MATERIAL_GRADE_OPTIONS,
  composeMaterialName,
  findMissingRequiredFields,
  requiredMaterialFields,
  splitLegacyMaterialName,
} from '../materialMasterFields'
import type { MaterialCategoryOption } from '../materialMasterFields'

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

describe('requiredMaterialFields', () => {
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

// 分类树收口后前端不再内置品类镜像；测试用最小夹具代替接口返回。
const TEST_CATEGORIES: MaterialCategoryOption[] = [
  { categoryId: 6, categoryCode: 'RAW_AUX_ELECTRONIC', categoryName: '电子元件', rootType: 'R', categoryRootName: '原材料' },
  { categoryId: 10, categoryCode: 'RAW_BASE_OTHER', categoryName: '其他基材', rootType: 'R', categoryRootName: '原材料' },
  { categoryId: 11, categoryCode: 'RAW_AUX_OTHER', categoryName: '其他辅材', rootType: 'R', categoryRootName: '原材料' },
]

describe('批量建档品类', () => {
  it('缺少品类不会默认归入原料或辅料', () => {
    expect(() => materialImportSaveWithCategory({ materialName: '测试' }, 'M1', TEST_CATEGORIES)).toThrow('未归类')
  })
  it('保留行内品类，品类决定大类', () => {
    const fields = readImportedCategoryFields({ 品类: '电子元件', material_type: 'R' })
    const result = materialImportSaveWithCategory(fields, 'M2', TEST_CATEGORIES)
    expect(result.payload.categoryId).toBe(6)
    expect(result.payload).not.toHaveProperty('materialType')
    expect(result.usedFallback).toBe(false)
  })
  it('未匹配品类提示兜底；默认品类不存在则明确失败', () => {
    expect(() => materialImportSaveWithCategory({ categoryName: '不存在' }, 'M3', TEST_CATEGORIES)).toThrow('未归类')
    expect(() => materialImportSaveWithCategory({}, 'M3', [])).toThrow('未归类')
  })
  it('优先使用仅登录接口返回的真实 ID', async () => {
    const options = [{ categoryId: 99, categoryCode: 'RAW_AUX_OTHER', categoryName: '其他辅材', rootType: 'R', categoryRootName: '原材料' }]
    vi.mocked(request.get).mockResolvedValueOnce({ data: options })
    expect(await loadMaterialCategoryOptions()).toEqual(options)
    expect(request.get).toHaveBeenCalledWith('/inventory/material/category-options')
    expect(materialImportSaveWithCategory({ categoryId: 99 }, 'M4', options).payload.categoryId).toBe(99)
  })
  it('接口失败或空数据时返回空数组（不再用本地镜像兜底）', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    vi.mocked(request.get).mockRejectedValueOnce(new Error('offline'))
    expect(await loadMaterialCategoryOptions()).toEqual([])
    vi.mocked(request.get).mockResolvedValueOnce({ data: [] })
    expect(await loadMaterialCategoryOptions()).toEqual([])
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })
})

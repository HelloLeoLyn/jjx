import { describe, expect, it, vi } from 'vitest'
vi.mock('@/utils/request', () => ({ default: {} }))
import { materialModeRows } from '../workSpecMaterialMode'
import { projectMaterialPrintRows, workSpecFlowSource, makePages, buildDocsetFromSnapshot, type DocsetData } from '../docset'
import { normalizeWorkSpec } from '@/api/product/workSpec'

// YL1025PWOO-BOM V1.1 五条材料，与用户Excel的两个B版区域逐行对照。
function fixture(): DocsetData {
  const rows = [
    [109, 'RM000105', 'PC-800 茶色 单面加硬', '450*225mm', 6, '面板'],
    [351, 'AUX000347', '3M 9080-W195-L80', '195mm*80mm', 1, '面板背胶'],
    [352, 'AUX000348', '离型纸 RP-W195-L80', '195mm*80mm', 1, '面板背胶'],
    [112, 'AUX000108', 'PET保护膜 PFPET-T4901', '205mm*440mm', 6, '撕贴正面保护膜'],
    [113, 'AUX000109', 'PE保护膜 PFPE-020', '110mm*215mm', 3, '撕贴反面保护膜'],
  ].map(([materialId, materialCode, materialName, specification, moduleQty, processName], i) => ({
    itemId: i + 8, materialId, materialCode, materialName, specification, moduleQty, processName,
    baseQty: 1, unit: 'PCS', widthMm: 0, lengthMm: 0, split: '', dieLocation: `刀模${i}`, quantity: 1,
  }))
  return { product: {}, bom: { items: rows }, routing: {}, workSpec: normalizeWorkSpec({}), files: [], warnings: [],
    materialSchemes: { byItem: {}, byMaterial: {
      351: { bomId: 1, items: [{ materialId: 110, materialName: '3M 9080', specification: '195mm*50000mm', pitchMm: 80, pcsPerPitch: 1 }] },
      352: { bomId: 2, items: [{ materialId: 111, materialName: '离型纸 RP-W195', specification: '195mm', pitchMm: 80, pcsPerPitch: 1 }] },
      112: { bomId: 5, items: [{ materialId: 358, materialName: 'PET保护膜 T4901', specification: '205mm', pitchMm: 440, pcsPerPitch: 1 }] },
    } },
  } as DocsetData
}
const expectedScheme = ['450*225mm', '195mm宽切195mm*80mm', '195mm宽切195mm*80mm', '205mm宽切205mm*440mm', '110mm*215mm']
const expectedOriginal = ['450*225mm', '195mm宽跳80mm', '195mm宽跳80mm', '205mm宽跳440mm', '110mm*215mm']
const spec = (row: Record<string, any>) => row.params || row.specification

describe('B版材料按Excel只转换规格（dev-20261118-002）', () => {
  it.each(['scheme', 'original'] as const)('真实五行%s规格与Excel一致，材料身份/比例/刀模/来源不变且不改输入', materialMode => {
    const data = { ...fixture(), materialMode }, before = JSON.stringify(data)
    const rows = materialModeRows(data.bom.items, data, 'B')
    expect(rows.map(spec)).toEqual(materialMode === 'scheme' ? expectedScheme : expectedOriginal)
    expect(rows.map(({ params, specification, manufacturingBomId, ...row }) => row))
      .toEqual(data.bom.items.map(({ params, specification, manufacturingBomId, ...row }: any) => row))
    expect(JSON.stringify(data)).toBe(before)
  })
  it('B纸张测量源与页面转换一致，项目材料独立维护时同样保留上层比例', () => {
    const data = fixture()
    expect(workSpecFlowSource(data).rows!.map(spec)).toEqual(expectedScheme)
    expect(workSpecFlowSource(data).rows!.map(row => row.moduleQty)).toEqual([6, 1, 1, 6, 3])
    data.workSpec = normalizeWorkSpec({ projectMaterials: data.bom.items })
    expect(projectMaterialPrintRows(data, 'B').map(spec)).toEqual(expectedScheme)
    expect(projectMaterialPrintRows(data, 'B').map(row => row.dieLocation)).toEqual(data.bom.items.map((row: any) => row.dieLocation))
  })
  it('A版保留现有原料展开及规格，B版不污染A版输入', () => {
    const data = fixture(), before = makePages(data, ['spec'], [], {}, {}, 'A')
    workSpecFlowSource(data)
    expect(makePages(data, ['spec'], [], {}, {}, 'A')).toEqual(before)
    expect(before[0].rows![3]).toMatchObject({ materialName: 'PET保护膜 T4901', params: '205mm跳440mm' })
  })
  it('多投入、缺尺寸或无效来源不猜规格、不增加材料行；原模式人工文本优先', () => {
    const data = fixture(), row = data.bom.items[3]
    data.materialSchemes!.byItem[row.itemId] = { items: [{ specification: '300mm' }, { specification: '205mm' }] }
    expect(materialModeRows([row], data, 'B')).toEqual([row])
    data.materialSchemes!.byItem[row.itemId] = { items: [{ specification: '300mm', lengthMm: 50000 }] }
    expect(spec(materialModeRows([row], { ...data, materialMode: 'original' }, 'B')[0])).toBe(row.specification)
    expect(materialModeRows([{ ...row, specification: '未填尺寸' }], data, 'B')[0].params).toBeUndefined()
    const manual = { ...row, params: '人工确认规格' }
    expect(materialModeRows([manual], { ...data, materialMode: 'original' }, 'B')[0]).toEqual(manual)
    data.materialSchemes!.byItem[row.itemId] = { warning: '来源不匹配', items: [{ specification: '300mm' }] }
    expect(materialModeRows([row], data, 'B')).toEqual([row])
  })
  it('新定版仅使用冻结方案，旧定版缺方案时保持当时的原规格', () => {
    const data = fixture(), snapshot = { schemaVersion: 2, spec: {}, bom: data.bom, files: [], counts: { files: 0 }, materialSchemes: data.materialSchemes }
    const frozen = buildDocsetFromSnapshot(1, JSON.stringify(snapshot))
    data.materialSchemes!.byMaterial[112].items![0].specification = '999mm'
    expect(workSpecFlowSource(frozen).rows!.map(spec)).toEqual(expectedScheme)
    const old = buildDocsetFromSnapshot(1, JSON.stringify({ ...snapshot, schemaVersion: 1, materialSchemes: undefined }))
    expect(workSpecFlowSource(old).rows!.map(spec)).toEqual(data.bom.items.map(spec))
  })
})

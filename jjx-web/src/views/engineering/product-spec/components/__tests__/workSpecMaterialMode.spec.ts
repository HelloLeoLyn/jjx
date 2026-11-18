import { describe, expect, it, vi } from 'vitest'
vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: vi.fn() } }))
import { materialModeRows, manufacturingMaterialSpec, materialModeWarnings, effectiveMaterialMode, normalizeMaterialMode } from '../workSpecMaterialMode'
import { buildDocsetFromSnapshot, projectMaterialPrintRows, makePages, workSpecFlowSource, type DocsetData } from '../docset'
import { normalizeWorkSpec } from '@/api/product/workSpec'

function data(): DocsetData {
  return {
    product: {}, routing: {}, files: [], warnings: [],
    bom: { items: [{ itemId: 28, materialId: 351, materialName: '3M 9080-W195-L80', materialCode: 'CUT', specification: '195mm*80mm', moduleQty: 1, baseQty: 1, processName: '背胶', sourceType: 'buy', dieLocation: '原刀模' }] },
    workSpec: normalizeWorkSpec({}),
    materialSchemes: { byItem: {}, byMaterial: {
      351: { bomId: 1, items: [{ materialId: 110, materialName: '3M 9080', materialCode: 'RAW', specification: '195mm*50000mm', pitchMm: 80, moduleQty: null, baseQty: null }] },
      355: { bomId: 10, items: [{ materialId: 112, materialName: 'PET原料', specification: '205mm*440mm', moduleQty: 6, baseQty: 1 }] },
    } },
  }
}
describe('项目材料方案/原模式', () => {
  it('A版默认方案模式保留片料身份并合并规格，切回原模式不改原对象', () => {
    const source = data(), before = JSON.stringify(source)
    const projected = materialModeRows(source.bom.items, source)
    expect(projected[0]).toMatchObject({ materialName: '3M 9080-W195-L80', params: '195mm宽切195mm*80mm', workSpecCombinedSpec: '195mm宽切195mm*80mm=1PCS', dieLocation: '原刀模', processName: '背胶' })
    expect(materialModeRows(source.bom.items, { ...source, materialMode: 'original' })[0].workSpecCombinedSpec).toBe('195mm宽跳80mm=1PCS')
    expect(JSON.stringify(source)).toBe(before)
    expect(normalizeMaterialMode('unexpected')).toBe('scheme')
  })
  it('A版采用片料尺寸及上层1/1，原模式人工文本保留', () => {
    const source = data()
    const input = [{ materialId: 355, materialName: '裁好PET', specification: '66×185mm', moduleQty: 1, baseQty: 1, params: '人工规格' }]
    expect(materialModeRows(input, source)[0]).toMatchObject({ materialName: '裁好PET', moduleQty: 1, baseQty: 1, workSpecCombinedSpec: '205mm宽切66mm*185mm=1PCS' })
    expect(materialModeRows(input, { ...source, materialMode: 'original' })[0].params).toBe('人工规格')
  })
  it('无制造方案保持原内容，来源错误明确提示而不猜选方案', () => {
    const source = data(), original = { materialId: 999, materialName: '外购材料' }
    expect(materialModeRows([original], source)[0]).toMatchObject(original)
    source.materialSchemes!.byMaterial[999] = { warning: '原料方案无法读取' }
    expect(materialModeRows([original], source)[0]).toMatchObject(original)
    expect(materialModeWarnings(source)).toContain('原料方案无法读取')
    expect(materialModeWarnings({ ...source, materialMode: 'original' })).toEqual([])
  })
  it('不同BOM行采用不同方案时按来源行匹配，不按材料随意合并', () => {
    const source = data()
    source.materialSchemes!.byItem[28] = { items: [{ materialName: '指定原料' }] }
    expect(materialModeRows(source.bom.items, source)[0].materialName).toBe('3M 9080-W195-L80')
  })
  it('A版多投入缺少唯一规格时保持原材料和分组', () => {
    const source = data()
    source.workSpec = normalizeWorkSpec({ projectMaterials: [{ ...source.bom.items[0], quantity: 1, unit: 'PCS' }] })
    source.materialSchemes!.byMaterial[351].items!.push({ materialName: '辅助原料', specification: '50mm' })
    const rows = projectMaterialPrintRows(source)
    expect(rows.map(row => row.materialName)).toEqual(['3M 9080-W195-L80'])
    expect(rows.map(row => row.projectSpan)).toEqual([1])
    expect(rows.map(row => row.projectNo)).toEqual([1])
  })
  it('A/B同源保留上层材料与比例，仅A合并模数', () => {
    const source = data()
    expect(makePages(source, ['spec'], [], {})[0].rows![0]).toMatchObject({ materialName: '3M 9080-W195-L80', workSpecCombinedSpec: '195mm宽切195mm*80mm=1PCS' })
    expect(workSpecFlowSource(source).rows![0]).toMatchObject({ materialName: '3M 9080-W195-L80', params: '195mm宽切195mm*80mm', moduleQty: 1, baseQty: 1 })
    source.materialMode = 'original'
    expect(makePages(source, ['spec'], [], {})[0].rows![0].materialName).toBe('3M 9080-W195-L80')
    expect(workSpecFlowSource(source).rows![0].params).toBe('195mm宽跳80mm')
  })
  it('A/B遇多投入保持原行，刀模位置跟随来源不串位', () => {
    const source = data()
    source.bom.items[0].children = [{ itemId: 29, materialId: 999, materialName: '其他材料', materialCode: 'OTHER', processName: '保护膜' }]
    source.workSpec.dieLocationText = '刀模甲\n刀模乙'
    source.materialSchemes!.byMaterial[351].items!.push({ materialName: '辅助投入', specification: '50mm' })
    expect(workSpecFlowSource(source).diePositionRows).toEqual(['刀模甲', '刀模乙'])
    expect(makePages(source, ['spec'], [], {})[0].diePositionRows).toEqual(['刀模甲', '刀模乙'])
    source.materialMode = 'original'
    expect(workSpecFlowSource(source).diePositionRows).toEqual(['刀模甲', '刀模乙'])
  })
  it('旧定版无方案快照始终原模式；新定版仅使用冻结的方案资料', () => {
    const source = data()
    const snapshot: any = { schemaVersion: 1, spec: {}, bom: source.bom, files: [], counts: { files: 0 } }
    let frozen = buildDocsetFromSnapshot(2, JSON.stringify(snapshot))
    expect(effectiveMaterialMode(frozen)).toBe('original')
    expect(projectMaterialPrintRows(frozen)[0].materialName).toBe('3M 9080-W195-L80')
    snapshot.schemaVersion = 2; snapshot.materialSchemes = source.materialSchemes
    frozen = buildDocsetFromSnapshot(2, JSON.stringify(snapshot))
    source.materialSchemes!.byMaterial[351].items![0].materialName = '后来更改的原料'
    expect(projectMaterialPrintRows(frozen)[0]).toMatchObject({ materialName: '3M 9080-W195-L80', workSpecCombinedSpec: '195mm宽切195mm*80mm=1PCS' })
  })
  it('跳距规格不把卷长带入，不凭空补出片数或模数', () => {
    expect(manufacturingMaterialSpec({ widthMm: 195, lengthMm: 50000, pitchMm: 80 })).toBe('195mm跳80mm')
    expect(manufacturingMaterialSpec({ specification: '195mm', pitchMm: 80 })).toBe('195mm跳80mm')
  })
  it('无跳距时保留规格符号、单位和空格，宽长字段不覆盖制造BOM规格原文', () => {
    const specification = '205 mm * 440 mm'
    expect(manufacturingMaterialSpec({ specification, widthMm: 999, lengthMm: 888 })).toBe(specification)
    expect(manufacturingMaterialSpec({ specification: '205mm*440mm', widthMm: 205, lengthMm: 440 })).toBe('205mm*440mm')
  })
})

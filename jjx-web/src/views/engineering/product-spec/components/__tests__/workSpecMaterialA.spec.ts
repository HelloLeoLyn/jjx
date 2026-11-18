import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
vi.mock('@/utils/request', () => ({ default: {} }))
import { materialModeRows } from '../workSpecMaterialMode'
import { makePages, workSpecFlowSource, buildDocsetFromSnapshot, type DocsetData } from '../docset'
import { normalizeWorkSpec } from '@/api/product/workSpec'
import WorkSpecPaper from '../WorkSpecPaper.vue'
import { defaultWorkSpecFontSettings } from '../workSpecFont'

;(globalThis as any).ResizeObserver = class { observe() {} disconnect() {} }
;(document as any).fonts = { ready: Promise.resolve(), addEventListener() {}, removeEventListener() {} }
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

function fixture(): DocsetData {
  const items = [
    [109, 'PC-800 茶色 单面加硬', '450*225mm', 6],
    [351, '3M 9080-W195-L80', '195mm*80mm', 1],
    [352, '离型纸 RP-W195-L80', '195mm*80mm', 1],
    [112, 'PET保护膜 PFPET-T4901', '205mm*440mm', 6],
    [113, 'PE保护膜 PFPE-020', '110mm*215mm', 3],
  ].map(([materialId, materialName, specification, moduleQty], i) => ({ itemId: i + 8, materialId, materialName, specification, moduleQty, baseQty: 1, unit: 'PCS', widthMm: 0, lengthMm: 0, dieLocation: `刀模${i}` }))
  return { product: {}, bom: { items }, routing: { items: [] }, workSpec: normalizeWorkSpec({}), files: [], warnings: [],
    materialSchemes: { byItem: {}, byMaterial: {
      351: { bomId: 1, items: [{ specification: '195mm*50000mm', pitchMm: 80 }] },
      352: { bomId: 2, items: [{ specification: '195mm', pitchMm: 80 }] },
      112: { bomId: 5, items: [{ specification: '205mm', pitchMm: 440 }] },
    } },
  } as DocsetData
}
const expected = {
  scheme: ['450*225mm=6PCS', '195mm宽切195mm*80mm=1PCS', '195mm宽切195mm*80mm=1PCS', '205mm宽切205mm*440mm=6PCS', '110mm*215mm=3PCS'],
  original: ['450*225mm=6PCS', '195mm宽跳80mm=1PCS', '195mm宽跳80mm=1PCS', '205mm宽跳440mm=6PCS', '110mm*215mm=3PCS'],
}
describe('A版按Excel合并规格及模数（dev-20261118-003）', () => {
  it.each(['scheme', 'original'] as const)('%s真实五行文本、纸张及上层材料身份一致，B版不增加合并文本', materialMode => {
    const data = { ...fixture(), materialMode }, before = JSON.stringify(data)
    const page = makePages(data, ['spec'], [], {}, {}, 'A')[0]
    expect(page.rows!.map(row => row.workSpecCombinedSpec)).toEqual(expected[materialMode])
    expect(page.rows!.map(row => row.materialName)).toEqual(data.bom.items.map((row: any) => row.materialName))
    expect(page.diePositionRows).toEqual(data.bom.items.map((row: any) => row.dieLocation))
    expect(workSpecFlowSource(data).rows!.every(row => row.workSpecCombinedSpec == null)).toBe(true)
    expect(JSON.stringify(data)).toBe(before)
    const wrapper = mount(WorkSpecPaper, { props: { data, page, pageIndex: 0, pageCount: 1, specTemplate: 'A', fontSettings: defaultWorkSpecFontSettings() }, global: { stubs: { SvgIcon: true } } })
    wrappers.push(wrapper)
    const cells = wrapper.findAll('[data-work-spec-cell$="-spec"]')
    expect(cells.slice(0, 5).map(cell => cell.text())).toEqual(expected[materialMode])
    expect(wrapper.find('.materials .engineering-notes').exists()).toBe(true)
    expect(wrapper.find('.materials .emboss-label').exists()).toBe(true)
    expect(wrapper.find('.materials thead').text()).toContain('规格及模数')
  })
  it('非1基数同时表达材料数和成品数，人工完整文本不重复拼接', () => {
    const data = fixture(), row = { ...data.bom.items[3], baseQty: 2 }
    expect(materialModeRows([row], data, 'A')[0].workSpecCombinedSpec).toBe('205mm宽切205mm*440mm（2PCS材料供6PCS成品）')
    const manual = { ...row, params: '客户确认：两片供六件' }
    expect(materialModeRows([manual], { ...data, materialMode: 'original' }, 'A')[0].workSpecCombinedSpec).toBe(manual.params)
  })
  it('明确宽长字段不会覆盖生成的宽切文本；不足资料沿用旧显示且不改全局BOM规格', () => {
    const data = fixture(), row = { ...data.bom.items[3], widthMm: 205, lengthMm: 440 }
    expect(materialModeRows([row], data, 'A')[0].workSpecCombinedSpec).toBe(expected.scheme[3])
    data.materialSchemes!.byMaterial[112].items!.push({ specification: '300mm' })
    expect(materialModeRows([row], data, 'A')[0].workSpecCombinedSpec).toBe('205*440mm=6PCS')
  })
  it('新定版只读冻结资料，历史定版无方案时保留原材料和原规格', () => {
    const data = fixture(), snapshot = { schemaVersion: 2, spec: {}, bom: data.bom, files: [], counts: { files: 0 }, materialSchemes: data.materialSchemes }
    const frozen = buildDocsetFromSnapshot(1, JSON.stringify(snapshot))
    data.materialSchemes!.byMaterial[112].items![0].specification = '999mm'
    expect(makePages(frozen, ['spec'], [], {}, {}, 'A')[0].rows!.map(row => row.workSpecCombinedSpec)).toEqual(expected.scheme)
    const old = buildDocsetFromSnapshot(1, JSON.stringify({ ...snapshot, materialSchemes: undefined, schemaVersion: 1 }))
    expect(makePages(old, ['spec'], [], {}, {}, 'A')[0].rows![3].workSpecCombinedSpec).toBe('205mm*440mm=6PCS')
  })
})

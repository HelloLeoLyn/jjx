import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import type { EngineeringBomItem } from '@/types/product/bom'

const api = vi.hoisted(() => ({ getEngineeringBomInfo: vi.fn() }))
vi.mock('@/api/product/bom', () => ({ productBomApi: { getEngineeringBomInfo: api.getEngineeringBomInfo } }))

import BomDetail from '../BomDetail.vue'
import BomDetailTable from '../BomDetailTable.vue'

const mounted: ReturnType<typeof mount>[] = []
afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  document.body.innerHTML = ''
  localStorage.clear()
})

const row = (over: Partial<EngineeringBomItem> = {}): EngineeringBomItem => ({
  itemId: 1,
  bomId: 6,
  materialId: 10,
  materialCode: 'AUX000348',
  materialName: '离型纸 RP-W195-L80',
  specification: '195*80mm',
  unit: 'PCS',
  quantity: 1,
  baseQty: 1,
  moduleQty: 1,
  lossRate: 0,
  appliedQty: 1,
  actualIssueQty: 1,
  minIssueQty: 0,
  sourceType: 'make',
  subBomId: 4,
  subBomCode: 'AUX000348-MFG',
  subBomVersion: 'V1.0',
  subBomApproveStatus: 3,
  itemOrder: 1,
  sortOrder: 1,
  ...over,
})

async function mountTable(items: EngineeringBomItem[]) {
  const wrapper = mount(BomDetailTable, { attachTo: document.body, props: { items } })
  mounted.push(wrapper)
  await nextTick()
  await flushPromises()
  return wrapper
}

const headers = (wrapper: ReturnType<typeof mount>) =>
  wrapper.findAll('th').map((node) => node.text().replace(/\s+/g, ''))

describe('BOM详情明细：字段口径与下层生产BOM', () => {
  it('显示来源、下层生产BOM、投料与跳距，且“数量”改名为“每件用量”', async () => {
    const wrapper = await mountTable([
      row({ pitchMm: 80, pcsPerPitch: 2, actualIssueQty: 1.25, appliedQty: 1.2 }),
    ])
    const titles = headers(wrapper)
    for (const title of ['来源', '下层生产BOM', '跳距(mm)', '每跳片数', '每件用量', '应用料(含损耗)', '实际投料']) {
      expect(titles).toContain(title)
    }
    expect(titles).not.toContain('数量')
    const text = wrapper.text()
    expect(text).toContain('自制')
    expect(text).toContain('AUX000348-MFG')
    expect(text).toContain('V1.0')
    expect(text).toContain('已批准')
    expect(text).toContain('80')
  })

  it('自制行没有下层方案时给出“缺方案”提示，外购行不提示', async () => {
    const wrapper = await mountTable([
      row({ itemId: 1, sourceType: 'make', subBomId: null, subBomCode: null, subBomVersion: null }),
      row({ itemId: 2, sourceType: 'buy', subBomId: null, subBomCode: null, subBomVersion: null }),
    ])
    expect(wrapper.text()).toContain('缺方案')
    expect(wrapper.findAll('.scheme-missing')).toHaveLength(1)
    expect(wrapper.text()).toContain('外购')
  })

  it('档案单位与 BOM 单位不一致时标红并整行提示，档案规格与本行规格不一致也标红', async () => {
    const wrapper = await mountTable([
      row({ unit: 'PCS', archiveUnit: 'M2', materialSpecification: '195*80mm' }),
      row({ itemId: 2, unit: 'M2', archiveUnit: '㎡', materialSpecification: '别的规格' }),
    ])
    expect(wrapper.findAll('.mismatch')).toHaveLength(2)
    expect(wrapper.findAll('.bom-row-error')).toHaveLength(1)
  })
})

describe('BOM详情明细：列设置', () => {
  it('隐藏过的列从 localStorage 恢复，全选后清空记忆', async () => {
    localStorage.setItem('jjx.bomDetail.visibleColumns', JSON.stringify(['appliedQty', 'pitch']))
    const wrapper = await mountTable([row()])
    let titles = headers(wrapper)
    expect(titles).toContain('每件用量')
    expect(titles).not.toContain('应用料(含损耗)')
    expect(titles).not.toContain('跳距(mm)')

    const state = wrapper.vm as any
    state.showAllColumns()
    await nextTick()
    titles = headers(wrapper)
    expect(titles).toContain('应用料(含损耗)')
    expect(localStorage.getItem('jjx.bomDetail.visibleColumns')).toBeNull()
  })

  it('序号列被隐藏时不再做组合合并，避免列序错位', async () => {
    localStorage.setItem('jjx.bomDetail.visibleColumns', JSON.stringify(['serial']))
    const wrapper = await mountTable([row(), row({ itemId: 2 })])
    const state = wrapper.vm as any
    expect(state.groupSpan({ rowIndex: 1, columnIndex: 0 })).toEqual([1, 1])
  })
})

describe('BOM详情头部：产出对象与版本状态', () => {
  it('显示 BOM名称、非当前版本、产出对象与版本总数', async () => {
    api.getEngineeringBomInfo.mockResolvedValue({
      data: {
        bomId: 6,
        bomCode: 'YL1025PWOO-BOM',
        bomName: 'YL1025PWOO-BOM',
        productCode: 'YL1025PWOO',
        productName: '茶色视窗',
        bomVersion: 'V1.1',
        approveStatus: 2,
        isCurrent: false,
        outputType: 'PRODUCT',
        outputCode: 'YL1025PWOO',
        outputName: '茶色视窗',
        versionTotal: 2,
        effectiveDate: '2026-11-01',
        expiryDate: '',
        remark: '',
        approveRemark: '',
        createTime: '',
        updateTime: '',
        createBy: 'admin',
        updateBy: 'admin',
        items: [row()],
      },
    })
    const wrapper = mount(BomDetail, { attachTo: document.body, props: { modelValue: true, bomId: 6 } })
    mounted.push(wrapper)
    await flushPromises()
    await nextTick()
    const text = document.body.textContent?.replace(/\s+/g, '') || ''
    expect(text).toContain('BOM名称')
    expect(text).toContain('非当前版本')
    expect(text).toContain('共2版')
    expect(text).toContain('产出对象')
    expect(text).toContain('茶色视窗')
  })

  it('物料产出的制造方案显示“物料”类型，不再显示空白产品名', async () => {
    api.getEngineeringBomInfo.mockResolvedValue({
      data: {
        bomId: 4,
        bomCode: 'AUX000348-MFG',
        bomName: '离型纸制造方案',
        productId: null,
        productCode: '',
        productName: '',
        bomVersion: 'V1.0',
        approveStatus: 3,
        isCurrent: false,
        outputType: 'MATERIAL',
        outputCode: 'AUX000348',
        outputName: '离型纸 RP-W195-L80',
        versionTotal: 1,
        items: [row()],
      },
    })
    const wrapper = mount(BomDetail, { attachTo: document.body, props: { modelValue: true, bomId: 4 } })
    mounted.push(wrapper)
    await flushPromises()
    await nextTick()
    const text = document.body.textContent?.replace(/\s+/g, '') || ''
    expect(text).toContain('物料')
    expect(text).toContain('AUX000348')
    expect(text).toContain('离型纸RP-W195-L80')
  })
})

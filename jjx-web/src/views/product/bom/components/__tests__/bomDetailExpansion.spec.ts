import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import type { EngineeringBom, EngineeringBomItem } from '@/types/product/bom'
import { BomSourceTypeEnum, BomStatusEnum, ManufacturingOutputTypeEnum } from '@/enums/product'

const api = vi.hoisted(() => ({ getEngineeringBomInfo: vi.fn(), getBomVersions: vi.fn() }))
vi.mock('@/api/product/bom', () => ({ productBomApi: api }))
vi.mock('@/components/ProcessName.vue', () => ({ default: { template: '<span />' } }))

import BomDetail from '../BomDetail.vue'
import BomDetailTable from '../BomDetailTable.vue'
import BomVersionTimeline from '../BomVersionTimeline.vue'
import ManufacturingBomPath from '../ManufacturingBomPath.vue'

type Detail = EngineeringBom & { items: EngineeringBomItem[] }
const item = (itemId: number, overrides: Partial<EngineeringBomItem> = {}): EngineeringBomItem => ({
  itemId,
  materialId: itemId,
  materialCode: `MAT-${itemId}`,
  materialName: `物料${itemId}`,
  specification: '',
  unit: 'PCS',
  quantity: 1,
  lossRate: 0,
  sortOrder: itemId,
  sourceType: BomSourceTypeEnum.BUY,
  ...overrides,
})
const make = (itemId: number, subBomId: number) => item(itemId, {
  sourceType: BomSourceTypeEnum.MAKE,
  subBomId,
  subBomCode: `BOM-${subBomId}`,
  subBomVersion: 'V1.0',
})
const bom = (bomId: number, items: EngineeringBomItem[], overrides: Partial<Detail> = {}): Detail => ({
  bomId,
  bomCode: `BOM-${bomId}`,
  bomName: `方案${bomId}`,
  bomVersion: 'V1.0',
  productId: 1,
  productCode: 'P-1',
  productName: '产品',
  outputType: ManufacturingOutputTypeEnum.PRODUCT,
  approveStatus: BomStatusEnum.APPROVED.value,
  isCurrent: true,
  effectiveDate: '',
  expiryDate: '',
  remark: '',
  createTime: '',
  updateTime: '',
  createBy: '',
  updateBy: '',
  items,
  ...overrides,
})

const mounted: ReturnType<typeof mount>[] = []
beforeEach(() => {
  vi.resetAllMocks()
  api.getBomVersions.mockResolvedValue({ data: [] })
})
afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  document.body.innerHTML = ''
  localStorage.clear()
})
async function settle() {
  await flushPromises()
  await nextTick()
}
async function table(root: Detail) {
  const wrapper = mount(BomDetailTable, {
    attachTo: document.body,
    props: { items: root.items, ancestors: [root], height: 'auto' },
  })
  mounted.push(wrapper)
  await settle()
  return wrapper
}
function mockDetails(details: Detail[]) {
  api.getEngineeringBomInfo.mockImplementation(async (id: number) => {
    const data = details.find((detail) => detail.bomId === id)
    if (!data) throw new Error(`未预期的 BOM 请求 ${id}`)
    return { data }
  })
}
async function expand(wrapper: ReturnType<typeof mount>) {
  await wrapper.get('button.el-table__expand-icon:not(:disabled)').trigger('click')
  await settle()
}
function childTableOf(wrapper: ReturnType<typeof mount>) {
  const depth = wrapper.props('ancestors').length
  return wrapper.findAllComponents(BomDetailTable)
    .find((component) => component.props('ancestors')?.length === depth + 1)
}

describe('BOM详情行内逐层展开（纯前端只读懒加载）', () => {
  it('make 行点击才读取子方案，子层行数与响应一致；收起再展开复用已读数据', async () => {
    const root = bom(1, [make(11, 2), item(12)])
    const child = bom(2, [item(21), item(22)])
    mockDetails([child])
    const wrapper = await table(root)
    expect(api.getEngineeringBomInfo).not.toHaveBeenCalled()

    await expand(wrapper)
    expect(api.getEngineeringBomInfo).toHaveBeenCalledExactlyOnceWith(2)
    const childTable = childTableOf(wrapper)!
    expect(childTable.props('items')).toEqual(child.items)
    expect(childTable.findAll('tr.el-table__row')).toHaveLength(2)
    expect(wrapper.text()).toContain('MAT-21')
    expect(wrapper.props('items')).toEqual(root.items)

    await expand(wrapper)
    expect(childTableOf(wrapper)).toBeUndefined()
    await expand(wrapper)
    expect(api.getEngineeringBomInfo).toHaveBeenCalledTimes(1)
    expect(childTableOf(wrapper)!.props('items')).toHaveLength(2)
  })

  it('三层路径为父→子→孙，点击上层路径仅收起相应子层', async () => {
    const root = bom(1, [make(11, 2)])
    const child = bom(2, [make(21, 3)])
    const grandchild = bom(3, [item(31)])
    mockDetails([child, grandchild])
    const wrapper = await table(root)
    await expand(wrapper)
    const childTable = childTableOf(wrapper)!
    await expand(childTable)
    const grandchildTable = childTableOf(childTable)!
    const path = grandchildTable.findComponent(ManufacturingBomPath)
    expect(path.props('nodes').map((node: any) => node.bomId)).toEqual([1, 2, 3])
    expect(path.findAll('.path-link').map((link) => link.text())).toEqual(['BOM-1 V1.0', 'BOM-2 V1.0'])
    expect(path.get('.path-current').text()).toBe('BOM-3 V1.0')
    await path.get('.path-back').trigger('click')
    await settle()
    expect(childTableOf(childTable)).toBeUndefined()
    expect(childTableOf(wrapper)).toBeDefined()

    await expand(childTable)
    await childTableOf(childTable)!.findComponent(ManufacturingBomPath)
      .findAll('.path-link')[0].trigger('click')
    await settle()
    expect(childTableOf(wrapper)).toBeUndefined()
  })

  it('子层指回祖先 BOM 时显示循环引用提示，不请求祖先、不生成更深层', async () => {
    const root = bom(1, [make(11, 2)])
    mockDetails([bom(2, [make(21, 1)])])
    const wrapper = await table(root)
    await expand(wrapper)
    const childTable = childTableOf(wrapper)!
    await expand(childTable)
    expect(childTable.text()).toContain('循环引用')
    expect(childTable.text()).toContain('已拦截展开')
    expect(api.getEngineeringBomInfo.mock.calls).toEqual([[2]])
    expect(childTableOf(childTable)).toBeUndefined()
  })

  it('外购行即使带 subBomId、自制行无 subBomId，都不提供可用展开入口', async () => {
    const wrapper = await table(bom(1, [
      item(11, { subBomId: 2 }),
      item(12, { sourceType: BomSourceTypeEnum.MAKE, subBomId: null }),
    ]))
    expect(wrapper.findAll('button.el-table__expand-icon:not(:disabled)')).toHaveLength(0)
    expect(wrapper.findAll('tr.no-sub-bom')).toHaveLength(2)
    for (const button of wrapper.findAll('button.el-table__expand-icon')) {
      await button.trigger('click')
    }
    await settle()
    expect(api.getEngineeringBomInfo).not.toHaveBeenCalled()
    expect(childTableOf(wrapper)).toBeUndefined()
  })

  it('新增展开列不破坏序号分组，展开列自身不被合并', async () => {
    mockDetails([bom(2, [item(21)])])
    const wrapper = await table(bom(1, [
      make(11, 2), item(12, { parentMaterialId: 11 }),
    ]))
    const cells = wrapper.findAll('tr.el-table__row')[0].findAll('td')
    expect(cells[0].attributes('rowspan')).toBe('1')
    expect(cells[1].attributes('rowspan')).toBe('2')
    expect(wrapper.findAll('tr.el-table__row')[1].findAll('td')[0].classes())
      .toContain('el-table__expand-column')
    await expand(wrapper)
    const parentRows = wrapper.findAll('tr.el-table__row')
      .filter((row) => !row.element.closest('.sub-bom-detail'))
    expect(parentRows[0].findAll('td')[1].attributes('rowspan')).toBe('1')
    expect(parentRows[1].findAll('td')[1].text()).toBe('1')
  })

  it('子方案读取失败显示提示，可重试且不写入空的成功缓存', async () => {
    const child = bom(2, [item(21)])
    api.getEngineeringBomInfo.mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ data: child })
    const wrapper = await table(bom(1, [make(11, 2)]))
    await expand(wrapper)
    expect(wrapper.text()).toContain('下层生产BOM加载失败')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试加载下层方案')!
    await retry.trigger('click')
    await settle()
    expect(childTableOf(wrapper)!.props('items')).toEqual(child.items)
    expect(wrapper.text()).not.toContain('下层生产BOM加载失败')
  })
})

describe('BOM详情版本演变', () => {
  it('点击真实时间线版本，同时刷新头部、明细、根路径并清空旧展开', async () => {
    const first = bom(1, [make(11, 2)], { bomName: '旧版方案', isCurrent: false })
    const latest = bom(3, [item(31)], {
      bomCode: first.bomCode, bomName: '新版方案', bomVersion: 'V2.0',
      outputType: ManufacturingOutputTypeEnum.MATERIAL, outputCode: 'SFG-3', outputName: '新版半成品',
    })
    mockDetails([first, latest, bom(2, [item(21)])])
    api.getBomVersions.mockResolvedValue({ data: [first, latest] })
    const wrapper = mount(BomDetail, {
      attachTo: document.body, props: { modelValue: true, bomId: 1 },
    })
    mounted.push(wrapper)
    await settle()
    await expand(wrapper.findComponent(BomDetailTable))
    expect(wrapper.findAllComponents(BomDetailTable)).toHaveLength(2)
    const versionPanel = wrapper.findComponent(BomVersionTimeline)
    expect(versionPanel.props('actions')).toEqual([])
    await wrapper.get('.el-collapse-item__header').trigger('click')
    await versionPanel.findAll('button').find((button) => button.text() === 'V2.0')!.trigger('click')
    await settle()
    expect(api.getEngineeringBomInfo).toHaveBeenLastCalledWith(3)
    const text = document.body.textContent || ''
    expect(text).toContain('新版方案')
    expect(text).toContain('新版半成品')
    expect(text).toContain('V2.0')
    expect(text).toContain('MAT-31')
    expect(text).not.toContain('MAT-11')
    expect(wrapper.findAllComponents(BomDetailTable)).toHaveLength(1)
    expect(wrapper.findComponent(BomDetailTable).props('ancestors')[0].bomId).toBe(3)
    expect(wrapper.findComponent(ManufacturingBomPath).get('.path-current').text()).toBe('BOM-1 V2.0')

    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    await settle()
    expect(api.getEngineeringBomInfo).toHaveBeenLastCalledWith(1)
    expect(wrapper.findComponent(BomDetailTable).props('ancestors')[0].bomId).toBe(1)
  })

  it('快速切换入口或关闭时忽略旧详情请求，避免头部与明细串版', async () => {
    let resolveOld!: (value: any) => void
    api.getEngineeringBomInfo.mockImplementation((id: number) => id === 1
      ? new Promise((resolve) => { resolveOld = resolve })
      : Promise.resolve({ data: bom(id, [item(31)]) }))
    const wrapper = mount(BomDetail, {
      attachTo: document.body, props: { modelValue: true, bomId: 1 },
    })
    mounted.push(wrapper)
    await wrapper.setProps({ bomId: 3 })
    await settle()
    resolveOld({ data: bom(1, [item(11)]) })
    await settle()
    expect(wrapper.findComponent(BomDetailTable).props('ancestors')[0].bomId).toBe(3)
    expect(wrapper.findComponent(BomDetailTable).props('items')[0].itemId).toBe(31)
    await wrapper.setProps({ modelValue: false })
    await settle()
    expect(wrapper.findComponent(BomDetailTable).exists()).toBe(false)
  })
})

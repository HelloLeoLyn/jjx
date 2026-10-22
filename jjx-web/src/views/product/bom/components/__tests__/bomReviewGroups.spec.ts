import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
const permission = vi.hoisted(() => ({ allowed: true }))
vi.mock('@/directives', () => ({ hasPermi: () => permission.allowed }))
vi.mock('@/components/Selector/BomMaterialSelector.vue', () => ({ default: { template: '<span class="material-selector" />' } }))
import BomItemReviewTable from '../BomItemReviewTable.vue'
import { BomItemReviewStatusEnum as Status } from '@/enums/product'
import type { EngineeringBomItem } from '@/types/product/bom'

const row = (itemId: number, reviewStatus: number = Status.PENDING.value, parentMaterialId: number | null = null): EngineeringBomItem => ({
  itemId, bomId: 1, materialId: 5, materialCode: '重复编码', materialName: `材料${itemId}`, specification: '规格',
  unit: 'PCS', quantity: 99, baseQty: 4, moduleQty: 2, lossRate: 10, sortOrder: itemId,
  processId: 7, processName: '模切', parentMaterialId, reviewStatus,
})
const items = () => [row(10), row(11, Status.PENDING.value, 10), row(12, Status.APPROVED.value, 10),
  row(13, Status.REJECTED.value, 10), row(14, Status.PENDING_REMOVE.value, 10),
  { ...row(20), processId: undefined, processName: '' }, { ...row(30, Status.PENDING.value, 10), bomId: 2 }]
const mounted: ReturnType<typeof mount>[] = []
async function table(extra = {}) {
  const wrapper = mount(BomItemReviewTable, { attachTo: document.body, props: { items: items(), bomId: 1, ...extra } })
  mounted.push(wrapper)
  await nextTick()
  await flushPromises()
  return wrapper
}
beforeEach(() => { permission.allowed = true })
afterEach(() => { mounted.splice(0).forEach(wrapper => wrapper.unmount()); document.body.innerHTML = '' })

describe('审核组合展示、选择与逐材料处理', () => {
  it('真实表格显示组合和独立材料、项目及基数模数损耗用量依据', async () => {
    const wrapper = await table()
    expect(wrapper.text()).toContain('组合1 · 模切')
    expect(wrapper.text()).toContain('独立材料')
    for (const title of ['基数', '模数', '损耗率(%)', '用量', '应用料']) expect(wrapper.text()).toContain(title)
    const state = wrapper.vm as any
    expect(state.unitQuantity(items()[0])).toBe(2)
    expect(state.appliedQuantity(items()[0])).toBe(2.2)
    expect(state.canSelect(items()[0])).toBe(true) // 组首行是真实材料，参与审核。
    for (const item of [items()[2], items()[3], items()[4], items()[6]]) expect(state.canSelect(item)).toBe(false)
  })
  it('按组只添加当前BOM合法待审行，不清空其他组已选行、不自动发审核请求', async () => {
    const wrapper = await table()
    const state = wrapper.vm as any
    const independent = state.items.find((item: EngineeringBomItem) => item.itemId === 20)
    state.tableRef.toggleRowSelection(independent, true)
    await nextTick()
    const button = wrapper.findAll('button').find(node => node.text() === '选择本组待审')!
    await button.trigger('click')
    expect([...state.selectedIds].sort()).toEqual([10, 11, 20])
    expect(wrapper.emitted('batch-approve')).toBeUndefined()
    await wrapper.findAll('button').find(node => node.text().includes('批量通过'))!.trigger('click')
    expect(wrapper.emitted('batch-approve')?.at(-1)).toEqual([[10, 11, 20]])
  })
  it('部分成功刷新后仅保留仍待审的失败行，成功行不再可选，失败提示与组合仍可见', async () => {
    const wrapper = await table()
    const state = wrapper.vm as any
    state.selectGroup(state.groups[0].groupKey)
    await nextTick()
    await wrapper.setProps({ items: items().map(item => item.itemId === 10 ? { ...item, reviewStatus: Status.APPROVED.value } : item),
      batchFailures: [{ itemId: 11, reason: '物料失效' }] })
    await nextTick(); await nextTick()
    expect([...state.selectedIds]).toEqual([11])
    expect(wrapper.text()).toContain('11：物料失效')
    expect(wrapper.text()).toContain('组合1 · 模切')
  })
  it('没有审核权限或BOM不在审核中时，隐藏操作入口且禁止组选择', async () => {
    permission.allowed = false
    let wrapper = await table()
    expect(wrapper.findAll('button')).toHaveLength(0)
    expect(wrapper.findAll('.material-selector')).toHaveLength(0)
    expect((wrapper.vm as any).canSelect(items()[0])).toBe(false)
    permission.allowed = true
    wrapper = await table({ canReview: false })
    expect(wrapper.findAll('button')).toHaveLength(0)
    expect((wrapper.vm as any).groupSelectable((wrapper.vm as any).groups[0].groupKey)).toBe(false)
  })
  it('批量/单行繁忙时不增选；切换BOM后清除旧选择；相邻同项目不同已保存组保持分离', async () => {
    const wrapper = await table({ batchBusy: true })
    const state = wrapper.vm as any
    state.selectGroup(state.groups[0].groupKey)
    expect([...state.selectedIds]).toEqual([])
    await wrapper.setProps({ batchBusy: false })
    state.selectGroup(state.groups[0].groupKey)
    await nextTick()
    expect([...state.selectedIds]).toEqual([10, 11])
    await wrapper.setProps({ busyItemId: 10 })
    expect(state.canSelect(state.items[1])).toBe(false)
    await wrapper.setProps({ bomId: 2, busyItemId: undefined })
    expect([...state.selectedIds]).toEqual([])
    expect(state.groups[0].groupKey).not.toBe(state.groups[5].groupKey)
  })
})

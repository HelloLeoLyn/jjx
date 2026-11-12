import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, shallowRef } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
vi.mock('@/api/inventory/material', () => ({ materialApi: { search: vi.fn() } }))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: { detail: vi.fn() } }))
vi.mock('@/components/inventory/MaterialFormDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/Selector/StandardProcessSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/composables/useEnabledProcesses', () => ({ useEnabledProcesses: () => ({ processes: [] }) }))
vi.mock('sortablejs', () => ({ default: { create: vi.fn(() => ({ destroy: vi.fn(), option: vi.fn() })) } }))
import Sortable from 'sortablejs'
import { ElMessage } from 'element-plus'
import { materialApi } from '@/api/inventory/material'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import BomItemEditor from '../BomItemEditor.vue'
const original = { itemId: 1, materialId: 5, materialCode: 'RM000105', materialName: '材料', specification: '450*225mm', materialSpecification: '450*225mm', quantity: 1, unit: 'M', lossRate: 0, sortOrder: 1 }
const replacement = { materialId: 9, materialCode: 'NEW', materialName: '新材料', specification: '195mm*80mm', unit: 'PCS' }
const detail = (specification = original.specification) => ({ data: { material: { specification, categoryId: 7 }, attributes: { coating: '单面加硬' } } })
const wrappers: any[] = []
// Render the real specification slot with the reactive row, without mounting the full table.
function editor(row: any = original, props: Record<string, unknown> = {}, slotProp = 'specification') {
  const renderedState = shallowRef<any>()
  const wrapper = shallowMount(BomItemEditor, {
    props: { modelValue: [{ ...row }], ...props },
    global: { stubs: {
      Check: true, InfoFilled: true,
      BomEntryTools: defineComponent({
        setup(_, { slots }) { return () => h('div', slots.default?.({
          resultFor: () => undefined, showConversions: false, showAdvanced: false,
          openPitch: () => {}, canPitch: () => false, loading: false,
        })) },
      }),
      ElTable: { template: '<div class="el-table__body-wrapper"><table><tbody /></table><slot /></div>' },
      ElTableColumn: defineComponent({
        props: ['prop'],
        setup(props, { slots }) {
          return () => props.prop === slotProp && renderedState.value
            ? h('div', { class: 'spec-cell' }, slots.default?.({ row: renderedState.value.items[0] })) : null
        },
      }),
      ElInput: defineComponent({
        props: ['modelValue'], emits: ['update:modelValue'],
        setup(props, { emit }) {
          return () => h('input', { value: props.modelValue, onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value) })
        },
      }),
    } },
  })
  wrappers.push(wrapper)
  const state = (wrapper.vm as any).$.setupState
  renderedState.value = state
  return { wrapper, state, row: renderedState.value.items[0], exposed: wrapper.vm as any }
}
beforeEach(() => {
  vi.mocked(materialMasterApi.detail).mockReset().mockImplementation(async (id: any) => detail(id === 9 ? replacement.specification : original.specification) as any)
  vi.mocked(materialApi.search).mockReset()
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.restoreAllMocks() })
describe('BOM规格只读，换料走物料名称列', () => {
  it('已选物料显示档案规格，无输入框、标记、候选弹窗或属性拼写', async () => {
    const { wrapper, state } = editor({ ...original, specification: '历史规格', materialSpecificationSource: { specification: original.specification, attributes: { coating: '单面加硬' } } })
    await flushPromises()
    const cell = wrapper.get('.spec-cell')
    expect(cell.text()).toBe('450*225mm')
    expect(cell.find('input').exists()).toBe(false)
    expect(cell.find('[tabindex], button, a').exists()).toBe(false)
    expect(wrapper.text()).not.toMatch(/已改写|恢复系统生成|单面加硬|按规格选择物料/)
    expect(wrapper.find('el-dialog-stub').exists()).toBe(false)
    expect(state.materialFormVisible).toBe(false)
    expect(materialApi.search).not.toHaveBeenCalled()
  })
  it('快速模式下已选物料仍只读', async () => {
    const { wrapper, state } = editor()
    state.setQuickMode(true)
    await flushPromises()
    expect(wrapper.get('.spec-cell').text()).toBe(original.specification)
    expect(wrapper.get('.spec-cell').find('input').exists()).toBe(false)
  })
  it('物料名称列选择后立即带出规格、单位等，刷新后可提交', async () => {
    const { wrapper, state, row, exposed } = editor()
    state.handleMaterialSelect(replacement, row)
    expect(row).toMatchObject(replacement)
    await flushPromises()
    expect(wrapper.get('.spec-cell').text()).toBe(replacement.specification)
    expect(await exposed.prepareSpecifications()).toBe(true)
    Object.assign(row, { baseQty: 1, moduleQty: 1 })
    expect(exposed.validateItems()).toBe(true)
    expect(exposed.getItems()[0]).toMatchObject(replacement)
    expect(materialApi.search).not.toHaveBeenCalled()
  })
  it('未建档快速行可以手填规格并提交，不查询、不自动建料', async () => {
    const { wrapper, state, row, exposed } = editor({ ...original, materialId: 0, specification: '' })
    state.setQuickMode(true)
    await flushPromises()
    await wrapper.get('.spec-cell input').setValue('手填规格')
    expect(row.specification).toBe('手填规格')
    expect(exposed.validateItems()).toBe(true)
    expect(exposed.getItems()[0].specification).toBe('手填规格')
    expect(materialApi.search).not.toHaveBeenCalled()
    expect(materialMasterApi.detail).not.toHaveBeenCalled()
    expect(state.materialFormVisible).toBe(false)
  })
  it('名称搜索的新建物料仍预填规格和单位，建成后回填', async () => {
    const { state, row } = editor({ ...original, materialId: 0, specification: '手填规格' })
    state.handleCreateMaterial(row, '新材料')
    expect(state.materialFormVisible).toBe(true)
    expect(state.materialPreset).toEqual({ materialName: '新材料', specification: '手填规格', unit: 'M' })
    state.handleMaterialCreated(replacement)
    expect(row).toMatchObject(replacement)
    await flushPromises()
  })
  it('程序改出不一致规格时仍阻断提交，提示名称列重新选料', () => {
    const notice = vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({} as any))
    const { row, exposed } = editor()
    row.specification = '别的规格'
    expect(exposed.validateItems()).toBe(false)
    expect(notice).toHaveBeenCalledWith('规格与物料档案不一致，请在物料名称列重新选料')
  })
  it('名称列连续换料时，旧详情响应不覆盖新物料的档案规格', async () => {
    let resolve!: (value: any) => void
    vi.mocked(materialMasterApi.detail).mockReturnValueOnce(new Promise(done => { resolve = done }))
    const { state, row } = editor()
    state.handleMaterialSelect({ ...replacement, materialId: 8, specification: '旧选择' }, row)
    state.handleMaterialSelect(replacement, row)
    await flushPromises()
    resolve(detail('迟到规格'))
    await flushPromises()
    expect(row).toMatchObject(replacement)
    expect(row.materialSpecification).toBe(replacement.specification)
  })
})

describe('制造BOM入口（dev-20261111-005）：只对已建档行开放，不改分组/物料', () => {
  it('已建档、未待移除的行可以打开制造BOM，只发出该行', () => {
    const { wrapper, state, row } = editor()
    expect(state.showManufacturing(row)).toBe(true)
    state.handleManufacturing(row)
    const payload = wrapper.emitted('manufacturing')?.[0]?.[0] as any
    expect(payload).toBe(row)
    expect(payload.materialId).toBe(original.materialId)
    expect(payload.parentMaterialId).toBeUndefined()
  })
  it('未建档行、待移除行、无权限或只读场景不显示入口、不发事件', () => {
    const unbuilt = editor({ ...original, materialId: 0 })
    expect(unbuilt.state.showManufacturing(unbuilt.row)).toBe(false)
    unbuilt.state.handleManufacturing(unbuilt.row)
    expect(unbuilt.wrapper.emitted('manufacturing')).toBeUndefined()

    const pendingRemove = editor({ ...original, reviewStatus: 5 })
    expect(pendingRemove.state.showManufacturing(pendingRemove.row)).toBe(false)

    const denied = editor(original, { canManufacturing: false })
    expect(denied.state.showManufacturing(denied.row)).toBe(false)
    denied.state.handleManufacturing(denied.row)
    expect(denied.wrapper.emitted('manufacturing')).toBeUndefined()

    // 只读用户可查看但不能新建下层：入口整体不显示
    const readonly = editor(original, { readonly: true })
    expect(readonly.state.showManufacturing(readonly.row)).toBe(false)
    readonly.state.handleManufacturing(readonly.row)
    expect(readonly.wrapper.emitted('manufacturing')).toBeUndefined()
  })
  it('采用制造方案后按 itemId 回填该行，其他行与派生值不受影响', () => {
    const { state, exposed } = editor()
    expect(exposed.updateItem(1, { subBomId: 124, sourceType: 'make' })).toBe(true)
    const submitted = exposed.getItems()[0]
    expect(submitted.subBomId).toBe(124)
    expect(submitted.sourceType).toBe('make')
    expect(submitted.materialId).toBe(original.materialId)
    expect(exposed.updateItem(999, { subBomId: 1 })).toBe(false)
    expect(state.showManufacturing(submitted)).toBe(true)
  })
})

describe('父BOM行模数/基数允许正小数（dev-20261111-005）', () => {
  it('0.5/2.5 合法（用量0.2）；0 与空值以外的非法值被拦，历史空值行不误拦', async () => {
    const notice = vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({} as any))
    const { state, row, exposed } = editor()
    state.handleMaterialSelect(replacement, row)
    await flushPromises()
    expect(await exposed.prepareSpecifications()).toBe(true)

    row.moduleQty = 2.5
    row.baseQty = 0.5
    state.recalcAppliedIssue(row)
    expect(row.quantity).toBe(0) // 派生量不在前端重写；保存按原始比例，显示由接口返回
    expect(row.quantityStale).toBe(true)
    expect(exposed.validateItems()).toBe(true)

    row.moduleQty = 0
    expect(exposed.validateItems()).toBe(false)
    expect(notice).toHaveBeenCalledWith(expect.stringContaining('模数必须大于0'))

    row.moduleQty = 6
    row.baseQty = 0
    expect(exposed.validateItems()).toBe(false)
    expect(notice).toHaveBeenCalledWith(expect.stringContaining('基数必须大于0'))
  })

  it('来源方式为空按历史片材口径，仍校验模数/基数（dev-20261112-008 回归）', async () => {
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({} as any))
    const { state, row, exposed } = editor()
    state.handleMaterialSelect(replacement, row)
    await flushPromises()
    // 片材/直接不自动写 consumptionMode，空值即历史口径
    expect((row as any).consumptionMode).toBeUndefined()
    expect(await exposed.prepareSpecifications()).toBe(true)

    row.moduleQty = 0
    expect(exposed.validateItems()).toBe(false)
    row.moduleQty = 6
    row.baseQty = 0
    expect(exposed.validateItems()).toBe(false)
  })
})

describe('编辑权只读时禁用物料选择与拖拽', () => {
  it('编辑权失效后卸载物料选择器，键盘和原弹层不能再改料', async () => {
    const { wrapper, state } = editor(original, {}, 'materialName')
    state.setQuickMode(false)
    await flushPromises()
    expect(wrapper.find('bom-material-selector-stub').exists()).toBe(true)
    await wrapper.setProps({ readonly: true })
    expect(wrapper.find('bom-material-selector-stub').exists()).toBe(false)
    expect(wrapper.get('.spec-cell').text()).toContain(original.materialName)
  })

  it('只读初始化禁拖拽，编辑权失效后关闭已有拖拽实例且不改顺序', async () => {
    const rows = [{ ...original }, { ...original, itemId: 2 }]
    const { wrapper, exposed } = editor(original, { modelValue: rows })
    await flushPromises()
    const create = vi.mocked(Sortable.create)
    const options = create.mock.calls.at(-1)?.[1] as any
    const instance = create.mock.results.at(-1)?.value as any
    await wrapper.setProps({ readonly: true })
    expect(instance.option).toHaveBeenCalledWith('disabled', true)
    options.onEnd({ oldIndex: 0, newIndex: 1 })
    expect(exposed.getItems().map((row: any) => row.itemId)).toEqual([1, 2])
    editor(original, { readonly: true })
    await flushPromises()
    expect(create.mock.calls.at(-1)?.[1]?.disabled).toBe(true)
  })
})

describe('精简录入保留计量语义', () => {
  it.each(['损耗率', '最低投料', '同物料同单位重新选择'])('历史仅用量行修改%s仍可保存原用量', async (change) => {
    const { state, row, exposed } = editor()
    await flushPromises()
    if (change === '损耗率') {
      row.lossRate = 2
      state.handleLossRateChange(row)
    } else if (change === '最低投料') {
      // 高级设置在原行回填最低投料并标记快照过期。
      row.minIssueQty = 2
      row.quantityStale = true
    } else {
      state.handleMaterialSelect({ ...original }, row)
      await flushPromises()
    }
    expect(row.moduleQty).toBeUndefined()
    expect(row.baseQty).toBeUndefined()
    expect(row.quantityStale).toBe(true)
    expect(exposed.validateItems()).toBe(true)
    expect(exposed.getItems()[0]).toMatchObject({ quantity: 1, lossRate: row.lossRate })
    expect(exposed.getItems()[0].minIssueQty).toBe(row.minIssueQty)
  })

  it('新草稿没有比例和有效旧用量时仍阻止保存', async () => {
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({} as any))
    const { row, exposed } = editor({ ...original, quantity: 0, quantityStale: true })
    await flushPromises()
    expect(exposed.validateItems()).toBe(false)
    expect(row.quantity).toBe(0)
  })

  it('同料号改为档案单位时也须重新确认比例，不把PCS比例直接当米数', async () => {
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({} as any))
    const { state, row, exposed } = editor({ ...original, unit: 'PCS', baseQty: 1, moduleQty: 6 })
    state.handleMaterialSelect({ ...replacement, materialId: original.materialId, unit: 'M' }, row)
    expect(row.unit).toBe('M')
    expect(row.baseQty).toBeUndefined()
    expect(row.moduleQty).toBeUndefined()
    expect(row.quantity).toBe(0)
    expect(row.quantityStale).toBe(true)
    await flushPromises()
    expect(exposed.validateItems()).toBe(false)
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, shallowRef } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
vi.mock('@/api/inventory/material', () => ({ materialApi: { search: vi.fn() } }))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: { detail: vi.fn() } }))
vi.mock('@/components/inventory/MaterialFormDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/Selector/StandardProcessSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/composables/useEnabledProcesses', () => ({ useEnabledProcesses: () => ({ processes: [] }) }))
vi.mock('sortablejs', () => ({ default: { create: () => ({ destroy() {} }) } }))
import { ElMessage } from 'element-plus'
import { materialApi } from '@/api/inventory/material'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import BomItemEditor from '../BomItemEditor.vue'
const original = { itemId: 1, materialId: 5, materialCode: 'RM000105', materialName: '材料', specification: '450*225mm', materialSpecification: '450*225mm', quantity: 1, unit: 'M', lossRate: 0, sortOrder: 1 }
const replacement = { materialId: 9, materialCode: 'NEW', materialName: '新材料', specification: '195mm*80mm', unit: 'PCS' }
const detail = (specification = original.specification) => ({ data: { material: { specification, categoryId: 7 }, attributes: { coating: '单面加硬' } } })
const wrappers: any[] = []
// Render the real specification slot with the reactive row, without mounting the full table.
function editor(row: any = original) {
  const renderedState = shallowRef<any>()
  const wrapper = shallowMount(BomItemEditor, {
    props: { modelValue: [{ ...row }] },
    global: { stubs: {
      Check: true, InfoFilled: true,
      ElTable: { template: '<div class="el-table__body-wrapper"><table><tbody /></table><slot /></div>' },
      ElTableColumn: defineComponent({
        props: ['prop'],
        setup(props, { slots }) {
          return () => props.prop === 'specification' && renderedState.value
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

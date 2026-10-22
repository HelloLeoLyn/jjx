import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/popover/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-menu/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-item/style/css', () => ({}))
import ProcessMaterialGroupEditor from '../ProcessMaterialGroupEditor.vue'
import ProcessMaterialEditor from '../ProcessMaterialEditor.vue'
import { assignMaterial, hydrateProcessItems } from '../../composables/sampleProcessGroups'
function fixture(readonly = false) {
  const items = hydrateProcessItems([
    { stdProcessId: 1, processName: '面板' },
    { stdProcessId: 2, processName: '下线' },
  ])
  const pc = reactive({
    uid: 'card',
    editing: true,
    items,
    operationRemark: '',
    materialRows: items.map((item, index) =>
      assignMaterial({ name: '材料' + index, uid: String(index) }, item)
    ),
  })
  const wrapper = mount(ProcessMaterialGroupEditor, {
    props: { pc, readonly, parseMaterials: () => [] },
    global: { stubs: { IconStepBadge: true, ProcessMaterialEditor: true } },
  })
  return { pc, wrapper }
}
describe('独立工序材料组件', () => {
  it('每子工序独立引用材料行，新增带归属，删除过滤表首行不误删原数组首行', () => {
    const { pc, wrapper } = fixture(),
      editors = wrapper.findAllComponents(ProcessMaterialEditor)
    expect(editors).toHaveLength(2)
    expect(editors[0].props('rows')[0]).toBe(pc.materialRows[0])
    expect(editors[1].props('rows')[0]).toBe(pc.materialRows[1])
    editors[1].vm.$emit('add-material-row')
    expect(wrapper.emitted('add-material-row')?.[0]).toEqual([pc.items[1].itemKey])
    editors[1].vm.$emit('remove-material', 0)
    expect(pc.materialRows.map((row) => row.name)).toEqual(['材料0'])
    wrapper.unmount()
  })
  it('空子表仍显示，历史模糊归属保留在待分配区', async () => {
    const { pc, wrapper } = fixture()
    pc.materialRows = [{ name: '历史材料', uid: 'legacy' }] as any
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('待分配材料（1）')
    expect(wrapper.findAllComponents(ProcessMaterialEditor)).toHaveLength(2)
    expect(
      wrapper
        .findAllComponents(ProcessMaterialEditor)
        .every((editor) => editor.props('rows').length === 0)
    ).toBe(true)
    wrapper.unmount()
  })
  it('只读即使editing=true也无输入、分配和增删入口', () => {
    const { wrapper } = fixture(true)
    expect(wrapper.findAll('input')).toHaveLength(0)
    expect(wrapper.findAll('button')).toHaveLength(0)
    expect(
      wrapper.findAllComponents(ProcessMaterialEditor).every((editor) => editor.props('readonly'))
    ).toBe(true)
    wrapper.unmount()
  })
})

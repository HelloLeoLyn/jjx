import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import MaterialMasterSettingsDialog from '../MaterialMasterSettingsDialog.vue'

const api = vi.hoisted(() => ({ categories: vi.fn(), dictionaries: vi.fn(), template: vi.fn(), saveTemplate: vi.fn() }))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: api }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() }, ElMessageBox: {} }))
const Slot = defineComponent({ template: '<div><slot /></div>' })
const Button = defineComponent({ emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' })
const stubs = Object.fromEntries(['ElDialog', 'ElTabs', 'ElTabPane', 'ElForm', 'ElFormItem', 'ElSelect', 'ElOption', 'ElTable'].map(name => [name, Slot]))

describe('属性模板界面保存', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.categories.mockResolvedValue({ data: [{ categoryId: 2, categoryCode: 'RAW_AUX_ELECTRONIC', categoryName: '电子元件', pathName: '原材料/电子元件', parentId: 1, categoryLevel: 3 }] })
    api.dictionaries.mockResolvedValue({ data: {} })
    api.template.mockResolvedValue({ data: { template: { templateCode: 'TPL_ELECTRONIC', templateName: '电子元件', status: 1 }, schema: {
      version: 1, meta_options: [], default_unit: 'PCS', fields: [{ key: 'component_type', label: '元件类型', type: 'enum', required: true, options: [{ code: 'SPRING', label: '弹片' }] }],
    } } })
    api.saveTemplate.mockResolvedValue({ data: {} })
  })
  it('用户补常用属性并配置显示设置后，真实保存方法提交完整模板元数据', async () => {
    const wrapper = mount(MaterialMasterSettingsDialog, { props: { modelValue: false }, global: { stubs: {
      ...stubs, ElButton: Button, ElTableColumn: true, ElInput: true, ElInputNumber: true, ElSwitch: true, ElCheckbox: true, ElAlert: true,
    } } })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    const state = wrapper.vm as any
    state.templateCategoryId = 2
    await state.loadTemplate(2)
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '补充本品类常用属性')!.trigger('click')
    const dimple = state.schema.fields.find((field: any) => field.key === 'has_dimple')
    expect(dimple.true_label).toBe('带点')
    dimple.spec_order = 8
    dimple.common = false
    await state.saveTemplate()
    expect(api.saveTemplate).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 2, attributeSchema: expect.objectContaining({
      fields: expect.arrayContaining([expect.objectContaining({ key: 'has_dimple', common: false, in_spec: true, spec_order: 8, true_label: '带点', visible_when: { component_type: 'SPRING' } })]),
    }) }))
    wrapper.unmount()
  })
  it('属性支持上移/下移与删除，顺序按调整后的数组提交', async () => {
    const wrapper = mount(MaterialMasterSettingsDialog, { props: { modelValue: false }, global: { stubs: {
      ...stubs, ElButton: Button, ElTableColumn: true, ElInput: true, ElInputNumber: true, ElSwitch: true, ElCheckbox: true, ElAlert: true,
    } } })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    const state = wrapper.vm as any
    state.templateCategoryId = 2
    await state.loadTemplate(2)
    await flushPromises()
    state.addField()
    state.schema.fields[1].key = 'b'
    state.schema.fields.push({ key: 'c', label: 'C', type: 'string', required: false, common: true, in_spec: true, spec_order: 3 })
    state.moveField(2, -1)
    expect(state.schema.fields.map((field: any) => field.key)).toEqual(['component_type', 'c', 'b'])
    state.moveField(0, 1)
    expect(state.schema.fields.map((field: any) => field.key)).toEqual(['c', 'component_type', 'b'])
    state.schema.fields.splice(2, 1)
    expect(state.schema.fields.map((field: any) => field.key)).toEqual(['c', 'component_type'])
    wrapper.unmount()
  })
})

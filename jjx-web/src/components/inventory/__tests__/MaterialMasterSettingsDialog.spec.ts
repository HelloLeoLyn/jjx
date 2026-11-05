import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import MaterialMasterSettingsPanel from '../MaterialMasterSettingsPanel.vue'

const api = vi.hoisted(() => ({ categories: vi.fn(), dictionaries: vi.fn(), template: vi.fn(), saveTemplate: vi.fn(), specPreview: vi.fn(), fieldOptions: vi.fn(), previewNaming: vi.fn(), saveCategory: vi.fn() }))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: api }))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success: vi.fn(), error: vi.fn() }, ElMessageBox: {} }
})
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
    api.saveCategory.mockResolvedValue({ data: {} })
    api.previewNaming.mockResolvedValue({ data: { samples: [] } })
    api.fieldOptions.mockResolvedValue({ data: [] })
    api.specPreview.mockResolvedValue({ data: { samples: [] } })
  })
  it('用户补常用属性并配置显示设置后，真实保存方法提交完整模板元数据', async () => {
    const wrapper = mount(MaterialMasterSettingsPanel, { global: { stubs: {
      ...stubs, ElButton: Button, ElTableColumn: true, ElInput: true, ElInputNumber: true, ElSwitch: true, ElCheckbox: true, ElAlert: true,
    } } })
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
    const wrapper = mount(MaterialMasterSettingsPanel, { global: { stubs: {
      ...stubs, ElButton: Button, ElTableColumn: true, ElInput: true, ElInputNumber: true, ElSwitch: true, ElCheckbox: true, ElAlert: true,
    } } })
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
  it('尺寸字段显示单位保存后立即刷新预览，不改属性自带单位或旧规格', async () => {
    const wrapper = mount(MaterialMasterSettingsPanel, { props: { focusSection: 'naming' }, global: { stubs: {
      ...stubs, ElButton: Button, ElTableColumn: true, ElInput: true, ElInputNumber: true, ElSwitch: true, ElCheckbox: true, ElAlert: true,
    } } })
    await flushPromises()
    const state = wrapper.vm as any
    state.templateCategoryId = 2
    await state.loadTemplate(2)
    await flushPromises()
    state.specPattern = { fields: [{ key: 'thickness_mm', sep: '*', unit: 'μm', scale: 1 }, { key: 'width', sep: '*' }, { key: 'length', sep: '*', unit: 'm', scale: 2 }], show_label: false, with_unit: true, unit_position: 'each' }
    await flushPromises()
    expect(wrapper.find('[data-test="unit-example-thickness_mm"]').text()).toContain('0.254mm → 254μm')
    api.specPreview.mockClear().mockResolvedValue({ data: { samples: [{ materialId: 1, materialCode: 'RM001', oldSpec: '旧规格', newSpec: '254μm*315mm*1000m' }] } })
    await state.saveTemplate()
    expect(api.saveTemplate).toHaveBeenCalledWith(expect.objectContaining({ attributeSchema: expect.objectContaining({ spec_pattern: expect.objectContaining({ fields: expect.arrayContaining([expect.objectContaining({ key: 'thickness_mm', unit: 'μm', scale: 1 })]) }) }) }))
    expect(api.specPreview).toHaveBeenCalledWith(2)
    expect(state.specSamples[0]).toMatchObject({ oldSpec: '旧规格', newSpec: '254μm*315mm*1000m' })
    wrapper.unmount()
  })

  it('型号规则共享字段候选但排除自身，单位/顺序/分隔符保存完整', async () => {
    api.fieldOptions.mockResolvedValue({ data: [
      { key: 'model', label: '型号' }, { key: 'categoryprefix', label: '品类前缀' }, { key: 'thickness_mm', label: '厚度' },
    ] })
    const wrapper = mount(MaterialMasterSettingsPanel, { props: { focusSection: 'naming' }, global: { stubs: {
      ...stubs, ElButton: Button, ElTableColumn: true, ElInput: true, ElInputNumber: true, ElSwitch: true, ElCheckbox: true, ElAlert: true,
    } } })
    await flushPromises()
    const state = wrapper.vm as any
    state.templateCategoryId = 2
    await state.loadTemplate(2)
    await flushPromises()
    expect(state.modelTokens.map((t: any) => t.key)).toEqual(['categoryprefix', 'thickness_mm'])
    state.toggleModelField('thickness_mm', true)
    state.toggleModelField('categoryprefix', true)
    state.moveModelField(1, -1)
    state.setModelSeparator('thickness_mm', '')
    state.setModelUnit('thickness_mm', 'μm')
    await state.saveTemplate()
    expect(api.saveTemplate).toHaveBeenCalledWith(expect.objectContaining({ attributeSchema: expect.objectContaining({
      model_pattern: { fields: ['categoryprefix', 'thickness_mm'], separator: '', empty: 'skip', separators: { thickness_mm: '' }, units: { thickness_mm: 'μm' } },
    }) }))
    wrapper.unmount()
  })

  it('分类编辑带回型号前缀，修改后通过现有保存入口提交', async () => {
    api.categories.mockResolvedValue({ data: [{ categoryId: 2, categoryCode: 'RAW_BASE_PC', categoryName: 'PC膜/片',
      modelPrefix: 'PC', pathName: '原材料/基材/PC膜/片', parentId: 1, categoryLevel: 3 }] })
    const wrapper = mount(MaterialMasterSettingsPanel, { global: { stubs: {
      ...stubs, ElButton: Button, ElTableColumn: true, ElInput: true, ElInputNumber: true, ElSwitch: true, ElCheckbox: true, ElAlert: true,
    } } })
    await flushPromises()
    const state = wrapper.vm as any
    state.categoryEditId = 2
    state.editCategory(2)
    expect(state.categoryForm.modelPrefix).toBe('PC')
    state.categoryForm.modelPrefix = 'PCX'
    await state.saveCategory()
    expect(api.saveCategory).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 2, modelPrefix: 'PCX' }))
    wrapper.unmount()
  })

})

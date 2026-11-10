import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import MaterialMasterSettingsPanel from '../MaterialMasterSettingsPanel.vue'
import MaterialMasterSettingsDialog from '../MaterialMasterSettingsDialog.vue'
import { ElMessageBox } from 'element-plus'

const api = vi.hoisted(() => ({ categories: vi.fn(), dictionaries: vi.fn(), template: vi.fn(), saveTemplate: vi.fn(), savePattern: vi.fn(), saveTemplateFields: vi.fn(), specPreview: vi.fn(), fieldOptions: vi.fn(), previewNaming: vi.fn(), detail: vi.fn(), saveCategory: vi.fn() }))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: api }))
vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }
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
    api.saveTemplateFields.mockImplementation(async (data) => ({ data: { template: {}, schema: JSON.parse(JSON.stringify(data.attributeSchema)) } }))
    api.savePattern.mockImplementation(async (data) => ({ data: { template: {}, schema: { [data.block]: JSON.parse(JSON.stringify(data.pattern)) } } }))
    api.saveCategory.mockResolvedValue({ data: {} })
    api.previewNaming.mockResolvedValue({ data: { samples: [] } })
    api.detail.mockReset().mockResolvedValue({ data: { material: { supplierMaterialName: '供应商品名', model: '原型号' } } })
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
    expect(api.saveTemplateFields).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 2, attributeSchema: expect.objectContaining({
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
    await state.savePattern('spec_pattern')
    expect(api.savePattern).toHaveBeenCalledWith({ categoryId: 2, block: 'spec_pattern', pattern: expect.objectContaining({ fields: expect.arrayContaining([expect.objectContaining({ key: 'thickness_mm', unit: 'μm', scale: 1 })]) }) })
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
    await state.savePattern('model_pattern')
    expect(api.savePattern).toHaveBeenCalledWith({ categoryId: 2, block: 'model_pattern', pattern: { fields: ['categoryprefix', 'thickness_mm'], separator: '', empty: 'skip', separators: { thickness_mm: '' }, units: { thickness_mm: 'μm' } } })
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

  it.each([
    { name: '有启用子类的父级', editId: 2, childStatus: 1, required: false },
    { name: '只有停用子类的品类', editId: 2, childStatus: 0, required: true },
    { name: '没有子类的叶子', editId: 3, childStatus: 1, required: true },
    { name: '新建叶子', editId: undefined, childStatus: 1, required: true },
  ])('$name 的前缀必填星号与提交预检一致', async ({ editId, childStatus, required }) => {
    api.categories.mockResolvedValue({ data: [
      { categoryId: 2, categoryCode: 'RAW_AUX_ELECTRONIC', categoryName: '电子元件',
        pathName: '原材料/辅材/电子元件', parentId: 1, categoryLevel: 3, status: 1 },
      { categoryId: 3, categoryCode: 'RAW_AUX_LED', categoryName: 'LED',
        pathName: '原材料/辅材/电子元件/LED', parentId: 2, categoryLevel: 4, status: childStatus },
    ] })
    const FormItem = defineComponent({ props: { label: String, required: Boolean }, template: '<div><slot /></div>' })
    const wrapper = mount(MaterialMasterSettingsPanel, { global: { stubs: {
      ...stubs, ElFormItem: FormItem, ElButton: Button, ElTableColumn: true, ElInput: true,
      ElInputNumber: true, ElSwitch: true, ElCheckbox: true, ElAlert: true,
    } } })
    await flushPromises()
    const state = wrapper.vm as any
    state.categoryEditId = editId
    state.editCategory(editId)
    await flushPromises()
    const prefixItem = wrapper.findAllComponents(FormItem).find(item => item.props('label') === '型号前缀')!
    expect(prefixItem.props('required')).toBe(required)
    for (const modelPrefix of ['', ' ']) {
      api.saveCategory.mockClear()
      state.categoryForm.modelPrefix = modelPrefix
      await state.saveCategory()
      if (required) {
        expect(api.saveCategory).not.toHaveBeenCalled()
        expect(state.error).toContain('型号前缀不能为空')
      } else {
        expect(api.saveCategory).toHaveBeenCalledWith(expect.objectContaining({ categoryId: editId, modelPrefix }))
        expect(state.error).toBe('')
      }
    }
    if (required) {
      for (const invalid of ['pc', 'P-C', 'ABCDEFGHI']) {
        state.categoryForm.modelPrefix = invalid
        await state.saveCategory()
        expect(api.saveCategory).not.toHaveBeenCalled()
        expect(state.error).toContain('型号前缀须为1～8位')
      }
    }
    state.categoryForm.modelPrefix = 'LED'
    await state.saveCategory()
    expect(api.saveCategory).toHaveBeenCalledWith(expect.objectContaining({ categoryId: editId, modelPrefix: 'LED' }))
    wrapper.unmount()
  })

  const editableStubs = { ...stubs, ElButton: Button, ElTableColumn: true, ElInput: true,
    ElInputNumber: true, ElSwitch: true, ElCheckbox: true, ElAlert: true }
  async function loadedPanel(props = {}) {
    const wrapper = mount(MaterialMasterSettingsPanel, { props, global: { stubs: editableStubs } })
    await flushPromises()
    const state = wrapper.vm as any
    state.templateCategoryId = 2
    await state.loadTemplate(2)
    await flushPromises()
    return { wrapper, state }
  }
  it.each(['name_pattern', 'model_pattern', 'spec_pattern'])('%s 多字符/空串保存回读保真，4字符拒绝保存且中文提示', async block => {
    const { wrapper, state } = await loadedPanel()
    state.toggleNameField('brand', true)
    state.toggleModelField('brand', true)
    state.toggleSpecField('brand', true)
    const set = (value: string) => block === 'name_pattern' ? state.setNameSeparator('brand', value)
      : block === 'model_pattern' ? state.setModelSeparator('brand', value) : state.setSpecSep(0, value)
    const separator = (pattern: any) => block === 'spec_pattern' ? pattern.fields[0].sep : pattern.separators.brand
    for (const value of ['-', '/-', '-/-', '😀/😀', ' / ', ' ', '']) {
      set(value)
      await state.savePattern(block)
      expect(separator(state.schema[block])).toBe(value)
      expect(separator(state.patternDraft(block))).toBe(value)
      expect(state.error).toBe('')
    }
    api.savePattern.mockClear()
    set('abcd')
    await state.savePattern(block)
    expect(api.savePattern).not.toHaveBeenCalled()
    expect(state.error).toContain('分隔符最多允许3个字符')
    expect(state.isDirty(block)).toBe(true)
    for (const value of ['\n', '\t', '\r', '\u0000', '\u007f', '\u0085']) {
      set(value)
      await state.savePattern(block)
      expect(api.savePattern).not.toHaveBeenCalled()
      expect(state.error).toContain('分隔符不能包含换行、制表符或其它控制字符')
    }
    wrapper.unmount()
  })

  it('型号全选按命名候选顺序且排除自身，重置只清本块字段并标记未保存', async () => {
    const { wrapper, state } = await loadedPanel()
    state.toggleNameField('brand', true)
    state.toggleSpecField('width', true)
    state.setModelSeparator('brand', '-')
    state.setModelUnit('width', 'cm')
    const panel = wrapper.get('[data-test="model-pattern"]')
    await panel.findAll('button').find(button => button.text() === '全选')!.trigger('click')
    expect(state.modelPattern.fields).toEqual(state.namingTokens.map((token: any) => token.key).filter((key: string) => key !== 'model'))
    await state.savePattern('model_pattern')
    expect(state.isDirty('model_pattern')).toBe(false)
    await panel.findAll('button').find(button => button.text() === '重置')!.trigger('click')
    expect(state.modelPattern.fields).toEqual([])
    expect(state.modelPattern.separators).toEqual({ brand: '-' })
    expect(state.modelPattern.units).toEqual({ width: 'cm' })
    expect(state.blockTitle('model_pattern', '型号规则')).toContain('未保存')
    expect(state.namePattern.fields).toEqual(['brand'])
    expect(state.specPattern.fields).toEqual([{ key: 'width', sep: '' }])
    wrapper.unmount()
  })

  it.each([
    ['naming', 'name_pattern'], ['model', 'model_pattern'], ['spec', 'spec_pattern'],
  ])('%s 拖拽有落点高亮，取消/跨块不排序，保存回读保留顺序与字段配置', async (kind, block) => {
    const { wrapper, state } = await loadedPanel()
    state.namePattern.fields = ['brand', 'width', 'color']
    state.modelPattern.fields = ['brand', 'width', 'color']
    state.modelPattern.units = { width: 'cm' }
    state.specPattern.fields = [{ key: 'brand', sep: '' }, { key: 'width', sep: '*', unit: 'cm', scale: 2 }, { key: 'color', sep: '-' }]
    await flushPromises()
    const fields = () => state.patternDraft(block).fields
    const keys = () => fields().map((field: any) => typeof field === 'string' ? field : field.key)
    const groups = wrapper.findAll('.naming-rule').filter(group => group.findAll('.naming-order-row').length)
    const group = groups[['naming', 'model', 'spec'].indexOf(kind)]
    let rows = group.findAll('.naming-order-row')
    await rows[0].trigger('dragstart')
    await rows[2].trigger('dragover')
    expect(rows[2].classes()).toContain('drop-target')
    expect(rows[0].classes()).not.toContain('drop-target')
    await rows[0].trigger('dragend')
    expect(group.find('.drop-target').exists()).toBe(false)
    expect(keys()).toEqual(['brand', 'width', 'color'])
    state.startDrag(kind, 0)
    state.dropAt(kind === 'spec' ? 'model' : 'spec', 2)
    expect(keys()).toEqual(['brand', 'width', 'color'])
    await rows[0].trigger('dragstart')
    await rows[2].trigger('dragover')
    await rows[2].trigger('drop')
    expect(keys()).toEqual(['width', 'color', 'brand'])
    expect(group.find('.drop-target').exists()).toBe(false)
    await state.savePattern(block)
    expect(state.schema[block].fields).toEqual(fields())
    expect(state.isDirty(block)).toBe(false)
    if (kind === 'spec') expect(fields()[0]).toMatchObject({ key: 'width', sep: '*', unit: 'cm', scale: 2 })
    if (kind === 'model') expect(state.schema[block].units).toEqual({ width: 'cm' })
    wrapper.unmount()
  })

  it('四个子页签各自标记未保存，切换只提示一次，取消留在本块且不丢草稿', async () => {
    const { wrapper, state } = await loadedPanel({ initialTab: 'template' })
    expect(wrapper.find('.block-tabs').exists()).toBe(true)
    expect(state.isDirty('name_pattern')).toBe(false)
    state.activeBlock = 'name_pattern'
    state.toggleNameField('brand', true)
    await flushPromises()
    expect(state.blockTitle('name_pattern', '命名规则')).toBe('命名规则（未保存）')
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    await wrapper.get('[data-test="summary-model_pattern"]').trigger('click')
    await flushPromises()
    expect(state.activeBlock).toBe('name_pattern')
    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(state.namePattern.fields).toEqual(['brand'])
    vi.mocked(ElMessageBox.confirm).mockResolvedValueOnce('confirm')
    await wrapper.get('[data-test="summary-model_pattern"]').trigger('click')
    await flushPromises()
    expect(state.activeBlock).toBe('model_pattern')
    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(2)
    expect(state.isDirty('name_pattern')).toBe(true)
    await state.savePattern('name_pattern')
    expect(state.isDirty('name_pattern')).toBe(false)
    expect(state.blockTitle('name_pattern', '命名规则')).toBe('命名规则')
    vi.mocked(ElMessageBox.confirm).mockClear()
    expect(await state.beforeBlockLeave('spec_pattern', 'name_pattern')).toBe(true)
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('规格保存只发本块，命名草稿保留；依次保存两块后摘要反映已保存配置', async () => {
    const { wrapper, state } = await loadedPanel()
    state.toggleNameField('brand', true)
    state.toggleModelField('categoryprefix', true)
    state.toggleSpecField('width', true)
    await state.savePattern('spec_pattern')
    expect(api.savePattern).toHaveBeenLastCalledWith({ categoryId: 2, block: 'spec_pattern', pattern: expect.objectContaining({ fields: [{ key: 'width', sep: '' }] }) })
    expect(state.isDirty('spec_pattern')).toBe(false)
    expect(state.isDirty('name_pattern')).toBe(true)
    expect(state.isDirty('model_pattern')).toBe(true)
    expect(state.namePattern.fields).toEqual(['brand'])
    expect(wrapper.get('[data-test="summary-spec_pattern"]').text()).toContain('宽')
    expect(wrapper.get('[data-test="summary-name_pattern"]').text()).toContain('未配置')
    await state.savePattern('name_pattern')
    expect(state.schema.spec_pattern.fields).toEqual([{ key: 'width', sep: '' }])
    expect(wrapper.get('[data-test="summary-name_pattern"]').text()).toContain('品牌')
    expect(api.saveTemplate).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('保存失败不清除标记，字段保存不提交规则草稿', async () => {
    const { wrapper, state } = await loadedPanel()
    state.toggleNameField('brand', true)
    api.savePattern.mockRejectedValueOnce(new Error('保存失败'))
    await state.savePattern('name_pattern')
    expect(state.isDirty('name_pattern')).toBe(true)
    expect(state.error).toBe('保存失败')
    state.schema.fields[0].label = '新的属性名称'
    await state.saveTemplate()
    expect(state.isDirty('fields')).toBe(false)
    expect(state.isDirty('name_pattern')).toBe(true)
    const submitted = api.saveTemplateFields.mock.calls[0][0].attributeSchema
    expect(submitted).not.toHaveProperty('name_pattern')
    expect(submitted).not.toHaveProperty('model_pattern')
    expect(submitted).not.toHaveProperty('spec_pattern')
    wrapper.unmount()
  })
  it('切换品类与外层页签也拦截草稿，取消时不加载其它品类', async () => {
    const { wrapper, state } = await loadedPanel()
    state.toggleNameField('brand', true)
    vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')
    const calls = api.template.mock.calls.length
    await state.changeTemplateCategory(3)
    expect(state.templateCategoryId).toBe(2)
    expect(api.template).toHaveBeenCalledTimes(calls)
    expect(await state.beforeOuterLeave('categories', 'template')).toBe(false)
    wrapper.unmount()
  })
  it('回载保留规格单位和精度，摘要按字段顺序展示并可直接跳转', async () => {
    const fixture = (await api.template()).data
    fixture.schema.spec_pattern = { fields: [{ key: 'thickness_mm', sep: '', unit: 'μm', scale: 1 }, { key: 'width', sep: '*' }], with_unit: true, unit_position: 'each' }
    fixture.schema.name_pattern = { fields: ['brand', 'model'], separator: '', empty: 'skip' }
    fixture.schema.model_pattern = { fields: ['categoryprefix', 'thickness_mm'], separator: '', empty: 'skip' }
    api.template.mockResolvedValue({ data: fixture })
    const { wrapper, state } = await loadedPanel()
    expect(state.specPattern.fields[0]).toMatchObject({ unit: 'μm', scale: 1 })
    expect(state.namePattern.separator).toBe('')
    expect(state.ruleSummary('name_pattern')).toBe('品牌 + 型号')
    expect(state.ruleSummary('model_pattern')).toBe('品类前缀 + 厚度(mm)')
    for (const key of ['name_pattern', 'model_pattern', 'spec_pattern']) {
      await wrapper.get(`[data-test="summary-${key}"]`).trigger('click')
      await flushPromises()
      expect(state.activeBlock).toBe(key)
    }
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it.each(['template', 'naming'] as const)('focusSection=%s 保持入口可见范围，singleSection 只隐藏外层页签', async (focusSection) => {
    const { wrapper, state } = await loadedPanel({ focusSection, singleSection: true, initialTab: 'template' })
    expect(state.tab).toBe('template')
    expect(wrapper.find('.single-section').exists()).toBe(true)
    expect(wrapper.find('.block-tabs').exists()).toBe(true)
    expect(wrapper.find('[data-test="model-pattern"]').exists()).toBe(focusSection === 'naming')
    expect(wrapper.find('[data-test="rule-summary"]').exists()).toBe(focusSection === 'naming')
    expect(wrapper.findAll('button').some(b => b.text() === '增加属性')).toBe(focusSection === 'template')
    expect(state.activeBlock).toBe(focusSection === 'naming' ? 'name_pattern' : 'fields')
    wrapper.unmount()
  })
  it('弹窗继续透传 initialTab、focusSection 与保存成功事件', async () => {
    const wrapper = mount(MaterialMasterSettingsDialog, { props: { modelValue: true, initialTab: 'template', focusSection: 'naming' }, global: { stubs: { ElDialog: Slot, MaterialMasterSettingsPanel: true } } })
    const panel = wrapper.getComponent(MaterialMasterSettingsPanel)
    expect(panel.props()).toMatchObject({ initialTab: 'template', focusSection: 'naming', singleSection: false })
    panel.vm.$emit('success')
    expect(wrapper.emitted('success')).toHaveLength(1)
    wrapper.unmount()
  })

  it('真实 el-tabs 点击经 before-leave 拦截，确认后切换保留草稿，保存后无需再确认', async () => {
    const wrapper = mount(MaterialMasterSettingsPanel, { props: { initialTab: 'template' }, global: { stubs: {
      ...editableStubs, ElTabs: false, ElTabPane: false,
    } } })
    await flushPromises()
    const state = wrapper.vm as any
    state.templateCategoryId = 2
    await state.loadTemplate(2)
    await flushPromises()
    expect(wrapper.findAll('.block-tabs [role="tab"]').map(t => t.text())).toEqual(['属性字段', '命名规则', '型号规则', '规格模板'])
    await wrapper.get('#tab-name_pattern').trigger('click')
    await flushPromises()
    state.toggleNameField('brand', true)
    await flushPromises()
    expect(wrapper.get('#tab-name_pattern').text()).toContain('未保存')
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    await wrapper.get('#tab-model_pattern').trigger('click')
    await flushPromises()
    expect(state.activeBlock).toBe('name_pattern')
    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    vi.mocked(ElMessageBox.confirm).mockResolvedValueOnce('confirm')
    await wrapper.get('#tab-model_pattern').trigger('click')
    await flushPromises()
    expect(state.activeBlock).toBe('model_pattern')
    expect(state.namePattern.fields).toEqual(['brand'])
    await wrapper.get('#tab-name_pattern').trigger('click')
    await flushPromises()
    const namePane = wrapper.get('#pane-name_pattern')
    await namePane.findAll('button').find(b => b.text() === '保存本块')!.trigger('click')
    await flushPromises()
    expect(wrapper.get('#tab-name_pattern').text()).not.toContain('未保存')
    vi.mocked(ElMessageBox.confirm).mockClear()
    await wrapper.get('#tab-spec_pattern').trigger('click')
    await flushPromises()
    expect(state.activeBlock).toBe('spec_pattern')
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    await wrapper.get('#tab-name_pattern').trigger('click')
    await flushPromises()
    state.toggleNameField('model', true)
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm')
    await wrapper.get('[data-test="summary-model_pattern"]').trigger('click')
    await flushPromises()
    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(state.activeBlock).toBe('model_pattern')
    expect(wrapper.get('#tab-model_pattern').attributes('aria-selected')).toBe('true')
    wrapper.unmount()
  })

  async function modelPanel() {
    const wrapper = mount(MaterialMasterSettingsPanel, { props: { focusSection: 'naming' }, global: { stubs: {
      ...stubs, ElButton: Button, ElTableColumn: true, ElInput: true, ElInputNumber: true, ElSwitch: true, ElCheckbox: true, ElAlert: true,
    } } })
    await flushPromises()
    const state = wrapper.vm as any
    state.templateCategoryId = 2
    await state.loadTemplate(2)
    await flushPromises()
    return { wrapper, state }
  }
  const sample = (id = 10) => ({ materialId: id, materialCode: `M${id}`, oldName: '档案名称不是原型号', newName: '生成型号实例' })

  it('型号实例新增供应商品名及原型号，取档案model而非oldName，并缓存参考资料', async () => {
    const { wrapper, state } = await modelPanel()
    api.previewNaming.mockResolvedValue({ data: { samples: [sample()] } })
    api.detail.mockResolvedValue({ data: { material: { supplierMaterialName: '供应商原品名', model: '旧型号0603' } } })
    await state.previewModel()
    expect(wrapper.find('[label="供应商品名"]').exists()).toBe(true)
    expect(wrapper.find('[label="原型号实例"]').exists()).toBe(true)
    expect(state.modelSamples[0]).toMatchObject({ supplierMaterialName: '供应商原品名', originalModel: '旧型号0603', newName: '生成型号实例' })
    expect(api.detail).toHaveBeenCalledWith(10)
    await state.previewModel()
    expect(api.detail).toHaveBeenCalledTimes(1)
    api.detail.mockResolvedValue({ data: { material: { supplierMaterialName: '更新品名', model: '更新原型号' } } })
    await state.previewModel(true)
    expect(api.detail).toHaveBeenCalledTimes(2)
    expect(state.modelSamples[0].originalModel).toBe('更新原型号')
    wrapper.unmount()
  })

  it('参考资料失败保留生成示例并明确提示，显式刷新可重试', async () => {
    const { wrapper, state } = await modelPanel()
    api.previewNaming.mockResolvedValue({ data: { samples: [sample()] } })
    api.detail.mockRejectedValueOnce(new Error('无查看权限'))
    await state.previewModel()
    expect(state.modelSamples[0]).toMatchObject({ newName: '生成型号实例', originalModel: '加载失败', referenceError: true })
    expect(wrapper.text()).toContain('部分参考物料资料加载失败')
    await state.previewModel(true)
    expect(state.modelSamples[0]).toMatchObject({ originalModel: '原型号', supplierMaterialName: '供应商品名' })
    expect(wrapper.text()).not.toContain('部分参考物料资料加载失败')
    wrapper.unmount()
  })

  it('切换品类后旧资料响应不能覆盖新实例，空值明确显示横线', async () => {
    const { wrapper, state } = await modelPanel()
    let finishOld!: (value: any) => void
    api.previewNaming.mockResolvedValue({ data: { samples: [sample()] } })
    api.detail.mockImplementation((id) => id === 10
      ? new Promise(resolve => { finishOld = resolve })
      : Promise.resolve({ data: { material: {} } }))
    const old = state.previewModel()
    await flushPromises()
    state.templateCategoryId = 3
    await flushPromises()
    api.previewNaming.mockResolvedValue({ data: { samples: [sample(20)] } })
    await state.previewModel()
    finishOld({ data: { material: { model: '迟到旧型号' } } })
    await old
    expect(state.modelSamples).toHaveLength(1)
    expect(state.modelSamples[0]).toMatchObject({ materialId: 20, originalModel: '-', supplierMaterialName: '-' })
    wrapper.unmount()
  })

})

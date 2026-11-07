import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import MaterialSizeEditor from '../MaterialSizeEditor.vue'
import MaterialMasterFormDialog from '../MaterialMasterFormDialog.vue'
import { emptyMaterialSize, renderMaterialDimensions, validateMaterialDimensions } from '../materialSize'
import type { MaterialSizeValue } from '../materialSize'

vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/col/style/css', () => ({}))
vi.mock('element-plus/es/components/collapse/style/css', () => ({}))
vi.mock('element-plus/es/components/collapse-item/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/row/style/css', () => ({}))
vi.mock('element-plus/es/components/skeleton/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))

const Input = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'],
  template: `<input :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />` })
const Select = defineComponent({ props: ['modelValue'], emits: ['change', 'update:modelValue'], template: '<div><slot /></div>' })
function create(value: MaterialSizeValue, dimensionFields?: import('../materialSize').MaterialDimensionFields) {
  return mount(MaterialSizeEditor, { props: { modelValue: value, dimensionFields },
    global: { stubs: { ElInput: Input, ElSelect: Select, ElOption: true } } })
}
describe('专用数值尺寸编辑组件', () => {
  it('三个尺寸按模板单位录入、回显，移除配置恢复mm', async () => {
    const value = { ...emptyMaterialSize(), widthMm: 315, lengthMm: 1000000 }
    const fields = [{ key: 'thickness_mm', sep: '*', unit: 'μm' as const }, { key: 'width', sep: '*' }, { key: 'length', sep: '*', unit: 'm' as const }]
    const wrapper = create(value, fields)
    await wrapper.findAll('input')[0].setValue('315')
    await wrapper.findAll('input')[1].setValue('1000')
    await wrapper.findAll('input')[2].setValue('254')
    expect(wrapper.find('[aria-label="尺寸单位"]').exists()).toBe(false)
    expect(wrapper.findAllComponents(Select)).toHaveLength(4)
    const stored = { ...value, thicknessMm: 0.254 }
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([stored])
    expect(wrapper.text()).toContain('254μm*315mm*1000m')
    wrapper.unmount()
    const reopened = create(stored, fields)
    expect(reopened.findAll('input').map(i => i.element.value)).toEqual(['315', '1000', '254'])
    expect(reopened.emitted('update:modelValue')).toBeUndefined()
    await reopened.setProps({ dimensionFields: [] })
    expect(reopened.findAll('input').map(i => i.element.value)).toEqual(['315', '1000000', '0.254'])
    expect(reopened.text()).toContain('315*1000000*0.254mm')
    reopened.unmount()
  })
  it('显示舍入不改变源厚度，修改宽度仍提交原始mm', async () => {
    const wrapper = create({ ...emptyMaterialSize(), widthMm: 315, lengthMm: 1000, thicknessMm: 0.2541 }, [{ key: 'thickness_mm', sep: '*', unit: 'μm', scale: 0 }])
    expect(wrapper.findAll('input')[2].element.value).toBe('254')
    await wrapper.findAll('input')[0].setValue('320')
    expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({ widthMm: 320, thicknessMm: 0.2541 })
    wrapper.unmount()
  })
  it('回显不写回，编辑厚度只输出三个mm字段，不改动输入对象', async () => {
    const value = { ...emptyMaterialSize(), widthMm: 30, lengthMm: 90 }
    const wrapper = create(value)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.findAll('input').map(i => i.element.value)).toEqual(['30', '90', ''])
    await wrapper.findAll('input')[2].setValue('0.175')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{ ...value, thicknessMm: 0.175 }])
    expect(value.thicknessMm).toBeNull()
    expect(wrapper.text()).toContain('30*90*0.175mm')
    wrapper.unmount()
  })
  it('非法值或精度超限阻止保存，修正和清空恢复有效状态', async () => {
    const wrapper = create({ ...emptyMaterialSize(), widthMm: 30, lengthMm: 90 })
    for (const invalid of ['0', '-1', '按图纸', '1.001', '100000000']) {
      await wrapper.findAll('input')[0].setValue(invalid)
      expect(wrapper.emitted('valid-change')?.at(-1)).toEqual([false])
      expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    }
    await wrapper.findAll('input')[0].setValue('30')
    expect(wrapper.emitted('valid-change')?.at(-1)).toEqual([true])
    await wrapper.findAll('input')[0].setValue('')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{ ...emptyMaterialSize(), lengthMm: 90 }])
    wrapper.unmount()
  })
  it('直径映射宽字段，厚度位置不变，单位独立写入并可回显', async () => {
    const wrapper = create({ ...emptyMaterialSize(), widthMm: 8 })
    wrapper.findAllComponents(Select)[0].vm.$emit('update:modelValue', true)
    await flushPromises()
    expect(wrapper.findAll('input')).toHaveLength(2)
    await wrapper.findAll('input')[1].setValue('0.05')
    wrapper.findAllComponents(Select)[1].vm.$emit('update:modelValue', 'cm')
    await flushPromises()
    const value = { widthMm: 80, lengthMm: null, thicknessMm: 0.05 }
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([value])
    await wrapper.setProps({ modelValue: value })
    expect(wrapper.text()).toContain('Φ80*0.05mm')
    wrapper.unmount()
    const reopened = create(value)
    expect(reopened.findAll('input').map(i => i.element.value)).toEqual(['80', '0.05'])
    reopened.unmount()
  })
  it('已有长度时切换直径不会静默丢失数值', async () => {
    const wrapper = create({ ...emptyMaterialSize(), widthMm: 30, lengthMm: 90 })
    wrapper.findAllComponents(Select)[0].vm.$emit('update:modelValue', true)
    await flushPromises()
    expect(wrapper.text()).toContain('请先清空长度')
    expect(wrapper.findAll('input')).toHaveLength(3)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })
  it('数值预览保留缺项，单位和厚度精度与后端一致', () => {
    expect(renderMaterialDimensions({ widthMm: 914, thicknessMm: 0.175, sizeUnit: 'm' })).toBe('914**0.175mm')
    // 只填厚度合法：前导缺位不补占位（dev-20261030-016）
    expect(renderMaterialDimensions({ thicknessMm: 0.175, sizeUnit: 'mm' })).toBe('0.175mm')
    expect(renderMaterialDimensions({ thicknessMm: 0.05, sizeUnit: 'mm' }, true)).toBe('Φ0.05mm')
    expect(renderMaterialDimensions(emptyMaterialSize())).toBe('')
    expect(() => validateMaterialDimensions({ ...emptyMaterialSize(), thicknessMm: 0.00001 })).toThrow()
  })
})

const api = vi.hoisted(() => ({ categories: vi.fn(), dictionaries: vi.fn(), configuration: vi.fn(), detail: vi.fn(), save: vi.fn(), tags: vi.fn(), renderSpec: vi.fn(), renderModel: vi.fn(), renderNaming: vi.fn(), requiredFields: vi.fn() }))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: api }))
vi.mock('@/api/inventory/material', () => ({ materialApi: { getTags: api.tags } }))
vi.mock('element-plus', async importOriginal => ({
  ...await importOriginal<typeof import('element-plus')>(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))
const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const Button = defineComponent({ props: ['disabled'], emits: ['click'], template: `<button :disabled="disabled" @click="$emit('click')"><slot /></button>` })
const configuration = {
  category: { categoryId: 2, categoryCode: 'RAW_BASE_PC', categoryName: 'PC', parentId: 1, categoryLevel: 3, materialType: 'R', pathName: '原材料/PC', selectable: true },
  path: [], template: { templateId: 2, templateCode: 'PC', templateName: 'PC', status: 1 },
  schema: { version: 1, meta_options: ['PC'], default_meta: 'PC', default_unit: 'M', fields: [] },
}
function form(materialId?: number) {
  return mount(MaterialMasterFormDialog, { props: { modelValue: true, materialId }, global: { stubs: {
    ElDialog: Slot, ElForm: Slot, ElFormItem: Slot, ElRow: Slot, ElCol: Slot, ElCollapse: Slot, ElCollapseItem: Slot,
    ElSelect: Select, ElInput: Input, ElButton: Button, ElDivider: Slot, ElCheckbox: Slot, ElInputNumber: true,
    SupplierSelector: true, ElOption: true, ElSwitch: true, ElSkeleton: true, ElAlert: true, MaterialAttributeFields: true,
  } } })
}
beforeEach(() => {
  api.requiredFields.mockResolvedValue({ data: { material_grade: false, model: false, color: false, specification: false, brand: false } })
  api.renderModel.mockReset().mockResolvedValue({ data: 'PET125' })
  api.renderNaming.mockResolvedValue({ data: 'PET125名称' })
  api.categories.mockResolvedValue({ data: [configuration.category] })
  api.dictionaries.mockResolvedValue({ data: { material_meta: [{ code: 'PC', label: 'PC' }], unit: [{ code: 'M', label: '米' }] } })
  api.configuration.mockResolvedValue({ data: configuration })
  api.tags.mockResolvedValue({ data: [] })
  api.save.mockClear().mockResolvedValue({ data: { materialId: 100 } })
})
describe('物料表单数值尺寸保存', () => {
  it('新增请求直提三个mm字段，既不提交size也不提交厚度属性', async () => {
    const wrapper = form()
    await flushPromises()
    wrapper.getComponent('[data-test="material-category"]').vm.$emit('change', 2)
    await flushPromises()
    const editor = wrapper.findComponent(MaterialSizeEditor)
    await editor.findAll('input')[0].setValue('300')
    await editor.findAll('input')[1].setValue('305')
    await editor.findAll('input')[2].setValue('0.175')
    editor.findAllComponents(Select)[1].vm.$emit('update:modelValue', 'cm')
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text() === '保存物料')!.trigger('click')
    await flushPromises()
    expect(api.save).toHaveBeenCalledTimes(1)
    const payload = api.save.mock.calls[0][0]
    expect(payload).toMatchObject({ widthMm: 3000, lengthMm: 305, thicknessMm: 0.175 })
    expect(payload).not.toHaveProperty('size')
    expect(payload).not.toHaveProperty('sizeUnit')
    expect(payload.attributeJson).not.toHaveProperty('thickness_mm')
    wrapper.unmount()
  })
  it('编辑以服务端数值回显，清空厚度提交null并保留宽长', async () => {
    api.detail.mockResolvedValue({ data: {
      material: { materialId: 100, materialName: '数值PC', categoryId: 2, materialMeta: 'PC', unit: 'M',
        size: '错误文本不参与回显', widthMm: 30, lengthMm: 90, thicknessMm: 0.175, sizeUnit: 'm' },
      attributes: { thickness_mm: 999 }, tags: [], configuration,
    } })
    const wrapper = form(100)
    await flushPromises()
    const editor = wrapper.findComponent(MaterialSizeEditor)
    expect(editor.findAll('input').map(i => i.element.value)).toEqual(['30', '90', '0.175'])
    await editor.findAll('input')[2].setValue('')
    await wrapper.findAll('button').find(b => b.text() === '保存物料')!.trigger('click')
    await flushPromises()
    expect(api.save.mock.calls[0][0]).toMatchObject({ materialId: 100, widthMm: 30, lengthMm: 90, thicknessMm: null })
    expect(api.save.mock.calls[0][0]).not.toHaveProperty('size')
    expect(api.save.mock.calls[0][0]).not.toHaveProperty('sizeUnit')
    wrapper.unmount()
  })
  it('编辑按三个字段单位回显，直接保存保留原始mm且不提交旧单位', async () => {
    const fields = [{ key: 'thickness_mm', sep: '*', unit: 'μm' as const, scale: 1 }, { key: 'width', sep: '*', unit: 'mm' as const }, { key: 'length', sep: '*', unit: 'm' as const, scale: 2 }]
    api.detail.mockResolvedValue({ data: {
      material: { materialId: 100, materialName: '保护膜', categoryId: 2, unit: 'M', widthMm: 315, lengthMm: 1000000, thicknessMm: 0.254, sizeUnit: 'cm' },
      attributes: {}, tags: [], configuration: { ...configuration, schema: { ...configuration.schema, spec_pattern: { fields } } },
    } })
    const wrapper = form(100)
    await flushPromises()
    const editor = wrapper.findComponent(MaterialSizeEditor)
    expect(editor.findAll('input').map(i => i.element.value)).toEqual(['315', '1000', '254'])
    expect(editor.findAllComponents(Select).slice(1).map(c => c.props('modelValue'))).toEqual(['mm', 'm', 'μm'])
    expect(editor.emitted('update:modelValue')).toBeUndefined()
    await wrapper.findAll('button').find(b => b.text() === '保存物料')!.trigger('click')
    await flushPromises()
    expect(api.save.mock.calls[0][0]).toMatchObject({ widthMm: 315, lengthMm: 1000000, thicknessMm: 0.254 })
    expect(api.save.mock.calls[0][0]).not.toHaveProperty('sizeUnit')
    wrapper.unmount()
  })
  it('无命名规则的品类仍请求字段单位规格预览，并传给尺寸编辑器', async () => {
    const fields = [{ key: 'thickness_mm', sep: '*', unit: 'μm' as const }, { key: 'width', sep: '*' }, { key: 'length', sep: '*', unit: 'm' as const }]
    api.configuration.mockResolvedValue({ data: { ...configuration, schema: { ...configuration.schema, spec_pattern: { fields, with_unit: true, show_label: false, unit_position: 'each' } } } })
    api.renderSpec.mockResolvedValue({ data: '254μm*315mm*1000m' })
    const wrapper = form()
    await flushPromises()
    wrapper.getComponent('[data-test="material-category"]').vm.$emit('change', 2)
    await flushPromises()
    const editor = wrapper.findComponent(MaterialSizeEditor)
    expect(editor.props('dimensionFields')).toEqual(fields)
    await editor.findAll('input')[0].setValue('315')
    await editor.findAll('input')[1].setValue('1000')
    await editor.findAll('input')[2].setValue('254')
    await new Promise(resolve => setTimeout(resolve, 350))
    await flushPromises()
    expect(api.renderSpec).toHaveBeenCalledWith(2, expect.objectContaining({ thicknessMm: 0.254 }))
    expect(wrapper.find('[data-test="master-preview"]').text()).toContain('254μm*315mm*1000m')
    wrapper.unmount()
  })

})

const modelConfiguration = { ...configuration, schema: { ...configuration.schema,
  meta_options: ['PET'], default_meta: 'PET',
  model_pattern: { fields: ['meta', 'thickness_mm'], separator: '', empty: 'skip', units: { thickness_mm: 'μm' } },
} }
async function newModelForm() {
  api.configuration.mockResolvedValue({ data: modelConfiguration })
  const wrapper = form()
  await flushPromises()
  wrapper.getComponent('[data-test="material-category"]').vm.$emit('change', 2)
  await flushPromises()
  const state = wrapper.vm as any
  state.form.thicknessMm = 0.125
  await flushPromises()
  return wrapper
}
describe('新建物料型号生成与手改保护', () => {
  it('新建按后端规则带出，手改后保存保留原值，后续改尺寸也不覆盖', async () => {
    const wrapper = await newModelForm()
    await new Promise(resolve => setTimeout(resolve, 350))
    await flushPromises()
    expect(api.renderModel).toHaveBeenCalledWith(2, expect.objectContaining({ thicknessMm: 0.125 }))
    expect(wrapper.get<HTMLInputElement>('[data-test="material-model"]').element.value).toBe('PET125')
    await wrapper.get('[data-test="material-model"]').setValue('PET125A')
    ;(wrapper.vm as any).form.thicknessMm = 0.254
    await new Promise(resolve => setTimeout(resolve, 350))
    await wrapper.findAll('button').find(b => b.text() === '保存物料')!.trigger('click')
    await flushPromises()
    expect(api.save).toHaveBeenCalledWith(expect.objectContaining({ model: 'PET125A' }))
    wrapper.unmount()
  })
  it('未手改时，保存前刷新型号以覆盖尚未完成的防抖预览', async () => {
    const wrapper = await newModelForm()
    await wrapper.findAll('button').find(b => b.text() === '保存物料')!.trigger('click')
    await flushPromises()
    expect(api.renderModel).toHaveBeenCalled()
    expect(api.save).toHaveBeenCalledWith(expect.objectContaining({ model: 'PET125' }))
    wrapper.unmount()
  })
  it('正在请求的旧响应不能覆盖用户手改', async () => {
    let resolve!: (response: { data: string }) => void
    api.renderModel.mockReturnValue(new Promise(done => { resolve = done }))
    const wrapper = await newModelForm()
    await new Promise(done => setTimeout(done, 350))
    expect(api.renderModel).toHaveBeenCalled()
    await wrapper.get('[data-test="material-model"]').setValue('PET125A')
    resolve({ data: 'PET125' })
    await flushPromises()
    expect(wrapper.get<HTMLInputElement>('[data-test="material-model"]').element.value).toBe('PET125A')
    wrapper.unmount()
  })
  it('编辑已有物料即使有规则也不预览或改写型号', async () => {
    api.detail.mockResolvedValue({ data: {
      material: { materialId: 100, materialName: '旧名称', categoryId: 2, model: '旧型号', unit: 'M' },
      attributes: {}, tags: [], configuration: modelConfiguration,
    } })
    const wrapper = form(100)
    await flushPromises()
    ;(wrapper.vm as any).form.thicknessMm = 0.254
    await new Promise(resolve => setTimeout(resolve, 350))
    await wrapper.findAll('button').find(b => b.text() === '保存物料')!.trigger('click')
    await flushPromises()
    expect(api.renderModel).not.toHaveBeenCalled()
    expect(api.save).toHaveBeenCalledWith(expect.objectContaining({ model: '旧型号' }))
    wrapper.unmount()
  })
  it('未配规则时维持人工型号，不调用生成接口', async () => {
    const wrapper = form()
    await flushPromises()
    wrapper.getComponent('[data-test="material-category"]').vm.$emit('change', 2)
    await flushPromises()
    await wrapper.get('[data-test="material-model"]').setValue('人工型号')
    await wrapper.findAll('button').find(b => b.text() === '保存物料')!.trigger('click')
    await flushPromises()
    expect(api.renderModel).not.toHaveBeenCalled()
    expect(api.save).toHaveBeenCalledWith(expect.objectContaining({ model: '人工型号' }))
    wrapper.unmount()
  })
})

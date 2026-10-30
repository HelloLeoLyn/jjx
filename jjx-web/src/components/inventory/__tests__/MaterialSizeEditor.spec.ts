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
function create(value: MaterialSizeValue) {
  return mount(MaterialSizeEditor, { props: { modelValue: value },
    global: { stubs: { ElInput: Input, ElSelect: Select, ElOption: true } } })
}
describe('专用数值尺寸编辑组件', () => {
  it('回显不写回，编辑厚度只输出四字段对象，不改动输入对象', async () => {
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
    const value = { widthMm: 8, lengthMm: null, thicknessMm: 0.05, sizeUnit: 'cm' }
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([value])
    await wrapper.setProps({ modelValue: value })
    expect(wrapper.text()).toContain('Φ8*0.05cm')
    wrapper.unmount()
    const reopened = create(value)
    expect(reopened.findAll('input').map(i => i.element.value)).toEqual(['8', '0.05'])
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
    expect(renderMaterialDimensions({ widthMm: 914, thicknessMm: 0.175, sizeUnit: 'm' })).toBe('914**0.175m')
    // 只填厚度合法：前导缺位不补占位（dev-20261030-016）
    expect(renderMaterialDimensions({ thicknessMm: 0.175, sizeUnit: 'mm' })).toBe('0.175mm')
    expect(renderMaterialDimensions({ thicknessMm: 0.05, sizeUnit: 'mm' }, true)).toBe('Φ0.05mm')
    expect(renderMaterialDimensions(emptyMaterialSize())).toBe('')
    expect(() => validateMaterialDimensions({ ...emptyMaterialSize(), thicknessMm: 0.00001 })).toThrow()
    expect(() => validateMaterialDimensions({ ...emptyMaterialSize(), sizeUnit: 'inch' })).toThrow()
  })
})

const api = vi.hoisted(() => ({ categories: vi.fn(), dictionaries: vi.fn(), configuration: vi.fn(), detail: vi.fn(), save: vi.fn(), tags: vi.fn() }))
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
    ElOption: true, ElSwitch: true, ElSkeleton: true, ElAlert: true, MaterialAttributeFields: true,
  } } })
}
beforeEach(() => {
  api.categories.mockResolvedValue({ data: [configuration.category] })
  api.dictionaries.mockResolvedValue({ data: { material_meta: [{ code: 'PC', label: 'PC' }], unit: [{ code: 'M', label: '米' }] } })
  api.configuration.mockResolvedValue({ data: configuration })
  api.tags.mockResolvedValue({ data: [] })
  api.save.mockClear().mockResolvedValue({ data: { materialId: 100 } })
})
describe('物料表单数值尺寸保存', () => {
  it('新增请求直提四字段，既不提交size也不提交厚度属性', async () => {
    const wrapper = form()
    await flushPromises()
    wrapper.findAllComponents(Select)[0].vm.$emit('change', 2)
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
    expect(payload).toMatchObject({ widthMm: 300, lengthMm: 305, thicknessMm: 0.175, sizeUnit: 'cm' })
    expect(payload).not.toHaveProperty('size')
    expect(payload.attributeJson).not.toHaveProperty('thickness_mm')
    wrapper.unmount()
  })
  it('编辑以服务端数值回显，清空厚度提交null并保留宽长和单位', async () => {
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
    expect(api.save.mock.calls[0][0]).toMatchObject({ materialId: 100, widthMm: 30, lengthMm: 90, thicknessMm: null, sizeUnit: 'm' })
    expect(api.save.mock.calls[0][0]).not.toHaveProperty('size')
    wrapper.unmount()
  })
})

vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/col/style/css', () => ({}))
vi.mock('element-plus/es/components/collapse/style/css', () => ({}))
vi.mock('element-plus/es/components/collapse-item/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/row/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/skeleton/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import MaterialMasterFormDialog from '../MaterialMasterFormDialog.vue'
import type { MaterialMasterConfiguration } from '@/types/inventory/materialMaster'

const mocks = vi.hoisted(() => ({
  categories: vi.fn(),
  dictionaries: vi.fn(),
  configuration: vi.fn(),
  detail: vi.fn(),
  save: vi.fn(),
  tags: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
}))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: mocks }))
vi.mock('@/api/inventory/material', () => ({ materialApi: { getTags: mocks.tags } }))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal<typeof import('element-plus')>(),
  ElMessage: { success: mocks.success, error: mocks.error, warning: mocks.warning },
}))
const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const Select = defineComponent({
  props: ['modelValue'],
  emits: ['change', 'update:modelValue'],
  template: '<div><slot /></div>',
})
const Input = defineComponent({
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
})
const Button = defineComponent({
  props: ['disabled'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
})
const config = (id: number, name: string): MaterialMasterConfiguration => ({
  category: {
    categoryId: id,
    categoryCode: `CAT_${id}`,
    categoryName: name,
    parentId: 1,
    categoryLevel: 3,
    materialType: 'R',
    pathName: `原材料/${name}`,
    selectable: true,
  },
  path: [
    {
      categoryId: 1,
      categoryCode: 'RAW',
      categoryName: '原材料',
      parentId: 0,
      categoryLevel: 1,
      materialType: 'R',
      pathName: '原材料',
      selectable: false,
    },
  ],
  template: { templateId: id, templateCode: `TPL_${id}`, templateName: name, status: 1 },
  schema: {
    version: 1,
    meta_options: ['PC'],
    default_meta: 'PC',
    default_form: 'FILM',
    default_unit: 'M',
    fields: [],
  },
})
const create = () =>
  mount(MaterialMasterFormDialog, {
    props: { modelValue: true },
    global: {
      stubs: {
        ElDialog: Slot,
        ElForm: Slot,
        ElFormItem: Slot,
        ElRow: Slot,
        ElCol: Slot,
        ElCollapse: Slot,
        ElCollapseItem: Slot,
        ElSelect: Select,
        ElInput: Input,
        ElButton: Button,
        ElDivider: Slot,
        ElCheckbox: Slot,
        ElInputNumber: true,
        ElOption: true,
        ElSwitch: true,
        ElSkeleton: true,
        ElAlert: true,
        MaterialAttributeFields: true,
      },
    },
  })

beforeEach(() => {
  vi.resetAllMocks()
  mocks.categories.mockResolvedValue({
    data: [config(2, 'PC').category, config(3, 'PET').category],
  })
  mocks.dictionaries.mockResolvedValue({
    data: { material_meta: [{ code: 'PC', label: 'PC' }], unit: [{ code: 'M', label: '米' }] },
  })
  mocks.tags.mockResolvedValue({ data: [] })
  mocks.configuration.mockImplementation((id) =>
    Promise.resolve({ data: config(id, id === 2 ? 'PC' : 'PET') })
  )
  mocks.save.mockResolvedValue({
    data: { materialId: 100, materialCode: 'RM100', materialName: '测试物料' },
  })
})

describe('物料一次保存与异步品类切换', () => {
  it('新建只调用一次主数据保存，成功后返回完整物料', async () => {
    const wrapper = create()
    await flushPromises()
    wrapper.findAllComponents(Select)[0].vm.$emit('change', 2)
    await flushPromises()
    await wrapper.findAllComponents(Input)[0].find('input').setValue('测试原名称')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '保存物料')!
      .trigger('click')
    await flushPromises()
    expect(mocks.save).toHaveBeenCalledTimes(1)
    expect(mocks.save.mock.calls[0][0]).toMatchObject({
      originalName: '测试原名称',
      categoryId: 2,
      materialMeta: 'PC',
      unit: 'M',
      attributeJson: {},
    })
    expect(wrapper.emitted('success')?.[0][0]).toMatchObject({ materialId: 100 })
    wrapper.unmount()
  })

  it('保存失败保留弹窗和草稿，不发成功事件', async () => {
    mocks.save.mockRejectedValue(Error('事务回滚'))
    const wrapper = create()
    await flushPromises()
    wrapper.findAllComponents(Select)[0].vm.$emit('change', 2)
    await flushPromises()
    await wrapper.findAllComponents(Input)[0].find('input').setValue('失败后保留')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '保存物料')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.emitted('success')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(mocks.error).toHaveBeenCalledWith('事务回滚')
    expect(wrapper.findAllComponents(Input)[0].find('input').element.value).toBe('失败后保留')
    wrapper.unmount()
  })

  it('旧品类响应晚到也不会覆盖新选择', async () => {
    let resolveOld!: (value: unknown) => void
    mocks.configuration.mockImplementation((id) =>
      id === 2
        ? new Promise((resolve) => {
            resolveOld = resolve
          })
        : Promise.resolve({ data: config(3, 'PET') })
    )
    const wrapper = create()
    await flushPromises()
    wrapper.findAllComponents(Select)[0].vm.$emit('change', 2)
    wrapper.findAllComponents(Select)[0].vm.$emit('change', 3)
    await flushPromises()
    resolveOld({ data: config(2, 'PC') })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'MaterialAttributeFields' }).props('schema')).toEqual(
      config(3, 'PET').schema
    )
    expect(wrapper.findAllComponents(Select)[0].props('modelValue')).toBe(3)
    wrapper.unmount()
  })
})

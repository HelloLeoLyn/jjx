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
  requiredFields: vi.fn(),
  renderSpec: vi.fn(),
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
const create = (props: { materialId?: number; fromBom?: boolean } = {}) =>
  mount(MaterialMasterFormDialog, {
    props: { modelValue: true, ...props },
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
  mocks.requiredFields.mockResolvedValue({ data: { material_grade: false, model: false, color: false, specification: false, brand: false } })
  mocks.renderSpec.mockResolvedValue({ data: '300mm' })
  mocks.configuration.mockImplementation((id) =>
    Promise.resolve({ data: config(id, id === 2 ? 'PC' : 'PET') })
  )
  mocks.save.mockResolvedValue({
    data: { materialId: 100, materialCode: 'RM100', materialName: '测试物料' },
  })
})

describe('品类/子类两级选择（dev-20261104-008）', () => {
  const cat = (id: number, name: string, level: number, parentId: number | null, selectable: boolean) => ({
    categoryId: id,
    categoryCode: `C${id}`,
    categoryName: name,
    parentId,
    categoryLevel: level,
    materialType: 'R',
    pathName: `原材料/${name}`,
    selectable,
  })
  it('品类下拉含"有子类的父级"，选中父级才出现子类，且不调用模板接口', async () => {
    mocks.categories.mockResolvedValue({
      data: [
        cat(13, '原材料', 1, 0, false),
        cat(17, '辅材', 2, 13, false),
        cat(18, 'PC膜/片', 3, 17, true),
        cat(29, '电子元件/辅料', 3, 17, false),
        cat(32, 'LED', 4, 29, true),
        cat(33, '端子', 4, 29, true),
      ],
    })
    const wrapper = create()
    await flushPromises()
    const state = (wrapper.vm as any).$.setupState
    // 父级（29，有子类）与普通叶子（18）都在品类下拉里
    expect(state.parentCategoryOptions.map((c: any) => c.categoryId)).toEqual([17, 18, 29])
    expect(state.subCategoryOptions).toEqual([])

    // 选父级 29：不加载模板，等选子类
    await state.selectParentCategory(29)
    expect(state.subCategoryOptions.map((c: any) => c.categoryName)).toEqual(['LED', '端子'])
    expect(mocks.configuration).not.toHaveBeenCalled()

    // 选子类 32：按子类加载模板
    await state.selectCategory(32)
    expect(mocks.configuration).toHaveBeenCalledWith(32)
  })
})

describe('物料一次保存与异步品类切换', () => {
  it('新建只调用一次主数据保存，成功后返回完整物料', async () => {
    const wrapper = create()
    await flushPromises()
    wrapper.findAllComponents(Select)[0].vm.$emit('change', 2)
    await flushPromises()
    // 名称输入独立于前面的供应商品名；以语义定位，避免字段插入导致错填。
    const nameInput = wrapper.findAllComponents(Input).find(input => input.attributes('maxlength') === '200' && !input.attributes('placeholder'))!
    await nameInput.find('input').setValue('测试原名称')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '保存物料')!
      .trigger('click')
    await flushPromises()
    expect(mocks.save).toHaveBeenCalledTimes(1)
    expect(mocks.save.mock.calls[0][0]).toMatchObject({
      materialName: '测试原名称',
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


describe('公共字段必填读取品类配置', () => {
  const rules = (fields = {}) => ({ data: { material_grade: false, model: false, color: false, specification: false, brand: false, ...fields } })
  const stateOf = (wrapper: ReturnType<typeof create>) => (wrapper.vm as any).$.setupState

  it('取消颜色必填后允许空颜色新建；下次打开读取最新配置', async () => {
    mocks.requiredFields.mockResolvedValue(rules({ color: true }))
    const wrapper = create()
    await flushPromises()
    const state = stateOf(wrapper)
    await state.selectCategory(2)
    expect(state.commonFieldRequired('color')).toBe(true)
    await state.save()
    expect(mocks.warning).toHaveBeenCalledWith('颜色不能为空（PC 必填）')
    expect(mocks.save).not.toHaveBeenCalled()
    mocks.requiredFields.mockResolvedValue(rules())
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    await state.selectCategory(2)
    expect(state.commonFieldRequired('color')).toBe(false)
    await state.save()
    expect(mocks.save).toHaveBeenCalledWith(expect.objectContaining({ color: '', categoryId: 2 }))
    expect(mocks.requiredFields).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('勾选品牌后显示必填星号，空品牌保存被前端拦截且文案与后端一致', async () => {
    mocks.requiredFields.mockResolvedValue(rules({ brand: true }))
    const wrapper = create()
    await flushPromises()
    const state = stateOf(wrapper)
    await state.selectCategory(2)
    expect(wrapper.findAll('[label="品牌"]').some(item => item.attributes('required') !== undefined)).toBe(true)
    await state.save()
    expect(mocks.warning).toHaveBeenCalledWith('品牌不能为空（PC 必填）')
    expect(mocks.save).not.toHaveBeenCalled()
    state.form.brand = '3M'
    await state.save()
    expect(mocks.save).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('规格必填用保存前的服务端规格渲染结果，不采信旧预览', async () => {
    mocks.requiredFields.mockResolvedValue(rules({ specification: true }))
    mocks.renderSpec.mockResolvedValueOnce({ data: null })
    const wrapper = create()
    await flushPromises()
    const state = stateOf(wrapper)
    await state.selectCategory(2)
    await state.save()
    expect(mocks.warning).toHaveBeenCalledWith('规格不能为空（PC 必填）')
    expect(mocks.save).not.toHaveBeenCalled()
    await state.save()
    expect(mocks.save).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('必填读取失败阻止保存，旧响应不会覆盖新选择或父级选择', async () => {
    mocks.requiredFields.mockRejectedValueOnce(Error('必填读取失败'))
    const wrapper = create()
    await flushPromises()
    const state = stateOf(wrapper)
    await state.selectCategory(2)
    expect(state.configuration).toBeUndefined()
    await state.save()
    expect(mocks.save).not.toHaveBeenCalled()
    let resolveOld!: (value: unknown) => void
    mocks.requiredFields.mockImplementation(id => id === 2 ? new Promise(resolve => { resolveOld = resolve }) : Promise.resolve(rules({ brand: true })))
    const old = state.selectCategory(2)
    await state.selectCategory(3)
    resolveOld(rules({ color: true }))
    await old
    expect(state.commonFieldRequired('brand')).toBe(true)
    expect(state.commonFieldRequired('color')).toBe(false)
    const pending = state.selectCategory(2)
    state.categories.push({ ...config(4, '子类').category, parentId: 3 })
    await state.selectParentCategory(3)
    resolveOld(rules({ color: true }))
    await pending
    expect(state.configuration).toBeUndefined()
    expect(state.form.categoryId).toBeNull()
    wrapper.unmount()
  })

  it('编辑已有物料即使切换品类也不追溯公共字段必填', async () => {
    mocks.detail.mockResolvedValue({ data: {
      material: { materialId: 100, categoryId: 2, materialName: '已有物料', unit: 'M' },
      attributes: {}, tags: [], configuration: config(2, 'PC'),
    } })
    const wrapper = create({ materialId: 100 })
    await flushPromises()
    const state = stateOf(wrapper)
    await state.selectCategory(3)
    await state.save()
    expect(mocks.requiredFields).not.toHaveBeenCalled()
    expect(state.commonFieldRequired('brand')).toBe(false)
    expect(mocks.save).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('BOM 快速建档仍豁免颜色，其余勾选照常校验', async () => {
    mocks.requiredFields.mockResolvedValue(rules({ color: true, brand: true }))
    const wrapper = create({ fromBom: true })
    await flushPromises()
    const state = stateOf(wrapper)
    await state.selectCategory(2)
    expect(state.commonFieldRequired('color')).toBe(false)
    await state.save()
    expect(mocks.warning).toHaveBeenCalledWith('品牌不能为空（PC 必填）')
    wrapper.unmount()
  })
})

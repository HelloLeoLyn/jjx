import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import Category from '../category.vue'

vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('@/components/common-ui/SearchForm.vue', () => ({ default: { template: '<div />' } }))
const mocks = vi.hoisted(() => ({ tree: vi.fn(), requiredFields: vi.fn(), saveCategory: vi.fn(), success: vi.fn(), error: vi.fn(), warning: vi.fn() }))
vi.mock('@/api/inventory/materialCategory', () => ({ materialCategoryApi: { getTree: mocks.tree } }))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: { requiredFields: mocks.requiredFields, saveCategory: mocks.saveCategory } }))
vi.mock('element-plus', async importOriginal => ({
  ...await importOriginal<typeof import('element-plus')>(),
  ElMessage: { success: mocks.success, error: mocks.error, warning: mocks.warning },
}))
const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const Form = defineComponent({
  setup(_, { expose }) { expose({ validate: async () => true, resetFields: () => {} }) },
  template: '<div><slot /></div>',
})
const row = { categoryId: 23, categoryCode: 'RAW_AUX_PROTECT', categoryName: '保护膜', parentId: 17, categoryLevel: 3, modelPrefix: 'PF', status: '1', remark: '保留备注' }
const fields = { material_grade: true, model: true, color: true, specification: true, brand: false }
const create = () => shallowMount(Category, { global: {
  stubs: { ElDialog: Slot, ElForm: Form, ElRow: Slot, ElCol: Slot, ElFormItem: Slot },
  directives: { hasPermi: {}, loading: {} },
} })
const stateOf = (wrapper: ReturnType<typeof create>) => (wrapper.vm as any).$.setupState

beforeEach(() => {
  vi.resetAllMocks()
  mocks.tree.mockResolvedValue({ code: 200, data: [row] })
  mocks.requiredFields.mockResolvedValue({ data: { ...fields } })
  mocks.saveCategory.mockResolvedValue({ data: row })
})

describe('物料分类公共字段必填配置', () => {
  it('回显五个勾选，取消颜色、勾选品牌随品类一次保存并保留备注', async () => {
    const wrapper = create()
    await flushPromises()
    const state = stateOf(wrapper)
    await state.handleUpdate(row)
    expect(mocks.requiredFields).toHaveBeenCalledWith(23)
    expect(state.requiredFields).toEqual(fields)
    expect(state.requiredFieldOptions).toHaveLength(5)
    state.requiredFields.color = false
    state.requiredFields.brand = true
    await state.submitForm()
    expect(mocks.saveCategory).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
      categoryId: 23, remark: '保留备注', status: 1,
      requiredFields: { ...fields, color: false, brand: true },
    }))
    expect(state.open).toBe(false)
    wrapper.unmount()
  })

  it('加载失败不能以全不勾选覆盖配置；重新打开可重试', async () => {
    mocks.requiredFields.mockRejectedValueOnce(Error('读取失败'))
    const wrapper = create()
    await flushPromises()
    const state = stateOf(wrapper)
    await state.handleUpdate(row)
    await state.submitForm()
    expect(mocks.saveCategory).not.toHaveBeenCalled()
    expect(state.requiredFieldsError).toContain('加载失败')
    await state.handleUpdate(row)
    await state.submitForm()
    expect(mocks.saveCategory).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('保存失败保留品类与勾选草稿；新增不沿用上次编辑的品类ID', async () => {
    mocks.saveCategory.mockRejectedValueOnce(Error('保存失败'))
    const wrapper = create()
    await flushPromises()
    const state = stateOf(wrapper)
    await state.handleUpdate(row)
    state.requiredFields.brand = true
    await state.submitForm()
    expect(state.open).toBe(true)
    expect(state.requiredFields.brand).toBe(true)
    state.handleAdd()
    expect(state.form.categoryId).toBeUndefined()
    wrapper.unmount()
  })
})

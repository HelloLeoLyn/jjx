import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  getEngineeringBomInfo: vi.fn(),
  listEngineeringBomItem: vi.fn(),
  addEngineeringBom: vi.fn(),
  editEngineeringBom: vi.fn(),
  confirm: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}))

vi.mock('@/api/product/bom', () => ({
  productBomApi: {
    getEngineeringBomInfo: mocks.getEngineeringBomInfo,
    listEngineeringBomItem: mocks.listEngineeringBomItem,
    addEngineeringBom: mocks.addEngineeringBom,
    editEngineeringBom: mocks.editEngineeringBom,
  },
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: {
    success: mocks.success,
    error: mocks.error,
    warning: mocks.warning,
    info: vi.fn(),
  },
  ElMessageBox: { confirm: mocks.confirm },
}))
vi.mock('xlsx', () => ({
  utils: {
    aoa_to_sheet: () => ({}),
    book_new: () => ({}),
    book_append_sheet: () => {},
    sheet_to_json: () => [],
  },
  read: () => ({ SheetNames: [], Sheets: {} }),
  writeFile: () => {},
}))
vi.mock('@/components/BomItemEditor.vue', () => ({
  // 与真实编辑器同等对外契约（getItems/validateItems/updateItem），只隔离表格渲染
  default: defineComponent({
    name: 'BomItemEditorStub',
    props: ['modelValue', 'bomId'],
    setup(props, { expose }) {
      const rows = () => ((props.modelValue as any[]) || [])
      expose({
        prepareSpecifications: async () => true,
        validateItems: () => true,
        getItems: () => rows().map((row) => ({ ...row })),
        updateItem: (itemId: number, patch: Record<string, unknown>) => {
          const target = rows().find((row) => Number(row?.itemId) === Number(itemId))
          if (!target) return false
          Object.assign(target, patch)
          return true
        },
      })
      return () => h('div', { class: 'editor-stub' })
    },
  }),
}))
vi.mock('@/components/Selector/ProductSelector.vue', () => ({
  default: defineComponent({
    name: 'ProductSelectorStub',
    props: ['modelValue'],
    emits: ['update:modelValue', 'change'],
    template: '<div class="product-selector-stub" />',
  }),
}))
vi.mock('../ManufacturingBomDialog.vue', () => ({
  default: defineComponent({
    name: 'ManufacturingBomDialogStub',
    props: ['modelValue', 'material', 'productId', 'subBomId', 'primaryBomId'],
    emits: ['update:modelValue', 'adopt', 'primary-change'],
    template: '<div class="manufacturing-dialog-stub" />',
  }),
}))
vi.mock('../BomPrintPreview.vue', () => ({
  default: defineComponent({ name: 'BomPrintPreviewStub', template: '<div />' }),
}))

import BomFormDialog from '../BomFormDialog.vue'
import type { EngineeringBomItem } from '@/types/product/bom'

const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const Passthrough = defineComponent({ template: '<div><slot /></div>' })
// el-form 只需要对外契约；真实校验规则不是本用例的验证对象
const FormStub = defineComponent({
  setup(_, { slots, expose }) {
    expose({ validate: async () => {}, resetFields: () => {}, clearValidate: () => {} })
    return () => h('div', slots.default?.())
  },
})
// el-upload 只需要 clearFiles 契约（本用例不测导入流程）
const UploadStub = defineComponent({
  setup(_, { slots, expose }) {
    expose({ clearFiles: () => {} })
    return () => h('div', slots.default?.())
  },
})

const baseRow = (): EngineeringBomItem => ({
  itemId: 11,
  bomId: 9,
  materialId: 349,
  materialCode: 'SFG000345',
  materialName: '中间件',
  specification: '70*180mm',
  unit: 'PCS',
  quantity: 1,
  moduleQty: 1,
  baseQty: 1,
  lossRate: 0,
  sortOrder: 1,
  reviewStatus: 1,
  subBomId: null,
})

const mounted: ReturnType<typeof mount>[] = []

async function form(props: Record<string, unknown> = {}) {
  const wrapper = mount(BomFormDialog, {
    props: { modelValue: true, bomId: undefined, ...props },
    global: {
      stubs: {
        ElDialog: Slot,
        ElForm: FormStub,
        ElRow: Passthrough,
        ElCol: Passthrough,
        ElFormItem: Passthrough,
        ElUpload: UploadStub,
        ElDatePicker: true,
        FullScreen: true,
        UploadFilled: true,
        Download: true,
      },
    },
  })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  mocks.getEngineeringBomInfo.mockResolvedValue({
    data: {
      bomId: 9,
      productId: 42,
      productCode: 'P42',
      productName: '产品甲',
      bomCode: 'P42-BOM',
      bomName: 'P42-BOM',
      bomVersion: 'V1.0',
      effectiveDate: '2026-10-20',
      approveStatus: 1,
      schemeRevision: 7,
      schemeChoices: [],
    },
  })
  mocks.listEngineeringBomItem.mockResolvedValue({ data: [baseRow()] })
  mocks.addEngineeringBom.mockResolvedValue({ data: 9 })
  mocks.editEngineeringBom.mockResolvedValue({ data: { success: true, schemeRevision: 8 } })
})

afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
})

async function editingForm() {
  const wrapper = await form({ bomId: 9 })
  const state = wrapper.vm as any
  await state.loadBomData(9)
  await flushPromises()
  state.setBaseline()
  return { wrapper, state, rows: state.formData.items as EngineeringBomItem[] }
}

describe('父产品BOM：采用制造方案只回填草稿', () => {
  it('采用后只改该行 subBomId/sourceType，不发保存请求、不设主方案、不改其他行', async () => {
    const { wrapper, state } = await editingForm()
    state.formData.items.push({ ...baseRow(), itemId: 12, materialId: 350, materialName: '小片' })
    await flushPromises()
    state.setBaseline()

    state.manufacturingRow = state.formData.items[0]
    state.handleSchemeAdopt({ materialId: 349, bomId: 124 })

    expect(state.formData.items[0].subBomId).toBe(124)
    expect(state.formData.items[0].sourceType).toBe('make')
    expect(state.formData.items[1].subBomId).toBeFalsy()
    expect(mocks.editEngineeringBom).not.toHaveBeenCalled()
    expect(mocks.addEngineeringBom).not.toHaveBeenCalled()
    state.recomputeDirty()
    expect(state.isDirty).toBe(true)
    // 采用不写主方案
    expect(state.schemeChoicesPayload()).toEqual([])
    expect(wrapper.findComponent({ name: 'ManufacturingBomDialogStub' }).exists()).toBe(true)
  })

  it('行物料已变化时拒绝回填，避免串到别的行', async () => {
    const { state } = await editingForm()
    state.manufacturingRow = state.formData.items[0]
    state.handleSchemeAdopt({ materialId: 999, bomId: 124 })
    expect(state.formData.items[0].subBomId).toBeFalsy()
    expect(mocks.warning).toHaveBeenCalledWith('该行物料已变化，请重新打开制造BOM窗口后再采用')
  })
})

describe('父保存：主方案只有显式调整才提交，并带 schemeRevision', () => {
  it('显式设主方案后父保存提交 schemeChoices(isPrimary=true) 与加载时的 revision', async () => {
    const { state } = await editingForm()
    state.handlePrimaryChange({ materialId: 349, bomId: 124 })
    expect(await state.doSave()).toBe(true)

    expect(mocks.editEngineeringBom).toHaveBeenCalledTimes(1)
    const payload = mocks.editEngineeringBom.mock.calls[0][0]
    expect(payload.schemeChoices).toEqual([{ materialId: 349, bomId: 124, isPrimary: true }])
    expect(payload.schemeRevision).toBe(7)
    expect(state.schemeRevision).toBe(8)
  })

  it('只采用不设主方案时，父保存不带 schemeChoices/schemeRevision（不覆盖原主）', async () => {
    const { state } = await editingForm()
    state.manufacturingRow = state.formData.items[0]
    state.handleSchemeAdopt({ materialId: 349, bomId: 124 })
    expect(await state.doSave()).toBe(true)

    const payload = mocks.editEngineeringBom.mock.calls[0][0]
    expect(payload.schemeChoices).toBeUndefined()
    expect(payload.schemeRevision).toBeUndefined()
    expect(payload.items[0].subBomId).toBe(124)
    expect(payload.items[0].sourceType).toBe('make')
  })

  it('编辑已保存BOM但缺 schemeRevision 时，显式主方案保存被拦，不发请求', async () => {
    const wrapper = await form({ bomId: 9 })
    const state = wrapper.vm as any
    mocks.getEngineeringBomInfo.mockResolvedValue({
      data: {
        bomId: 9,
        productId: 42,
        productCode: 'P42',
        productName: '产品甲',
        bomCode: 'P42-BOM',
        bomName: 'P42-BOM',
        schemeRevision: null,
        schemeChoices: [],
      },
    })
    await state.loadBomData(9)
    await flushPromises()
    state.setBaseline()

    state.handlePrimaryChange({ materialId: 349, bomId: 124 })
    expect(await state.doSave()).toBe(false)
    expect(mocks.editEngineeringBom).not.toHaveBeenCalled()
    expect(mocks.warning).toHaveBeenCalledWith(
      '缺少产品主方案版本信息，请关闭后重新打开产品BOM再保存主方案'
    )
  })

  it('换产品清空上一产品的主方案草稿，不同产品之间不串选择', async () => {
    const { state } = await editingForm()
    state.handlePrimaryChange({ materialId: 349, bomId: 124 })
    expect(state.schemeChoicesPayload()).toHaveLength(1)

    state.handleProductChange(43, { productId: 43, productCode: 'P43', productName: '产品乙' })
    expect(state.schemeChoicesPayload()).toEqual([])
    await flushPromises()
    state.setBaseline()
    expect(await state.doSave()).toBe(true)
    const payload = mocks.editEngineeringBom.mock.calls.at(-1)?.[0]
    expect(payload.schemeChoices).toBeUndefined()
  })
})

describe('父BOM取消与权限边界', () => {
  it('父取消只丢草稿，不删独立方案、不发任何写请求', async () => {
    const { wrapper, state } = await editingForm()
    state.formData.remark = '未保存的父BOM修改'
    state.recomputeDirty()
    expect(state.isDirty).toBe(true)

    mocks.confirm.mockRejectedValue('cancel') // 放弃修改
    expect(await state.resolveLeave()).toBe(true)
    await state.handleCancel()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
    expect(mocks.editEngineeringBom).not.toHaveBeenCalled()
    expect(mocks.addEngineeringBom).not.toHaveBeenCalled()
    // 没有任何删除独立方案的入口被调用
    expect(Object.keys(mocks)).not.toContain('removeEngineeringBom')
  })

  it('行已采用方案但用户选“继续编辑”时不关闭、不保存', async () => {
    const { state } = await editingForm()
    state.formData.remark = '还在改'
    state.recomputeDirty()
    mocks.confirm.mockRejectedValue('close')
    expect(await state.resolveLeave()).toBe(false)
    expect(mocks.editEngineeringBom).not.toHaveBeenCalled()
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  getEngineeringBomInfo: vi.fn(),
  listEngineeringBomItem: vi.fn(),
  addEngineeringBom: vi.fn(),
  editEngineeringBom: vi.fn(),
  getProductInfo: vi.fn(),
  acquireBomEditSession: vi.fn(),
  renewBomEditSession: vi.fn(),
  releaseBomEditSession: vi.fn(),
  describeBomEditSession: vi.fn(),
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
    acquireBomEditSession: mocks.acquireBomEditSession,
    renewBomEditSession: mocks.renewBomEditSession,
    releaseBomEditSession: mocks.releaseBomEditSession,
    describeBomEditSession: mocks.describeBomEditSession,
  },
}))
// 新建 BOM 首次设主方案：用现有 GET /product/{id} 的 ProductVo.schemeRevision（不新增接口）
vi.mock('@/api/product', () => ({ getProductInfo: mocks.getProductInfo }))
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
    props: ['modelValue', 'bomId', 'readonly'],
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
      return () =>
        h('div', { class: 'editor-stub', 'data-readonly': String(Boolean(props.readonly)) })
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
  props: ['model', 'modelValue', 'rules', 'disabled', 'labelWidth'],
  setup(props, { slots, expose }) {
    expose({ validate: async () => {}, resetFields: () => {}, clearValidate: () => {} })
    return () =>
      h(
        'div',
        { class: 'form-stub', 'data-disabled': String(Boolean(props.disabled)) },
        slots.default?.(),
      )
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
      contentRevision: 'rev-1',
    },
  })
  mocks.listEngineeringBomItem.mockResolvedValue({ data: [baseRow()] })
  mocks.addEngineeringBom.mockResolvedValue({ data: 9 })
  mocks.editEngineeringBom.mockResolvedValue({
    data: { success: true, schemeRevision: 8, contentRevision: 'rev-2' },
  })
  mocks.getProductInfo.mockResolvedValue({ data: { productId: 42, schemeRevision: 7 } })
  // 编辑占用：默认本窗口取得编辑权（占用失败/失效用例单独覆盖）
  mocks.acquireBomEditSession.mockResolvedValue({
    data: {
      leaseAvailable: true,
      acquired: true,
      occupied: false,
      ownerDisplayName: '张三',
      ownerIsSameUser: true,
      ownerIsSameClient: true,
      expiresAt: Date.now() + 90_000,
      token: 'lease-token',
    },
  })
  mocks.renewBomEditSession.mockResolvedValue({ data: { leaseAvailable: true, acquired: true, token: 'lease-token' } })
  mocks.releaseBomEditSession.mockResolvedValue({ data: true })
  mocks.describeBomEditSession.mockResolvedValue({ data: { leaseAvailable: true, acquired: false, occupied: false } })
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
    expect(mocks.warning).toHaveBeenCalledWith('该行物料已变化，请重新打开生产BOM窗口后再采用')
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

/** 新建产品BOM（尚未保存）：产品已存在，主方案版本必须来自 GET /product/{id} */
async function newForm(product = { productId: 42, productCode: 'P42', productName: '产品甲' }) {
  const wrapper = await form()
  const state = wrapper.vm as any
  state.handleProductChange(product.productId, product)
  await flushPromises()
  state.formData.bomCode = 'P42-BOM'
  state.formData.bomName = 'P42-BOM'
  state.formData.effectiveDate = '2026-11-11'
  state.formData.items = [baseRow()]
  await flushPromises()
  state.setBaseline()
  return { wrapper, state }
}

describe('新建产品BOM：首次设主方案用真实 revision，不默认 0', () => {
  it('选定已存在产品后用 GET /product/{id} 读到 revision=7，随新增提交 7', async () => {
    const { state } = await newForm()
    state.handlePrimaryChange({ materialId: 349, bomId: 124 })
    expect(await state.doSave()).toBe(true)

    expect(mocks.getProductInfo).toHaveBeenCalledWith(42)
    expect(mocks.addEngineeringBom).toHaveBeenCalledTimes(1)
    const payload = mocks.addEngineeringBom.mock.calls[0][0]
    expect(payload.schemeRevision).toBe(7)
    expect(payload.schemeRevision).not.toBe(0)
    expect(payload.schemeChoices).toEqual([{ materialId: 349, bomId: 124, isPrimary: true }])
  })

  it('读不到产品主方案版本时阻止保存主方案，不发请求、不默认 0', async () => {
    mocks.getProductInfo.mockResolvedValue({ data: { productId: 42, schemeRevision: null } })
    const { state } = await newForm()
    state.handlePrimaryChange({ materialId: 349, bomId: 124 })
    expect(await state.doSave()).toBe(false)
    expect(mocks.addEngineeringBom).not.toHaveBeenCalled()
    expect(mocks.warning).toHaveBeenCalledWith(
      '未读取到产品主方案的当前版本，暂时不能保存主方案；请重新选择产品后再试'
    )
  })

  it('异步选产品 A→B：A 的迟到版本响应不污染 B', async () => {
    const pending: Record<number, (value: any) => void> = {}
    mocks.getProductInfo.mockImplementation(
      (id: number) => new Promise((resolve) => {
        pending[id] = resolve
      })
    )
    const wrapper = await form()
    const state = wrapper.vm as any

    state.handleProductChange(42, { productId: 42, productCode: 'P42', productName: '产品甲' })
    state.handleProductChange(43, { productId: 43, productCode: 'P43', productName: '产品乙' })
    await flushPromises()

    pending[43]({ data: { productId: 43, schemeRevision: 9 } })
    await flushPromises()
    expect(state.productSchemeRevision).toBe(9)

    // A 的响应晚到：产品已切到 B，不能把 A 的版本写进来
    pending[42]({ data: { productId: 42, schemeRevision: 7 } })
    await flushPromises()
    expect(state.productSchemeRevision).toBe(9)
    expect(Number(state.formData.productId)).toBe(43)
  })

  it('并发 409：保存失败但保留草稿，提示他人已修改主方案', async () => {
    const { state } = await editingForm()
    mocks.editEngineeringBom.mockRejectedValueOnce({
      response: { status: 409, data: { msg: 'CONCURRENT_MODIFICATION：主方案已被其他会话修改' } },
    })
    state.formData.remark = '并发前的草稿'
    state.handlePrimaryChange({ materialId: 349, bomId: 124 })
    expect(await state.doSave()).toBe(false)

    // 草稿与主方案调整都还在，可以核对后重试
    expect(state.formData.remark).toBe('并发前的草稿')
    expect(state.schemeChoicesPayload()).toEqual([{ materialId: 349, bomId: 124, isPrimary: true }])
    expect(mocks.error).toHaveBeenCalledWith(
      '产品主方案已被其他会话修改，当前编辑内容已保留；请核对后重试'
    )
  })
})

// ==================== 编辑占用 + 服务端内容版本（dev-20261111-018） ====================

describe('父产品BOM：编辑占用与服务端内容版本', () => {
  it('保存请求带 expectedContentRevision 与 editToken，保存成功后不释放租约', async () => {
    const { state } = await editingForm()
    state.formData.remark = '正常保存'
    expect(await state.doSave()).toBe(true)

    expect(mocks.editEngineeringBom).toHaveBeenCalledTimes(1)
    const payload = mocks.editEngineeringBom.mock.calls[0][0]
    expect(payload.expectedContentRevision).toBe('rev-1')
    expect(payload.editToken).toBe('lease-token')
    // 保存成功：用后端返回的新指纹继续编辑，且不释放（保持到关闭/取消）
    expect(state.formData.expectedContentRevision).toBe('rev-2')
    expect(mocks.releaseBomEditSession).not.toHaveBeenCalled()
  })

  it('其他窗口占用时只读并禁止保存，草稿仍可查看', async () => {
    mocks.acquireBomEditSession.mockResolvedValue({
      data: {
        leaseAvailable: true,
        acquired: false,
        occupied: true,
        ownerDisplayName: '李四',
        ownerIsSameUser: false,
        ownerIsSameClient: false,
        message: '该BOM正由 李四 编辑，本窗口为只读',
      },
    })
    const { wrapper, state } = await editingForm()
    expect(state.editSession.readOnly.value).toBe(true)
    expect(state.editSession.message.value).toContain('只读')

    // 只读不是只禁保存按钮：表单与明细编辑入口也必须禁用（修复卡 F6b）
    expect(wrapper.find('.form-stub').attributes('data-disabled')).toBe('true')
    expect(wrapper.find('.editor-stub').attributes('data-readonly')).toBe('true')

    state.formData.remark = '只读窗口的草稿'
    expect(await state.doSave()).toBe(false)
    expect(mocks.editEngineeringBom).not.toHaveBeenCalled()
    expect(state.formData.remark).toBe('只读窗口的草稿')
  })

  it('取得编辑权时表单与明细编辑器可用', async () => {
    const { wrapper } = await editingForm()
    expect(wrapper.find('.form-stub').attributes('data-disabled')).toBe('false')
    expect(wrapper.find('.editor-stub').attributes('data-readonly')).toBe('false')
  })

  it('占用服务不可用（Redis 故障）时只读，不出现“提示失效但照样保存”', async () => {
    mocks.acquireBomEditSession.mockRejectedValue(
      Object.assign(new Error('EDIT_LEASE_UNAVAILABLE：占用服务暂不可用，请稍后重试'), { bizCode: 503 })
    )
    const { state } = await editingForm()
    expect(state.editSession.readOnly.value).toBe(true)
    expect(await state.doSave()).toBe(false)
    expect(mocks.editEngineeringBom).not.toHaveBeenCalled()
  })

  it('保存返回 409 内容冲突：草稿保留、进入只读，不静默覆盖他人保存', async () => {
    const { state } = await editingForm()
    mocks.editEngineeringBom.mockRejectedValueOnce(
      Object.assign(new Error('BOM_CONTENT_STALE：BOM 已被其他人保存，请重新加载后再提交'), { bizCode: 409 })
    )
    state.formData.remark = '甲草稿'
    expect(await state.doSave()).toBe(false)
    expect(state.formData.remark).toBe('甲草稿')
    expect(state.editSession.readOnly.value).toBe(true)
    expect(mocks.error).toHaveBeenCalledWith(
      'BOM 已被其他人保存，当前修改未提交；草稿已保留，请重新加载最新内容或重新申请编辑权'
    )
  })

  it('关闭窗口时 compare-token 释放编辑权', async () => {
    const { wrapper, state } = await editingForm()
    await wrapper.setProps({ modelValue: false })
    await flushPromises()
    expect(mocks.releaseBomEditSession).toHaveBeenCalledWith(9, 'lease-token')
    expect(state.formData.items.length).toBeGreaterThan(0)
  })
})

describe('关闭期间迟到的BOM加载', () => {
  it('关闭后详情才返回，不重新占用旧BOM也不恢复旧内容', async () => {
    const wrapper = await form({ bomId: 9 })
    const state = wrapper.vm as any
    let resolve!: (value: any) => void
    mocks.getEngineeringBomInfo.mockReturnValueOnce(new Promise(done => { resolve = done }))
    const loading = state.loadBomData(9)
    await wrapper.setProps({ modelValue: false })
    resolve({ data: { bomId: 9, remark: '迟到内容' } })
    await loading
    await flushPromises()
    expect(mocks.acquireBomEditSession).not.toHaveBeenCalled()
    expect(state.formData.remark).not.toBe('迟到内容')
  })
})

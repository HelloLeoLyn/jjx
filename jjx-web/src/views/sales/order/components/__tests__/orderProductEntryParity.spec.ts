vi.mock('element-plus/es/components/dropdown/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-item/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-menu/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/autocomplete/style/css', () => ({}))
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { ElSelect, ElMessageBox } from 'element-plus'
import OrderForm from '../OrderForm.vue'
import AddPage from '../../add.vue'
import EditPage from '../../edit.vue'
import SampleConvertPage from '../../sample-convert.vue'
import SalesOrderFormDialog from '../SalesOrderFormDialog.vue'
vi.mock('element-plus/es/components/page-header/style/css', () => ({}))
import CustomerSelector from '@/components/Selector/CustomerSelector.vue'
import ProductSelector from '@/components/Selector/ProductSelector.vue'
import ProductCodeGenerator from '@/components/ProductCodeGenerator/index.vue'
import ProductCodeGenDialog from '../OrderProductDraftDialog.vue'
import ProductFormContent from '@/views/product/list/components/ProductFormContent.vue'

vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/card/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/col/style/css', () => ({}))
vi.mock('element-plus/es/components/date-picker/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/popover/style/css', () => ({}))
vi.mock('element-plus/es/components/row/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/pagination/style/css', () => ({}))

const api = vi.hoisted(() => ({
  addOrder: vi.fn(), updateOrder: vi.fn(), getOrder: vi.fn(),
  getCustomer: vi.fn(), search: vi.fn(), convertSample: vi.fn(),
  getInfo: vi.fn(), getProducts: vi.fn(), getSourceQuotationSummary: vi.fn(), getItems: vi.fn(),
}))
vi.mock('@/api/sales/order', () => ({ orderApi: api }))
vi.mock('@/api/product', () => ({ productApi: { search: api.search,
  generateSerialNo: vi.fn().mockResolvedValue({ data: '002' }),
  category: { getProductCategoryTree: vi.fn().mockResolvedValue({ data: [] }) },
} }))
vi.mock('@/api/sales/customer', () => ({ customerApi: {
  getCustomer: api.getCustomer, getCustomerAddresses: vi.fn().mockResolvedValue({ data: [] }),
} }))
vi.mock('@/api/system/user', () => ({ userApi: { salesPersons: vi.fn().mockResolvedValue({ data: [] }) } }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('@/api/sales/quotation', () => ({ quotationApi: api }))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn().mockResolvedValue({ data: '002' }) } }))
vi.mock('@/composables/useDict', () => ({ useDict: () => ({ options: ref([]) }) }))
vi.mock('@/composables/useCompanyConfig', () => ({ useCompanyConfig: () => ({ company: {} }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }), useRoute: () => ({ params: { id: '9', sampleId: '21' } }) }))
vi.mock('@/composables/useAttachmentSession', () => ({ useAttachmentSession: () => ({
  pending: ref([]), refs: ref([]), refRows: ref([]), getChanges: () => ({ uploads: [] }),
}) }))
vi.mock('@/composables/useAttachmentRefs', () => ({ useAttachmentDraftInstance: () => ({
  draftId: ref('draft-017'), choices: ref([]), complete: vi.fn(), ensure: vi.fn(),
  close: vi.fn(), startNew: vi.fn(), resumeId: null,
}) }))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: {} }))
vi.mock('@/components/attachment/AttachmentUpload.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/attachment/AttachmentManager.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/InternationalAddressEditor.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/product/WorkSpecVersionLink.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/Selector/CustomerSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/sales/customer/components/CustomerFormDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/sales/customer/components/CustomerAddressDialog.vue', () => ({ default: { template: '<div />' } }))

const product = { productId: 11, productCode: 'AA001MEOO', productName: '已有产品', specification: '档案规格' }
const row = () => ({ id: 71, orderId: 9, ...product, workSpecVersionId: 88,
  serialNo: '001', panelType: 'P', panelFeature: 'W', circuitType: 'M', circuitFeature: 'C',
  unit: 'PCS', quantity: 3, unitPrice: 12, amount: 36, deliveryDays: 7,
  customRequirements: '要求', customerMaterialNo: '客户料号', lineRemark: '行备注',
})
const data = () => ({ orderId: 9, customerId: 5, customerName: '客户', orderDate: '2026-10-18',
  deliveryDate: '2026-10-30', salesManagerId: 3, salesManagerName: '销售',
  deliveryAddress: '地址', shippingFee: 0, taxRate: 0, discountAmount: 0,
  totalAmount: 36, finalAmount: 36, items: [row()],
})
const wrappers: any[] = []
beforeEach(() => {
  vi.clearAllMocks()
  api.getCustomer.mockResolvedValue({ code: 200, data: { customerShortName: 'AA' } })
  api.getOrder.mockResolvedValue({ code: 200, data: data() })
  api.search.mockResolvedValue({ code: 200, data: [product] })
  api.addOrder.mockResolvedValue({ code: 200, data: { orderId: 99, traceId: 't' } })
  api.updateOrder.mockResolvedValue({ code: 200 })
  api.convertSample.mockResolvedValue({ code: 200, data: { orderId: 99 } })
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); document.body.innerHTML = '' })
async function form(edit = false, props = {}) {
  const w = mount(OrderForm, { attachTo: document.body, props: { isEdit: edit, orderId: edit ? 9 : undefined, ...props },
    global: { directives: { loading: () => {} } },
  })
  wrappers.push(w)
  await flushPromises()
  if (!edit && !('sampleOrderId' in props)) {
    Object.assign(w.vm.form, data(), { orderId: undefined, salesPersonId: 3, shippingAddress: '地址' })
  }
  await nextTick()
  return w
}
async function open(w: any) {
  await w.findAll('button').find((b: any) => b.text() === '新建产品')!.trigger('click')
  await flushPromises()
}
async function generate(w: any, confirm = true) {
  await open(w)
  const content = w.findComponent(ProductFormContent)
  Object.assign(content.vm.formData, { productName: '新产品' })
  const gen = w.findComponent(ProductCodeGenerator)
  await gen.vm.generate()
  await flushPromises()
  const selects = gen.findAllComponents(ElSelect)
  for (const [i, value] of ['M', 'E', 'O', 'O'].entries()) {
    selects[i].vm.$emit('update:modelValue', value)
    await nextTick()
  }
  await flushPromises()
  const button = Array.from(document.body.querySelectorAll('.el-dialog button')).find(b => b.textContent?.replace(/\s/g, '') === (confirm ? '确认并回填订单' : '取消')) as HTMLButtonElement
  expect(button.disabled).toBe(false)
  button.click()
  await flushPromises()
}
async function chooseExisting(w: any) {
  const input = w.findComponent(ProductSelector).find('input')
  await input.trigger('focus')
  await input.setValue('已有')
  await new Promise(resolve => setTimeout(resolve, 330))
  await flushPromises()
  const option = document.body.querySelector('.ess-option') as HTMLElement
  expect(option).toBeTruthy()
  option.click()
  await flushPromises()
  expect(api.search).toHaveBeenLastCalledWith('已有', 5, 'active')
}

describe('销售订单产品入口真实组件（API与附件/地址外设替身）', () => {
  for (const edit of [false, true]) {
    it(`${edit ? '编辑' : '新增'}：取消不动原行；确认清身份并要求名称；选回已有清编码参数`, async () => {
      const w = await form(edit)
      const before = { ...w.vm.form.items[0] }
      await generate(w, false)
      expect(w.vm.form.items[0]).toEqual(before)
      await open(w)
      const gen = w.findComponent(ProductCodeGenerator)
      expect(gen.findAllComponents(ElSelect).map(s => s.props('modelValue'))).toEqual(['', '', '', ''])
      // 先关闭，重新走生成器确认
      ;(Array.from(document.body.querySelectorAll('.el-dialog button')).find(b => b.textContent?.replace(/\s/g, '') === '取消') as HTMLButtonElement).click()
      await flushPromises()
      await generate(w)
      const item = w.vm.form.items[0]
      expect(item).toMatchObject({ id: 71, productId: null, productCode: 'AA002MEOO', productName: '新产品', specification: expect.any(String),
        workSpecVersionId: null, serialNo: '002', panelType: 'M', panelFeature: 'E', circuitType: 'O', circuitFeature: 'O',
        quantity: 3, unitPrice: 12, deliveryDays: 7, customerMaterialNo: '客户料号', lineRemark: '行备注' })
      // dev-20261019-005：产品类型已从产品表单隐藏，草稿不携带 productType
      expect(item.productDraft).toMatchObject({ productName: '新产品' })
      expect(item.productDraft).not.toHaveProperty('productType')
      const name = w.find('input[placeholder="请输入新产品名称"]')
      expect(name.attributes('readonly')).toBeUndefined()
      await name.setValue('新产品')
      await chooseExisting(w)
      expect(item).toMatchObject({ ...product, workSpecVersionId: null, serialNo: undefined, panelType: undefined,
        panelFeature: undefined, circuitType: undefined, circuitFeature: undefined })
      expect(w.find('input[placeholder="请输入新产品名称"]').attributes('readonly')).toBeDefined()
    })
    for (const fresh of [false, true]) {
      it(`${edit ? '编辑' : '新增'} × ${fresh ? '新建' : '已有'}：提交客户及契约字段，失败保留草稿`, async () => {
        const w = await form(edit)
        if (fresh) { await generate(w); await w.find('input[placeholder="请输入新产品名称"]').setValue('新产品') }
        else await chooseExisting(w)
        const save = edit ? api.updateOrder : api.addOrder
        save.mockResolvedValueOnce({ code: 500 })
        const snapshot = JSON.stringify(w.vm.form)
        expect(await w.vm.submitForm()).toBe(false)
        expect(JSON.stringify(w.vm.form)).toBe(snapshot)
        expect(w.emitted('success')).toBeUndefined()
        expect(await w.vm.submitForm()).toBe(true)
        const sent = save.mock.calls.at(-1)![0]
        expect(sent).toMatchObject({ customerId: 5, deliveryAddress: '地址', totalQuantity: 3 })
        expect(sent.items[0]).toMatchObject({ id: 71, productId: fresh ? null : 11,
          productCode: fresh ? 'AA002MEOO' : product.productCode, productName: fresh ? '新产品' : product.productName })
        if (fresh) expect(sent.items[0]).toMatchObject({ serialNo: '002', panelType: 'M', workSpecVersionId: null })
      })
    }
  }
  it('清空已有产品同时清除ID、规格、参数及版本，保留业务字段', async () => {
    const w = await form()
    const selector = w.findComponent(ProductSelector)
    // 真实选择器的显式清空事件，经过表单 change 处理。
    selector.vm.$emit('change', null, null)
    await nextTick()
    expect(w.vm.form.items[0]).toMatchObject({ productId: null, productCode: '', productName: '', specification: '',
      workSpecVersionId: null, serialNo: undefined, quantity: 3, unitPrice: 12, deliveryDays: 7 })
  })
  it('客户切换/清空后旧编码弹窗不能回填原客户产品', async () => {
    const w = await form()
    await open(w)
    const oldDialog = w.findComponent(ProductCodeGenDialog)
    w.vm.form.customerId = 6
    await nextTick()
    expect(oldDialog.props('modelValue')).toBe(false)
    oldDialog.vm.$emit('confirm', { params: { productCode: 'AA999MEOO' } })
    await nextTick()
    expect(w.vm.form.items[0].productCode).toBe(product.productCode)
    w.findComponent(CustomerSelector).vm.$emit('change', undefined, null)
    await nextTick()
    expect(w.vm.form.items[0]).toMatchObject({ productId: null, productCode: '', productName: '',
      workSpecVersionId: null, serialNo: undefined, quantity: 3, unitPrice: 12 })
  })
  it('客户简称请求失败时保留原行，保存抛错时保留新产品草稿', async () => {
    const w = await form()
    api.getCustomer.mockRejectedValueOnce(new Error('离线'))
    const before = JSON.stringify(w.vm.form.items)
    await open(w)
    expect(JSON.stringify(w.vm.form.items)).toBe(before)
    await generate(w)
    await w.find('input[placeholder="请输入新产品名称"]').setValue('失败保留')
    const snapshot = JSON.stringify(w.vm.form)
    api.addOrder.mockRejectedValueOnce(new Error('保存失败'))
    const log = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(await w.vm.submitForm()).toBe(false)
    expect(JSON.stringify(w.vm.form)).toBe(snapshot)
    log.mockRestore()
  })
  it('历史金额取消重新计算时恢复完整原产品，接受后才切换', async () => {
    api.getOrder.mockResolvedValue({ code: 200, data: { ...data(), shippingFee: null, finalAmount: 50 } })
    const w = await form(true)
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValueOnce('cancel')
    await generate(w)
    expect(w.vm.form.items[0]).toMatchObject(product)
    expect(w.vm.form.finalAmount).toBe(50)
    confirm.mockResolvedValueOnce('confirm')
    await generate(w)
    expect(w.vm.form.items[0].productId).toBeNull()
    expect(w.vm.form.finalAmount).toBe(36)
    confirm.mockRestore()
  })
  it('样品转量产：仅补零价且按产品匹配报价，提交仍走convertSample', async () => {
    api.getInfo.mockResolvedValue({ data: { orderId: 21, customerId: 5, sourceType: 'QUOTATION', sourceNo: 'QT1',
      orderDate: '2026-10-18', deliveryDate: '2026-10-30', salesManagerId: 3, deliveryAddress: '地址' } })
    api.getProducts.mockResolvedValue({ data: [{ ...row(), unitPrice: 0 }, { ...row(), id: 72, productId: 12, productCode: 'BB001MEOO', unitPrice: 20 }] })
    api.getSourceQuotationSummary.mockResolvedValue({ data: { quotationId: 31 } })
    api.getItems.mockResolvedValue({ data: [{ ...product, unitPrice: 15 }] })
    const w = await form(false, { sampleOrderId: 21 })
    expect(w.vm.form.items.map(i => i.unitPrice)).toEqual([15, 20])
    expect(await w.vm.submitForm()).toBe(true)
    expect(api.convertSample).toHaveBeenCalledWith(21, expect.objectContaining({ customerId: 5 }))
    expect(api.addOrder).not.toHaveBeenCalled()
  })
})


describe('逐个入口挂载及保存路径', () => {
  for (const [label, page] of [['路由新增', AddPage], ['路由编辑', EditPage]] as const) {
    it(label, async () => {
      const w = mount(page, { attachTo: document.body, global: { directives: { loading: () => {} } } })
      wrappers.push(w)
      await flushPromises()
      const order = w.findComponent(OrderForm)
      if (page === AddPage) Object.assign(order.vm.form, data(), { orderId: undefined, salesPersonId: 3, shippingAddress: '地址' })
      await nextTick()
      await generate(order)
      await order.find('input[placeholder="请输入新产品名称"]').setValue('入口新产品')
      await w.findAll('button').find(b => b.text() === (page === AddPage ? '提 交' : '保 存'))!.trigger('click')
      await flushPromises()
      expect(page === AddPage ? api.addOrder : api.updateOrder).toHaveBeenCalled()
    })
  }
  for (const mode of ['edit', 'convert'] as const) {
    it(`弹窗${mode === 'edit' ? '编辑' : '样品转量产'}`, async () => {
      api.getInfo.mockResolvedValue({ data: { ...data(), salesManagerId: 3 } })
      api.getProducts.mockResolvedValue({ data: [row()] })
      const w = mount(SalesOrderFormDialog, { attachTo: document.body,
        props: { modelValue: false, mode, orderId: mode === 'edit' ? 9 : 21 }, global: { directives: { loading: () => {} } } })
      wrappers.push(w)
      await w.setProps({ modelValue: true })
      await flushPromises()
      const order = w.findComponent(OrderForm)
      expect(order.props('isEdit')).toBe(mode === 'edit')
      expect(order.props('sampleOrderId')).toBe(mode === 'convert' ? 21 : undefined)
      await generate(order)
      await order.find('input[placeholder="请输入新产品名称"]').setValue('弹窗新产品')
      const btn = Array.from(document.body.querySelectorAll('button')).find(b =>
        b.textContent?.replace(/\s/g, '') === (mode === 'edit' ? '保存' : '确认转量产')) as HTMLButtonElement
      btn.click()
      await flushPromises()
      expect(mode === 'edit' ? api.updateOrder : api.convertSample).toHaveBeenCalled()
    })
  }
  it('路由样品转量产', async () => {
    api.getInfo.mockResolvedValue({ data: { ...data(), salesManagerId: 3 } })
    api.getProducts.mockResolvedValue({ data: [row()] })
    const w = mount(SampleConvertPage, { attachTo: document.body, global: { directives: { loading: () => {} } } })
    wrappers.push(w)
    await flushPromises()
    expect(w.findComponent(OrderForm).props('sampleOrderId')).toBe(21)
    await w.findAll('button').find(b => b.text() === '确认转量产')!.trigger('click')
    await flushPromises()
    expect(api.convertSample).toHaveBeenCalledWith(21, expect.objectContaining({ customerId: 5 }))
  })
})

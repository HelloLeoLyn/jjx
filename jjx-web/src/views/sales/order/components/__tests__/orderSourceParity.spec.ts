vi.mock('element-plus/es/components/dropdown/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-item/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-menu/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/autocomplete/style/css', () => ({}))
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
vi.mock('element-plus/es/components/progress/style/css', () => ({}))
vi.mock('element-plus/es/components/image-viewer/style/css', () => ({}))
vi.mock('element-plus/es/components/upload/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions-item/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline-item/style/css', () => ({}))
vi.mock('element-plus/es/components/drawer/style/css', () => ({}))

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import OrderForm from '../OrderForm.vue'
import GeneratePlanDialog from '../GeneratePlanDialog.vue'
import OrderDetailDrawer from '../OrderDetailDrawer.vue'
import SalesOrderPage from '../../index.vue'
import { orderOwnership, productOwnership } from '@/composables/useAttachmentSession'

vi.mock('@/components/attachment/AttachmentUpload.vue', async () => {
  const { defineComponent } = await import('vue')
  return {
    default: defineComponent({
      name: 'AttachmentUploadStub',
      props: {
        session: Object,
        ownership: Object,
        accept: Array,
        buttonText: String,
        dropZone: Boolean,
      },
      emits: ['uploaded', 'error', 'changed'],
      template: '<div class="upload-stub" />',
    }),
  }
})
vi.mock('@/components/attachment/AttachmentManager.vue', async () => {
  const { defineComponent } = await import('vue')
  return {
    default: defineComponent({
      name: 'AttachmentManagerStub',
      props: {
        session: Object,
        title: String,
        sourceType: String,
        sourceNo: String,
        rowSave: Boolean,
        batchSave: Boolean,
        showOwnershipFilter: Boolean,
      },
      emits: ['changed', 'error'],
      template: '<div class="manager-stub" />',
    }),
  }
})
vi.mock('@/components/attachment/AttachmentDocList.vue', async () => {
  const { defineComponent } = await import('vue')
  return {
    default: defineComponent({
      name: 'AttachmentDocListStub',
      props: { scopes: Array, title: String, columns: Array, rows: Array },
      template: '<div class="doc-list-stub" />',
    }),
  }
})
vi.mock('@/components/InternationalAddressEditor.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/product/WorkSpecVersionLink.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/Selector/CustomerSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/Selector/ProductSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/sales/customer/components/CustomerFormDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/sales/customer/components/CustomerAddressDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../OrderProductCodeGenDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/TraceTimeline/index.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../ReviewDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../ValidationDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../ShortageCheckDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/api/sales/log', () => ({ salesLogApi: { getLogsByOrderId: vi.fn().mockResolvedValue({ data: [] }) } }))

const api = vi.hoisted(() => ({
  addOrder: vi.fn(),
  updateOrder: vi.fn(),
  getOrder: vi.fn(),
  getOrders: vi.fn(),
  deleteOrder: vi.fn(),
  copyOrder: vi.fn(),
  getExchangeRate: vi.fn(),
  getCustomer: vi.fn(),
  search: vi.fn(),
  convertSample: vi.fn(),
  getInfo: vi.fn(),
  getProducts: vi.fn(),
  getSourceQuotationSummary: vi.fn(),
  getItems: vi.fn(),
}))

const orderStatusApi = vi.hoisted(() => ({
  generatePlan: vi.fn(),
  startReview: vi.fn(),
  resubmit: vi.fn(),
  completeOrder: vi.fn(),
  cancelOrder: vi.fn(),
}))

const attachApi = vi.hoisted(() => ({
  tempUpload: vi.fn(),
  drafts: vi.fn(),
  updateDraft: vi.fn(),
  removeDraft: vi.fn(),
  createRef: vi.fn(),
  listRefs: vi.fn(),
  list: vi.fn(),
  listByTrace: vi.fn(),
  documentTypes: vi.fn(),
  batchUpdate: vi.fn(),
  refsBySha256: vi.fn(),
  unbind: vi.fn(),
  updateMeta: vi.fn(),
  downloadUrlByRef: vi.fn(() => ''),
  previewUrlByRef: vi.fn(() => ''),
}))

vi.mock('@/api/sales/order', () => ({ orderApi: api }))
vi.mock('@/api/sales/orderStatus', () => ({ orderStatusApi }))
vi.mock('@/api/product', () => ({ productApi: { search: api.search } }))
vi.mock('@/api/sales/customer', () => ({
  customerApi: {
    getCustomer: api.getCustomer,
    getCustomerAddresses: vi.fn().mockResolvedValue({ data: [] }),
  },
}))
vi.mock('@/api/system/user', () => ({ userApi: { salesPersons: vi.fn().mockResolvedValue({ data: [] }) } }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('@/api/sales/quotation', () => ({ quotationApi: api }))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: attachApi }))
vi.mock('@/api/system/attachment', () => ({ attachmentApi: attachApi }))
vi.mock('@/api/sales/delivery', () => ({ salesDeliveryApi: {} }))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn().mockResolvedValue({ data: 'SO261018001' }) } }))
vi.mock('@/composables/useDict', () => ({ useDict: () => ({ options: { value: [] } }) }))
vi.mock('@/composables/useCompanyConfig', () => ({ useCompanyConfig: () => ({ company: {} }) }))
vi.mock('@/composables/usePermission', () => ({
  usePermission: () => ({ hasPermission: () => true, hasAnyPermission: () => true, hasAllPermissions: () => true, hasRole: () => true }),
}))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ userId: 1, permissions: ['*'], userInfo: { userId: 1 } }),
}))
vi.mock('@/directives', () => ({ hasPermi: () => true, hasRole: () => true }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ path: '/sales/order', params: {}, query: {} }),
}))

const product = { productId: 11, productCode: 'AA001MEOO', productName: '已有产品', specification: '档案规格' }
const row = () => ({
  id: 71, orderId: 9, ...product, workSpecVersionId: 88,
  serialNo: '001', unit: 'PCS', quantity: 3, unitPrice: 12, amount: 36,
  deliveryDays: 7, customRequirements: '', customerMaterialNo: '客户料号', lineRemark: '行备注',
})
const orderData = (extra: Record<string, any> = {}) => ({
  orderId: 9, orderNo: 'SO9', customerId: 5, customerName: '客户',
  orderDate: '2026-10-18', deliveryDate: '2026-10-30',
  salesManagerId: 3, salesManagerName: '销售', deliveryAddress: '地址',
  shippingFee: 0, taxRate: 0, discountAmount: 0, totalAmount: 36, finalAmount: 36,
  traceId: 'TR-ORDER-9', items: [row()],
  ...extra,
})

const wrappers: any[] = []
let seq = 800

beforeEach(() => {
  localStorage.clear()
  seq = 800
  vi.clearAllMocks()
  attachApi.tempUpload.mockImplementation(() => Promise.resolve({ data: ++seq }))
  attachApi.drafts.mockResolvedValue({ data: [] })
  attachApi.listRefs.mockResolvedValue({ data: [] })
  attachApi.list.mockResolvedValue({ data: [] })
  attachApi.listByTrace.mockResolvedValue({ data: [] })
  attachApi.documentTypes.mockResolvedValue({ data: [] })
  attachApi.createRef.mockImplementation(() => Promise.resolve({ data: ++seq }))
  attachApi.batchUpdate.mockResolvedValue({ data: 1 })
  attachApi.updateDraft.mockResolvedValue({ data: true })
  attachApi.removeDraft.mockResolvedValue({ data: true })
  api.getCustomer.mockResolvedValue({ code: 200, data: { customerShortName: 'AA' } })
  api.getOrder.mockResolvedValue({ code: 200, data: orderData() })
  api.getOrders.mockResolvedValue({ data: { records: [orderData()], total: 1 } })
  api.search.mockResolvedValue({ code: 200, data: [product] })
  api.addOrder.mockResolvedValue({ code: 200, data: { orderId: 99, traceId: 'TR-NEW' } })
  api.updateOrder.mockResolvedValue({ code: 200 })
  api.convertSample.mockResolvedValue({ code: 200, data: { orderId: 99, traceId: 'TR-CONVERT' } })
  orderStatusApi.generatePlan.mockResolvedValue({ code: 200 })
})

afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount())
  document.body.innerHTML = ''
  localStorage.clear()
})

async function mountOrderForm(edit = false, props: Record<string, any> = {}) {
  const w = mount(OrderForm, {
    attachTo: document.body,
    props: { isEdit: edit, orderId: edit ? 9 : undefined, ...props },
    global: { directives: { loading: () => {} } },
  })
  wrappers.push(w)
  await flushPromises()
  if (!edit && !props.sampleOrderId) {
    Object.assign(w.vm.form, orderData(), {
      orderId: undefined, orderNo: '', traceId: '',
      salesManagerId: 3, salesPersonId: 3, shippingAddress: '地址', totalQuantity: 3,
    })
    await nextTick()
  }
  return w
}

const uploadSession = (w: any) => w.findComponent({ name: 'AttachmentUploadStub' }).props('session')
async function upload(w: any, fileName: string, ownership: any) {
  const session = uploadSession(w)
  const uploaded = await session.uploadTemporary(new File(['x'], fileName), ownership)
  session.registerUploaded(uploaded.entry, ownership)
  session.pending.value[0].documentType = 'contract'
  await flushPromises()
  return uploaded.attachmentId as number
}

describe('销售订单来源链路与 traceId 归属', () => {
  it('报价转订单：编辑保存保留报价ID/明细版本/金额，附件按产品级落到本单，trace 不串单', async () => {
    api.getOrder.mockResolvedValue({
      code: 200,
      data: orderData({ quotationId: 31, traceId: 'TR-QUOTE-9', finalAmount: 36 }),
    })
    const w = await mountOrderForm(true)
    expect(w.vm.form).toMatchObject({ orderId: 9, quotationId: 31, traceId: 'TR-QUOTE-9' })
    expect(attachApi.listByTrace).toHaveBeenCalledWith('TR-QUOTE-9')

    const attachmentId = await upload(w, '报价来源.pdf', productOwnership({
      rowKey: 'item-71', productId: 11, productCode: 'AA001MEOO',
    }))
    attachApi.listRefs.mockResolvedValue({
      data: [{ id: 950, attachmentId, sourceType: 'sales_order', sourceId: 9, productId: 11, deleted: false }],
    })
    expect(await w.vm.submitForm()).toBe(true)
    const sent = api.updateOrder.mock.calls.at(-1)![0]
    expect(sent).toMatchObject({
      orderId: 9,
      quotationId: 31,
      customerId: 5,
      traceId: 'TR-QUOTE-9',
      finalAmount: 36,
    })
    expect(sent.items[0]).toMatchObject({ id: 71, productId: 11, workSpecVersionId: 88, amount: 36 })
    expect(sent.attachments).toEqual([
      { attachmentId, documentType: 'contract', productId: 11, productCode: 'AA001MEOO' },
    ])
  })

  it('样品转量产：提交 convertSample（含随单附件），保存后用返回的订单ID/traceId 收尾', async () => {
    api.getInfo.mockResolvedValue({
      data: {
        orderId: 21, customerId: 5, contactPerson: '张三', contactPhone: '138',
        orderDate: '2026-10-18', deliveryDate: '2026-10-30', salesManagerId: 3,
        deliveryAddress: '地址', currency: 'CNY', exchangeRate: 1,
      },
    })
    api.getProducts.mockResolvedValue({ data: [{ ...row(), unitPrice: 20, amount: 60 }] })
    const w = await mountOrderForm(false, { sampleOrderId: 21 })
    expect(w.vm.form.customerId).toBe(5)
    const attachmentId = await upload(w, '转量产.pdf', orderOwnership())
    attachApi.listRefs.mockResolvedValue({
      data: [{ id: 951, attachmentId, sourceType: 'sales_order', sourceId: 99, deleted: false }],
    })
    expect(await w.vm.submitForm()).toBe(true)
    expect(api.addOrder).not.toHaveBeenCalled()
    expect(api.convertSample).toHaveBeenCalledWith(21, expect.objectContaining({
      customerId: 5,
      attachments: [{ attachmentId, documentType: 'contract', productId: null, productCode: null }],
    }))
    expect(w.vm.form.orderId).toBe(99)
    expect(w.vm.form.traceId).toBe('TR-CONVERT')
    expect(attachApi.listByTrace).toHaveBeenCalledWith('TR-CONVERT')
    expect(uploadSession(w).pending.value).toHaveLength(0)
  })

  it('手工新增：保存后回填新单号来源（orderId+traceId），附件队列随之切换', async () => {
    const w = await mountOrderForm()
    const attachmentId = await upload(w, '新增.pdf', orderOwnership())
    attachApi.listRefs.mockResolvedValue({
      data: [{ id: 952, attachmentId, sourceType: 'sales_order', sourceId: 99, deleted: false }],
    })
    expect(await w.vm.submitForm()).toBe(true)
    expect(w.vm.form.orderId).toBe(99)
    expect(w.vm.form.traceId).toBe('TR-NEW')
    expect(attachApi.listRefs).toHaveBeenLastCalledWith('sales_order', 99)
    expect(uploadSession(w).pending.value).toHaveLength(0)
  })

  it('列表确认凭证：会话按行订单ID+traceId 打开，换行不串队列', async () => {
    const w = mount(SalesOrderPage, {
      attachTo: document.body,
      global: { directives: { loading: () => {}, hasPermi: () => {} } },
    })
    wrappers.push(w)
    await flushPromises()
    const action = w.findComponent({ name: 'TableActionColumn' })
    expect(action).toBeTruthy()
    action.vm.$emit('action', 'proof', { orderId: 9, orderNo: 'SO9', traceId: 'TR9-ROW' })
    await nextTick()
    await flushPromises()
    const uploadStub = w.findComponent({ name: 'AttachmentUploadStub' })
    const managerStub = w.findComponent({ name: 'AttachmentManagerStub' })
    expect(uploadStub.exists()).toBe(true)
    expect(uploadStub.props('session').sourceId.value).toBe(9)
    expect(managerStub.props()).toMatchObject({
      sourceType: 'sales_order', sourceNo: 'SO9', rowSave: true, batchSave: true,
    })
    expect(attachApi.listByTrace).toHaveBeenCalledWith('TR9-ROW')

    const session = uploadStub.props('session')
    const uploaded = await session.uploadTemporary(new File(['x'], '确认凭证.pdf'), orderOwnership())
    session.registerUploaded(uploaded.entry, orderOwnership())
    session.pending.value[0].documentType = 'contract'
    expect(session.pending.value).toHaveLength(1)

    const saveButton = Array.from(document.body.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('保存附件'),
    ) as HTMLButtonElement | undefined
    expect(saveButton).toBeTruthy()
    saveButton!.click()
    await flushPromises()
    expect(attachApi.createRef).toHaveBeenCalledWith(expect.objectContaining({
      attachmentId: uploaded.attachmentId,
      sourceType: 'sales_order',
      sourceId: 9,
      productId: null,
      productCode: null,
      documentType: 'contract',
    }))
    expect(session.pending.value).toHaveLength(0)

    // 未保存的新上传随本单队列保留；换到另一订单行后不串队列
    const again = await session.uploadTemporary(new File(['y'], '未保存.pdf'), orderOwnership())
    session.registerUploaded(again.entry, orderOwnership())
    expect(session.pending.value).toHaveLength(1)

    action.vm.$emit('action', 'proof', { orderId: 10, orderNo: 'SO10', traceId: 'TR10-ROW' })
    await nextTick()
    await flushPromises()
    expect(uploadStub.props('session').sourceId.value).toBe(10)
    expect(managerStub.props('sourceNo')).toBe('SO10')
    expect(uploadStub.props('session').pending.value).toHaveLength(0)
    expect(attachApi.listByTrace).toHaveBeenCalledWith('TR10-ROW')
    const order9Key = Object.keys(localStorage).find((key) => key.includes(':sales_order:id-9'))
    expect(order9Key).toBeTruthy()
    expect(JSON.parse(localStorage.getItem(order9Key!) || '[]').map((e: any) => e.fileName)).toEqual([
      '未保存.pdf',
    ])
  })

  it('生成计划：确认书先落定（产品级=整单级 null）再生成计划；落定失败不生成且保留队列', async () => {
    const w = mount(GeneratePlanDialog, {
      attachTo: document.body,
      props: { visible: false, order: { orderId: 9, orderNo: 'SO9', traceId: 'TR9', customerName: '客户', finalAmount: 36 } },
      global: { directives: { loading: () => {} } },
    })
    wrappers.push(w)
    await w.setProps({ visible: true })
    await flushPromises()
    const uploadStub = w.findComponent({ name: 'AttachmentUploadStub' })
    expect(uploadStub.exists()).toBe(true)
    const session = uploadStub.props('session')
    const uploaded = await session.uploadTemporary(new File(['x'], '确认书.pdf'), orderOwnership())
    session.registerUploaded(uploaded.entry, orderOwnership())
    session.pending.value[0].documentType = 'contract'

    const confirm = Array.from(document.body.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('确定生成'),
    ) as HTMLButtonElement | undefined
    expect(confirm).toBeTruthy()
    confirm!.click()
    await flushPromises()
    expect(attachApi.createRef).toHaveBeenCalledWith(expect.objectContaining({
      attachmentId: uploaded.attachmentId,
      sourceType: 'sales_order',
      sourceId: 9,
      productId: null,
      productCode: null,
      documentType: 'contract',
    }))
    expect(orderStatusApi.generatePlan).toHaveBeenCalledWith(9)
    expect(session.pending.value).toHaveLength(0)
  })

  it('生成计划反例：附件落定失败时不调用生成计划，待保存队列保留', async () => {
    attachApi.createRef.mockRejectedValueOnce(new Error('落定失败'))
    const w = mount(GeneratePlanDialog, {
      attachTo: document.body,
      props: { visible: false, order: { orderId: 9, orderNo: 'SO9', traceId: 'TR9' } },
      global: { directives: { loading: () => {} } },
    })
    wrappers.push(w)
    await w.setProps({ visible: true })
    await flushPromises()
    const session = w.findComponent({ name: 'AttachmentUploadStub' }).props('session')
    const uploaded = await session.uploadTemporary(new File(['x'], '确认书.pdf'), orderOwnership())
    session.registerUploaded(uploaded.entry, orderOwnership())
    session.pending.value[0].documentType = 'contract'
    const log = vi.spyOn(console, 'error').mockImplementation(() => {})
    const confirm = Array.from(document.body.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('确定生成'),
    ) as HTMLButtonElement | undefined
    expect(confirm).toBeTruthy()
    confirm!.click()
    await flushPromises()
    expect(attachApi.createRef).toHaveBeenCalled()
    expect(orderStatusApi.generatePlan).not.toHaveBeenCalled()
    expect(session.pending.value).toHaveLength(1)
    log.mockRestore()
  })

  it('详情只读：按本单 sourceType+sales_order/sourceId 读新引用，不带同链路扩大范围', async () => {
    const w = mount(OrderDetailDrawer, {
      attachTo: document.body,
      props: { modelValue: false, orderId: 9 },
      global: { directives: { loading: () => {} } },
    })
    wrappers.push(w)
    await w.setProps({ modelValue: true })
    await flushPromises()
    const list = w.findComponent({ name: 'AttachmentDocListStub' })
    expect(list.exists()).toBe(true)
    expect(list.props('scopes')).toEqual([
      { kind: 'own', sourceType: 'sales_order', sourceId: 9 },
    ])
  })
})

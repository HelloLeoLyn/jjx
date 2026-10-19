vi.mock('element-plus/es/components/page-header/style/css', () => ({}))
vi.mock('element-plus/theme-chalk/base.css', () => ({}))
vi.mock('element-plus/es/components/skeleton/style/css', () => ({}))
vi.mock('element-plus/es/components/skeleton-item/style/css', () => ({}))
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
import OrderForm from '@/views/sales/order/components/OrderForm.vue'
import CheckDialog from '../SampleConvertCheckDialog.vue'
import ConvertPage from '@/views/sales/order/sample-convert.vue'
import FormDialog from '@/views/sales/order/components/SalesOrderFormDialog.vue'

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
vi.mock('@/components/product/WorkSpecVersionLink.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/Selector/CustomerSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/Selector/ProductSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/sales/customer/components/CustomerFormDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/sales/customer/components/CustomerAddressDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/sales/order/components/OrderProductDraftDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/TraceTimeline/index.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/sales/order/components/ReviewDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/sales/order/components/ValidationDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/sales/order/components/ShortageCheckDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/api/sales/log', () => ({
  salesLogApi: { getLogsByOrderId: vi.fn().mockResolvedValue({ data: [] }) },
}))

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
  convertCheck: vi.fn(),
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
vi.mock('@/api/system/user', () => ({
  userApi: { salesPersons: vi.fn().mockResolvedValue({ data: [] }) },
}))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('@/api/sales/quotation', () => ({ quotationApi: api }))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: attachApi }))
vi.mock('@/api/system/attachment', () => ({ attachmentApi: attachApi }))
vi.mock('@/api/sales/delivery', () => ({ salesDeliveryApi: {} }))
vi.mock('@/utils/request', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: 'SO261018001' }) },
}))
vi.mock('@/composables/useDict', () => ({ useDict: () => ({ options: { value: [] } }) }))
vi.mock('@/composables/useCompanyConfig', () => ({ useCompanyConfig: () => ({ company: {} }) }))
vi.mock('@/composables/usePermission', () => ({
  usePermission: () => ({
    hasPermission: () => true,
    hasAnyPermission: () => true,
    hasAllPermissions: () => true,
    hasRole: () => true,
  }),
}))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ userId: 1, permissions: ['*'], userInfo: { userId: 1 } }),
}))
vi.mock('@/directives', () => ({ hasPermi: () => true, hasRole: () => true }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ path: '/sales/order', params: { sampleId: '200' }, query: {} }),
}))

const wrappers: any[] = []
const readiness = (extra: any = {}) => ({
  orderId: 200,
  orderNo: 'SP37',
  returnToSourceOrder: true,
  sourceOrderId: 100,
  sourceOrderNo: 'SO37',
  productCode: 'P37',
  productName: '产品',
  allPass: true,
  items: [{ code: 'product', name: '产品建档', level: 'required', pass: true }],
  ...extra,
})
beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  for (const key of ['drafts', 'listRefs', 'list', 'listByTrace', 'documentTypes'])
    (attachApi as any)[key].mockResolvedValue({ data: [] })
  api.convertCheck.mockResolvedValue({ code: 200, data: readiness() })
  api.convertSample.mockResolvedValue({ code: 200, data: { orderId: 100, orderNo: 'SO37' } })
  api.getInfo.mockResolvedValue({ data: { orderId: 200, customerId: 7, orderNo: 'SP37' } })
  api.getProducts.mockResolvedValue({ data: [] })
})
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount())
  localStorage.clear()
})
async function render(component: any, props: any) {
  const w = mount(component, {
    props,
    global: {
      directives: { loading: () => {} },
      stubs: {
        ElDialog: { template: '<div><slot/><slot name="footer"/></div>' },
      },
    },
  })
  wrappers.push(w)
  await flushPromises()
  return w
}
describe('样品按真实来源关联回原销售订单', () => {
  it('检查只读、关闭不保存，确认后显示原单入口而非生成新单', async () => {
    const w = await render(CheckDialog, { modelValue: true, orderId: 200, embedded: true })
    expect(w.text()).toContain('关联回原订单')
    expect(api.convertSample).not.toHaveBeenCalled()
    expect(w.find('a').attributes('href')).toBe('/dashboard/order/detail/100')
    await (w.vm as any).confirmReturn()
    expect(api.convertSample).toHaveBeenCalledExactlyOnceWith(200)
    expect(w.text()).toContain('已关联回原订单')
    expect(w.text()).toContain('查看原订单 SO37')
    await (w.vm as any).confirmReturn()
    expect(api.convertSample).toHaveBeenCalledTimes(1)
    expect(w.emitted('success')).toHaveLength(1)
  })
  it('加载失败可重试，就绪检查失败不可提交', async () => {
    api.convertCheck.mockRejectedValueOnce(new Error('network'))
    const w = await render(CheckDialog, { modelValue: true, orderId: 200, embedded: true })
    expect(w.text()).toContain('就绪检查加载失败')
    await (w.vm as any).confirmReturn()
    expect(api.convertSample).not.toHaveBeenCalled()
    api.convertCheck.mockResolvedValue({
      data: readiness({ allPass: false, items: [{ name: 'BOM', level: 'required', pass: false }] }),
    })
    await w
      .findAll('button')
      .find((b) => b.text() === '重试')!
      .trigger('click')
    await flushPromises()
    expect(w.text()).toContain('BOM')
    await (w.vm as any).confirmReturn()
    expect(api.convertSample).not.toHaveBeenCalled()
  })
  it('双击只发一次请求，关联失败不显示成功', async () => {
    const w = await render(CheckDialog, { modelValue: true, orderId: 200, embedded: true })
    let reject!: (e: Error) => void
    api.convertSample.mockImplementationOnce(
      () =>
        new Promise((_, r) => {
          reject = r
        })
    )
    const first = (w.vm as any).confirmReturn()
    await (w.vm as any).confirmReturn()
    expect(api.convertSample).toHaveBeenCalledTimes(1)
    reject(new Error('原单状态已变化'))
    await first
    await flushPromises()
    expect(w.text()).not.toContain('已关联回原订单')
    expect(w.emitted('success')).toBeUndefined()
  })
  it('OrderForm 销售单来源隐藏数量价格表单，不读取预填明细，不调用普通新增', async () => {
    const w = await render(OrderForm, { sampleOrderId: 200 })
    expect(w.findComponent(CheckDialog).exists()).toBe(true)
    expect(w.findComponent({ name: 'ElForm' }).exists()).toBe(false)
    expect(api.getInfo).not.toHaveBeenCalled()
    expect(api.getProducts).not.toHaveBeenCalled()
    await (w.vm as any).submitForm()
    expect(api.convertSample).toHaveBeenCalledExactlyOnceWith(200)
    expect(api.addOrder).not.toHaveBeenCalled()
  })
  it('来源加载失败不能退化为普通新增，重试后显示关联', async () => {
    api.convertCheck.mockRejectedValueOnce(new Error('network'))
    const w = await render(OrderForm, { sampleOrderId: 200 })
    expect(w.text()).toContain('无法读取样品来源')
    expect(await (w.vm as any).submitForm()).toBe(false)
    expect(api.addOrder).not.toHaveBeenCalled()
    await w
      .findAll('button')
      .find((b) => b.text() === '重试')!
      .trigger('click')
    await flushPromises()
    expect(w.findComponent(CheckDialog).exists()).toBe(true)
  })
  it('报价和独立打样保留标准订单预填', async () => {
    api.convertCheck.mockResolvedValue({ data: readiness({ returnToSourceOrder: false }) })
    const w = await render(OrderForm, { sampleOrderId: 200 })
    expect(w.findComponent(CheckDialog).exists()).toBe(false)
    expect(api.getInfo).toHaveBeenCalledWith(200)
    expect(api.getProducts).toHaveBeenCalledWith(200)
    expect(w.findComponent({ name: 'ElForm' }).exists()).toBe(true)
  })
  it('转换页面入口按真实来源显示关联，取消前不写数据', async () => {
    const w = await render(ConvertPage, {})
    expect(w.text()).toContain('关联回原订单')
    expect(w.text()).not.toContain('数据已带入，请确认数量/单价后提交')
    expect(w.findComponent(CheckDialog).exists()).toBe(true)
    expect(api.convertSample).not.toHaveBeenCalled()
  })
  it('转换弹窗入口按真实来源显示关联', async () => {
    const w = await render(FormDialog, { modelValue: false, mode: 'convert', orderId: 200 })
    await w.setProps({ modelValue: true })
    await flushPromises()
    expect(w.findComponent(CheckDialog).exists()).toBe(true)
    expect(w.text()).toContain('关联回原订单')
    expect(api.convertSample).not.toHaveBeenCalled()
  })
})

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
vi.mock('element-plus/es/components/radio/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions-item/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline-item/style/css', () => ({}))
vi.mock('element-plus/es/components/drawer/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox-group/style/css', () => ({}))

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { ElSelect } from 'element-plus'
import OrderForm from '../OrderForm.vue'
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
vi.mock('@/components/InternationalAddressEditor.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/product/WorkSpecVersionLink.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/Selector/CustomerSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/Selector/ProductSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/sales/customer/components/CustomerFormDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/sales/customer/components/CustomerAddressDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../OrderProductCodeGenDialog.vue', () => ({ default: { template: '<div />' } }))

const api = vi.hoisted(() => ({
  addOrder: vi.fn(),
  updateOrder: vi.fn(),
  getOrder: vi.fn(),
  getCustomer: vi.fn(),
  search: vi.fn(),
  convertSample: vi.fn(),
  getInfo: vi.fn(),
  getProducts: vi.fn(),
  getSourceQuotationSummary: vi.fn(),
  getItems: vi.fn(),
  getExchangeRate: vi.fn(),
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
vi.mock('@/utils/request', () => ({ default: { get: vi.fn().mockResolvedValue({ data: 'SO261018001' }) } }))
vi.mock('@/composables/useDict', () => ({ useDict: () => ({ options: { value: [] } }) }))
vi.mock('@/composables/useCompanyConfig', () => ({ useCompanyConfig: () => ({ company: {} }) }))
vi.mock('@/composables/usePermission', () => ({
  usePermission: () => ({ hasPermission: () => true, hasAnyPermission: () => true, hasAllPermissions: () => true, hasRole: () => true }),
}))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ userId: 1, permissions: ['*'], userInfo: { userId: 1 } }),
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useRoute: () => ({ params: {}, query: {} }),
}))

const product = { productId: 11, productCode: 'AA001MEOO', productName: '已有产品', specification: '档案规格' }
const row = () => ({
  id: 71, orderId: 9, ...product, workSpecVersionId: 88,
  serialNo: '001', unit: 'PCS', quantity: 3, unitPrice: 12, amount: 36,
  deliveryDays: 7, customRequirements: '', customerMaterialNo: '客户料号', lineRemark: '行备注',
})
const data = () => ({
  orderId: 9, orderNo: 'SO9', customerId: 5, customerName: '客户',
  orderDate: '2026-10-18', deliveryDate: '2026-10-30',
  salesManagerId: 3, salesManagerName: '销售', deliveryAddress: '地址',
  shippingFee: 0, taxRate: 0, discountAmount: 0, totalAmount: 36, finalAmount: 36,
  items: [row()],
})

const wrappers: any[] = []
let seq = 500

beforeEach(() => {
  localStorage.clear()
  seq = 500
  vi.clearAllMocks()
  attachApi.tempUpload.mockImplementation(() => Promise.resolve({ data: ++seq }))
  attachApi.drafts.mockResolvedValue({ data: [] })
  attachApi.listRefs.mockResolvedValue({ data: [] })
  attachApi.list.mockResolvedValue({ data: [] })
  attachApi.listByTrace.mockResolvedValue({ data: [] })
  attachApi.documentTypes.mockResolvedValue({ data: [] })
  attachApi.batchUpdate.mockResolvedValue({ data: 1 })
  attachApi.updateDraft.mockResolvedValue({ data: true })
  attachApi.removeDraft.mockResolvedValue({ data: true })
  api.getCustomer.mockResolvedValue({ code: 200, data: { customerShortName: 'AA' } })
  api.getOrder.mockResolvedValue({ code: 200, data: data() })
  api.search.mockResolvedValue({ code: 200, data: [product] })
  api.addOrder.mockResolvedValue({ code: 200, data: { orderId: 99, traceId: 'TR-NEW' } })
  api.updateOrder.mockResolvedValue({ code: 200 })
  api.convertSample.mockResolvedValue({ code: 200, data: { orderId: 99, traceId: 'TR-CONVERT' } })
})

afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount())
  document.body.innerHTML = ''
  localStorage.clear()
})

async function form(edit = false) {
  const w = mount(OrderForm, {
    attachTo: document.body,
    props: { isEdit: edit, orderId: edit ? 9 : undefined },
    global: { directives: { loading: () => {} } },
  })
  wrappers.push(w)
  await flushPromises()
  if (!edit) {
    Object.assign(w.vm.form, data(), { orderId: undefined, salesManagerId: 3, shippingAddress: '地址', salesPersonId: 3, totalQuantity: 3 })
    await nextTick()
  }
  return w
}

const uploadStub = (w: any) => w.findComponent({ name: 'AttachmentUploadStub' })
const managerStub = (w: any) => w.findComponent({ name: 'AttachmentManagerStub' })
const sessionOf = (w: any) => uploadStub(w).props('session')

async function upload(w: any, fileName: string, ownership: any) {
  const session = sessionOf(w)
  const uploaded = await session.uploadTemporary(new File(['x'], fileName), ownership)
  session.registerUploaded(uploaded.entry, ownership)
  await flushPromises()
  return uploaded
}

async function pickOwnership(w: any, key: string) {
  const select = w.findAllComponents(ElSelect).find((item: any) => item.props('modelValue') === 'order')
  expect(select).toBeTruthy()
  select!.vm.$emit('update:modelValue', key)
  await nextTick()
}

describe('销售订单随单附件（真实会话 + 真实表单模板）', () => {
  it('整单级上传：待保存不冒充已归属，保存请求 productId/productCode 均为空', async () => {
    const w = await form()
    const { attachmentId } = await upload(w, '整单.pdf', orderOwnership())
    const session = sessionOf(w)
    expect(session.pending.value).toHaveLength(1)
    expect(session.refRows.value).toHaveLength(0)
    expect(managerStub(w).props()).toMatchObject({ sourceType: 'sales_order', rowSave: true, batchSave: true })

    expect(await w.vm.submitForm()).toBe(false)
    expect(api.addOrder).not.toHaveBeenCalled()
    session.pending.value[0].documentType = 'contract'
    attachApi.listRefs.mockResolvedValue({
      data: [{ id: 901, attachmentId, sourceType: 'sales_order', sourceId: 99, deleted: false }],
    })

    expect(await w.vm.submitForm()).toBe(true)
    const sent = api.addOrder.mock.calls.at(-1)![0]
    expect(sent.attachments).toEqual([
      { attachmentId, documentType: 'contract', productId: null, productCode: null },
    ])
    expect(session.pending.value).toHaveLength(0)
  })

  it('产品级：上传归属固定到所选产品行，保存请求带 productId + productCode；失败保留、重试同一请求', async () => {
    const w = await form()
    await pickOwnership(w, 'product:11')
    expect(uploadStub(w).props('ownership')).toMatchObject({
      key: 'product:11', productId: 11, productCode: 'AA001MEOO',
    })
    const { attachmentId } = await upload(w, '产品.pdf', uploadStub(w).props('ownership'))
    const session = sessionOf(w)
    session.pending.value[0].documentType = 'drawing'

    api.addOrder.mockResolvedValueOnce({ code: 500 })
    const before = JSON.stringify(session.pending.value)
    expect(await w.vm.submitForm()).toBe(false)
    expect(JSON.stringify(session.pending.value)).toBe(before)
    expect(session.pending.value).toHaveLength(1)

    attachApi.listRefs.mockResolvedValue({
      data: [{ id: 902, attachmentId, sourceType: 'sales_order', sourceId: 99, productId: 11, productCode: 'AA001MEOO', deleted: false }],
    })
    expect(await w.vm.submitForm()).toBe(true)
    const first = api.addOrder.mock.calls.at(-1)![0].attachments
    expect(first).toEqual([
      { attachmentId, documentType: 'drawing', productId: 11, productCode: 'AA001MEOO' },
    ])
    expect(session.pending.value).toHaveLength(0)
    // 保存成功后才清已接受项：失败那次与成功那次提交的是同一条（同 attachmentId，不重复入队）
    expect(api.addOrder.mock.calls.filter((call) => call[0].attachments?.length).length).toBe(2)
  })

  it('编辑入口同样提交产品级附件；未选类型不提交保存', async () => {
    const w = await form(true)
    const session = sessionOf(w)
    const { attachmentId } = await upload(w, '编辑.pdf', productOwnership({ rowKey: 'item-71', productId: 11, productCode: 'AA001MEOO' }))
    expect(await w.vm.submitForm()).toBe(false)
    expect(api.updateOrder).not.toHaveBeenCalled()
    session.pending.value[0].documentType = 'contract'
    attachApi.listRefs.mockResolvedValue({
      data: [{ id: 903, attachmentId, sourceType: 'sales_order', sourceId: 9, productId: 11, deleted: false }],
    })
    expect(await w.vm.submitForm()).toBe(true)
    const sent = api.updateOrder.mock.calls.at(-1)![0]
    expect(sent).toMatchObject({ orderId: 9, customerId: 5 })
    expect(sent.attachments).toEqual([
      { attachmentId, documentType: 'contract', productId: 11, productCode: 'AA001MEOO' },
    ])
  })

  it('反例：产品被切换/移除后旧产品待保存附件不静默降级，保存前置拦截且不发请求', async () => {
    const w = await form()
    await upload(w, '旧产品.pdf', productOwnership({ rowKey: 'item-71', productId: 11, productCode: 'AA001MEOO' }))
    sessionOf(w).pending.value[0].documentType = 'contract'
    w.vm.form.items[0].productId = 12
    w.vm.form.items[0].productCode = 'AA002MEOO'
    await nextTick()
    expect(await w.vm.submitForm()).toBe(false)
    expect(api.addOrder).not.toHaveBeenCalled()
    expect(sessionOf(w).pending.value).toHaveLength(1)

    w.vm.form.items.splice(0, 1)
    await nextTick()
    expect(await w.vm.submitForm()).toBe(false)
    expect(api.addOrder).not.toHaveBeenCalled()
  })

  it('新建实例之间队列隔离；已保存单据的队列键与编辑页一致（列表/编辑共用）', async () => {
    const a = await form()
    await upload(a, 'A.pdf', orderOwnership())
    // 新窗口/新实例：自动恢复指针在 sessionStorage，按新实例重建（跨窗口隔离）
    sessionStorage.clear()
    const b = await form()
    await upload(b, 'B.pdf', orderOwnership())
    expect(sessionOf(a).pending.value.map((e: any) => e.fileName)).toEqual(['A.pdf'])
    expect(sessionOf(b).pending.value.map((e: any) => e.fileName)).toEqual(['B.pdf'])
    expect(sessionOf(a).storageKey.value).not.toBe(sessionOf(b).storageKey.value)

    const edit = await form(true)
    await upload(edit, '共享.pdf', orderOwnership())
    const savedKey = sessionOf(edit).storageKey.value
    expect(savedKey).toContain(':sales_order:id-9')
    const stored = JSON.parse(localStorage.getItem(savedKey) || '[]')
    expect(stored.map((e: any) => e.fileName)).toEqual(['共享.pdf'])
  })

  it('附件信息保存只提交类型/备注/排序，不建引用', async () => {
    const w = await form(true)
    const session = sessionOf(w)
    attachApi.listRefs.mockResolvedValue({
      data: [{ id: 904, attachmentId: 700, sourceType: 'sales_order', sourceId: 9, documentType: 'contract', remark: null, sortOrder: 0, deleted: false }],
    })
    await session.refresh()
    await flushPromises()
    session.setEdit(904, { remark: '备注', sortOrder: 2 })
    const committed = await session.commit({ kind: 'row', refId: 904 })
    expect(committed.validation.ok).toBe(true)
    expect(attachApi.createRef).not.toHaveBeenCalled()
    expect(attachApi.batchUpdate).toHaveBeenCalledTimes(1)
    expect(attachApi.batchUpdate.mock.calls[0][0][0]).toMatchObject({
      refId: 904, remark: '备注', sortOrder: 2,
    })
  })
})

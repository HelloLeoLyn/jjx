/**
 * dev-20261018-038：样品单客户选择统一走公共 CustomerSelector
 *
 * 覆盖面（按卡面 B 段验收）：
 * 1）新增/编辑同一入口都是 CustomerSelector（不再手写 remote el-select、不再维护本地候选）；
 * 2）编辑正确回显当前客户（id + 名称）；
 * 3）选中后正确带出联系人/电话，清空同步清理身份；
 * 4）来源锁定（报价单带出客户时客户不可改）等既有行为保留。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// element-plus 组件样式在 jsdom 下不需要真实 CSS：与本目录既有用例同款处理
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/option-group/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/date-picker/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-menu/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-item/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/card/style/css', () => ({}))
vi.mock('element-plus/es/components/upload/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))

import SampleForm from '../SampleForm.vue'
import CustomerSelector from '@/components/Selector/CustomerSelector.vue'

const customerApiMock = vi.hoisted(() => ({
  getCustomer: vi.fn(),
  searchCustomers: vi.fn(),
  getCustomerContacts: vi.fn(),
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

vi.mock('@/api/sales/customer', () => ({ customerApi: customerApiMock }))
// 附件草稿实例会读当前登录人（useUserStore）；测试里给一个固定用户，避免依赖 pinia 装配
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ userId: 1, userInfo: { userId: 1 }, permissions: ['*'] }),
}))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: attachApi }))
vi.mock('@/api/system/attachment', () => ({ attachmentApi: attachApi }))
vi.mock('@/api/sales/sampleOrder', () => ({
  sampleOrderApi: {
    getInfo: vi.fn(),
    getProducts: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}))
vi.mock('@/api/sales/quotation', () => ({ quotationApi: { getItems: vi.fn() } }))
vi.mock('@/api/product', () => ({ searchProduct: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: vi.fn().mockResolvedValue({ data: { records: [] } }) }))
vi.mock('@/components/ProductCodeGenDialog/index.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/attachment/AttachmentUpload.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/attachment/AttachmentManager.vue', () => ({ default: { template: '<div />' } }))

const customer = (extra: Record<string, any> = {}) => ({
  customerId: 7,
  customerCode: 'C007',
  customerName: '客户甲',
  customerShortName: '甲',
  contactPerson: '张三',
  contactPhone: '13800000001',
  email: '',
  address: '',
  creditLimit: 0,
  status: 1,
  remark: '',
  ...extra,
})

const productRow = () => ({
  productId: 11,
  productCode: 'P001',
  productName: '产品A',
  quantity: 2,
  unit: 'PCS',
})

/** 取某个表单项里的输入框（el-input / el-select 内置 input 都命中） */
function fieldInput(w: any, label: string) {
  const item = w
    .findAll('.el-form-item')
    .find((el: any) => (el.find('.el-form-item__label').text() || '').startsWith(label))
  return item?.find('input')
}

function mountForm(props: Record<string, any> = {}) {
  // inline 模式直接渲染表单本体（dialog 模式的 el-dialog 内容会 teleport 到 body）
  return mount(SampleForm, {
    props: { mode: 'inline', ...props },
  })
}

describe('SampleForm 客户选择统一走 CustomerSelector（dev-20261018-038）', () => {
  beforeEach(() => {
    customerApiMock.getCustomer.mockReset()
    customerApiMock.searchCustomers.mockReset()
    customerApiMock.getCustomer.mockResolvedValue({ code: 200, data: customer() })
  })

  it('新增入口用公共 CustomerSelector，不再手写远程 el-select', async () => {
    const w = mountForm()
    await flushPromises()

    const selectors = w.findAllComponents(CustomerSelector)
    expect(selectors).toHaveLength(1)
    expect(selectors[0].props('valueType')).toBe('customerId')
    // 公共组件实例挂的就是 .customer-selector；页面内不再另建候选
    expect(w.find('.customer-selector').exists()).toBe(true)
    // 客户表单里只剩一个 el-select（来自公共组件），没有第二套手写 remote 下拉
    const customerItem = w
      .findAll('.el-form-item')
      .find((el: any) => (el.find('.el-form-item__label').text() || '').startsWith('客户'))
    expect(customerItem!.findAll('.el-select').length).toBe(1)
    w.unmount()
  })

  it('编辑回显当前客户：id 传入且按 id 取回客户名显示', async () => {
    const w = mountForm({
      visible: true,
      editId: 9,
      editOrderNo: 'SP9',
      order: {
        orderId: 9,
        customerId: 7,
        customerName: '客户甲',
        sourceType: '',
        sourceNo: '',
        contactPerson: '单据联系人',
        contactPhone: '010-1111',
        deliveryDate: '2026-10-30',
      },
      products: [productRow()],
    })
    await flushPromises()

    const selector = w.findComponent(CustomerSelector)
    expect(selector.props('modelValue')).toBe(7)
    expect(customerApiMock.getCustomer).toHaveBeenCalledWith(7)
    // 回显名称来自公共组件的 getCustomer 补载，而不是页面本地候选
    expect(w.text()).toContain('客户甲')
    // 编辑时联系人取单据快照，不被客户档案覆盖
    expect((fieldInput(w, '联系人')!.element as HTMLInputElement).value).toBe('单据联系人')
    expect(customerApiMock.searchCustomers).not.toHaveBeenCalled()
    w.unmount()
  })

  it('选中客户后带出联系人/电话，并清掉上一位客户的产品选择', async () => {
    const w = mountForm({
      visible: true,
      editId: 9,
      order: { orderId: 9, customerId: 7, sourceType: '', sourceNo: '', contactPerson: '', contactPhone: '' },
      products: [productRow()],
    })
    await flushPromises()
    expect((fieldInput(w, '产品名称')!.element as HTMLInputElement).value).toBe('产品A')

    w.findComponent(CustomerSelector).vm.$emit(
      'change',
      8,
      customer({ customerId: 8, customerName: '客户乙', contactPerson: '李四', contactPhone: '13900000002' }),
    )
    await flushPromises()

    expect((fieldInput(w, '联系人')!.element as HTMLInputElement).value).toBe('李四')
    expect((fieldInput(w, '联系电话')!.element as HTMLInputElement).value).toBe('13900000002')
    expect((fieldInput(w, '产品名称')!.element as HTMLInputElement).value).toBe('')
    w.unmount()
  })

  it('同一客户重选不覆盖手工改过的联系人；清空同步清理身份', async () => {
    const w = mountForm({
      visible: true,
      editId: 9,
      order: { orderId: 9, customerId: 7, sourceType: '', sourceNo: '', contactPerson: '张三', contactPhone: '13800000001' },
      products: [productRow()],
    })
    await flushPromises()

    const contact = fieldInput(w, '联系人')!
    await contact.setValue('手工联系人')
    w.findComponent(CustomerSelector).vm.$emit('change', 7, customer())
    await flushPromises()
    expect((contact.element as HTMLInputElement).value).toBe('手工联系人')

    w.findComponent(CustomerSelector).vm.$emit('change', null, null)
    await flushPromises()
    expect((fieldInput(w, '联系人')!.element as HTMLInputElement).value).toBe('')
    expect((fieldInput(w, '联系电话')!.element as HTMLInputElement).value).toBe('')
    // 清空后客户 id 为 undefined（公共组件 prop 默认值会把 undefined 归一为 null），选中项为空
    expect(w.findComponent(CustomerSelector).props('modelValue')).toBeFalsy()
    w.unmount()
  })

  it('来源报价单锁定客户不可改，销售订单来源切客户清来源单号', async () => {
    const w = mountForm()
    w.vm.fillFromOrder(
      { orderId: 9, customerId: 7, sourceType: 'QUOTATION', sourceNo: 'Q-1', contactPerson: '张三', contactPhone: '1' },
      [productRow()],
    )
    await flushPromises()
    expect(w.findComponent(CustomerSelector).props('disabled')).toBe(true)

    w.vm.fillFromOrder(
      { orderId: 9, customerId: 7, sourceType: 'SALES_ORDER', sourceNo: 'SO-1', contactPerson: '张三', contactPhone: '1' },
      [productRow()],
    )
    await flushPromises()
    expect(w.findComponent(CustomerSelector).props('disabled')).toBe(false)

    w.findComponent(CustomerSelector).vm.$emit('change', 8, customer({ customerId: 8, contactPerson: '李四' }))
    await flushPromises()
    expect((fieldInput(w, '来源单号')!.element as HTMLInputElement).value).toBe('')
    w.unmount()
  })
})

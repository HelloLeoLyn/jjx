import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, shallowMount, flushPromises } from '@vue/test-utils'
import { reactive } from 'vue'
import { writeFileSync } from 'node:fs'
import ElementPlus from 'element-plus'
import Form from '../PaymentForm.vue'
import Print from '../PaymentPrint.vue'
import OrderDialog from '../../order/components/OrderPaymentDialog.vue'
import Page from '../../payment/index.vue'
import { PaymentStatusEnum, PaymentApprovalStatus } from '@/enums/purchase/payment'

const api = vi.hoisted(() => ({
  summary: vi.fn(),
  number: vi.fn(),
  add: vi.fn(),
  update: vi.fn(),
  list: vi.fn(),
  statistics: vi.fn(),
  pending: vi.fn(),
  detail: vi.fn(),
  vouchers: vi.fn(),
}))
vi.mock('@/api/purchase/payment', () => ({
  getOrderPaymentSummary: api.summary,
  generatePaymentNo: api.number,
  addPayment: api.add,
  updatePayment: api.update,
  listPayment: api.list,
  getPaymentStatistics: api.statistics,
  getPendingPaymentOrders: api.pending,
  getPayment: api.detail,
  downloadPaymentVouchers: api.vouchers,
}))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ hasPermission: () => true, userInfo: { realName: '审批人' } }),
}))
vi.mock('@/components/attachment/AttachmentCenter.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/purchase/order/components/OrderDetailDialog.vue', () => ({
  default: { template: '<div />' },
}))
const wrappers: any[] = []
function track(w: any) {
  wrappers.push(w)
  return w
}
const state = (w: any) => w.vm.$.setupState
const summary = (id = 1) => ({
  orderId: id,
  orderNo: `PO${id}`,
  supplierName: '供应商',
  orderTotalAmount: 1000,
  paidAmount: 100,
  pendingAmount: 200,
  availableAmount: 700,
  currency: 'CNY',
})
const form = () =>
  reactive({
    orderId: 1,
    paymentNo: 'PAY1',
    paymentDate: '2026-11-18',
    paymentAmount: 100,
    paymentMethod: 'bank',
    bankAccount: '',
    remark: '',
  })
beforeEach(() => {
  vi.clearAllMocks()
  api.summary.mockResolvedValue({ data: summary() })
  api.number.mockResolvedValue({ data: 'PAY1' })
  api.add.mockResolvedValue({})
  api.update.mockResolvedValue({})
  api.pending.mockResolvedValue({ data: [{ orderId: 1, orderNo: 'PO1', supplierName: '供应商' }] })
  api.list.mockResolvedValue({ data: { records: [], total: 0 } })
  api.statistics.mockResolvedValue({
    data: { monthDueAmount: 100, monthPaidAmount: 80, overdueAmount: 20 },
  })
})
afterEach(() => wrappers.splice(0).forEach((w) => w.unmount()))
describe('采购付款P2', () => {
  it('共享表单真实校验金额上限、空额度、非数字与备注长度', async () => {
    const model = form()
    const w = track(
      mount(Form, {
        props: { modelValue: model },
        global: { plugins: [ElementPlus], stubs: { ElInputNumber: { template: '<input />' } } },
      })
    )
    await flushPromises()
    expect(await w.vm.validate()).toBe(true)
    for (const amount of [0, 701, NaN]) {
      model.paymentAmount = amount
      await flushPromises()
      expect(await w.vm.validate()).toBe(false)
    }
    model.paymentAmount = 100
    model.remark = '字'.repeat(501)
    await flushPromises()
    expect(await w.vm.validate()).toBe(false)
    model.remark = ''
    model.bankAccount = '1'.repeat(101)
    await flushPromises()
    expect(await w.vm.validate()).toBe(false)
    model.bankAccount = ''
    api.summary.mockRejectedValueOnce(new Error('offline'))
    model.orderId = 2
    await flushPromises()
    expect(await w.vm.validate()).toBe(false)
    await w.setProps({ disabled: true })
    expect(await w.vm.validate()).toBe(false)
  })
  it('编辑额度排除本单；切换订单时旧额度立即失效且迟到响应不覆盖新订单', async () => {
    let resolveOld!: (v: any) => void
    api.summary
      .mockReturnValueOnce(
        new Promise((r) => {
          resolveOld = r
        })
      )
      .mockResolvedValueOnce({ data: summary(2) })
    const model = form()
    const w = track(
      mount(Form, {
        props: { modelValue: model, editing: true },
        global: { plugins: [ElementPlus] },
      })
    )
    model.orderId = 2
    await flushPromises()
    resolveOld({ data: { ...summary(1), availableAmount: 99999 } })
    await flushPromises()
    expect(w.emitted('summary-loaded')?.at(-1)).toEqual([summary(2)])
    expect(api.summary).toHaveBeenLastCalledWith(2, undefined)
    model.paymentId = 42
    await flushPromises()
    expect(api.summary).toHaveBeenLastCalledWith(2, 42)
  })
  it('订单申请与付款列表新增/编辑都使用共享表单并保留各自保存接口', async () => {
    const dialog = { template: '<div><slot /><slot name="footer" /></div>' }
    const order = track(
      shallowMount(OrderDialog, {
        props: {
          visible: true,
          orderId: 1,
          orderNo: 'PO1',
          orderTotalAmount: 1000,
          paidAmount: 100,
          currency: 'CNY',
        },
        global: { stubs: { ElDialog: dialog } },
      })
    )
    await flushPromises()
    expect(order.findComponent(Form).exists()).toBe(true)
    const os = state(order)
    os.handleSummaryLoaded(summary())
    os.formRef = { validate: async () => true, resetFields: () => {} }
    await os.handleSubmit()
    expect(api.add).toHaveBeenCalledWith(
      expect.objectContaining({ orderId: 1, paymentNo: 'PAY1', paymentAmount: 700 })
    )
    const page = track(
      shallowMount(Page, {
        global: { directives: { hasPermi: () => {} }, stubs: { ElDialog: dialog } },
      })
    )
    await flushPromises()
    const ps = state(page)
    await ps.openCreate()
    await flushPromises()
    expect(page.findComponent(Form).exists()).toBe(true)
    ps.handleSummaryLoaded(summary())
    ps.formRef = { validate: async () => true }
    await ps.submitForm()
    expect(api.add).toHaveBeenCalledTimes(2)
    api.detail.mockResolvedValue({ data: { ...form(), paymentId: 9 } })
    await ps.openEdit({ paymentId: 9 })
    await flushPromises()
    ps.handleSummaryLoaded(summary())
    ps.formRef = { validate: async () => true }
    await ps.submitForm()
    expect(api.update).toHaveBeenCalledWith(
      expect.objectContaining({ paymentId: 9, paymentAmount: 100 })
    )
  })
  it('统计传递已应用筛选，忽略分页；迟到列表和统计不能覆盖新查询', async () => {
    const page = track(shallowMount(Page, { global: { directives: { hasPermi: () => {} } } }))
    await flushPromises()
    const s = state(page)
    s.query.paymentNo = 'PAY'
    s.query.pageNum = 7
    s.query.paymentStatus = PaymentStatusEnum.COMPLETED.value
    let finishOld!: (v: any) => void
    api.statistics.mockReturnValueOnce(
      new Promise((r) => {
        finishOld = r
      })
    )
    const old = s.load()
    s.query.paymentNo = 'NEW'
    await s.load()
    finishOld({ data: { monthDueAmount: 9999 } })
    await old
    expect(api.statistics).toHaveBeenLastCalledWith(
      expect.objectContaining({
        paymentNo: 'NEW',
        paymentStatus: PaymentStatusEnum.COMPLETED.value,
      })
    )
    expect(api.statistics.mock.calls.at(-1)?.[0]).not.toHaveProperty('pageNum')
    expect(s.statistics.monthDueAmount).toBe(100)
    api.statistics.mockRejectedValueOnce(new Error('offline'))
    await s.load()
    expect(s.statistics).toBeNull()
  })
  it('打印预览保留关键字段，转义单据文字，不把未付款申请显示为已付', async () => {
    const w = track(
      mount(Print, {
        props: {
          payment: {
            paymentNo: 'PAY1',
            supplierName: '<script>bad()</script>',
            orderNo: 'PO1',
            paymentAmount: 123.45,
            paymentDate: '2026-11-18',
            actualPaymentDate: '2026-11-19',
            voucherNo: 'BANK001',
            approvalStatus: PaymentApprovalStatus.PENDING,
            paymentStatus: PaymentStatusEnum.PENDING.value,
          },
        },
      })
    )
    for (const text of ['PAY1', 'PO1', '123.45', '2026-11-18', '2026-11-19', 'BANK001', '待审批'])
      expect(w.text()).toContain(text)
    expect(w.find('script').exists()).toBe(false)
    expect(w.html()).toContain('&lt;script&gt;')
    await w.setProps({
      payment: {
        paymentNo: 'PAY202611180001',
        orderNo: 'PO261118001',
        supplierName: '示例供应商',
        paymentAmount: 31110,
        paymentMethod: 'bank',
        paymentDate: '2026-11-18',
        actualPaymentDate: '2026-11-18',
        voucherNo: 'BANK001',
        approvalStatus: PaymentApprovalStatus.APPROVED,
        paymentStatus: PaymentStatusEnum.COMPLETED.value,
        approverName: '示例审批人',
        approvalTime: '2026-11-18 10:00',
        remark: '仅为打印样例；无业务数据写入。',
      },
    })
    w.vm.print()
    const frame = document.querySelector('iframe')!
    expect(frame.srcdoc).toContain('@page{size:A4')
    expect(frame.srcdoc).toContain('BANK001')
    expect(frame.srcdoc).not.toContain('<script>bad()')
    frame.onload = null
    if (process.env.JJX_PAYMENT_PRINT_PREVIEW)
      writeFileSync(process.env.JJX_PAYMENT_PRINT_PREVIEW, frame.srcdoc)
  })
})

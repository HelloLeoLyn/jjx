import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import Page from '../index.vue'
import { PaymentApprovalStatus, PaymentStatusEnum, PaymentStageEnum, paymentStageOf } from '@/enums/purchase/payment'
const api = vi.hoisted(() => Object.fromEntries(['listPayment','getPayment','addPayment','updatePayment','delPayment','approvePayment','confirmPayment','getPendingPaymentOrders','generatePaymentNo','batchApprove','exportPayment'].map((name) => [name, vi.fn()])) as Record<string, ReturnType<typeof vi.fn>>)
const messages = vi.hoisted(() => ({ error: vi.fn(), warning: vi.fn(), success: vi.fn(), confirm: vi.fn() }))
const auth = vi.hoisted(() => ({ approve: true, export: true }))
vi.mock('@/api/purchase/payment', () => api)
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ userName: '审批员', userInfo: { realName: '当前审批员' }, hasPermission: (permission: string) => permission === 'purchase:payment:approve' ? auth.approve : permission === 'purchase:payment:export' ? auth.export : true }) }))
vi.mock('element-plus', async (original) => ({ ...(await original<typeof import('element-plus')>()), ElMessage: messages, ElMessageBox: { confirm: messages.confirm } }))
vi.mock('@/components/attachment/AttachmentCenter.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/common-ui/TableActionColumn/index.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/purchase/components/PaymentAmountSummary.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/purchase/order/components/OrderDetailDialog.vue', () => ({ default: { template: '<div />' } }))
const wrappers: ReturnType<typeof mount>[] = []
const pending = { paymentId: 1, paymentNo: 'PAY-1', orderNo: 'PO-1', supplierName: '供应商甲', paymentDate: '2000-01-01', paymentAmount: 100, paymentStatus: PaymentStatusEnum.PENDING.value, approvalStatus: PaymentApprovalStatus.PENDING }
async function render() {
  const wrapper = mount(Page, { global: { directives: { loading: () => {}, hasPermi: () => {} }, stubs: {
    ElCard: { template: '<div><slot /></div>' }, ElTable: { template: '<div />' },
    ElDialog: { template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>', props: ['modelValue'] },
    ElForm: { template: '<div><slot /></div>', methods: { clearValidate() {}, validate: () => Promise.resolve(false) } }, ElFormItem: { template: '<div><slot /></div>' },
    ElTabs: { template: '<div><slot /></div>' }, ElTabPane: true, ElSelect: { template: '<div />' },
    ElInput: true, ElDatePicker: true, ElPagination: true,
  } } })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, state: (wrapper.vm as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  auth.approve = true
  auth.export = true
  api.listPayment.mockResolvedValue({ data: { records: [pending], total: 1 } })
  api.getPendingPaymentOrders.mockResolvedValue({ data: [] })
  api.getPayment.mockResolvedValue({ data: pending })
  messages.confirm.mockResolvedValue('confirm')
  api.approvePayment.mockResolvedValue({})
  api.batchApprove.mockResolvedValue({})
})

describe('付款P1审批与导出', () => {
  it('单笔驳回空意见不发请求，填意见后保留摘要并提交', async () => {
    const { state } = await render()
    state.openApprove(pending)
    expect(state.approveTargets).toEqual([pending])
    await state.submitApprove(PaymentApprovalStatus.REJECTED)
    expect(api.approvePayment).not.toHaveBeenCalled()
    expect(messages.warning).toHaveBeenCalledWith('驳回意见不能为空')
    state.approveForm.approvalComment = '  金额依据不全  '
    await state.submitApprove(PaymentApprovalStatus.REJECTED)
    expect(api.approvePayment).toHaveBeenCalledWith(1, PaymentApprovalStatus.REJECTED, '金额依据不全')
    expect(state.approveVisible).toBe(false)
  })
  it('批量使用统一决定/意见，快速连点只发一次；成功后刷新并清选择', async () => {
    const { state } = await render()
    state.selectedPayments = [pending, { ...pending, paymentId: 2, paymentNo: 'PAY-2' }]
    state.openBatchApprove()
    expect(state.approveTargets).toHaveLength(2)
    state.approveForm.approvalComment = '统一核准'
    let finish!: (value: any) => void
    api.batchApprove.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    const first = state.submitApprove(PaymentApprovalStatus.APPROVED)
    await state.submitApprove(PaymentApprovalStatus.APPROVED)
    expect(api.batchApprove).toHaveBeenCalledTimes(1)
    expect(api.batchApprove).toHaveBeenCalledWith([1, 2].map((paymentId) => ({ paymentId, approvalStatus: PaymentApprovalStatus.APPROVED, approvalComment: '统一核准' })))
    expect(api.approvePayment).not.toHaveBeenCalled()
    finish({})
    await first
    expect(state.selectedPayments).toEqual([])
  })
  it('批量失败保留弹窗和意见，不显示成功', async () => {
    const { state } = await render()
    state.selectedPayments = [pending]
    state.openBatchApprove()
    state.approveForm.approvalComment = '仍待核对'
    api.batchApprove.mockRejectedValueOnce(new Error('其中一单已审批，整批回滚'))
    await state.submitApprove(PaymentApprovalStatus.APPROVED)
    expect(state.approveVisible).toBe(true)
    expect(state.approveForm.approvalComment).toBe('仍待核对')
    expect(messages.success).not.toHaveBeenCalled()
    expect(messages.error).toHaveBeenCalledWith('其中一单已审批，整批回滚')
  })
  it('混入非待审批付款或缺权限不发批量请求；刷新清旧选择', async () => {
    const { state } = await render()
    state.selectedPayments = [{ ...pending, approvalStatus: PaymentApprovalStatus.APPROVED }]
    state.openBatchApprove()
    expect(state.approveVisible).toBe(false)
    auth.approve = false
    state.selectedPayments = [pending]
    state.openBatchApprove()
    expect(state.approveVisible).toBe(false)
    await state.load()
    expect(state.selectedPayments).toEqual([])
    expect(api.batchApprove).not.toHaveBeenCalled()
  })
  it('阶段优先展示已驳回，保留部分付款与未知状态，不出现待付款+已驳回', () => {
    expect(paymentStageOf({ ...pending, approvalStatus: PaymentApprovalStatus.REJECTED })).toBe(PaymentStageEnum.REJECTED.value)
    expect(paymentStageOf(pending)).toBe(PaymentStageEnum.PENDING_APPROVAL.value)
    expect(paymentStageOf({ ...pending, approvalStatus: PaymentApprovalStatus.APPROVED })).toBe(PaymentStageEnum.WAITING_PAYMENT.value)
    expect(paymentStageOf({ ...pending, approvalStatus: PaymentApprovalStatus.APPROVED, paymentStatus: PaymentStatusEnum.PARTIALLY_PAID.value })).toBe(PaymentStageEnum.PARTIALLY_PAID.value)
    expect(paymentStageOf({})).toBe(PaymentStageEnum.UNKNOWN.value)
  })
  it('导出发送当前全部筛选且不分页，生成下载并释放URL', async () => {
    const { state } = await render()
    state.query.orderNo = 'PO-9'
    state.query.paymentMethod = 'bank'
    state.query.approvalStatus = PaymentApprovalStatus.REJECTED
    const blob = new Blob(['xlsx'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    api.exportPayment.mockResolvedValueOnce(blob)
    const create = vi.fn().mockReturnValue('blob:payment')
    const revoke = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: create })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revoke })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    try {
      await state.handleExport()
      expect(api.exportPayment).toHaveBeenCalledWith(expect.objectContaining({ orderNo: 'PO-9', paymentMethod: 'bank', approvalStatus: PaymentApprovalStatus.REJECTED }))
      expect(api.exportPayment.mock.calls[0][0]).not.toHaveProperty('pageNum')
      expect(api.exportPayment.mock.calls[0][0]).not.toHaveProperty('pageSize')
      expect(click).toHaveBeenCalledOnce()
      expect(revoke).toHaveBeenCalledWith('blob:payment')
    } finally { click.mockRestore() }
  })
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
describe('付款P0依赖行为复核', () => {
  it('待付款页签只查已批准未付款，计划日期/订单筛选仍随请求传递', async () => {
    const { state } = await render()
    state.query.orderNo = 'PO-1'
    state.handleTabChange('pending-payment')
    await flushPromises()
    expect(api.listPayment).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 1, orderNo: 'PO-1', approvalStatus: PaymentApprovalStatus.APPROVED, paymentStatus: PaymentStatusEnum.PENDING.value }))
  })
  it('逾期排除已付款和已驳回；本页金额合计保留', async () => {
    const { state } = await render()
    expect(state.isOverdue(pending)).toBe(true)
    expect(state.isOverdue({ ...pending, paymentStatus: PaymentStatusEnum.COMPLETED.value })).toBe(false)
    expect(state.isOverdue({ ...pending, approvalStatus: PaymentApprovalStatus.REJECTED })).toBe(false)
    expect(state.summaryMethod({ columns: [{}, {}, {}, {}, { property: 'paymentAmount' }] })[4]).toBe('100.00')
  })
  it('详情承接订单号和供应商摘要', async () => {
    const { state } = await render()
    await state.detail(pending)
    expect(state.current.orderNo).toBe('PO-1')
    expect(state.current.supplierName).toBe('供应商甲')
  })
  it('确认付款缺必填信息或取消二次确认均不调用付款接口', async () => {
    const { state } = await render()
    state.openConfirm(pending)
    state.confirmFormRef = { validate: vi.fn().mockResolvedValue(false), clearValidate: vi.fn() }
    await state.submitConfirm()
    expect(api.confirmPayment).not.toHaveBeenCalled()
    state.confirmFormRef = { validate: vi.fn().mockResolvedValue(true), clearValidate: vi.fn() }
    messages.confirm.mockRejectedValueOnce('cancel')
    await state.submitConfirm()
    expect(messages.confirm).toHaveBeenCalledTimes(1)
    expect(api.confirmPayment).not.toHaveBeenCalled()
  })
})

import { createRequire } from 'node:module'
const req = createRequire(process.cwd() + '/package.json')
const Schema = req(req.resolve('async-validator', { paths: [req.resolve('element-plus')] })).default
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/radio/style/css', () => ({}))
vi.mock('element-plus/es/components/radio-group/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
import { beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import { readFileSync } from 'node:fs'
import ts from 'typescript'
import { ElRadioGroup, ElInput } from 'element-plus'
import ReviewDialog from '../ReviewDialog.vue'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'
import { orderStatusApi } from '@/api/sales/orderStatus'
import { SalesOrderStatusEnum } from '@/enums/sales/OrderEnum'
vi.mock('@/api/sales/orderStatus', () => ({
  orderStatusApi: {
    approveOrder: vi.fn(),
    rejectOrder: vi.fn(),
    startReview: vi.fn(),
  },
}))
vi.mock('@/api/sales/order', () => ({
  orderApi: {
    getOrder: vi.fn(async () => ({
      data: {
        orderId: 7,
        orderNo: 'SO7',
        customerName: '客户',
        totalAmount: 1234,
      },
    })),
  },
}))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ userInfo: { userName: '审核员' } }),
}))
vi.mock('@/components/attachment/AttachmentCenter.vue', () => ({
  default: {
    props: ['sourceId'],
    emits: ['bound', 'unbound'],
    data: () => ({ uploading: false, pendingCount: 0 }),
    template: '<div class="attachments" />',
  },
}))
const slot = defineComponent({
  setup(_, { slots }) {
    return () => h('div', [slots.default?.(), slots.footer?.()])
  },
})
const button = defineComponent({
  props: ['disabled', 'loading'],
  setup(p, { slots }) {
    return () => h('button', { disabled: p.disabled || p.loading }, slots.default?.())
  },
})
// 表单容器隔离 Element Plus 外部模块的 CJS 加载；用真实 async-validator 执行业务规则。
const form = defineComponent({
  props: ['model', 'rules'],
  setup(p, { slots, expose }) {
    const error = ref('')
    expose({
      async validate() {
        try {
          await new Schema(p.rules).validate(p.model, { suppressWarning: true })
          return true
        } catch (e: any) {
          error.value = e.errors[0].message
          throw e
        }
      },
      clearValidate() {
        error.value = ''
      },
      resetFields() {
        error.value = ''
      },
    })
    return () => h('form', [slots.default?.(), h('p', error.value)])
  },
})
const formItem = defineComponent({
  props: ['label'],
  setup(p, { slots }) {
    return () => h('label', [p.label, slots.default?.()])
  },
})
async function render() {
  const w = mount(ReviewDialog, {
    props: { modelValue: false, orderId: 7 },
    global: {
      stubs: {
        ElDialog: slot,
        ElButton: button,
        ElDivider: slot,
        ElForm: form,
        ElFormItem: formItem,
      },
    },
  })
  await w.setProps({ modelValue: true })
  await flushPromises()
  return w
}
const submit = (w: Awaited<ReturnType<typeof render>>) =>
  w.findAll('button').find((b) => b.text() === '提交审核结果')!
const choose = async (w: Awaited<ReturnType<typeof render>>, result: string) => {
  w.getComponent(ElRadioGroup).vm.$emit('update:modelValue', result)
  await flushPromises()
}
beforeEach(() => vi.clearAllMocks())
it('不预选，展示订单及审核人，关闭重开仍需选择', async () => {
  const w = await render()
  expect(w.getComponent(ElRadioGroup).props('modelValue')).toBeUndefined()
  expect(submit(w).attributes('disabled')).toBeDefined()
  expect(w.findAllComponents(ElInput).map((x) => x.props('modelValue'))).toEqual(
    expect.arrayContaining(['SO7', '客户', '1,234.00', '审核员'])
  )
  await choose(w, 'approve')
  await w
    .findAll('button')
    .find((b) => b.text() === '取消')!
    .trigger('click')
  await w.setProps({ modelValue: false })
  await w.setProps({ modelValue: true })
  expect(w.getComponent(ElRadioGroup).props('modelValue')).toBeUndefined()
  w.unmount()
})
it('驳回必填原因，切换通过后可选填；附件引用及成功回调保留', async () => {
  const w = await render()
  await choose(w, 'reject')
  expect(w.text()).toContain('驳回原因')
  await submit(w).trigger('click')
  await flushPromises()
  expect(orderStatusApi.rejectOrder).not.toHaveBeenCalled()
  expect(w.text()).toContain('请填写驳回原因')
  await choose(w, 'approve')
  expect(w.text()).toContain('审核意见')
  expect(w.text()).not.toContain('请填写驳回原因')
  const center = w.getComponent(AttachmentCenter)
  expect(center.props('sourceId')).toBe(7)
  center.vm.$emit('bound', { refId: 11 })
  center.vm.$emit('bound', { refId: 12 })
  center.vm.$emit('unbound', { refId: 11 })
  await submit(w).trigger('click')
  await flushPromises()
  expect(orderStatusApi.approveOrder).toHaveBeenCalledWith(7, '', [12])
  expect(w.emitted('success')).toHaveLength(1)
  w.unmount()
})
it('驳回提交原因，提交中禁用并防止重复调用', async () => {
  let resolve!: () => void
  vi.mocked(orderStatusApi.rejectOrder).mockImplementationOnce(
    () =>
      new Promise<void>((r) => {
        resolve = r
      }) as any
  )
  const w = await render()
  await choose(w, 'reject')
  await w.get('textarea').setValue('交期需调整')
  await submit(w).trigger('click')
  await flushPromises()
  await submit(w).trigger('click')
  expect(orderStatusApi.rejectOrder).toHaveBeenCalledExactlyOnceWith(7, '交期需调整', [])
  expect(submit(w).attributes('disabled')).toBeDefined()
  resolve()
  await flushPromises()
  w.unmount()
})
it.each(['uploading', 'pendingCount'])('附件 %s 未完成时不提交，完成后可重试', async (field) => {
  const w = await render()
  await choose(w, 'approve')
  const center = w.getComponent(AttachmentCenter)
  await center.setData({ [field]: field === 'uploading' ? true : 1 })
  await submit(w).trigger('click')
  await flushPromises()
  expect(orderStatusApi.approveOrder).not.toHaveBeenCalled()
  expect(submit(w).attributes('disabled')).toBeUndefined()
  await center.setData({ uploading: false, pendingCount: 0 })
  await submit(w).trigger('click')
  await flushPromises()
  expect(orderStatusApi.approveOrder).toHaveBeenCalledOnce()
  w.unmount()
})
// 执行列表真实动作定义及处理函数，隔离无关弹窗。
const source = readFileSync('src/views/sales/order/index.vue', 'utf8')
function listHarness() {
  const actions =
    source.slice(source.indexOf('const statusIs'), source.indexOf('const orderRowActions')) +
    source
      .slice(source.indexOf('const orderRowActions'), source.indexOf("    key: 'sample',"))
      .replace(/  \{\s*$/, '') +
    ']\n'
  const handlers = source.slice(
    source.indexOf('const startingReviewIds'),
    source.indexOf('// 重新提交审核')
  )
  const body = ts.transpile(
    actions + handlers + '\nreturn {orderRowActions, handleStartReview, handleReview}',
    { target: ts.ScriptTarget.ES2022 }
  )
  const visible = ref(false),
    orderId = ref(0),
    getList = vi.fn()
  const api = { startReview: vi.fn(async () => {}) },
    confirm = vi.fn(async () => {})
  const result = new Function(
    'ref',
    'SalesOrderStatusEnum',
    'reviewDialogVisible',
    'currentOrderId',
    'getList',
    'orderStatusApi',
    'ElMessageBox',
    body
  )(ref, SalesOrderStatusEnum, visible, orderId, getList, api, { confirm })
  return { ...result, visible, orderId, getList, api }
}
it('审核中单一审核入口和待审核开始审核，权限各自保留', () => {
  const h = listHarness()
  const review = h.orderRowActions.filter((a: any) => a.permission === 'sales:order:approve')
  expect(review.map((a: any) => a.label)).toEqual(['审核'])
  const start = h.orderRowActions.find((a: any) => a.key === 'startReview')
  expect(start.permission).toBe('sales:order:review')
  for (const status of SalesOrderStatusEnum.items) {
    const row = { orderStatus: status.value }
    expect(review[0].visible({ row })).toBe(status.value === SalesOrderStatusEnum.REVIEWING.value)
    expect(start.visible({ row })).toBe(status.value === SalesOrderStatusEnum.PENDING_REVIEW.value)
  }
  h.handleReview({ orderId: 7 })
  expect(h.visible.value).toBe(true)
  expect(h.orderId.value).toBe(7)
})
it('开始审核调用接口刷新列表，不打开弹窗；重复点击只请求一次', async () => {
  const h = listHarness()
  await Promise.all([h.handleStartReview({ orderId: 7 }), h.handleStartReview({ orderId: 7 })])
  expect(h.api.startReview).toHaveBeenCalledExactlyOnceWith(7)
  expect(h.getList).toHaveBeenCalledOnce()
  expect(h.visible.value).toBe(false)
  expect(h.orderId.value).toBe(0)
})

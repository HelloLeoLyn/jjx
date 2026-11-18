import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import ReceiveDialog from '../PurchaseReceiveDialog.vue'
import ProcessingReceive from '../PurchaseProcessingReceive.vue'

const api = vi.hoisted(() => ({
  items: vi.fn(),
  history: vi.fn(),
  preview: vi.fn(),
  confirm: vi.fn(),
  documents: vi.fn(),
}))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ hasPermission: () => true }) }))
vi.mock('@/api/purchase/receipt', () => ({
  getReceiptsByOrder: api.items,
  confirmBatchReceive: api.confirm,
  getReceiptDocRefs: api.documents,
}))
vi.mock('@/api/purchase/orderEntry', async (original) => ({
  ...(await original<typeof import('@/api/purchase/orderEntry')>()),
  getProcessingHistory: api.history,
  previewProcessingReceipt: api.preview,
}))
// 公共附件内核不属于本卡；只提供父弹窗所依赖的草稿、上传中和资料类型接口。
vi.mock('@/components/attachment/AttachmentCenter.vue', async () => {
  const { defineComponent, ref } = await import('vue')
  return {
    default: defineComponent({
      name: 'AttachmentCenter',
      setup(_props, { expose }) {
        const pendingEntries = ref<{ attachmentId: number; fileName: string }[]>([])
        const uploading = ref(false),
          missing = ref(0)
        expose({
          pendingEntries,
          uploading,
          missing,
          getMissingTypeCount: () => missing.value,
          setPending: (value: typeof pendingEntries.value) => {
            pendingEntries.value = value
          },
          setUploading: (value: boolean) => {
            uploading.value = value
          },
          setMissing: (value: number) => {
            missing.value = value
          },
          clearPending: () => {
            pendingEntries.value = []
          },
        })
        return () => null
      },
    }),
  }
})

const plan = JSON.stringify({
  groups: [
    {
      groupId: 'g1',
      name: '母卷1',
      motherRolls: 1,
      slits: [
        { widthMm: 285, rolls: 2 },
        { widthMm: 335, rolls: 1 },
      ],
      outputs: [
        {
          outputId: 'p1',
          materialId: 501,
          materialCode: 'CUT-285',
          materialName: '切片',
          unit: 'PCS',
          widthMm: 285,
          lengthMm: 315,
          quantity: 985,
        },
        {
          outputId: 'r1',
          materialId: 502,
          materialCode: 'ROLL-335',
          materialName: '余卷',
          unit: 'ROLL',
          widthMm: 335,
          rollLengthM: 100,
          quantity: 1,
        },
      ],
    },
  ],
})
const source = {
  itemId: 99,
  materialCode: 'MOTHER',
  materialName: '母卷',
  unit: '㎡',
  quantity: 366,
  receivedQuantity: 0,
  rollCount: 3,
  rollWidthMm: 1220,
  rollLengthM: 100,
  processingPlan: plan,
}
const wrappers: VueWrapper[] = []
const button = (w: VueWrapper, text: string) => {
  const result = w.findAll('button').find((b) => b.text().startsWith(text))
  if (!result) throw new Error(`缺少按钮：${text}`)
  return result
}
async function open() {
  const w = mount(ReceiveDialog, {
    props: { visible: false, orderId: 77, orderNo: 'PO77' },
    global: {
      plugins: [ElementPlus],
      stubs: { ElDialog: { template: '<section><slot /><slot name="footer" /></section>' } },
    },
  })
  wrappers.push(w)
  await w.setProps({ visible: true })
  await flushPromises()
  await button(w, '登记交货').trigger('click')
  await flushPromises()
  expect(w.findComponent(ProcessingReceive).exists()).toBe(true)
  return w
}
async function enter(w: VueWrapper, index: number, value: string) {
  const inputs = w.findComponent(ProcessingReceive).findAll('input[aria-label="实际交付片数卷数"]')
  await inputs[index].setValue(value)
  await inputs[index].trigger('blur')
  await flushPromises()
}
const attachment = (w: VueWrapper) => w.findComponent({ name: 'AttachmentCenter' }).vm as any
beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
  api.items.mockResolvedValue({ data: [{ ...source }] })
  api.history.mockResolvedValue({ data: { processingPlan: plan, receipts: [] } })
  api.preview.mockResolvedValue({
    data: {
      receivedQuantity: 44.8875,
      items: [{ materialCode: 'CUT-285', quantity: 500, unit: 'PCS', amount: 3376.82 }],
    },
  })
  api.documents.mockResolvedValue({
    data: [{ refId: 5, attachmentId: 900, fileName: '历史.png', inboundNo: 'IN-OLD' }],
  })
  api.confirm.mockResolvedValue({ data: { inboundNo: 'IN-NEW' } })
})
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount())
  vi.restoreAllMocks()
  document.body.innerHTML = ''
})

describe('共享收货弹窗与加工子组件集成（API替身，不写业务库）', () => {
  it('母卷计划不冒充实收；填满操作不默认填写加工切片或余卷', async () => {
    const w = await open()
    await button(w, '全部填满').trigger('click')
    await flushPromises()
    const child = w.findComponent(ProcessingReceive)
    expect(
      child
        .findAll('input[aria-label="实际交付片数卷数"]')
        .map((i) => (i.element as HTMLInputElement).value)
    ).toEqual(['0', '0'])
    expect((child.find('input[aria-label="余卷实际长度"]').element as HTMLInputElement).value).toBe(
      ''
    )
    expect(button(w, '确认收货').attributes('disabled')).toBeDefined()
    expect(api.preview).not.toHaveBeenCalled()
    expect(api.confirm).not.toHaveBeenCalled()
  })
  it('只收500片：父弹窗采用后端折算量、只提交本次交付和本次票据，不提交母卷或税费公式', async () => {
    const w = await open()
    await enter(w, 0, '500')
    attachment(w).setPending([{ attachmentId: 100, fileName: '本次.png' }])
    await flushPromises()
    await button(w, '确认收货').trigger('click')
    await flushPromises()
    expect(api.preview).toHaveBeenCalledWith(99, [
      expect.objectContaining({
        groupId: 'g1',
        outputs: [{ outputId: 'p1', quantity: 500, rollLengthM: undefined }],
      }),
    ])
    const [id, rows, evidence] = api.confirm.mock.calls[0]
    expect(id).toBe(77)
    expect(rows).toEqual([
      { itemId: 99, receivedQuantity: 44.8875, processingGroups: expect.any(Array) },
    ])
    expect(rows[0].processingGroups[0].outputs).toHaveLength(1)
    expect(evidence).toEqual({ receiptRequestKey: expect.any(String), attachmentIds: [100] })
    expect(evidence.attachmentIds).not.toContain(900)
    expect(w.emitted('success')).toEqual([[{ inboundNo: 'IN-NEW' }]])
    await button(w, '去来料检验').trigger('click')
    expect(w.emitted('goto-iqc')).toEqual([['IN-NEW']])
  })
  it('余卷可单独实收；实际长度与卷数从子组件传递，不顺带收切片', async () => {
    api.preview.mockResolvedValue({
      data: {
        receivedQuantity: 3.35,
        items: [{ materialCode: 'ROLL-335', quantity: 3.35, unit: '㎡' }],
      },
    })
    const w = await open()
    await enter(w, 1, '1')
    const length = w.findComponent(ProcessingReceive).find('input[aria-label="余卷实际长度"]')
    await length.setValue('10')
    await length.trigger('blur')
    await flushPromises()
    await button(w, '确认收货').trigger('click')
    await flushPromises()
    expect(api.confirm.mock.calls[0][1]).toEqual([
      {
        itemId: 99,
        receivedQuantity: 3.35,
        processingGroups: [
          expect.objectContaining({ outputs: [{ outputId: 'r1', quantity: 1, rollLengthM: 10 }] }),
        ],
      },
    ])
  })
  it('加工校核失败保留实收值，并阻止父弹窗确认；修正后可重试', async () => {
    const w = await open()
    await enter(w, 0, '500')
    api.preview.mockRejectedValueOnce(new Error('超过未收数量'))
    await button(w, '确认收货').trigger('click')
    await flushPromises()
    expect(api.confirm).not.toHaveBeenCalled()
    expect(w.findComponent(ProcessingReceive).text()).toContain('超过未收数量')
    expect(
      (
        w.findComponent(ProcessingReceive).find('input[aria-label="实际交付片数卷数"]')
          .element as HTMLInputElement
      ).value
    ).toBe('500')
    await button(w, '确认收货').trigger('click')
    await flushPromises()
    expect(api.confirm).toHaveBeenCalledTimes(1)
  })
  it('服务失败重试沿用原请求键和附件；成功后再次收货使用新草稿', async () => {
    const w = await open()
    await enter(w, 0, '500')
    attachment(w).setPending([{ attachmentId: 100, fileName: '本次.png' }])
    api.confirm.mockRejectedValueOnce(new Error('收货保存失败'))
    await button(w, '确认收货').trigger('click')
    await flushPromises()
    const first = structuredClone(api.confirm.mock.calls[0][2])
    expect(attachment(w).pendingEntries).toHaveLength(1)
    await button(w, '确认收货').trigger('click')
    await flushPromises()
    expect(api.confirm.mock.calls[1][2]).toEqual(first)
    // 成功后只读重载，展开的新子组件不沿用上次实收值。
    await button(w, '登记交货').trigger('click')
    await flushPromises()
    await enter(w, 0, '1')
    await button(w, '确认收货').trigger('click')
    await flushPromises()
    expect(api.confirm.mock.calls[2][2].receiptRequestKey).not.toBe(first.receiptRequestKey)
    expect(api.confirm.mock.calls[2][2].attachmentIds).toEqual([])
  })
  it('缺少票据资料类型或上传中不允许提交', async () => {
    const w = await open()
    await enter(w, 0, '500')
    attachment(w).setMissing(1)
    await button(w, '确认收货').trigger('click')
    await flushPromises()
    expect(api.confirm).not.toHaveBeenCalled()
    attachment(w).setMissing(0)
    attachment(w).setUploading(true)
    await flushPromises()
    expect(button(w, '确认收货').attributes('disabled')).toBeDefined()
    expect(button(w, '关 闭').attributes('disabled')).toBeDefined()
  })
})

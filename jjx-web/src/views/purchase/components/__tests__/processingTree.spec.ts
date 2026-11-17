import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { shallowMount, mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import TreeEditor from '../../order/components/PurchaseProcessingTreeEditor.vue'
import Editor from '../../order/components/PurchaseProcessingEditor.vue'
import Rows from '../../order/components/PurchaseProcessingTreeRows.vue'
import Summary from '../../order/components/PurchaseProcessingSummary.vue'
import Print from '../../order/components/PurchaseItemsPrintTable.vue'
import Receive from '../PurchaseProcessingReceive.vue'
import { PurchaseProcessingOperationEnum as Action } from '@/enums/purchase/processing'
const api = vi.hoisted(() => ({ plan: vi.fn(), history: vi.fn(), receipt: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('@/api/purchase/orderEntry', async (original) => ({
  ...(await original<typeof import('@/api/purchase/orderEntry')>()),
  previewProcessingPlan: api.plan,
  getProcessingHistory: api.history,
  previewProcessingReceipt: api.receipt,
}))
vi.mock('@/api/inventory/material', () => ({
  materialApi: { search: vi.fn().mockResolvedValue({ data: { records: [] } }) },
}))
const original = {
  nodeId: 'root-unused',
  role: '未加工原料',
  unit: 'ROLL',
  widthMm: 1220,
  lengthMm: 100000,
  quantity: 2,
  materialId: 354,
  materialCode: 'RM000350',
  children: [],
}
const slit = {
  nodeId: 'root-s0',
  role: '分切料',
  unit: 'ROLL',
  widthMm: 285,
  lengthMm: 100000,
  quantity: 2,
  children: [],
}
const remainder = {
  nodeId: 'root-width',
  role: '宽度余料',
  unit: 'ROLL',
  widthMm: 650,
  lengthMm: 100000,
  quantity: 1,
  children: [],
}
const initial = {
  nodeId: 'root',
  role: '采购原卷',
  unit: 'ROLL',
  widthMm: 1220,
  lengthMm: 100000,
  quantity: 3,
  materialId: 354,
  materialCode: 'RM000350',
  children: [],
}
const split = {
  ...initial,
  operation: { type: Action.SLIT.value, useQuantity: 1, slits: [{ widthMm: 285, rolls: 2 }] },
  children: [slit, remainder, original],
}
const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v))
const snapshot = (tree: any) =>
  JSON.stringify({
    tree,
    groups: [
      {
        groupId: 'root',
        name: '最终交付',
        motherRolls: 3,
        slits: [],
        outputs: [
          {
            outputId: 'root-s0',
            materialId: 501,
            materialCode: 'CUT-285',
            materialName: '分切料',
            unit: 'ROLL',
            widthMm: 285,
            rollLengthM: 100,
            quantity: 2,
          },
          {
            outputId: 'root-width',
            materialId: 502,
            materialCode: 'REM-650',
            materialName: '余料',
            unit: 'ROLL',
            widthMm: 650,
            rollLengthM: 100,
            quantity: 1,
          },
          {
            outputId: 'root-unused',
            materialId: 354,
            materialCode: 'RM000350',
            materialName: '原卷',
            unit: 'ROLL',
            widthMm: 1220,
            rollLengthM: 100,
            quantity: 2,
          },
        ],
      },
    ],
  })
const item = {
  materialId: 354,
  materialCode: 'RM000350',
  materialName: 'EBA180',
  unit: '㎡',
  purchaseUnit: 'ROLL',
  priceUnit: '㎡',
  purchaseQuantity: 3,
  quantity: 366,
  unitPrice: 85,
  taxRate: 13,
  priceTaxInclusive: true,
  quotedAmount: 31110,
  amount: 27530.97,
  rollWidthMm: 1220,
  rollLengthM: 100,
}
const wrappers: any[] = []
function wrap(component: any, props: any) {
  const w = shallowMount(component, { props })
  wrappers.push(w)
  return { w, s: (w.vm as any).$?.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  api.plan.mockResolvedValue({ data: { ...item, processingPlan: snapshot(initial) } })
  api.history.mockResolvedValue({ data: { processingPlan: snapshot(split), receipts: [] } })
  api.receipt.mockResolvedValue({
    data: {
      receivedQuantity: 244,
      items: [
        { materialCode: 'RM000350', quantity: 122, unit: '㎡' },
        { materialCode: 'RM000350', quantity: 122, unit: '㎡' },
      ],
    },
  })
})
afterEach(() => wrappers.splice(0).forEach((w) => w.unmount()))
describe('加工树及自动余料交互', () => {
  it('打开空方案只向后台请求根节点，不在前端计算余料', async () => {
    const { s } = wrap(TreeEditor, { item })
    await s.open()
    expect(s.tree.quantity).toBe(3)
    expect(s.tree.children).toHaveLength(0)
    expect(api.plan).toHaveBeenCalledWith(
      expect.objectContaining({ materialId: 354, processingPlan: expect.stringContaining('root') })
    )
  })
  it('分切仅发送操作参数并采用服务端生成的650余料及原卷2卷', async () => {
    const { s, w } = wrap(TreeEditor, { item })
    await s.open()
    s.edit(s.tree, Action.SLIT.value)
    s.operation.useQuantity = 1
    s.operation.slits = [{ widthMm: 285, rolls: 2 }]
    api.plan.mockResolvedValueOnce({ data: { ...item, processingPlan: snapshot(split) } })
    await s.applyOperation()
    const sent = JSON.parse(api.plan.mock.calls[1][0].processingPlan)
    expect(sent.tree.operation).toEqual({
      type: Action.SLIT.value,
      useQuantity: 1,
      slits: [{ widthMm: 285, rolls: 2 }],
      pieceWidthMm: 1220,
      rotated: false,
      piecesPerUnit: null,
    })
    expect(sent.tree.children).toHaveLength(0)
    expect(s.tree.children.map((n: any) => [n.widthMm, n.quantity])).toEqual([
      [285, 2],
      [650, 1],
      [1220, 2],
    ])
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })
  it('采用整个后台规范化快照，取消编辑不改原方案', async () => {
    const saved = snapshot(split)
    const { s, w } = wrap(TreeEditor, { item, modelValue: saved })
    api.plan.mockResolvedValue({ data: { ...item, processingPlan: saved } })
    await s.open()
    s.tree.operation.useQuantity = 2
    s.visible = false
    s.invalidate()
    expect(w.emitted('update:modelValue')).toBeUndefined()
    await s.open()
    expect(s.tree.operation.useQuantity).toBe(1)
    await s.adopt()
    expect(w.emitted('update:modelValue')?.[0]).toEqual([saved])
  })
  it('超出宽度时保留操作输入和原树，不采用失败方案', async () => {
    const { s, w } = wrap(TreeEditor, { item })
    await s.open()
    s.edit(s.tree, Action.SLIT.value)
    s.operation.slits = [{ widthMm: 2000, rolls: 2 }]
    api.plan.mockRejectedValueOnce(new Error('分切宽度合计超过来源宽度'))
    await s.applyOperation()
    expect(s.error).toContain('超过来源宽度')
    expect(s.operationVisible).toBe(true)
    expect(s.operation.slits[0].widthMm).toBe(2000)
    expect(s.tree.children).toHaveLength(0)
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })
  it('关闭后迟到校核不得重新写入加工草稿', async () => {
    let done!: (r: any) => void
    api.plan.mockReturnValueOnce(
      new Promise((r) => {
        done = r
      })
    )
    const { s, w } = wrap(TreeEditor, { item })
    const pending = s.open()
    s.visible = false
    s.invalidate()
    done({ data: { processingPlan: snapshot(split) } })
    await pending
    expect(s.tree).toBeUndefined()
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })
  it('切片参数明确使用卷数、尺寸、方向，每卷片数留空交给后台', async () => {
    const { s } = wrap(TreeEditor, { item, modelValue: snapshot(split) })
    api.plan.mockResolvedValue({ data: { ...item, processingPlan: snapshot(split) } })
    await s.open()
    s.edit(s.tree.children[0], Action.CUT.value)
    expect(s.operation.useQuantity).toBe(2)
    s.operation.pieceWidthMm = 285
    s.operation.pieceLengthMm = 315
    s.operation.rotated = false
    await s.applyOperation()
    const request = JSON.parse(
      api.plan.mock.calls[api.plan.mock.calls.length - 1][0].processingPlan
    )
    expect(request.tree.children[0].operation).toMatchObject({
      type: Action.CUT.value,
      useQuantity: 2,
      pieceWidthMm: 285,
      pieceLengthMm: 315,
      piecesPerUnit: null,
    })
  })
  it('树表与打印显示缩进所属关系、原卷2卷和含税主行金额', () => {
    const w = mount(Rows, { props: { tree: split, materialName: 'EBA180' } })
    wrappers.push(w)
    expect(w.findAll('tbody tr')).toHaveLength(4)
    expect(w.text()).toContain('1.1')
    expect(w.text()).toContain('650mm')
    expect(w.text()).toContain('未加工原料')
    const p = mount(Print, { props: { items: [{ ...item, processingPlan: snapshot(split) }] } })
    wrappers.push(p)
    expect(p.text()).toContain('31110.00')
    expect(p.text()).toContain('宽度余料')
  })
  it('新空方案默认加工树，旧分组保持旧编辑方式', () => {
    const n = wrap(Editor, { item })
    expect(n.s.treeMode).toBe(true)
    const legacy = wrap(Editor, { item, modelValue: JSON.stringify({ groups: [] }) })
    expect(legacy.s.treeMode).toBe(false)
    expect(legacy.s.treeValue).toContain('groups')
  })
  it('加工树收货只按后台终端列表初始化，未加工原卷可以单独收', async () => {
    const { s } = wrap(Receive, { itemId: 99 })
    await flushPromises()
    expect(s.inputs[0].outputs.map((o: any) => o.quantity)).toEqual([0, 0, 0])
    s.inputs[0].outputs[2].quantity = 2
    s.inputs[0].outputs[2].rollLengthM = 100
    const result = await s.validate()
    expect(result.receivedQuantity).toBe(244)
    expect(api.receipt).toHaveBeenCalledWith(99, [
      expect.objectContaining({
        outputs: [{ outputId: 'root-unused', quantity: 2, rollLengthM: 100 }],
      }),
    ])
  })
  it('连续收货解锁后数字输入的无障碍禁用标记与真实状态一致', async () => {
    const w = mount(Receive, {
      props: { itemId: 99, disabled: true },
      global: { plugins: [ElementPlus] },
    })
    wrappers.push(w)
    await flushPromises()
    const inputs = () => w.findAll('input[role="spinbutton"]')
    expect(inputs().length).toBeGreaterThan(3)
    for (const input of inputs()) expect(input.attributes('aria-disabled')).toBe('true')
    await w.setProps({ disabled: false })
    await flushPromises()
    for (const input of inputs()) {
      expect((input.element as HTMLInputElement).disabled).toBe(false)
      expect(input.attributes('aria-disabled')).toBe('false')
    }
  })
})

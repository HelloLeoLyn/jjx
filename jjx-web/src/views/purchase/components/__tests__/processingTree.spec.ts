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
import { ElMessageBox } from 'element-plus'
import { processingTreeDisplayRows } from '../../order/components/processingTreeDisplay'
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
function fullTree() {
  const tree: any = clone(split)
  const leaf = (
    id: string,
    role: string,
    unit: string,
    width: number,
    length: number,
    quantity: number
  ) => ({ nodeId: id, role, unit, widthMm: width, lengthMm: length, quantity, children: [] })
  const first = tree.children[0]
  first.operation = {
    type: Action.CUT.value,
    useQuantity: 2,
    pieceWidthMm: 285,
    pieceLengthMm: 315,
  }
  first.children = [
    leaf('root-s0-pieces', '切片', 'PCS', 285, 315, 634),
    leaf('root-s0-tail', '尾料', 'PCS', 285, 145, 2),
  ]
  const width = tree.children[1]
  width.operation = { type: Action.SLIT.value, useQuantity: 1, slits: [{ widthMm: 315, rolls: 1 }] }
  const second = leaf('root-width-s0', '分切料', 'ROLL', 315, 100000, 1) as any
  second.operation = {
    type: Action.CUT.value,
    useQuantity: 1,
    pieceWidthMm: 285,
    pieceLengthMm: 315,
    rotated: true,
  }
  second.children = [
    leaf('root-width-s0-pieces', '切片', 'PCS', 285, 315, 350),
    leaf('root-width-s0-tail', '尾料', 'PCS', 315, 250, 1),
  ]
  width.children = [second, leaf('root-width-width', '宽度余料', 'ROLL', 335, 100000, 1)]
  return tree
}
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
    expect(w.findAll('tbody tr')).toHaveLength(5)
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
  it('同一卷分出的285、315、335同层，634和350片分属各自窄卷，原卷2与加工1并列', () => {
    const tree = fullTree(),
      before = JSON.stringify(tree)
    const rows = processingTreeDisplayRows(tree)
    const at = (id: string) => rows.find((r) => r.key === id)!
    expect(at('root::used')).toMatchObject({
      number: '1.1',
      quantity: 1,
      role: '加工其中1卷',
      group: true,
    })
    expect(at('root-unused')).toMatchObject({ number: '1.2', quantity: 2, depth: 1 })
    expect(
      ['root-s0', 'root-width-s0', 'root-width-width'].map((id) => [
        at(id).number,
        at(id).depth,
        at(id).node.widthMm,
      ])
    ).toEqual([
      ['1.1.1', 2, 285],
      ['1.1.2', 2, 315],
      ['1.1.3', 2, 335],
    ])
    expect(at('root-s0-pieces')).toMatchObject({ number: '1.1.1.1', quantity: 634, depth: 3 })
    expect(at('root-width-s0-pieces')).toMatchObject({ number: '1.1.2.1', quantity: 350, depth: 3 })
    expect(rows.some((r) => r.key === 'root-width')).toBe(false)
    expect(at('root-width-s0-pieces').path).toContain('315mm × 100m')
    expect(at('root-width-s0-pieces').path).not.toContain('650mm')
    expect(at('root::used').slitSources.map((n) => n.nodeId)).toEqual(['root', 'root-width'])
    expect(at('root-width-s0').node).toBe(tree.children[1].children[0])
    expect(JSON.stringify(tree)).toBe(before)
  })
  it('实际表格、预览及打印都收起650过程节点，显示同一母卷分组', () => {
    const tree = fullTree()
    const saved = snapshot(tree)
    for (const [component, props] of [
      [Rows, { tree }],
      [Summary, { modelValue: saved }],
      [Print, { items: [{ ...item, processingPlan: saved }] }],
    ] as const) {
      const w = mount(component as any, { props })
      wrappers.push(w)
      expect(w.text()).toContain('加工其中1卷')
      expect(w.text()).toContain('634片')
      expect(w.text()).toContain('350片')
      expect(w.text()).not.toContain('650mm')
      const strips = ['root-s0', 'root-width-s0', 'root-width-width'].map((id) =>
        w.find(`tr[data-node-id="${id}"]`).findAll('td')[1].attributes('style')
      )
      expect(new Set(strips).size).toBe(1)
    }
  })
  it('尚未加工的650余料保留；全部3卷一起加工不虚构部分加工分组', () => {
    expect(processingTreeDisplayRows(clone(split)).some((r) => r.key === 'root-width')).toBe(true)
    const all = clone(split)
    all.operation.useQuantity = 3
    all.children = all.children.slice(0, 2)
    expect(processingTreeDisplayRows(all).some((r) => r.group)).toBe(false)
  })
  it('余料只加工一部分时保留来源和未用部分，不能直接混入上层母卷', () => {
    const tree = fullTree(),
      width = tree.children[1]
    width.quantity = 2
    width.children.push({
      ...clone(width),
      nodeId: 'root-width-unused',
      operation: null,
      quantity: 1,
      children: [],
    })
    const rows = processingTreeDisplayRows(tree)
    expect(rows.find((r) => r.key === 'root-width')).toMatchObject({
      sourceOnly: true,
      quantity: 2,
    })
    expect(rows.find((r) => r.key === 'root-width::used')).toMatchObject({ quantity: 1, depth: 3 })
    expect(rows.find((r) => r.key === 'root-width-unused')).toMatchObject({ quantity: 1, depth: 3 })
  })
  it('余料用于切片时仍是切片来源，不作为临时分切步骤收起', () => {
    const tree = fullTree()
    tree.children[1].operation = {
      type: Action.CUT.value,
      useQuantity: 1,
      pieceWidthMm: 285,
      pieceLengthMm: 315,
    }
    expect(processingTreeDisplayRows(tree).some((r) => r.key === 'root-width')).toBe(true)
  })
  it('从母卷加工分组切换追加分切步骤，修改仍发往原650节点而不改285或其切片', async () => {
    const tree = fullTree(),
      saved = snapshot(tree)
    api.plan.mockResolvedValue({ data: { ...item, processingPlan: saved } })
    const { s } = wrap(TreeEditor, { item, modelValue: saved })
    await s.open()
    const group = processingTreeDisplayRows(s.tree).find((r) => r.group)!
    s.edit(group.node, Action.SLIT.value, group.sourceLabel, group.slitSources)
    s.selectSlitStep('root-width')
    expect(s.active.widthMm).toBe(650)
    expect(s.activeLabel).toBe('序号1 采购原卷')
    s.operation.slits[0].widthMm = 300
    await s.applyOperation()
    const sent = JSON.parse(api.plan.mock.calls.at(-1)![0].processingPlan).tree
    expect(sent.children[1].operation.slits[0].widthMm).toBe(300)
    expect(sent.operation.slits[0].widthMm).toBe(285)
    expect(sent.children[0]).toEqual(tree.children[0])
    expect(sent.children[2].quantity).toBe(2)
  })
  it('收起的追加分切步骤可以单独清除，不清掉另一条切片分支', async () => {
    const tree = fullTree(),
      saved = snapshot(tree)
    api.plan.mockResolvedValue({ data: { ...item, processingPlan: saved } })
    const { s } = wrap(TreeEditor, { item, modelValue: saved })
    await s.open()
    const group = processingTreeDisplayRows(s.tree).find((r) => r.group)!
    s.edit(group.node, Action.SLIT.value, group.sourceLabel, group.slitSources)
    s.selectSlitStep('root-width')
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    try {
      await s.clearActive()
    } finally {
      confirm.mockRestore()
    }
    const sent = JSON.parse(api.plan.mock.calls.at(-1)![0].processingPlan).tree
    expect(sent.children[1].operation).toBeNull()
    expect(sent.children[1].children).toEqual([])
    expect(sent.children[0]).toEqual(tree.children[0])
    expect(s.operationVisible).toBe(false)
  })
  it('收货所属路径复用同一显示层级，提交仍使用原终端节点标识', async () => {
    const tree = fullTree(),
      parsed = JSON.parse(snapshot(tree))
    parsed.groups[0].outputs = [
      {
        outputId: 'root-width-s0-pieces',
        nodePath: '原卷1220 / 临时余料650mm / 切片',
        unit: 'PCS',
        quantity: 350,
        widthMm: 285,
        lengthMm: 315,
      },
    ]
    api.history.mockResolvedValue({
      data: { processingPlan: JSON.stringify(parsed), receipts: [] },
    })
    const w = mount(Receive, { props: { itemId: 99 }, global: { plugins: [ElementPlus] } })
    wrappers.push(w)
    await flushPromises()
    expect(w.find('.node-path').text()).toContain('加工其中1卷')
    expect(w.find('.node-path').text()).toContain('315mm × 100m')
    expect(w.find('.node-path').text()).not.toContain('650mm')
    const s = (w.vm as any).$?.setupState
    s.inputs[0].outputs[0].quantity = 100
    await s.validate()
    expect(api.receipt.mock.calls.at(-1)![1][0].outputs).toEqual([
      { outputId: 'root-width-s0-pieces', quantity: 100 },
    ])
  })
})

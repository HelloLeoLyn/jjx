import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import PickPreviewDialog from '../PickPreviewDialog.vue'
import PickPreviewPrintDialog from '../PickPreviewPrintDialog.vue'
import { PICK_PRINT_STORAGE_KEY, allPickPrintColumns } from '../pickPreviewPrint'

const api = vi.hoisted(() => ({
  preview: vi.fn(),
  create: vi.fn(),
  append: vi.fn(),
  supplement: vi.fn(),
  order: vi.fn(),
  bom: vi.fn(),
}))
vi.mock('@/api/production/order', () => ({ getProductionOrderDetail: api.order }))
vi.mock('@/api/product/bom', () => ({ productBomApi: { getEngineeringBomInfo: api.bom } }))
vi.mock('@/api/inventory/materialPick', () => ({
  materialPickApi: {
    pickPreview: api.preview,
    createFromProduction: api.create,
    createProductionPick: api.append,
    createProductionSupplement: api.supplement,
  },
}))

const Dialog = defineComponent({
  props: ['modelValue'],
  setup(props, { slots }) {
    return () => (props.modelValue ? h('section', [slots.default?.(), slots.footer?.()]) : null)
  },
})
const Button = defineComponent({
  props: ['disabled'],
  setup(props, { slots }) {
    return () => h('button', { disabled: props.disabled }, slots.default?.())
  },
})
const Popover = defineComponent({
  setup:
    (_, { slots }) =>
    () =>
      h('aside', [slots.reference?.(), slots.default?.()]),
})
const row = (extra = {}) => ({
  materialId: 11,
  bomItemId: 9,
  materialCode: 'M11',
  materialName: '胶片',
  specification: '10×20',
  unit: 'PCS',
  moduleQty: 1.2345,
  baseQty: 0.0025,
  qtyNeeded: 10,
  qtyPick: 8,
  available: 8,
  substitute: false,
  ...extra,
})
const wrappers: ReturnType<typeof shallowMount>[] = []
const global = {
  directives: { loading: () => {} },
  renderStubDefaultSlot: true,
  stubs: { ElDialog: Dialog, ElButton: Button, ElPopover: Popover, PickPreviewPrintDialog: false },
}
async function open(mode: 'first' | 'append' = 'first') {
  const wrapper = shallowMount(PickPreviewDialog, {
    props: { modelValue: true, workOrderId: 7, orderNo: 'WO7', mode },
    global,
  })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
function button(wrapper: ReturnType<typeof shallowMount>, label: string) {
  return wrapper.findAll('button').find((node) => node.text() === label)!
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  api.order.mockResolvedValue({ data: { bomId: 4 } })
  api.bom.mockResolvedValue({
    data: { bomId: 4, items: [{ itemId: 9, materialId: 11, sourceType: 'buy' }] },
  })
  api.preview.mockResolvedValue({ data: [row()] })
  api.create.mockResolvedValue({ data: 123 })
  api.append.mockResolvedValue({ data: 124 })
  api.supplement.mockResolvedValue({ data: 125 })
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.restoreAllMocks()
})

describe('生成领料单 - 预览确认', () => {
  it('保留规格列并显示完整四位精度的模数/基数，空表合并全部十列', async () => {
    const wrapper = await open()
    const headers = wrapper.findAll('th').map((node) => node.text())
    expect(headers).toContain('规格')
    expect(headers).toContain('模数/基数')
    expect(wrapper.text()).toContain('1.2345 / 0.0025')
    api.preview.mockResolvedValue({ data: [] })
    const empty = await open()
    expect(empty.get('td').attributes('colspan')).toBe('10')
    expect(button(empty, '打印预览').attributes('disabled')).toBeDefined()
  })

  it.each(['first', 'append'] as const)(
    '%s：修改数量后，打印与确认提交使用同一数量且原上限不变',
    async (mode) => {
      const wrapper = await open(mode)
      const input = wrapper.findComponent({ name: 'ElInputNumber' })
      input.vm.$emit('update:modelValue', 3)
      await flushPromises()
      expect(input.props('max')).toBe(8)
      expect(input.props('modelValue')).toBe(3)
      await button(wrapper, '打印预览').trigger('click')
      await flushPromises()
      const print = wrapper.getComponent(PickPreviewPrintDialog)
      expect(print.props('rows')[0].qtyPick).toBe(3)
      expect(print.get('iframe').attributes('srcdoc')).toContain('<td>3</td>')
      expect(api.create).not.toHaveBeenCalled()
      expect(api.append).not.toHaveBeenCalled()
      print.vm.$emit('update:modelValue', false)
      await flushPromises()
      await button(wrapper, mode === 'first' ? '确认生成领料单' : '确认追加领料单').trigger('click')
      await flushPromises()
      expect(mode === 'first' ? api.create : api.append).toHaveBeenCalledWith(7, [
        {
          materialId: 11,
          quantity: 3,
          ...(mode === 'append' ? { materialCode: 'M11', materialName: '胶片' } : {}),
        },
      ])
    }
  )

  it('加载期间及接口失败时禁用打印预览', async () => {
    let reject!: (reason: Error) => void
    api.preview.mockReturnValue(
      new Promise((_, onReject) => {
        reject = onReject
      })
    )
    const wrapper = await open()
    expect(button(wrapper, '打印预览').attributes('disabled')).toBeDefined()
    reject(new Error('预览加载失败'))
    await flushPromises()
    expect(button(wrapper, '打印预览').attributes('disabled')).toBeDefined()
    expect(wrapper.findComponent(PickPreviewPrintDialog).exists()).toBe(false)
  })

  it('关闭打印预览不关闭确认弹窗、不生成单据', async () => {
    const wrapper = await open()
    await button(wrapper, '打印预览').trigger('click')
    const print = wrapper.getComponent(PickPreviewPrintDialog)
    await button(print, '关闭').trigger('click')
    expect(wrapper.findComponent(PickPreviewPrintDialog).exists()).toBe(false)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(api.create).not.toHaveBeenCalled()
  })

  it.each(['first', 'append'] as const)(
    '%s：默认展开下层明细，可折叠；打印仍包含全部层级，提交仅本层',
    async (mode) => {
      api.preview.mockResolvedValue({
        data: [row({ materialId: 1976, bomItemId: 10, materialName: '3M9080' })],
      })
      api.bom.mockImplementation(async (id: number) => ({
        data:
          id === 4
            ? {
                bomId: 4,
                items: [{ itemId: 10, materialId: 1976, sourceType: 'make', subBomId: 2 }],
              }
            : {
                bomId: 2,
                outputType: 'MATERIAL',
                outputId: 1976,
                items: [
                  {
                    itemId: 7,
                    materialId: 1105,
                    materialCode: 'AUX001101',
                    materialName: '3M9080A',
                    unit: 'M',
                    quantity: 0.08,
                  },
                ],
              },
      }))
      const wrapper = await open(mode)
      expect(wrapper.findAll('tbody tr')).toHaveLength(2)
      expect(wrapper.findAllComponents({ name: 'ElInputNumber' })).toHaveLength(1)
      expect(wrapper.text()).toContain('3M9080A')
      expect(wrapper.text()).toContain('- / -')
      await wrapper.get('.tree-toggle').trigger('click')
      expect(wrapper.findAll('tbody tr')).toHaveLength(1)
      const input = wrapper.findComponent({ name: 'ElInputNumber' })
      input.vm.$emit('update:modelValue', 3)
      await flushPromises()
      await button(wrapper, '打印预览').trigger('click')
      const print = wrapper.getComponent(PickPreviewPrintDialog)
      expect(print.props('rows')).toHaveLength(2)
      expect(print.props('rows')[1]).toMatchObject({
        materialName: '3M9080A',
        depth: 1,
        sequence: '1.1',
        isBomDetail: true,
      })
      print.vm.$emit('update:modelValue', false)
      await flushPromises()
      await button(wrapper, mode === 'first' ? '确认生成领料单' : '确认追加领料单').trigger('click')
      await flushPromises()
      const items = (mode === 'first' ? api.create : api.append).mock.calls[0][1]
      expect(items).toHaveLength(1)
      expect(items[0]).toMatchObject({ materialId: 1976, quantity: 3 })
    }
  )

  it('未绑定历史工单和制造BOM加载失败时保留本层，不擅自改用产品当前BOM', async () => {
    api.order.mockResolvedValue({ data: { bomId: null } })
    const historical = await open()
    expect(api.bom).not.toHaveBeenCalled()
    expect(historical.findAll('tbody tr')).toHaveLength(1)
    expect(historical.findComponent({ name: 'ElAlert' }).props('title')).toContain('工单未绑定 BOM')
    api.order.mockResolvedValue({ data: { bomId: 4 } })
    api.bom.mockRejectedValue(new Error('读取失败'))
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const wrapper = await open()
    expect(warn).toHaveBeenCalled()
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.findComponent({ name: 'ElAlert' }).props('title')).toContain('明细加载失败')
    expect(wrapper.findComponent({ name: 'ElInputNumber' }).props('modelValue')).toBe(8)
  })

  it('补料模式下制造投入仍只读，提交保留补产数量、原因且不混入下层材料', async () => {
    api.preview.mockResolvedValue({
      data: [row({ materialId: 1976, bomItemId: 10, materialName: '3M9080' })],
    })
    api.bom.mockImplementation(async (id: number) => ({
      data:
        id === 4
          ? { bomId: 4, items: [{ itemId: 10, materialId: 1976, sourceType: 'make', subBomId: 2 }] }
          : {
              bomId: 2,
              outputType: 'MATERIAL',
              outputId: 1976,
              items: [{ itemId: 7, materialId: 1105, materialName: '3M9080A', unit: 'M' }],
            },
    }))
    const wrapper = shallowMount(PickPreviewDialog, {
      props: {
        modelValue: true,
        workOrderId: 7,
        orderNo: 'WO7',
        mode: 'supplement',
        presetProductionQuantity: 10,
      },
      global,
    })
    wrappers.push(wrapper)
    await flushPromises()
    const inputs = wrapper.findAllComponents({ name: 'ElInputNumber' })
    expect(inputs).toHaveLength(2) // 补产数量 + 本层实领；子明细没有输入框
    inputs[1].vm.$emit('update:modelValue', 5)
    wrapper.findComponent({ name: 'ElInput' }).vm.$emit('update:modelValue', '生产超耗')
    await flushPromises()
    await button(wrapper, '提交补料申请').trigger('click')
    await flushPromises()
    expect(api.supplement).toHaveBeenCalledWith(
      7,
      expect.objectContaining({
        productionQuantity: 10,
        reason: '生产超耗',
        items: [expect.objectContaining({ materialId: 1976, quantity: 5 })],
      })
    )
  })

  it('关闭后旧请求返回不会覆盖下一次打开的树', async () => {
    let resolveOld!: (value: any) => void
    api.order.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveOld = resolve
      })
    )
    const wrapper = await open()
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    resolveOld({ data: { bomId: 99 } })
    await flushPromises()
    expect(api.bom).not.toHaveBeenCalledWith(99)
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
  })
})

describe('领料单打印预览', () => {
  it('仅在纸张加载后调用独立iframe打印，更新数据后重新等待加载', async () => {
    const wrapper = shallowMount(PickPreviewPrintDialog, {
      props: { modelValue: true, orderNo: 'WO7', rows: [row()] },
      global,
      attachTo: document.body,
    })
    wrappers.push(wrapper)
    const iframe = wrapper.get('iframe')
    const printWindow = (iframe.element as HTMLIFrameElement).contentWindow!
    const print = vi.spyOn(printWindow, 'print').mockImplementation(() => {})
    vi.spyOn(printWindow, 'focus').mockImplementation(() => {})
    expect(button(wrapper, '打印').attributes('disabled')).toBeDefined()
    await iframe.trigger('load')
    await button(wrapper, '打印').trigger('click')
    expect(print).toHaveBeenCalledOnce()
    await wrapper.setProps({ rows: [row({ qtyPick: 4 })] })
    expect(button(wrapper, '打印').attributes('disabled')).toBeDefined()
    expect(iframe.attributes('srcdoc')).toContain('<td>4</td>')
  })

  it('勾选打印列后隐藏表头/单元格并记忆，重新打开恢复选择，恢复默认显示全部', async () => {
    const options = { props: { modelValue: true, orderNo: 'WO7', rows: [row()] }, global }
    const wrapper = shallowMount(PickPreviewPrintDialog, options)
    wrappers.push(wrapper)
    wrapper
      .findComponent({ name: 'ElCheckboxGroup' })
      .vm.$emit('update:modelValue', ['material', 'specification'])
    await flushPromises()
    let doc = new DOMParser().parseFromString(
      wrapper.get('iframe').attributes('srcdoc')!,
      'text/html'
    )
    expect([...doc.querySelectorAll('th')].map((cell) => cell.textContent)).toEqual([
      '物料编码/原料品名',
      '规格',
    ])
    expect(doc.querySelectorAll('tbody td')).toHaveLength(2)
    expect(JSON.parse(localStorage.getItem(PICK_PRINT_STORAGE_KEY)!)).toEqual([
      'material',
      'specification',
    ])
    const again = shallowMount(PickPreviewPrintDialog, options)
    wrappers.push(again)
    expect(again.findComponent({ name: 'ElCheckboxGroup' }).props('modelValue')).toEqual([
      'material',
      'specification',
    ])
    await button(again, '恢复默认').trigger('click')
    expect(again.findComponent({ name: 'ElCheckboxGroup' }).props('modelValue')).toEqual(
      allPickPrintColumns()
    )
    doc = new DOMParser().parseFromString(again.get('iframe').attributes('srcdoc')!, 'text/html')
    expect(doc.querySelectorAll('th')).toHaveLength(10)
    expect(doc.querySelectorAll('tbody td')).toHaveLength(10)
  })

  it('全部取消时不打印空表；坏的本机记忆回退默认，存储不可写仍可选列', async () => {
    localStorage.setItem(PICK_PRINT_STORAGE_KEY, 'broken-json')
    const wrapper = shallowMount(PickPreviewPrintDialog, {
      props: { modelValue: true, orderNo: 'WO7', rows: [row()] },
      global,
    })
    wrappers.push(wrapper)
    expect(wrapper.findComponent({ name: 'ElCheckboxGroup' }).props('modelValue')).toHaveLength(10)
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('存储禁用')
    })
    wrapper.findComponent({ name: 'ElCheckboxGroup' }).vm.$emit('update:modelValue', [])
    await flushPromises()
    await wrapper.get('iframe').trigger('load')
    expect(button(wrapper, '打印').attributes('disabled')).toBeDefined()
    expect(wrapper.get('iframe').attributes('srcdoc')).toContain('请选择至少一列打印内容')
    expect(wrapper.get('iframe').attributes('srcdoc')).not.toContain('<table>')
  })
})

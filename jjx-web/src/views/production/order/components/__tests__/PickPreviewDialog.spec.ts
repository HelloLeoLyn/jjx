import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import PickPreviewDialog from '../PickPreviewDialog.vue'
import PickPreviewPrintDialog from '../PickPreviewPrintDialog.vue'

const api = vi.hoisted(() => ({ preview: vi.fn(), create: vi.fn(), append: vi.fn() }))
vi.mock('@/api/inventory/materialPick', () => ({
  materialPickApi: {
    pickPreview: api.preview,
    createFromProduction: api.create,
    createProductionPick: api.append,
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
const row = (extra = {}) => ({
  materialId: 11,
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
  stubs: { ElDialog: Dialog, ElButton: Button, PickPreviewPrintDialog: false },
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
  api.preview.mockResolvedValue({ data: [row()] })
  api.create.mockResolvedValue({ data: 123 })
  api.append.mockResolvedValue({ data: 124 })
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
})

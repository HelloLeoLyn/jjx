import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { computed, defineComponent, inject, provide } from 'vue'
import SampleReturnDialog from '../SampleReturnDialog.vue'
import { InboundTypeEnum, InboundSourceTypeEnum } from '@/enums/inventory/InboundEnum'
import { SourceTypeEnum } from '@/enums/inventory/TransactionEnum'

const api = vi.hoisted(() => ({ sampleReturnPreview: vi.fn(), sampleReturn: vi.fn() }))
vi.mock('@/api/inventory/inbound', () => ({ inboundApi: api }))
const Table = defineComponent({
  props: ['data'],
  setup(props) {
    provide(
      'returnRows',
      computed(() => props.data)
    )
    return {}
  },
  template: '<div><slot /></div>',
})
const Column = defineComponent({
  props: ['prop', 'label'],
  setup() {
    return { rows: inject('returnRows') }
  },
  template:
    '<div><span>{{ label }}</span><div v-for="row in rows" :key="row.returnKey"><slot :row="row">{{ row[prop] }}</slot></div></div>',
})
function mountDialog() {
  return mount(SampleReturnDialog, {
    props: { modelValue: true, sampleOrderId: 1, sampleOrderNo: 'SP-1' },
    global: {
      directives: { loading: () => {} },
      stubs: {
        ElDialog: { template: '<div><slot /><slot name="footer" /></div>' },
        ElAlert: true,
        ElTable: Table,
        ElTableColumn: Column,
        ElButton: {
          props: ['disabled', 'loading'],
          template: '<button :disabled="disabled || loading"><slot /></button>',
        },
        ElInput: true,
        ElInputNumber: {
          props: ['modelValue', 'max', 'disabled'],
          emits: ['update:modelValue'],
          template:
            '<input type="number" :value="modelValue" :max="max" :disabled="disabled" @input="$emit(\'update:modelValue\', Number($event.target.value))" />',
        },
      },
    },
  })
}
const row = {
  returnKey: '81',
  materialId: 11,
  inventoryItemId: 31,
  materialCode: 'MAT-1',
  materialName: '原料',
  batchNo: 'ORIGINAL',
  warehouseId: 2,
  locationId: 3,
  issuedQty: 5,
  returnedQty: 0,
  returnableQty: 5,
}
beforeEach(() => {
  vi.clearAllMocks()
  api.sampleReturnPreview.mockResolvedValue({ data: [row] })
  api.sampleReturn.mockResolvedValue({ data: 10 })
})
const submitButton = (wrapper: ReturnType<typeof mountDialog>) =>
  wrapper.findAll('button').find((b) => b.text() === '退料入库')!

describe('打样退料弹窗', () => {
  it('首次打开即加载原批次；提交后刷新可退量并通知入口刷新', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    expect(api.sampleReturnPreview).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('ORIGINAL')
    expect(wrapper.find('input').attributes('max')).toBe('5')
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()
    await wrapper.find('input').setValue('2')
    api.sampleReturnPreview.mockResolvedValue({
      data: [{ ...row, returnedQty: 2, returnableQty: 3 }],
    })
    await submitButton(wrapper).trigger('click')
    await flushPromises()
    expect(api.sampleReturn).toHaveBeenCalledExactlyOnceWith(
      1,
      [{ returnKey: '81', quantity: 2 }],
      ''
    )
    expect(wrapper.emitted('success')).toHaveLength(1)
    expect(wrapper.find('input').attributes('max')).toBe('3')
    expect(wrapper.find('input').element.value).toBe('0')
    wrapper.unmount()
  })
  it('超退不发送请求，接口拒绝时保留输入且不通知成功', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    await wrapper.find('input').setValue('6')
    await submitButton(wrapper).trigger('click')
    expect(api.sampleReturn).not.toHaveBeenCalled()
    await wrapper.find('input').setValue('2')
    api.sampleReturn.mockRejectedValue(new Error('超过可退量'))
    await submitButton(wrapper).trigger('click')
    await flushPromises()
    expect(wrapper.emitted('success')).toBeUndefined()
    expect(wrapper.find('input').element.value).toBe('2')
    wrapper.unmount()
  })
  it('重复点击只发送一次；预览失败清空旧选择', async () => {
    const wrapper = mountDialog()
    await flushPromises()
    await wrapper.find('input').setValue('2')
    let finish!: (value: any) => void
    api.sampleReturn.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        })
    )
    await submitButton(wrapper).trigger('click')
    await submitButton(wrapper).trigger('click')
    expect(api.sampleReturn).toHaveBeenCalledTimes(1)
    api.sampleReturnPreview.mockRejectedValue(new Error('预览失败'))
    finish({ data: 10 })
    await flushPromises()
    expect(wrapper.findAll('input')).toHaveLength(0)
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
  it('入库与流水枚举提供一致的打样退料筛选口径', () => {
    expect(InboundTypeEnum.getLabel('sample_return')).toBe('打样退料入库')
    expect(InboundSourceTypeEnum.getLabel('sample_return')).toBe('打样退料')
    expect(SourceTypeEnum.getLabel('sample_return')).toBe('打样退料入库')
  })
})

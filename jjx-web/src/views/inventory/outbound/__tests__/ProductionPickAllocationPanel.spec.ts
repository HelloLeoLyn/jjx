import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import ProductionPickAllocationPanel from '../components/ProductionPickAllocationPanel.vue'
import { outboundApi } from '@/api/inventory/outbound'
import { OutboundOrderStatusEnum } from '@/enums/inventory/OutboundEnum'
import type { PickAllocationView } from '@/types/inventory/outbound'
vi.mock('@/api/inventory/outbound', () => ({
  outboundApi: {
    getAllocation: vi.fn(),
    saveAllocation: vi.fn(),
    allocate: vi.fn(),
    reviewMixedBatch: vi.fn(),
    submitApprove: vi.fn(),
  },
}))
const data = (): PickAllocationView => ({
  outboundId: 1,
  status: OutboundOrderStatusEnum.APPROVED.value,
  fingerprint: 'version-1',
  mixedSupplier: false,
  unknownSupplier: false,
  demands: [
    {
      demandItemId: 1,
      materialId: 3,
      materialCode: 'M3',
      materialName: '原料',
      quantity: 6,
      allocations: [{ itemId: 1, materialId: 3, stockItemId: 21, quantity: 6 }],
    },
  ],
  batches: [
    {
      stockItemId: 21,
      materialId: 3,
      warehouseId: 2,
      locationId: 20,
      quantity: 10,
      availableQuantity: 10,
      supplierId: 7,
    },
  ],
})
function mountPanel() {
  return shallowMount(ProductionPickAllocationPanel, {
    props: { outboundId: 1 },
    global: {
      directives: { hasPermi: {}, loading: {} },
      stubs: {
        ElButton: {
          props: ['disabled', 'loading'],
          template: '<button :disabled="disabled"><slot /></button>',
        },
      },
    },
  })
}
async function click(wrapper: ReturnType<typeof mountPanel>, text: string) {
  const button = wrapper.findAll('button').find((b) => b.text() === text)
  expect(button).toBeTruthy()
  await button!.trigger('click')
  await flushPromises()
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(outboundApi.getAllocation).mockResolvedValue({ data: data() } as any)
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
})
describe('生产领料分配页面行为', () => {
  it('加载已批准且无欠料的分配后允许确认发料', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(outboundApi.getAllocation).toHaveBeenCalledWith('1')
    expect(wrapper.emitted('ready')?.at(-1)).toEqual([true])
  })
  it('清零后阻止发料，保存通过专用接口带原指纹', async () => {
    const cleared = data()
    cleared.demands[0].allocations[0].stockItemId = undefined
    vi.mocked(outboundApi.saveAllocation).mockResolvedValue({ data: cleared } as any)
    const wrapper = mountPanel()
    await flushPromises()
    await click(wrapper, '清空本次数量')
    expect(wrapper.emitted('ready')?.at(-1)).toEqual([false])
    await click(wrapper, '保存分配')
    expect(outboundApi.saveAllocation).toHaveBeenCalledWith('1', {
      expectedFingerprint: 'version-1',
      lines: [],
    })
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })
  it('重新分配调用现有allocate接口后重读', async () => {
    vi.mocked(outboundApi.allocate).mockResolvedValue({
      data: { allocatedLines: 1, shortageLines: 0 },
    } as any)
    const wrapper = mountPanel()
    await flushPromises()
    await click(wrapper, '按 FIFO 重新分配')
    expect(outboundApi.allocate).toHaveBeenCalledWith('1')
    expect(outboundApi.getAllocation).toHaveBeenCalledTimes(2)
  })
  it('未知供应商禁用混批通过并阻止发料', async () => {
    const unknown = data()
    unknown.status = OutboundOrderStatusEnum.MIXED_BATCH_PENDING.value
    unknown.unknownSupplier = true
    vi.mocked(outboundApi.getAllocation).mockResolvedValue({ data: unknown } as any)
    const wrapper = mountPanel()
    await flushPromises()
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === '混批审批通过')!
        .attributes('disabled')
    ).toBeDefined()
    expect(wrapper.emitted('ready')?.at(-1)).toEqual([false])
  })
})

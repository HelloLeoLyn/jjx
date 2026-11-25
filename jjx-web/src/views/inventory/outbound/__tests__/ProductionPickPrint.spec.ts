import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import ProductionPickPrint from '../components/ProductionPickPrint.vue'
import { OutboundOrderStatusEnum } from '@/enums/inventory/OutboundEnum'
import type { OutboundVO, PickAllocationView } from '@/types/inventory/outbound'
const allocation: PickAllocationView = {
  outboundId: 1,
  status: OutboundOrderStatusEnum.COMPLETED.value,
  fingerprint: 'x',
  mixedSupplier: false,
  unknownSupplier: false,
  demands: [
    {
      demandItemId: 1,
      materialId: 3,
      materialCode: 'M3',
      materialName: '原料',
      quantity: 10,
      unit: 'PCS',
      allocations: [
        { itemId: 1, materialId: 3, stockItemId: 21, quantity: 6 },
        { itemId: 2, materialId: 3, stockItemId: 22, quantity: 4 },
      ],
    },
  ],
  batches: [
    {
      stockItemId: 21,
      materialId: 3,
      warehouseId: 2,
      locationName: 'A10',
      quantity: 0,
      availableQuantity: 0,
      batchNo: 'B1',
    },
    {
      stockItemId: 22,
      materialId: 3,
      warehouseId: 2,
      locationName: 'A2',
      quantity: 0,
      availableQuantity: 0,
      batchNo: 'B2',
    },
  ],
}
const detail = {
  outboundId: '1',
  outboundNo: 'PICK-ONLY-THIS',
  sourceNo: 'WO-1',
  warehouseName: '原料仓',
} as OutboundVO
describe('生产领料打印', () => {
  it('领料单合并拆批、保留需求与实发和QR031编号及签字栏', () => {
    const wrapper = mount(ProductionPickPrint, { props: { allocation, detail, mode: 'pick' } })
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    expect(wrapper.text()).toContain('JJX-QR-031')
    expect(wrapper.text()).toContain('PICK-ONLY-THIS')
    expect(wrapper.text()).toContain('需求')
    expect(wrapper.text()).toContain('实发')
    expect(wrapper.text()).toContain('领料人：')
  })
  it('拣货单按库位排序输出两个批次，保留选定单据号', () => {
    const wrapper = mount(ProductionPickPrint, { props: { allocation, detail, mode: 'picking' } })
    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('A2')
    expect(rows[0].text()).toContain('B2')
    expect(wrapper.text()).toContain('PICK-ONLY-THIS')
  })
})

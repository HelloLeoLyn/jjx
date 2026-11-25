import { describe, it, expect } from 'vitest'
import {
  allocationDraft,
  batchLimit,
  materialRows,
  pickingRows,
} from '../components/pickAllocation'
import { OutboundOrderStatusEnum } from '@/enums/inventory/OutboundEnum'
import type { PickAllocationView } from '@/types/inventory/outbound'
const view = (): PickAllocationView => ({
  outboundId: 1,
  status: OutboundOrderStatusEnum.PENDING.value,
  fingerprint: 'test',
  mixedSupplier: false,
  unknownSupplier: false,
  demands: [
    {
      demandItemId: 1,
      materialId: 3,
      materialCode: 'M3',
      materialName: 'material',
      sourceBomItemId: 11,
      quantity: 6,
      allocations: [{ itemId: 1, materialId: 3, stockItemId: 21, quantity: 6 }],
    },
    {
      demandItemId: 2,
      materialId: 3,
      materialCode: 'M3',
      materialName: 'material',
      sourceBomItemId: 12,
      quantity: 6,
      allocations: [
        { itemId: 2, materialId: 3, stockItemId: 21, quantity: 4 },
        { itemId: 3, materialId: 3, quantity: 2 },
      ],
    },
  ],
  batches: [
    {
      stockItemId: 21,
      materialId: 3,
      warehouseId: 2,
      locationName: 'A10',
      quantity: 10,
      availableQuantity: 10,
      supplierId: 7,
    },
    {
      stockItemId: 22,
      materialId: 3,
      warehouseId: 2,
      locationName: 'A2',
      quantity: 8,
      availableQuantity: 8,
      supplierId: 7,
    },
  ],
})
describe('生产领料分配的展示与编辑', () => {
  it('同物料多需求和拆批只展示一物料行，需求及分配不重复', () => {
    const rows = materialRows(view())
    expect(rows).toHaveLength(1)
    expect(rows[0].quantity).toBe(12)
    expect(rows[0].assigned).toBe(10)
    expect(rows[0].issued).toBe(0)
    expect(rows[0].demands).toHaveLength(2)
  })
  it('实发只在已发料后展示，欠料不当实发', () => {
    const data = view()
    data.status = OutboundOrderStatusEnum.COMPLETED.value
    expect(materialRows(data)[0].issued).toBe(10)
  })
  it('父件作为独立不可编辑分组，不计入需求', () => {
    const rows = materialRows(view(), [
      { materialId: 3, sourceBomItemId: 11, parentRowKey: 'parent' },
      { rowKey: 'parent', materialName: 'parent material' },
    ])
    expect(rows).toHaveLength(2)
    expect(rows[0].isGroup).toBe(true)
    expect(rows[1].quantity).toBe(12)
  })
  it('同一批次跨需求的上限扣除其他需求已分配量', () => {
    const data = view(),
      draft = allocationDraft(data)
    expect(batchLimit(data.demands[1], data.batches[0], draft, data)).toBe(4)
  })
  it('单需求跨批次也不能超过需求总量', () => {
    const data = view(),
      draft = allocationDraft(data)
    expect(batchLimit(data.demands[0], data.batches[1], draft, data)).toBe(0)
  })
  it('卷材上限只能为零或整卷', () => {
    const data = view(),
      batch = { ...data.batches[1], rollNo: 'R22' }
    expect(batchLimit(data.demands[0], batch, {}, data)).toBe(0)
    batch.quantity = 5
    expect(batchLimit(data.demands[0], batch, {}, data)).toBe(5)
  })
  it('同供应商多批次只提示，跨供应商可独立识别', () => {
    const data = view()
    data.demands[1].allocations = [{ itemId: 2, materialId: 3, stockItemId: 22, quantity: 6 }]
    let material = materialRows(data)[0]
    expect(material.batchIds.size).toBe(2)
    expect(material.supplierIds.size).toBe(1)
    data.batches[1].supplierId = 8
    material = materialRows(data)[0]
    expect(material.supplierIds.size).toBe(2)
  })
  it('供应商未知不当作同供应商', () => {
    const data = view()
    data.batches[0].supplierId = undefined
    expect(materialRows(data)[0].unknownSupplier).toBe(true)
  })
  it('拣货单按库位自然排序，保留批次行且不打印欠料行', () => {
    const data = view()
    data.demands[1].allocations.push({ itemId: 4, materialId: 3, stockItemId: 22, quantity: 1 })
    const rows = pickingRows(data)
    expect(rows).toHaveLength(3)
    expect(rows[0].locationName).toBe('A2')
    expect(rows[1].locationName).toBe('A10')
    expect(rows.every((row) => row.stockItemId != null)).toBe(true)
  })
})

import { describe, expect, it, vi } from 'vitest'
import {
  buildPickPreviewTree,
  flattenPickPreviewTree,
  type PickBomDetail,
} from '../pickPreviewTree'

/** 只读核对的现存 BOM：YL1025PWOO-BOM V1.1，BOM4 -> BOM2/BOM3。 */
const boms: Record<number, PickBomDetail> = {
  4: {
    bomId: 4,
    outputType: 'PRODUCT',
    outputId: 1,
    items: [
      {
        itemId: 9,
        materialId: 1975,
        materialCode: 'RM001970',
        materialName: '0.8mm 浅茶PC 单面加硬11008',
        sourceType: 'buy',
      },
      {
        itemId: 10,
        materialId: 1976,
        materialCode: 'AUX001971',
        materialName: '3M9080',
        sourceType: 'make',
        subBomId: 2,
      },
      {
        itemId: 11,
        materialId: 1977,
        materialCode: 'AUX001972',
        materialName: '离型纸 RP-W195',
        parentMaterialId: 10,
        sourceType: 'make',
        subBomId: 3,
      },
      {
        itemId: 12,
        materialId: 1978,
        materialCode: 'AUX001973',
        materialName: 'PET保护膜 PF-T4901',
        sourceType: 'buy',
      },
      {
        itemId: 13,
        materialId: 1979,
        materialCode: 'AUX001974',
        materialName: '020#低粘PE保护膜',
        sourceType: 'buy',
      },
    ],
  },
  2: {
    bomId: 2,
    outputType: 'MATERIAL',
    outputId: 1976,
    items: [
      {
        itemId: 7,
        materialId: 1105,
        materialCode: 'AUX001101',
        materialName: '3M9080A',
        specification: '195mm',
        unit: 'M',
        quantity: 0.08,
        moduleQty: undefined,
        baseQty: undefined,
        sourceType: 'buy',
      },
    ],
  },
  3: {
    bomId: 3,
    outputType: 'MATERIAL',
    outputId: 1977,
    items: [
      {
        itemId: 8,
        materialId: 802,
        materialCode: 'AUX000798',
        materialName: '120g进口黄色离型纸',
        specification: '120.0g/m²*195mm',
        unit: 'M',
        quantity: 0.08,
        sourceType: 'buy',
      },
    ],
  },
}
const topRows = () =>
  boms[4].items!.map((item) => ({
    materialId: item.materialId,
    bomItemId: item.itemId,
    materialName: item.materialName,
    qtyPick: 0,
  }))

describe('领料制造BOM明细树', () => {
  it('真实五主行+两制造投入默认展开，不把原组合父行当制造关系，不补造数量', async () => {
    const rows = topRows()
    const load = vi.fn(async (id: number) => boms[id])
    const tree = await buildPickPreviewTree(rows, 4, load)
    const flat = flattenPickPreviewTree(tree)
    expect(tree).toHaveLength(5)
    expect(flat).toHaveLength(7)
    expect(tree[1].children[0].row.materialName).toBe('3M9080A')
    expect(tree[2].children[0].row.materialName).toBe('120g进口黄色离型纸')
    expect(flat.map(({ sequence }) => sequence)).toEqual(['1', '2', '2.1', '3', '3.1', '4', '5'])
    expect(
      flat
        .filter(({ depth }) => depth === 1)
        .every(
          ({ row }) =>
            row.isBomDetail &&
            row.qtyNeeded === undefined &&
            row.qtyPick === undefined &&
            row.available === undefined
        )
    ).toBe(true)
    expect(tree[1].children[0].row.moduleQty).toBeUndefined()
    expect(load.mock.calls.map(([id]) => id)).toEqual([4, 2, 3])
    tree[1].row.qtyPick = 5
    expect(rows[1].qtyPick).toBe(5)
    expect(flattenPickPreviewTree(tree, new Set(['pick:1']))).toHaveLength(6)
  })

  it('同物料重复行按bomItemId定位，同一制造BOM只读取一次但保留两份各自层级', async () => {
    const root = { ...boms[4], items: [boms[4].items![1], { ...boms[4].items![1], itemId: 20 }] }
    const rows = root.items.map((item) => ({ materialId: item.materialId, bomItemId: item.itemId }))
    const load = vi.fn(async (id: number) => (id === 4 ? root : boms[id]))
    const tree = await buildPickPreviewTree(rows, 4, load)
    expect(flattenPickPreviewTree(tree)).toHaveLength(4)
    expect(tree[0].children[0].key).not.toBe(tree[1].children[0].key)
    expect(load.mock.calls.filter(([id]) => id === 2)).toHaveLength(1)
  })

  it('循环、方案身份错误和读取失败只阻止对应展开，不损坏本层领料行', async () => {
    const rows = topRows()
    const cycle = { ...boms[2], items: [{ ...boms[4].items![1], itemId: 30 }] }
    const load = async (id: number) => {
      if (id === 4) return boms[4]
      if (id === 2) return cycle
      throw new Error('下层方案读取失败')
    }
    const tree = await buildPickPreviewTree(rows, 4, load)
    expect(tree[1].children[0].error).toContain('循环引用')
    expect(tree[2].error).toContain('读取失败')
    expect(tree[1].row).toBe(rows[1])
    const wrong = await buildPickPreviewTree(rows, 4, async (id) =>
      id === 4 ? boms[4] : { ...boms[id], outputId: -1 }
    )
    expect(wrong[1].error).toContain('产出与当前材料不一致')
    expect(wrong[1].children).toHaveLength(0)
  })

  it('替代料不挂原料制造方案；不按相同物料猜测缺失的BOM行身份', async () => {
    const tree = await buildPickPreviewTree(
      [{ materialId: 1976, bomItemId: 10, substitute: true }, { materialId: 1976 }],
      4,
      async (id) => boms[id]
    )
    expect(tree[0].children).toHaveLength(0)
    expect(tree[0].error).toBeUndefined()
    expect(tree[1].children).toHaveLength(0)
    expect(tree[1].error).toContain('不一致')
  })
})

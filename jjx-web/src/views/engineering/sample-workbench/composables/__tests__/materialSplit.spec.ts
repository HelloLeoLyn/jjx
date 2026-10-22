import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useSampleBom } from '../useSampleBom'
import { usePlanCopy } from '../usePlanCopy'
import { normalizeMaterial, setMaterialSplit, validateMaterials } from '../sampleMaterials'

const api = vi.hoisted(() => ({ listBom: vi.fn(), listProcesses: vi.fn() }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('@/api/inventory/material', () => ({ materialApi: {} }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }, ElMessageBox: {} }))

function fixture() {
  const context = {
    orderId: ref(10), card: ref({ sampleRound: 2 }), readonlyMode: ref(false),
    planList: ref<any[]>([{ uid: 'assembly', editing: true, materialRows: [{ name: '面板', qty: 0.5, split: '50mm' }] }]),
    printList: ref<any[]>([{ uid: 'print', editing: false, materials: JSON.stringify([{ name: '油墨', qty: 1, split: '80mm' }]) }]),
    markOwnerDirty: vi.fn(),
  }
  return { context, bom: useSampleBom(context) }
}
beforeEach(() => vi.clearAllMocks())

describe('打样跳距共用草稿和复制', () => {
  it('工序与BOM双向同步，印刷材料也写回原工序，用量不受跳距影响', () => {
    const { context: c, bom } = fixture()
    expect(bom.bomList.value[0].split).toBe('50mm')
    let row = bom.bomList.value[0]
    setMaterialSplit(row, 60); bom.updateMaterial(row)
    expect(c.planList.value[0].materialRows[0].split).toBe('60mm')
    expect(bom.bomList.value[0].qty).toBe(0.5)
    c.planList.value[0].materialRows[0].split = '70mm'
    expect(bom.bomList.value[0].split).toBe('70mm')
    row = bom.bomList.value[1]
    setMaterialSplit(row, 90); bom.updateMaterial(row)
    expect(JSON.parse(c.printList.value[0].materials)[0].split).toBe('90mm')
    expect(c.markOwnerDirty).toHaveBeenCalledTimes(2)
    setMaterialSplit(row, undefined); bom.updateMaterial(row)
    expect(bom.bomList.value[1].split).toBe('')
  })
  it('回读仅取当前轮，历史未填保持空值；只读不写回', async () => {
    const { context: c, bom } = fixture()
    api.listBom.mockResolvedValue({ data: [
      { roundNo: 1, materialName: '旧轮', split: '90mm', quantity: 1 },
      { roundNo: 2, materialName: '本轮', split: '50mm', quantity: 1 },
      { roundNo: 2, materialName: '未填', quantity: 1 },
    ] })
    await bom.loadBom()
    expect(bom.standaloneMaterials.value.map(row => row.split)).toEqual(['50mm', undefined])
    const row = bom.bomList.value[0]; row.split = '100mm'; c.readonlyMode.value = true
    bom.updateMaterial(row)
    expect(c.planList.value[0].materialRows[0].split).toBe('50mm')
  })
  it.each([null, '', '50mm', '50'])('兼容合法或空跳距 %s，保留既有数量口径', split => {
    const row = normalizeMaterial({ name: '材料', qty: 0.25, split })
    expect(validateMaterials([row])).toBeNull()
    expect(row.qty).toBe(0.25)
  })
  it.each(['-1mm', '1.5mm', 'abc', '10cm', '9007199254740992mm'])('非法跳距 %s 阻止提交', split => {
    expect(validateMaterials([normalizeMaterial({ name: '材料', qty: 1, split })])).toContain('跳距')
  })
  it('跨单及指定历史轮复制均保留工序材料、独立材料跳距', async () => {
    for (const round of [1, null]) {
      const { context: c, bom } = fixture()
      api.listProcesses.mockResolvedValue({ data: [{ processName: '冲型', processOrder: 1, materials: JSON.stringify([{ name: '面板', split: '50mm' }]) }] })
      api.listBom.mockResolvedValue({ data: [{ roundNo: 1, materialName: '独立胶', split: '60mm', quantity: 1 }] })
      const copy = usePlanCopy({ ...c, standaloneMaterials: bom.standaloneMaterials,
        allProcesses: ref([]), makeCard: (_rows, extra) => extra, makePrintRow: row => row,
        genUid: () => 'copied', parseMaterials: json => JSON.parse(json || '[]') })
      copy.copyOverwrite.value = true
      if (round != null) await copy.copyRoundFrom(round)
      else { copy.historySelected.value = { orderId: 20, sampleRound: 1 }; await copy.confirmHistoryCopy() }
      expect(JSON.parse(c.planList.value[0].materials)[0].split).toBe('50mm')
      expect(bom.standaloneMaterials.value[0].split).toBe('60mm')
    }
  })
})

import { beforeEach, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useSampleBom } from '../useSampleBom'
import { usePlanPersistence } from '../usePlanPersistence'

const api = vi.hoisted(() => ({ listBom: vi.fn(), saveScheme: vi.fn(), listProcesses: vi.fn() }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }))
beforeEach(() => { vi.clearAllMocks(); api.saveScheme.mockResolvedValue({}); api.listProcesses.mockResolvedValue({ data: [] }) })

it('BOM回读只加载独立行，工序行从工序草稿投影，独立材料的projectIds不改变归属', async () => {
  const planList = ref<any[]>([{ uid: 'c', items: [{ itemKey: 'a', stdProcessId: 22 }],
    materials: JSON.stringify([{ materialId: 113, name: 'PE', qty: 1, processItemKey: 'a' }]) }])
  api.listBom.mockResolvedValue({ data: [
    { roundNo: 1, materialName: 'PE', remark: 'sample-process-material:32', projectIds: '[22]', quantity: 1 },
    { roundNo: 1, materialName: '独立', projectIds: '[22]', quantity: 1, remark: '保留备注' },
    { roundNo: 2, materialName: '历史', quantity: 1 },
  ] })
  const bom = useSampleBom({ orderId: ref(1), card: ref({ sampleRound: 1 }), planList, printList: ref([]), readonlyMode: ref(false) })
  await bom.loadBom(); await bom.loadBom()
  expect(bom.standaloneMaterials.value).toHaveLength(1)
  expect(bom.standaloneMaterials.value[0].projectIds).toEqual([22])
  expect(bom.standaloneMaterials.value[0].remark).toBe('保留备注')
  expect(bom.bomList.value).toHaveLength(2)
  expect(bom.bomList.value.find(row => row.name === '独立').ownerUid).toBeNull()
})

it('保存组合卡片时各子工序只提交自己的材料，独立材料不混入工序', async () => {
  const planList = ref<any[]>([{ uid: 'c', items: [
    { itemKey: 'a', stdProcessId: 22, processName: '保护膜' },
    { itemKey: 'b', stdProcessId: 24, processName: '冲孔' },
  ], materials: JSON.stringify([
    { materialId: 113, name: 'PE', baseQty: 1, moduleQty: 3, processItemKey: 'a', projectIds: [22] },
    { materialId: 109, name: 'PC', baseQty: 1, moduleQty: 6, processItemKey: 'b', projectIds: [24] },
  ]) }])
  planList.value[0].editing = true
  planList.value[0].materialRows = JSON.parse(planList.value[0].materials).map((row: any) => {
    const { processItemKey, ...draft } = row
    return draft
  })
  const p = usePlanPersistence({ readonlyMode: ref(false), orderId: ref(1), card: ref({ sampleRound: 1 }), note: ref(''),
    planList, printList: ref([]), standaloneMaterials: ref([{ name: '独立', qty: 1, remark: '保留备注', projectIds: [22] }]),
    allProcesses: ref([]), makeCard: vi.fn(), makePrintRow: vi.fn(), loadBom: vi.fn(), refreshCard: vi.fn(), loadSummary: vi.fn() })
  expect(await p.savePlan()).toBe(true)
  const body = api.saveScheme.mock.calls[0][1]
  expect(body.items.map((item: any) => JSON.parse(item.materials).map((row: any) => row.materialId))).toEqual([[113], [109]])
  expect(JSON.parse(body.items[0].materials)[0].processItemKey).toBe('a')
  expect(body.standaloneMaterials).toHaveLength(1)
  expect(body.standaloneMaterials[0].remark).toBe('保留备注')
  expect(body.standaloneMaterials[0].projectIds).toBe('[22]')
})

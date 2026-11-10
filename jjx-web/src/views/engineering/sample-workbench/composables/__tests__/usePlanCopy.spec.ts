import { beforeEach, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePlanCopy } from '../usePlanCopy'

const api = vi.hoisted(() => ({ listBom: vi.fn(), listProcesses: vi.fn() }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }, ElMessageBox: {} }))
beforeEach(() => {
  vi.clearAllMocks()
  api.listProcesses.mockResolvedValue({ data: [{ processId: 32, roundNo: 1, processName: '工序',
    materials: '[{"name":"PE","qty":1}]' }] })
  api.listBom.mockResolvedValue({ data: [
    { roundNo: 1, materialName: 'PE', quantity: 1, remark: 'sample-process-material:32' },
    { roundNo: 1, materialName: '独立', quantity: 1, projectIds: '[22]' },
    { roundNo: 2, materialName: '其他轮', quantity: 1 },
  ] })
})
it.each(['previous', 'history'] as const)('%s复制保留工序JSON和独立材料，排除结构化同步行', async (source) => {
  const standaloneMaterials = ref<any[]>([])
  const planList = ref<any[]>([])
  const copy = usePlanCopy({ orderId: ref(1), card: ref({ sampleRound: 2 }), readonlyMode: ref(false),
    planList, printList: ref([]), standaloneMaterials, allProcesses: ref([]),
    makeCard: (_items, extra) => extra, makePrintRow: vi.fn(), genUid: () => 'uid', parseMaterials: JSON.parse })
  if (source === 'previous') await copy.copyPreviousRound()
  else {
    copy.historySelected.value = { orderId: 9, sampleRound: 1 }
    await copy.confirmHistoryCopy()
  }
  expect(standaloneMaterials.value.map(row => row.name)).toEqual(['独立'])
  expect(standaloneMaterials.value[0].projectIds).toEqual([22])
  expect(JSON.parse(planList.value[0].materials).map((row: any) => row.name)).toEqual(['PE'])
  await copy.copyRoundFrom(1)
  expect(standaloneMaterials.value.map(row => row.name)).toEqual(['独立'])
  copy.copyOverwrite.value = true
  await copy.copyRoundFrom(1)
  expect(standaloneMaterials.value.map(row => row.name)).toEqual(['独立'])
})

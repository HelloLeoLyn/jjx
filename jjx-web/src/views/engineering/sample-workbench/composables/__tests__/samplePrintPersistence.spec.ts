import { beforeEach, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePlanPersistence } from '../usePlanPersistence'
import { usePlanCopy } from '../usePlanCopy'
import { useProcessExecution } from '../useProcessExecution'
import { SampleProcessStatusEnum } from '@/enums/sales'

const api = vi.hoisted(() => ({ saveScheme: vi.fn(), listProcesses: vi.fn(), listBom: vi.fn(), updateProcessItemStatus: vi.fn() }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }))
beforeEach(() => vi.resetAllMocks())

// Row hydration is an injected boundary here; persistence/copy/execution are the real composables.
const hydrate = (src: any) => {
  const { selectedActualPlateId, selectedActualResource, ...persisted } = src
  return { ...persisted, printName: src.processName, category: src.processCategory, saveState: 'synced' }
}
const plan = { artworkVersionId: 41, defaultPlateId: 51, printRequirements: JSON.stringify({ position: 'front', meshMin: 80, meshMax: 120, tensionMin: 18, tensionMax: 24 }) }
const facts = { actualPlateId: 61, executionSnapshot: '{"plateNo":"ACTUAL-61"}', startTime: '2026-11-26 10:00:00', endTime: null, durationMinutes: 8, operator: 'printer' }
function setup() {
  const context = {
    readonlyMode: ref(false), orderId: ref(10), card: ref<any>({ sampleRound: 2 }), note: ref(''),
    planList: ref<any[]>([]), printList: ref<any[]>([]), standaloneMaterials: ref<any[]>([]), allProcesses: ref<any[]>([]),
    makeCard: vi.fn(), makePrintRow: hydrate, loadBom: vi.fn(), refreshCard: vi.fn(), loadSummary: vi.fn(),
  }
  return { context, persistence: usePlanPersistence(context) }
}
it('loads bound plans, saves changes, reopens without losing plan or readonly execution facts', async () => {
  let stored: any = { processId: 71, processName: '丝印', processCategory: 'PANEL', majorCategory: 'PRINT', materials: '[]', status: SampleProcessStatusEnum.DOING.value, ...plan, ...facts }
  api.listProcesses.mockImplementation(async () => ({ data: [structuredClone(stored)] }))
  api.saveScheme.mockImplementation(async (_id, body) => {
    stored = { ...stored, ...body.items[0] }
    return { data: {} }
  })
  const { context: c, persistence: p } = setup()
  await p.loadPlan()
  expect(c.printList.value[0]).toMatchObject({ ...plan, ...facts })
  c.printList.value[0].printRequirements = '{"position":"back","tensionMin":20}'
  const edited = { ...plan, printRequirements: c.printList.value[0].printRequirements }
  expect(p.hasUnsavedChanges.value).toBe(true)
  expect(await p.savePlan()).toBe(true)
  const payload = api.saveScheme.mock.calls[0][1].items[0]
  expect(payload).toMatchObject(edited)
  for (const key of [...Object.keys(facts), 'selectedActualPlateId', 'selectedActualResource']) expect(payload).not.toHaveProperty(key)
  expect(c.printList.value[0]).toMatchObject({ ...edited, ...facts })
  const reopened = setup()
  await reopened.persistence.loadPlan()
  expect(reopened.context.printList.value[0]).toMatchObject({ ...edited, ...facts })
  reopened.context.readonlyMode.value = true
  reopened.context.printList.value[0].defaultPlateId = null
  expect(await reopened.persistence.savePlan()).toBe(false)
  expect(api.saveScheme).toHaveBeenCalledTimes(1)
})
it('new copied row clears execution facts and temporary actual selection', async () => {
  const source = { processId: 71, processName: '丝印', majorCategory: 'PRINT', ...plan, ...facts, selectedActualPlateId: 99, selectedActualResource: { plateId: 99 } }
  api.listProcesses.mockResolvedValue({ data: [source] })
  api.listBom.mockResolvedValue({ data: [] })
  const { context: c } = setup()
  const copy = usePlanCopy({ ...c, genUid: () => 'copy', parseMaterials: JSON.parse })
  await copy.copyPreviousRound()
  const row = c.printList.value[0]
  expect(row).toMatchObject({ ...plan, status: SampleProcessStatusEnum.TODO.value, actualPlateId: null, startTime: null, endTime: null, durationMinutes: null, operator: '' })
  expect(row.uid).toBe('new-copy')
  expect(row.executionSnapshot).toBeUndefined()
  expect(row.selectedActualPlateId == null).toBe(true)
  expect(row.selectedActualResource == null).toBe(true)
})
it('finishes a reloaded running print using the persisted actual plate, ignoring temporary selection', async () => {
  const row: any = { processId: 71, status: SampleProcessStatusEnum.DOING.value, saveState: 'synced', ...facts, selectedActualPlateId: 99 }
  api.updateProcessItemStatus.mockResolvedValue({ data: { ...facts, status: SampleProcessStatusEnum.DONE.value } })
  const { context: c } = setup()
  const execution = useProcessExecution({ ...c, savingPlan: ref(false) })
  await execution.advancePrint(row)
  expect(api.updateProcessItemStatus).toHaveBeenCalledWith(10, 71, { status: SampleProcessStatusEnum.DONE.value, actualPlateId: 61 })
  expect(row.executionSnapshot).toBe(facts.executionSnapshot)
  await execution.advancePrint(row)
  expect(api.updateProcessItemStatus).toHaveBeenCalledTimes(1)
})

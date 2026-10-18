import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePlanPersistence } from '../usePlanPersistence'

const api = vi.hoisted(() => ({ saveScheme: vi.fn(), listProcesses: vi.fn(), startEngineering: vi.fn() }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }))

function fixture() {
  const pc = { uid: 'card-1', items: [{ processId: 41, processName: '冲型' }], category: 'PANEL',
    status: 1, materials: '[]', editing: false, operationRemark: '', saveState: 'synced' }
  const context = {
    readonlyMode: ref(false), orderId: ref(10), card: ref({ sampleRound: 1, engineeringNote: '旧备注' }),
    note: ref('旧备注'), planList: ref<any[]>([pc]), printList: ref<any[]>([]), standaloneMaterials: ref<any[]>([]),
    allProcesses: ref<any[]>([]), makeCard: vi.fn(), makePrintRow: vi.fn(), loadBom: vi.fn().mockResolvedValue(undefined),
    refreshCard: vi.fn().mockResolvedValue(undefined), loadSummary: vi.fn().mockResolvedValue(undefined), canSave: vi.fn(() => true),
  }
  const persistence = usePlanPersistence(context)
  persistence.markSaved()
  return { context, persistence, pc }
}
beforeEach(() => { vi.clearAllMocks(); api.saveScheme.mockResolvedValue({}); api.listProcesses.mockResolvedValue({ data: [] }) })

describe('统一保存打样方案', () => {
  it('仅改备注只提交备注和当前轮次，保留工序对象、执行ID及BOM', async () => {
    const { context: c, persistence: p, pc } = fixture()
    c.note.value = '新备注'
    expect(p.hasUnsavedChanges.value).toBe(true)
    const before = c.planList.value
    expect(await p.savePlan()).toBe(true)
    const [id, body] = api.saveScheme.mock.calls[0]
    expect(id).toBe(10)
    expect(body.engineeringNote).toBe('新备注')
    expect(body.roundNo).toBe(1)
    expect(body.items).toBeUndefined()
    expect(body.standaloneMaterials).toBeUndefined()
    expect(c.planList.value).toBe(before)
    expect(c.planList.value[0].items[0].processId).toBe(pc.items[0].processId)
    expect(api.listProcesses).not.toHaveBeenCalled()
    expect(c.loadBom).not.toHaveBeenCalled()
    expect(api.startEngineering).not.toHaveBeenCalled()
    expect(p.hasUnsavedChanges.value).toBe(false)
  })
  it('备注未修改时省略该字段，不让历史null备注阻断原有工序保存', async () => {
    const { context: c, persistence: p } = fixture()
    c.card.value.engineeringNote = null as any
    c.note.value = ''
    p.markSaved()
    c.planList.value[0].operationRemark = '只改工艺'
    expect(await p.savePlan()).toBe(true)
    expect(api.saveScheme.mock.calls[0][1].engineeringNote).toBeUndefined()
    expect(api.saveScheme.mock.calls[0][1].items).toHaveLength(1)
  })
  it('工序、印刷、BOM和工程备注一次请求一起提交', async () => {
    const { context: c, persistence: p } = fixture()
    c.note.value = '整份备注'
    c.planList.value[0].operationRemark = '新工艺'
    c.printList.value.push({ printName: '印刷', materials: '[]', category: 'PANEL', status: 0 })
    c.standaloneMaterials.value.push({ name: '独立材料', qty: 1, baseQty: 1, moduleQty: 1, unit: 'PCS' })
    expect(await p.savePlan()).toBe(true)
    expect(api.saveScheme).toHaveBeenCalledTimes(1)
    const body = api.saveScheme.mock.calls[0][1]
    expect(body.engineeringNote).toBe('整份备注')
    expect(body.items).toHaveLength(2)
    expect(body.items[0].processId).toBe(41)
    expect(body.items[1].majorCategory).toBe('PRINT')
    expect(body.standaloneMaterials[0].materialName).toBe('独立材料')
    expect(api.startEngineering).not.toHaveBeenCalled()
  })
  it('保存失败保留工序、印刷、BOM、备注草稿且保持未保存', async () => {
    const { context: c, persistence: p } = fixture()
    c.note.value = '未保存备注'
    c.planList.value[0].operationRemark = '未保存工艺'
    c.printList.value.push({ printName: '印刷草稿', materials: '[]', status: 0 })
    c.standaloneMaterials.value.push({ name: '未保存材料', qty: 1, baseQty: 1, moduleQty: 1, unit: 'PCS' })
    api.saveScheme.mockRejectedValueOnce(new Error('保存失败'))
    expect(await p.savePlan()).toBe(false)
    expect(c.note.value).toBe('未保存备注')
    expect(c.planList.value[0].operationRemark).toBe('未保存工艺')
    expect(c.printList.value[0].printName).toBe('印刷草稿')
    expect(c.standaloneMaterials.value[0].name).toBe('未保存材料')
    expect(c.card.value.engineeringNote).toBe('旧备注')
    expect(p.hasUnsavedChanges.value).toBe(true)
    expect(api.listProcesses).not.toHaveBeenCalled()
    expect(c.refreshCard).not.toHaveBeenCalled()
  })
  it('只读及历史轮不能发保存请求', async () => {
    const { context: c, persistence: p } = fixture()
    c.note.value = '新备注'; c.readonlyMode.value = true
    expect(await p.savePlan()).toBe(false)
    c.readonlyMode.value = false; c.canSave.mockReturnValue(false)
    expect(await p.savePlan()).toBe(false)
    expect(api.saveScheme).not.toHaveBeenCalled()
  })
  it('备注可以明确清空，保存期间的新修改不被标成已保存', async () => {
    const { context: c, persistence: p } = fixture()
    c.note.value = ''
    let done!: (value: unknown) => void
    api.saveScheme.mockImplementationOnce(() => new Promise(resolve => { done = resolve }))
    const pending = p.savePlan()
    expect(api.saveScheme.mock.calls[0][1].engineeringNote).toBe('')
    c.note.value = '稍后的草稿'; done({})
    expect(await pending).toBe(true)
    expect(c.note.value).toBe('稍后的草稿')
    expect(p.hasUnsavedChanges.value).toBe(true)
  })
})

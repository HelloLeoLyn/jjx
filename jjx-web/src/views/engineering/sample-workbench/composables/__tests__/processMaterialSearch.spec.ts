/** dev-20261024-013：搜索归公共选择器；工序/BOM保持原归属、草稿与整单保存契约。 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, reactive, ref } from 'vue'
import { useProcessMaterials } from '../useProcessMaterials'
import { useSampleBom } from '../useSampleBom'
import { usePlanPersistence } from '../usePlanPersistence'
import { useSampleWorkbench } from '../useSampleWorkbench'
import { assignMaterial } from '../sampleProcessGroups'

vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: { orderId: '10' } }), useRouter: () => ({}), onBeforeRouteLeave: vi.fn() }))
vi.mock('@/composables/useDict', () => ({ useDict: () => ({ options: ref([]) }) }))

const api = vi.hoisted(() => ({ saveScheme: vi.fn(), listProcesses: vi.fn(), startEngineering: vi.fn() }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))
const PET = { materialId: 11, materialName: 'PET', specification: '008', materialCode: 'MAT-PET', unit: 'M' }

function fixture() {
  const readonlyMode = ref(false)
  let uid = 0
  const materials = useProcessMaterials(readonlyMode, () => `m-${++uid}`)
  const pc = reactive({ uid: 'pc-1', category: 'PANEL', editing: true, status: 1,
    items: [{ itemKey: 'one', stdProcessId: 41, processId: 41, processName: '冲型' }],
    materialRows: [] as any[], materials: '[]', saveState: 'synced' })
  materials.addMaterialRow(pc, 'one')
  return { readonlyMode, materials, pc, row: pc.materialRows[0] }
}
beforeEach(() => {
  vi.clearAllMocks()
  api.saveScheme.mockResolvedValue({})
  api.listProcesses.mockResolvedValue({ data: [] })
})

describe('公共搜索结果回填工序材料', () => {
  it('选中完整对象更新身份快照，模数/基数/跳距/子工序归属保留；清空同步解绑', () => {
    const { materials, row } = fixture()
    Object.assign(row, { moduleQty: 3, baseQty: 9, split: '50mm' })
    materials.onMaterialSelected(row, PET)
    expect(row).toMatchObject({ materialId: 11, materialCode: 'MAT-PET', name: 'PET', spec: '008', unit: 'M',
      moduleQty: 3, baseQty: 9, split: '50mm', processItemKey: 'one' })
    materials.clearMaterialRow(row)
    expect(row).toMatchObject({ materialId: undefined, materialCode: '', name: '', spec: '', unit: 'PCS',
      moduleQty: 3, baseQty: 9, split: '50mm', processItemKey: 'one' })
  })
  it('建档预填搜索关键词；成功回填目标行，保留另一行和数量归属', () => {
    const { materials, pc, row } = fixture()
    materials.addMaterialRow(pc, 'one')
    row._matKw = 'PET 008'; row.baseQty = 6
    materials.openMaterialCreate(pc, row)
    expect(materials.materialPreset.value.materialName).toBe('PET 008')
    materials.onMaterialCreated(PET)
    expect(row).toMatchObject({ name: 'PET', materialId: 11, spec: '008', baseQty: 6, processItemKey: 'one' })
    expect(pc.materialRows[1].materialId).toBeUndefined()
  })
  it('历史行进入编辑仍回显档案快照；只读禁止加行、选择和清空', () => {
    const { materials, readonlyMode, pc } = fixture()
    pc.editing = false; pc.materialRows = []
    pc.materials = JSON.stringify([{ name: 'PET', spec: '008', materialId: 11, materialCode: 'MAT-PET',
      unit: 'M', baseQty: 9, moduleQty: 3, qty: 3 }])
    materials.startEdit(pc)
    expect(pc.materialRows[0]).toMatchObject({ name: 'PET', materialId: 11, baseQty: 9, moduleQty: 3 })
    readonlyMode.value = true
    materials.addMaterialRow(pc)
    materials.clearMaterialRow(pc.materialRows[0])
    materials.onMaterialSelected(pc.materialRows[0], { ...PET, materialId: 99 })
    expect(pc.materialRows).toHaveLength(1)
    expect(pc.materialRows[0].materialId).toBe(11)
  })
})

describe('BOM公共选择器结果写回及整单保存', () => {
  it('换料写回正确子工序，独立材料单独保存，清空保留数量和归属', async () => {
    const { readonlyMode, pc } = fixture()
    pc.items.push({ itemKey: 'two', stdProcessId: 42, processId: 42, processName: '组装' })
    pc.materialRows = pc.items.map(item => assignMaterial({ name: '旧材料', materialId: 1, spec: '旧规格',
      qty: 2, unit: 'M', moduleQty: 2, baseQty: 4, split: '50mm' }, item))
    const context = { readonlyMode, orderId: ref(10), card: ref({ sampleRound: 1, engineeringNote: '' }),
      planList: ref<any[]>([pc]), printList: ref<any[]>([]), markOwnerDirty: vi.fn() }
    const bom = useSampleBom(context)
    const persistence = usePlanPersistence({ ...context, standaloneMaterials: bom.standaloneMaterials,
      note: ref(''), allProcesses: ref<any[]>([]), makeCard: vi.fn(), makePrintRow: vi.fn(),
      loadBom: vi.fn(), refreshCard: vi.fn(), loadSummary: vi.fn(), canSave: () => true })
    persistence.markSaved()
    const target = bom.bomList.value[1]
    bom.onBomMaterialSelected(target, PET)
    expect(pc.materialRows[0].materialId).toBe(1)
    expect(pc.materialRows[1]).toMatchObject({ materialId: 11, name: 'PET', spec: '008', processItemKey: 'two',
      baseQty: 4, moduleQty: 2, split: '50mm' })
    expect(context.markOwnerDirty).toHaveBeenCalledWith(pc)
    bom.addMaterial()
    const standalone = bom.bomList.value.find(row => row.ownerUid == null)!
    bom.onBomMaterialSelected(standalone, PET)
    expect(await persistence.savePlan()).toBe(true)
    const payload = api.saveScheme.mock.calls[0][1]
    const secondMaterials = JSON.parse(payload.items[1].materials)
    expect(secondMaterials).toHaveLength(2)
    expect(secondMaterials.find((row: any) => row.processItemKey === 'two')).toMatchObject({ materialId: 11, name: 'PET', spec: '008', processItemKey: 'two' })
    expect(payload.standaloneMaterials[0]).toMatchObject({ materialId: 11, materialName: 'PET', specification: '008' })
    bom.clearBomMaterial(bom.bomList.value.find(row => row.ownerUid == null)!)
    expect(bom.standaloneMaterials.value[0]).toMatchObject({ materialId: undefined, name: '', spec: '', baseQty: 1 })
  })
})


describe('批量添加材料使用完整公共候选', () => {
  it('确认才添加到选中卡片，保持原数量/归属；重新打开清除上次候选', () => {
    const scope = effectScope()
    const workbench = scope.run(() => useSampleWorkbench())!
    const { pc } = fixture()
    workbench.planList.value = [pc, { ...pc, uid: 'pc-2', materialRows: [], materials: '[]' }]
    workbench.batchSelected.value = new Set(['pc-1'])
    workbench.openBatchMaterial()
    workbench.batchMaterial.value = PET
    expect(pc.materialRows).toHaveLength(1)
    workbench.confirmBatchMaterial()
    expect(pc.materialRows).toHaveLength(2)
    expect(pc.materialRows[1]).toMatchObject({ name: 'PET', spec: '008', materialId: 11,
      materialCode: 'MAT-PET', unit: 'M', baseQty: 1, moduleQty: 1, projectIds: [41] })
    expect(workbench.planList.value[1].materialRows).toHaveLength(0)
    expect(workbench.batchMaterialVisible.value).toBe(false)
    workbench.openBatchMaterial()
    expect(workbench.batchMaterial.value).toBeNull()
    scope.stop()
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref } from 'vue'
import {
  assignMaterial,
  hydrateProcessItems,
  materialOwner,
  mergeProcessMaterials,
  processParams,
  workbenchProcessItems,
} from '../sampleProcessGroups'
import { usePlanPersistence } from '../usePlanPersistence'
import { useProcessMaterials } from '../useProcessMaterials'
import { useSampleBom } from '../useSampleBom'
import { usePlanCopy } from '../usePlanCopy'
const api = vi.hoisted(() => ({ saveScheme: vi.fn(), listProcesses: vi.fn(), listBom: vi.fn() }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('@/api/inventory/material', () => ({ materialApi: {} }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: {},
}))
const mat = (name = '同一种材料') => ({
  name,
  spec: '125',
  baseQty: 2,
  moduleQty: 3,
  split: '30mm',
  materialId: 8,
  unit: 'PCS',
})
function fixture() {
  const items = hydrateProcessItems([
    { stdProcessId: 11, processName: '面板', processId: 41 },
    { stdProcessId: 12, processName: '下线', processId: 42 },
  ])
  const c = {
    readonlyMode: ref(false),
    orderId: ref(10),
    card: ref({ sampleRound: 1 }),
    note: ref(''),
    planList: ref<any[]>([
      {
        uid: 'card',
        items,
        materials: null,
        materialRows: [],
        editing: true,
        operationRemark: '组合说明',
      },
    ]),
    printList: ref<any[]>([]),
    standaloneMaterials: ref<any[]>([]),
    allProcesses: ref<any[]>([]),
    makeCard: (rows: any[], extra: any) => ({
      ...extra,
      items: workbenchProcessItems(rows),
      editing: false,
      materialRows: [],
    }),
    makePrintRow: (row: any) => row,
    loadBom: vi.fn(),
    refreshCard: vi.fn(),
    loadSummary: vi.fn(),
  }
  const p = usePlanPersistence(c)
  p.markSaved()
  return { c, p }
}
beforeEach(() => {
  vi.clearAllMocks()
  api.saveScheme.mockResolvedValue({})
  api.listProcesses.mockResolvedValue({ data: [] })
})
describe('子工序材料归属与持久化', () => {
  it('新增标准工序不带执行ID，已有执行ID及显式自定义工序保持原值', () => {
    const rows = workbenchProcessItems([
      { processId: 11, processName: '新增标准工序' },
      { stdProcessId: 12, processId: 42, processNote: '原作业说明', indexNumber: 7 },
      { stdProcessId: null, processId: 43, processName: '历史自定义' },
    ])
    expect(rows.map((row) => row.stdProcessId)).toEqual([11, 12, null])
    expect(rows.map((row) => row.processId)).toEqual([null, 42, 43])
    expect(rows[1].workInstruction).toBe('原作业说明')
    expect(rows[1].indexNumber).toBe(7)
  })
  it('历史共用材料只取一次且模糊归属保留待分配，分行来源各归所属工序', () => {
    const shared = JSON.stringify([mat()]),
      items = [
        { stdProcessId: 11, materials: shared },
        { stdProcessId: 12, materials: shared },
      ]
    const rows = JSON.parse(mergeProcessMaterials(items)!)
    expect(rows).toHaveLength(1)
    expect(materialOwner({ items: hydrateProcessItems(items) }, rows[0])).toBeUndefined()
    const distinct = JSON.parse(
      mergeProcessMaterials([
        { ...items[0], materials: JSON.stringify([mat('面板料')]) },
        { ...items[1], materials: JSON.stringify([mat('下线料')]) },
      ])!
    )
    expect(distinct.map((row: any) => row.projectIds)).toEqual([[11], [12]])
  })
  it('同材料跨子工序不合并，稳定键不随删除前项和后端ID变化而漂移', () => {
    const items = hydrateProcessItems([{ stdProcessId: 11 }, { stdProcessId: 12 }]),
      rows = items.map((item) => assignMaterial(mat(), item))
    expect(
      JSON.parse(
        mergeProcessMaterials(items.map((item) => ({ ...item, materials: JSON.stringify(rows) })))!
      )
    ).toHaveLength(2)
    const restored = hydrateProcessItems([
      { processId: 999, customProcessParams: processParams(items[1]) },
    ])
    expect(materialOwner({ items: restored }, rows[1])).toBe(restored[0])
    expect(materialOwner({ items: restored }, rows[0])).toBeUndefined()
  })
  it('指定子工序新增，取消修改还原材料和作业说明，只读禁止新增', () => {
    const { c } = fixture(),
      scope = effectScope()
    let n = 0
    const editor = scope.run(() => useProcessMaterials(c.readonlyMode, () => String(++n)))!,
      pc = c.planList.value[0]
    pc.editing = false
    editor.startEdit(pc)
    editor.addMaterialRow(pc, pc.items[1].itemKey)
    expect(materialOwner(pc, pc.materialRows.at(-1))).toBe(pc.items[1])
    pc.items[1].workInstruction = '临时说明'
    editor.cancelEdit(pc)
    expect(pc.items[1].workInstruction).toBeUndefined()
    expect(pc.materialRows).toEqual([])
    c.readonlyMode.value = true
    editor.addMaterialRow(pc, pc.items[0].itemKey)
    expect(pc.materialRows).toEqual([])
    scope.stop()
  })
  it('保存及回读保留说明、两级备注、归属、用量跳距和未知扩展参数', async () => {
    const { c, p } = fixture(),
      pc = c.planList.value[0]
    pc.items[0].workInstruction = '冲窗口灯孔'
    pc.items[1].workInstruction = '线路外形'
    pc.items.forEach((item: any) => (item.subProcessRemark = item.processName + '说明'))
    pc.items[0].customProcessParams = '{"retained":"原扩展"}'
    pc.materialRows = pc.items.map((item: any, i: number) =>
      assignMaterial({ ...mat(), baseQty: i + 1, split: `${30 + i}mm` }, item)
    )
    api.saveScheme.mockImplementation((_id, body) => {
      api.listProcesses.mockResolvedValue({ data: body.items })
      return Promise.resolve({})
    })
    expect(await p.savePlan()).toBe(true)
    const payload = api.saveScheme.mock.calls[0][1].items
    expect(payload.map((row: any) => row.processNote)).toEqual(['冲窗口灯孔', '线路外形'])
    expect(payload.map((row: any) => row.remark)).toEqual(['组合说明', '组合说明'])
    expect(JSON.parse(payload[0].customProcessParams).retained).toBe('原扩展')
    const restored = c.planList.value[0],
      rows = JSON.parse(restored.materials)
    expect(rows).toHaveLength(2)
    expect(rows.map((row: any) => materialOwner(restored, row)?.subProcessRemark)).toEqual([
      '面板说明',
      '下线说明',
    ])
    expect(rows.map((row: any) => row.projectIds)).toEqual([[11], [12]])
    expect(rows.map((row: any) => row.split)).toEqual(['30mm', '31mm'])
    expect(p.hasUnsavedChanges.value).toBe(false)
  })
  it('未分配材料阻止提交并保留草稿，分配后才可提交', async () => {
    const { c, p } = fixture(),
      pc = c.planList.value[0]
    pc.materialRows.push(mat())
    expect(await p.savePlan()).toBe(false)
    expect(api.saveScheme).not.toHaveBeenCalled()
    expect(pc.materialRows).toHaveLength(1)
    assignMaterial(pc.materialRows[0], pc.items[0])
    expect(await p.savePlan()).toBe(true)
  })
  it('BOM同材料跨子工序身份独立，改跳距与项目写回正确源行', () => {
    const { c } = fixture(),
      pc = c.planList.value[0]
    pc.materialRows = pc.items.map((item: any) => assignMaterial(mat(), item))
    const bom = useSampleBom(c)
    expect(new Set(bom.bomList.value.map((row) => row.uid)).size).toBe(2)
    const row = bom.bomList.value[1]
    row.split = '60mm'
    bom.updateMaterial(row)
    expect(pc.materialRows[0].split).toBe('30mm')
    expect(pc.materialRows[1].split).toBe('60mm')
    const move = bom.bomList.value[1]
    move.projectIds = [11]
    bom.updateMaterial(move)
    expect(materialOwner(pc, pc.materialRows[1])).toBe(pc.items[0])
  })
  it('跨单与历史轮复制保留同材料的不同归属和子工序备注', async () => {
    for (const round of [1, null]) {
      const { c } = fixture(),
        pc = c.planList.value[0],
        rows = pc.items.map((item: any) => assignMaterial(mat(), item))
      api.listProcesses.mockResolvedValue({
        data: pc.items.map((item: any) => ({
          ...item,
          subProcessRemark: undefined,
          processOrder: 1,
          materials: JSON.stringify(rows),
          customProcessParams: processParams({ ...item, subProcessRemark: '来源说明' }),
        })),
      })
      api.listBom.mockResolvedValue({ data: [] })
      const copy = usePlanCopy({
        ...c,
        genUid: () => 'copy',
        parseMaterials: (json) => JSON.parse(json || '[]'),
      })
      copy.copyOverwrite.value = true
      if (round) await copy.copyRoundFrom(round)
      else {
        copy.historySelected.value = { orderId: 20 }
        await copy.confirmHistoryCopy()
      }
      const restored = c.planList.value[0],
        materials = JSON.parse(restored.materials)
      expect(materials).toHaveLength(2)
      expect(materials.map((row: any) => materialOwner(restored, row)?.processName)).toEqual([
        '面板',
        '下线',
      ])
      expect(restored.items.every((item: any) => item.subProcessRemark === '来源说明')).toBe(true)
    }
  })
})

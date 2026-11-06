import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePlanCopy } from '../usePlanCopy'
import { useSampleBom } from '../useSampleBom'

const api = vi.hoisted(() => ({ listBom: vi.fn(), listProcesses: vi.fn(), getProductPlan: vi.fn() }))
const message = vi.hoisted(() => ({ alert: vi.fn(() => Promise.resolve()), success: vi.fn(), warning: vi.fn(), error: vi.fn() }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('@/api/inventory/material', () => ({ materialApi: {} }))
vi.mock('element-plus', () => ({
  ElMessage: { success: message.success, warning: message.warning, error: message.error },
  ElMessageBox: { alert: message.alert, confirm: vi.fn(), prompt: vi.fn() },
}))

function fixture() {
  const context = {
    orderId: ref(2),
    card: ref({ sampleRound: 1 }),
    readonlyMode: ref(false),
    planList: ref<any[]>([]),
    printList: ref<any[]>([]),
    standaloneMaterials: ref<any[]>([]),
    allProcesses: ref<any[]>([]),
    makeCard: (items: any[], extra: any = {}) => ({ uid: `card-${items[0]?.processOrder}`, items, ...extra }),
    makePrintRow: (row: any) => row,
    genUid: () => 'uid',
    parseMaterials: (json?: string | null) => JSON.parse(json || '[]'),
  }
  const bom = useSampleBom(context)
  context.standaloneMaterials = bom.standaloneMaterials
  const copy = usePlanCopy(context)
  return { context, copy, bom }
}

const plan = {
  eligible: true,
  processCount: 14,
  materialCount: 13,
  rows: [
    {
      processOrder: 1,
      processName: '面板冲孔',
      majorCategory: 'ASSEMBLY',
      materials: null,
    },
    {
      processOrder: 2,
      processName: '下线',
      majorCategory: 'ASSEMBLY',
      materials: null,
    },
  ],
  unlinkedMaterials: [
    { name: 'F200', spec: '', qty: 0.125, unit: 'M', isCommon: false, processId: 17 },
    { name: 'PET-125', spec: '', qty: 0.25, unit: 'M', isCommon: false, processId: 38 },
    { name: 'PC-0.254', spec: '0.254', qty: 0.0167, unit: 'M', isCommon: false, processId: 78 },
    { name: 'SDT-25', spec: '', qty: 0.5, unit: 'M', isCommon: false },
    { name: 'PET-100白色', spec: '', qty: 0.5, unit: 'ROLL', isCommon: false },
    { name: 'PET保护膜-WG008', spec: '', qty: 0.5, unit: 'ROLL', isCommon: true },
  ],
}

beforeEach(() => vi.clearAllMocks())

describe('成品方案复制：材料以 BOM 为准整份带出（dev-20261102-004）', () => {
  it('基数3/模数4原样复制，不被默认1/1覆盖', async () => {
    api.getProductPlan.mockResolvedValue({ data: {
      ...plan, unlinkedMaterials: [{ name: '基材', qty: 0.75, baseQty: 3, moduleQty: 4 }],
    } })
    const { context, copy, bom } = fixture()
    await copy.loadProductPlan()
    await copy.confirmProductPlanCopy()
    expect(context.standaloneMaterials.value[0]).toMatchObject({ baseQty: 3, moduleQty: 4, qty: 0.75 })
    expect(bom.bomList.value[0]).toMatchObject({ baseQty: 3, moduleQty: 4, qty: 0.75 })
  })

  it.each([
    { baseQty: null, moduleQty: null },
    {},
    { baseQty: 3, moduleQty: null },
    { baseQty: null, moduleQty: 4 },
  ])('源基/模缺失保持空值，不按用量反推或补1：%j', async (quantities) => {
    api.getProductPlan.mockResolvedValue({ data: {
      ...plan, unlinkedMaterials: [{ name: '待核对基材', qty: 1, ...quantities }],
    } })
    const { context, copy, bom } = fixture()
    await copy.loadProductPlan()
    await copy.confirmProductPlanCopy()
    expect(context.standaloneMaterials.value[0]).toMatchObject({
      baseQty: quantities.baseQty ?? null, moduleQty: quantities.moduleQty ?? null,
    })
    expect(bom.bomList.value[0]).toMatchObject({
      baseQty: quantities.baseQty ?? null, moduleQty: quantities.moduleQty ?? null,
    })
  })

  it.each(['round', 'order'])('历史轮次/其他样品的3/4复制行为不变：%s', async (source) => {
    api.listProcesses.mockResolvedValue({ data: plan.rows })
    api.listBom.mockResolvedValue({ data: [{
      roundNo: 1, materialName: '历史基材', quantity: 0.75, baseQty: 3, moduleQty: 4,
    }] })
    const { context, copy } = fixture()
    if (source === 'round') {
      await copy.copyRoundFrom(1)
    } else {
      copy.historySelected.value = { orderId: 9, sampleRound: 1 }
      await copy.confirmHistoryCopy()
    }
    expect(context.standaloneMaterials.value[0]).toMatchObject({ baseQty: 3, moduleQty: 4, qty: 0.75 })
  })

  it('挂不上工序的材料进「独立材料」，不再暂挂首道工序', async () => {
    api.getProductPlan.mockResolvedValue({ data: plan })
    const { context, copy } = fixture()
    await copy.loadProductPlan()
    await copy.confirmProductPlanCopy()

    // 工序行不再带材料（BOM 复制 BOM，不挂工序）
    expect(context.planList.value[0].materials).toBeNull()
    expect(context.planList.value[1].materials).toBeNull()
    // BOM 材料整份进「独立材料」，名称与单位保留
    expect(context.standaloneMaterials.value.map((m: any) => m.name)).toEqual([
      'F200',
      'PET-125',
      'PC-0.254',
      'SDT-25',
      'PET-100白色',
      'PET保护膜-WG008',
    ])
    expect(context.standaloneMaterials.value.map((m: any) => m.unit)).toEqual(['M', 'M', 'M', 'M', 'ROLL', 'ROLL'])
    // 「项目」列跟着带出：有 processId 的材料落成 projectIds
    expect(context.standaloneMaterials.value[0].projectIds).toEqual([17])
    expect(context.standaloneMaterials.value[1].projectIds).toEqual([38])
    expect(context.standaloneMaterials.value[2].projectIds).toEqual([78])
    // 提示文案指向独立材料
    expect(message.alert).toHaveBeenCalled()
    expect(String(message.alert.mock.calls[0][0])).toContain('独立材料')
  })

  it('覆盖复制时先清空旧独立材料，追加复制时保留已有行', async () => {
    api.getProductPlan.mockResolvedValue({ data: plan })
    const { context, copy } = fixture()
    context.standaloneMaterials.value = [{ name: '手工旧行', qty: 1, unit: 'PCS' }]
    await copy.loadProductPlan()

    copy.copyOverwrite.value = false
    await copy.confirmProductPlanCopy()
    expect(context.standaloneMaterials.value.map((m: any) => m.name)).toContain('手工旧行')

    copy.copyOverwrite.value = true
    await copy.confirmProductPlanCopy()
    expect(context.standaloneMaterials.value.map((m: any) => m.name)).not.toContain('手工旧行')
    expect(context.standaloneMaterials.value).toHaveLength(6)
  })
})

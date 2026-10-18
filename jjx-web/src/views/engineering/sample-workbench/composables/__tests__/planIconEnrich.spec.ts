/**
 * 打样计划图标 enrich 定向测试（dev-20261018-014）
 * A：标准工序列表就绪后才 enrich（不依赖加载顺序）
 * B：列表缺失的 stdProcessId 按 id 单条补齐（缓存、只查缺失、单条失败不阻塞）
 * C：列表加载失败不阻塞计划加载
 *
 * 运行（带内存笼子）：
 *   cd /home/administrator/jjx/jjx-web
 *   systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- timeout 120 npx vitest run
 *     src/views/engineering/sample-workbench/composables/__tests__/planIconEnrich.spec.ts --maxWorkers=1
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

const { listProcesses } = vi.hoisted(() => ({ listProcesses: vi.fn() }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: { listProcesses } }))

import { usePlanPersistence } from '../usePlanPersistence'

function setup(opts: any = {}) {
  const allProcesses = ref<any[]>(opts.processes || [])
  const planList = ref<any[]>([])
  const printList = ref<any[]>([])
  const standaloneMaterials = ref<any[]>([])
  const card = ref<any>({ sampleRound: 1 })
  const note = ref('')
  const ensureProcessesLoaded = opts.ensure || vi.fn().mockResolvedValue(undefined)
  const resolveProcessById = opts.resolve || vi.fn().mockResolvedValue(null)
  listProcesses.mockResolvedValue({ data: opts.rows || [] })
  const api = usePlanPersistence({
    readonlyMode: ref(false),
    orderId: ref(1),
    card,
    note,
    planList,
    printList,
    standaloneMaterials,
    allProcesses,
    makeCard: (items: any[], extra: any = {}) => ({ ...extra, items }),
    makePrintRow: (src: any, order: number) => ({ ...src, processOrder: order }),
    loadBom: vi.fn(),
    refreshCard: vi.fn(),
    loadSummary: vi.fn(),
    ensureProcessesLoaded,
    resolveProcessById,
  })
  return { api, allProcesses, planList, printList, ensureProcessesLoaded, resolveProcessById }
}

const process17 = {
  processId: 17,
  processName: '面板',
  processType: 'FORM',
  processCategory: 'PANEL',
  icon: '面板',
  hasIndex: 0,
  hasWorkInstruction: 0,
}

beforeEach(() => {
  listProcesses.mockReset()
})

describe('loadPlan 图标 enrich（dev-20261018-014）', () => {
  it('A：先等标准工序列表就绪再 enrich，列表未就绪时不先渲染计划', async () => {
    let release!: () => void
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    const ensure = vi.fn(() => gate)
    const { api, planList } = setup({
      processes: [process17],
      rows: [{ stdProcessId: 17, processName: '面板', processOrder: 1, majorCategory: 'ASSEMBLY' }],
      ensure,
    })

    const loading = api.loadPlan()
    await new Promise((r) => setTimeout(r, 0))
    // ensure 未放行 → 还没 enrich，也没有先渲染出「工」
    expect(planList.value.length).toBe(0)

    release()
    await loading
    expect(planList.value.length).toBe(1)
    expect(planList.value[0].items[0].icon).toBe('面板')
  })

  it('B：列表缺失的 stdProcessId 按 id 补查后再显示图标', async () => {
    const resolve = vi.fn(async (id: number) => ({
      processId: id,
      processName: '停用工序' + id,
      processType: 'FORM',
      processCategory: 'PANEL',
      icon: '图标' + id,
      hasIndex: 1,
      hasWorkInstruction: 0,
    }))
    const { api, planList } = setup({
      processes: [],
      rows: [
        { stdProcessId: 17, processName: 'A', processOrder: 1, majorCategory: 'ASSEMBLY' },
        { stdProcessId: 20, processName: 'B', processOrder: 1, majorCategory: 'ASSEMBLY' },
      ],
      resolve,
    })

    await api.loadPlan()
    expect(resolve).toHaveBeenCalledTimes(2)
    expect(planList.value[0].items[0].icon).toBe('图标17')
    expect(planList.value[0].items[1].icon).toBe('图标20')
  })

  it('B：只补缺失项并缓存，重复加载不再请求', async () => {
    const resolve = vi.fn(async (id: number) => ({ processId: id, processName: 'X', icon: 'I' + id }))
    const { api } = setup({
      processes: [process17],
      rows: [
        { stdProcessId: 17, processOrder: 1, majorCategory: 'ASSEMBLY' },
        { stdProcessId: 99, processOrder: 2, majorCategory: 'ASSEMBLY' },
      ],
      resolve,
    })

    await api.loadPlan()
    expect(resolve).toHaveBeenCalledTimes(1)
    expect(resolve).toHaveBeenCalledWith(99)

    await api.loadPlan()
    expect(resolve).toHaveBeenCalledTimes(1)
  })

  it('B：单条补查失败不影响其它行，PRINT 行仍拆分到印刷列表', async () => {
    const resolve = vi.fn(async (id: number) => {
      if (id === 99) throw new Error('lookup failed')
      return { processId: id, processName: 'X', icon: 'I' }
    })
    const { api, planList, printList } = setup({
      processes: [],
      rows: [
        { stdProcessId: 17, processOrder: 1, majorCategory: 'ASSEMBLY' },
        { stdProcessId: 99, processOrder: 2, majorCategory: 'ASSEMBLY' },
        { stdProcessId: null, processName: '印刷A', processOrder: 3, majorCategory: 'PRINT' },
      ],
      resolve,
    })

    await api.loadPlan()
    expect(planList.value.length).toBe(2)
    expect(planList.value[0].items[0].icon).toBe('I')
    expect(planList.value[1].items[0].icon).toBeUndefined()
    expect(printList.value.length).toBe(1)
    expect(printList.value[0].processName).toBe('印刷A')
  })

  it('C：标准工序列表加载失败不阻塞计划加载（已有列表仍可用于 enrich）', async () => {
    const ensure = vi.fn().mockRejectedValue(new Error('list failed'))
    const { api, planList } = setup({
      processes: [process17],
      rows: [{ stdProcessId: 17, processOrder: 1, majorCategory: 'ASSEMBLY' }],
      ensure,
    })

    await api.loadPlan()
    expect(planList.value.length).toBe(1)
    expect(planList.value[0].items[0].icon).toBe('面板')
  })
})

/**
 * 工序材料搜索/回填契约测试（dev-20261018-035）
 *
 * 覆盖：
 * 1）输入关键词不改已选材料身份与档案快照；
 * 2）旧请求晚返回不覆盖新结果（每行独立请求序号）；
 * 3）选中新结果才更新身份/快照，模数、基数等行数据保持；
 * 4）显式清空解除身份与快照，并作废在途请求；
 * 5）搜索失败不冒充零结果（保留候选 + 失败标记 + 可重试）；
 * 6）加载更多沿用同一关键词、去重、防重复翻页。
 *
 * 运行（带内存笼子，禁止裸跑）：
 *   cd /home/administrator/jjx/jjx-web
 *   systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- timeout 120 npx vitest run \
 *     src/views/engineering/sample-workbench/composables/__tests__/processMaterialSearch.spec.ts --maxWorkers=1
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, reactive, ref } from 'vue'

vi.mock('@/api/inventory/material', () => ({ materialApi: { search: vi.fn() } }))

import { materialApi } from '@/api/inventory/material'
import { useProcessMaterials } from '../useProcessMaterials'

const searchMock = vi.mocked(materialApi.search)

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => (resolve = r))
  return { promise, resolve }
}

function page(records: any[], total = records.length) {
  return { data: { records, total } }
}

const A = { materialId: 1, materialName: '铜箔A', specification: '35um', materialCode: 'MAT-A', unit: 'M' }
const B = { materialId: 2, materialName: '铜箔B', specification: '70um', materialCode: 'MAT-B', unit: 'M' }

function fixture() {
  const scope = effectScope()
  const materials = scope.run(() => useProcessMaterials(ref(false), (() => {
    let n = 0
    return () => `uid-${++n}`
  })()))!
  const pc = { items: [{ stdProcessId: 41 }], materialRows: [] as any[], materials: '[]' }
  materials.addMaterialRow(pc)
  const row: any = pc.materialRows[0]
  return { scope, materials, pc, row }
}

let ctx: ReturnType<typeof fixture>
beforeEach(() => { searchMock.mockReset(); ctx = fixture() })
afterEach(() => { ctx.scope.stop() })

describe('工序材料搜索：关键词与已选身份分离', () => {
  it('选中后只改输入关键词，不动 materialId / 编码 / 规格 / 单位', async () => {
    const { materials, row } = ctx
    searchMock.mockResolvedValueOnce(page([A]))
    await materials.searchMaterials('铜箔', row, 1, false)
    materials.onMaterialSelected(row, A)
    expect(row).toMatchObject({ materialId: 1, materialCode: 'MAT-A', name: '铜箔A', spec: '35um', unit: 'M' })

    searchMock.mockResolvedValueOnce(page([B]))
    await materials.searchMaterials('铜箔B', row, 1, false)

    // 只是在搜新词：身份与快照保持原样
    expect(row.materialId).toBe(1)
    expect(row.materialCode).toBe('MAT-A')
    expect(row.name).toBe('铜箔A')
    expect(row.spec).toBe('35um')
    expect(row.unit).toBe('M')
    expect(row.lastQuery).toBe('铜箔B')
    expect(row.options.map((o: any) => o.materialId)).toEqual([2])
  })

  it('选中新结果时才切换身份与档案快照，模数、基数不被换料覆盖', async () => {
    const { materials, row } = ctx
    row.moduleQty = 3
    row.baseQty = 10
    materials.onMaterialSelected(row, B)

    expect(row).toMatchObject({ materialId: 2, materialCode: 'MAT-B', name: '铜箔B', spec: '70um', unit: 'M' })
    expect(row.moduleQty).toBe(3)
    expect(row.baseQty).toBe(10)
    // 选中项补进候选，供面板高亮
    expect(row.options.some((o: any) => o.materialId === 2)).toBe(true)
  })

  it('显式清空解除身份与关联快照，不留旧ID配新名称', () => {
    const { materials, row } = ctx
    materials.onMaterialSelected(row, A)
    materials.clearMaterialRow(row)

    expect(row.materialId).toBeUndefined()
    expect(row.materialCode).toBe('')
    expect(row.name).toBe('')
    expect(row.spec).toBe('')
    expect(row.options).toEqual([])
  })
})

describe('工序材料搜索：并发与失败', () => {
  it('旧请求晚返回不覆盖新结果，也不把 loading 卡死', async () => {
    const { materials, row } = ctx
    const slow = deferred<any>()
    const fast = deferred<any>()
    searchMock.mockReturnValueOnce(slow.promise).mockReturnValueOnce(fast.promise)

    const p1 = materials.searchMaterials('铜', row, 1, false)
    const p2 = materials.searchMaterials('铜箔B', row, 1, false)

    fast.resolve(page([B]))
    await p2
    expect(row.options.map((o: any) => o.materialId)).toEqual([2])
    expect(row.loading).toBe(false)

    slow.resolve(page([A]))
    await p1
    expect(row.options.map((o: any) => o.materialId)).toEqual([2]) // 旧响应整包丢弃
    expect(row.lastQuery).toBe('铜箔B')
    expect(row.loading).toBe(false)
  })

  it('搜索失败保留候选并标记 searchFailed，重试成功后清除标记', async () => {
    const { materials, row } = ctx
    searchMock.mockResolvedValueOnce(page([A]))
    await materials.searchMaterials('铜箔', row, 1, false)

    searchMock.mockRejectedValueOnce(new Error('网络错误'))
    await materials.searchMaterials('铜箔X', row, 1, false)
    expect(row.searchFailed).toBe(true)
    expect(row.loading).toBe(false)
    expect(row.options.length).toBe(1) // 不冒充零结果

    searchMock.mockResolvedValueOnce(page([B]))
    await materials.searchMaterials(row.lastQuery, row, 1, false)
    expect(row.searchFailed).toBe(false)
    expect(row.options.map((o: any) => o.materialId)).toEqual([2])
  })

  it('清空后旧请求晚返回也不能写回已解绑的行', async () => {
    const { materials, row } = ctx
    const pending = deferred<any>()
    searchMock.mockReturnValueOnce(pending.promise)
    const p = materials.searchMaterials('铜箔', row, 1, false)

    materials.clearMaterialRow(row)
    pending.resolve(page([A]))
    await p

    expect(row.materialId).toBeUndefined()
    expect(row.name).toBe('')
    expect(row.options).toEqual([])
    expect(row.loading).toBe(false)
  })

  it('加载更多沿用同一关键词、翻页追加去重、重复点击不并页', async () => {
    const { materials, row } = ctx
    const first = Array.from({ length: 20 }, (_, i) => ({ materialId: i + 1, materialName: `M${i + 1}` }))
    searchMock.mockResolvedValueOnce(page(first, 40))
    await materials.searchMaterials('M', row, 1, false)
    expect(row.options.length).toBe(20)
    expect(row.total).toBe(40)

    searchMock.mockResolvedValueOnce(page([...first.slice(0, 5), { materialId: 21, materialName: 'M21' }]))
    await materials.loadMoreMaterials(row)

    const second = searchMock.mock.calls[searchMock.mock.calls.length - 1][0] as any
    expect(second.materialName).toBe('M') // 与第一页同关键词
    expect(second.pageNum).toBe(2)
    expect(row.options.length).toBe(21) // 重复项不再追加
    expect(row.options[row.options.length - 1].materialId).toBe(21)

    // 已加载到 total 时不再翻页
    row.total = 21
    const before = searchMock.mock.calls.length
    await materials.loadMoreMaterials(row)
    expect(searchMock.mock.calls.length).toBe(before)
  })

  it('输入防抖：连续输入只按最后一个关键词请求一次', async () => {
    vi.useFakeTimers()
    try {
      const { materials, row } = ctx
      searchMock.mockResolvedValue(page([A]))
      materials.debouncedSearchMaterials('铜', row, 1, false)
      materials.debouncedSearchMaterials('铜箔', row, 1, false)
      expect(searchMock).not.toHaveBeenCalled()

      vi.advanceTimersByTime(320)
      await vi.runOnlyPendingTimersAsync()
      expect(searchMock).toHaveBeenCalledTimes(1)
      expect((searchMock.mock.calls[0][0] as any).materialName).toBe('铜箔')
    } finally {
      vi.useRealTimers()
    }
  })
})

describe('工序材料搜索：行间隔离与历史回显', () => {
  it('多行并行搜索各自回填本行，不串行也不互相覆盖', async () => {
    const { materials, pc } = ctx
    materials.addMaterialRow(pc)
    const [row1, row2] = pc.materialRows as any[]
    const d1 = deferred<any>()
    const d2 = deferred<any>()
    searchMock.mockReturnValueOnce(d1.promise).mockReturnValueOnce(d2.promise)

    const p1 = materials.searchMaterials('铜箔A', row1, 1, false)
    const p2 = materials.searchMaterials('铜箔B', row2, 1, false)

    d2.resolve(page([B]))
    d1.resolve(page([A]))
    await Promise.all([p1, p2])

    expect(row1.options.map((o: any) => o.materialId)).toEqual([1])
    expect(row2.options.map((o: any) => o.materialId)).toEqual([2])
    expect(row1.lastQuery).toBe('铜箔A')
    expect(row2.lastQuery).toBe('铜箔B')
  })

  it('删行后旧响应晚返回不会复活该行或写回别的行', async () => {
    const { materials, pc } = ctx
    materials.addMaterialRow(pc)
    const [row1, row2] = pc.materialRows as any[]
    const pending = deferred<any>()
    searchMock.mockReturnValueOnce(pending.promise)
    const p = materials.searchMaterials('铜箔', row1, 1, false)

    pc.materialRows.splice(0, 1) // 删掉正在搜的行
    pending.resolve(page([A]))
    await p

    expect(pc.materialRows.length).toBe(1)
    expect(pc.materialRows[0]).toBe(row2)
    expect(row2.options).toEqual([])
    expect(row1.loading).toBe(false)
  })

  it('进入编辑按已存材料回显名称并保留模数/基数，不显示裸ID', () => {
    const { materials } = ctx
    const pc = reactive({
      items: [{ stdProcessId: 41 }],
      materialRows: [] as any[],
      editing: false,
      materials: JSON.stringify([{ name: '铜箔A', spec: '35um', materialId: 7, materialCode: 'MAT-7', unit: 'M', baseQty: 9, moduleQty: 4, qty: 2.25 }]),
    })
    materials.startEdit(pc)

    const row: any = pc.materialRows[0]
    expect(pc.editing).toBe(true)
    expect(row.name).toBe('铜箔A')
    expect(row.materialId).toBe(7)
    expect(row.moduleQty).toBe(4)
    expect(row.baseQty).toBe(9)
    expect(row.options).toEqual([])
    expect(row.lastQuery).toBe('')
  })

  it('只读模式不进入编辑也不允许加材料行', () => {
    const scope = effectScope()
    const readonly = ref(true)
    const materials = scope.run(() => useProcessMaterials(readonly, () => 'uid-ro'))!
    const pc = reactive({ items: [], materialRows: [] as any[], editing: false, materials: '[]' })
    materials.startEdit(pc)
    materials.addMaterialRow(pc)
    expect(pc.editing).toBe(false)
    expect(pc.materialRows.length).toBe(0)
    scope.stop()
  })
})

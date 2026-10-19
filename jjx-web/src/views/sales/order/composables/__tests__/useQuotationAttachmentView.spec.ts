import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { useQuotationAttachmentView } from '../useQuotationAttachmentView'
import { useAttachmentManagerRows } from '@/components/attachment/useAttachmentManagerRows'
import type { AttachmentSession } from '@/composables/useAttachmentSession'

const api = vi.hoisted(() => ({ listRefs: vi.fn() }))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: api }))
const scopes: ReturnType<typeof effectScope>[] = []
afterEach(() => scopes.splice(0).forEach(scope => scope.stop()))
beforeEach(() => vi.clearAllMocks())
const refs = () => [
  { id: 3, attachmentId: 213, sourceType: 'quotation', sourceId: 2, sourceNo: 'QT261019002', productId: 2, productCode: 'GF1344POOO', documentType: 'CUSTOMER_DRAWING', fileName: '工程图 - 副本.jpg' },
  { id: 4, attachmentId: 214, sourceType: 'quotation', sourceId: 2, sourceNo: 'QT261019002', documentType: 'SALES_QUOTATION_CUSTOMER_EVIDENCE', fileName: '客户需求 - 副本.jpg' },
]
function setup(quotation: number | undefined = 2, order: number | undefined = undefined) {
  const quotationId = ref(quotation)
  const orderId = ref(order)
  const session = {
    canView: ref(true), canEdit: ref(true), canSave: ref(false),
    pending: ref([]), refRows: ref([]), ownerships: ref([]), savingRefIds: ref(new Set()),
    traceOnlyRows: ref([]), legacyOnlyRows: ref([]), hasReadonlyDocs: ref(false),
    traceError: ref(''), traceLoading: ref(false), error: ref(''),
    getChanges: vi.fn(() => ({ uploads: [], updates: [] })),
  } as unknown as AttachmentSession
  const scope = effectScope()
  scopes.push(scope)
  const view = scope.run(() => useQuotationAttachmentView(session, () => quotationId.value, () => orderId.value))!
  const manager = scope.run(() => useAttachmentManagerRows(view, () => ({ sourceType: 'sales_order' })))!
  return { scope, session, view, manager, quotationId, orderId }
}
function deferred() {
  let resolve!: (response: any) => void
  const promise = new Promise<any>(done => { resolve = done })
  return { promise, resolve }
}
describe('报价转订单来源附件展示', () => {
  it('QT261019002两种附件保留来源/产品/引用ID，只读展示且不进入本单保存', async () => {
    api.listRefs.mockResolvedValue({ data: refs() })
    const { view, session, manager } = setup()
    await flushPromises()
    expect(api.listRefs).toHaveBeenCalledExactlyOnceWith('quotation', 2)
    expect(manager.tableRows.value).toHaveLength(2)
    const [drawing, evidence] = manager.tableRows.value
    expect(drawing).toMatchObject({ attachmentId: 213, refId: 3, sourceType: 'quotation', sourceNo: 'QT261019002', productCode: 'GF1344POOO' })
    expect(evidence).toMatchObject({ attachmentId: 214, refId: 4, documentType: 'SALES_QUOTATION_CUSTOMER_EVIDENCE' })
    for (const row of manager.tableRows.value) {
      expect(manager.capabilities(row)).toMatchObject({ canEdit: false, canSelect: false, canRemove: false, canSave: false })
    }
    expect(view.pending).toBe(session.pending)
    expect(view.refRows.value).toEqual(session.refRows.value)
    expect(view.getChanges()).toEqual({ uploads: [], updates: [] })
    expect(session.refRows.value).toEqual([])
  })
  it('新增页报价两条加订单待保存一条合计三条，来源队列不混入保存内容', async () => {
    api.listRefs.mockResolvedValue({ data: refs() })
    const { session, view, manager } = setup()
    const pending = { attachmentId: 221, fileName: '工程图3.jpg', state: 'pending', ownershipKey: 'product:2', productCode: 'GF1344POOO' } as any
    session.pending.value = [pending]
    await flushPromises()
    expect(manager.tableRows.value.map(row => row.attachmentId).sort()).toEqual([213, 214, 221])
    expect(view.pending.value).toEqual([pending])
    expect(session.refRows.value).toEqual([])
    expect(manager.tableRows.value.find(row => row.attachmentId === 221)?.sourceType).toBe('sales_order')
  })

  it('SO261019004重开时显示三文件，旧重复订单引用不覆盖报价来源', async () => {
    api.listRefs.mockResolvedValue({ data: refs() })
    const { session, view, manager } = setup(2, 5)
    session.refRows.value = [
      { ...refs()[0], id: 15, sourceType: 'sales_order', sourceId: 5, sourceNo: 'SO261019004' },
      { id: 14, attachmentId: 221, sourceType: 'sales_order', sourceId: 5, sourceNo: 'SO261019004', productId: 2, productCode: 'GF1344POOO', documentType: 'PRODUCT_SPECIFICATION', fileName: '工程图3.jpg' },
    ]
    await flushPromises()
    const rows = manager.tableRows.value
    expect(rows).toHaveLength(3)
    expect(rows.map(row => [row.attachmentId, row.sourceType, row.sourceNo]).sort()).toEqual([
      [213, 'quotation', 'QT261019002'], [214, 'quotation', 'QT261019002'], [221, 'sales_order', 'SO261019004'],
    ])
    expect(rows.find(row => row.attachmentId === 213)?.refId).toBe(3)
    expect(rows.find(row => row.attachmentId === 214)?.refId).toBe(4)
    expect(manager.capabilities(rows.find(row => row.attachmentId === 221)!)).toMatchObject({ canEdit: true, canRemove: true })
    expect(view.refRows.value.map(row => row.attachmentId)).toEqual([221])
    expect(session.refRows.value.map(row => row.id)).toEqual([15, 14]) // 原引用未修改或删除
  })

  it('没有历史重复引用的新订单仍合并显示报价两条及订单新增一条', async () => {
    api.listRefs.mockResolvedValue({ data: refs() })
    const { session, manager } = setup(2, 5)
    session.refRows.value = [{ id: 14, attachmentId: 221, sourceType: 'sales_order', sourceId: 5, sourceNo: 'SO261019004', fileName: '工程图3.jpg' }]
    await flushPromises()
    expect(manager.tableRows.value.map(row => row.attachmentId).sort()).toEqual([213, 214, 221])
  })

  it('普通新增不查询来源附件', async () => {
    setup(0)
    await flushPromises()
    expect(api.listRefs).not.toHaveBeenCalled()
  })
  it('切换报价清空旧资料，旧回包不能覆盖新来源', async () => {
    const old = deferred()
    api.listRefs.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ data: [{ ...refs()[0], sourceId: 8, sourceNo: 'QT-NEW' }] })
    const { quotationId, view } = setup()
    quotationId.value = 8
    await flushPromises()
    old.resolve({ data: refs() })
    await flushPromises()
    expect(view.traceOnlyRows.value.map(row => row.sourceNo)).toEqual(['QT-NEW'])
  })
  it('订单保存后重新读取明确来源，保留报价附件', async () => {
    const old = deferred()
    api.listRefs.mockReturnValue(old.promise)
    const { orderId, view } = setup()
    orderId.value = 99
    await nextTick()
    old.resolve({ data: refs() })
    await flushPromises()
    expect(view.traceOnlyRows.value.map(row => row.id)).toEqual([213, 214])
    expect(api.listRefs).toHaveBeenCalledTimes(2)
  })
  it('离开页面后忽略回包', async () => {
    const old = deferred()
    api.listRefs.mockReturnValue(old.promise)
    const { scope, view } = setup()
    scope.stop()
    old.resolve({ data: refs() })
    await flushPromises()
    expect(view.traceOnlyRows.value).toEqual([])
  })
  it('加载失败可见；权限撤回清空资料', async () => {
    api.listRefs.mockRejectedValue(new Error('无报价查看权限'))
    const { session, view } = setup()
    await flushPromises()
    expect(view.traceError.value).toContain('来源报价附件加载失败')
    session.canView.value = false
    await flushPromises()
    expect(view.traceError.value).toBe('')
    expect(view.traceOnlyRows.value).toEqual([])
    expect(api.listRefs).toHaveBeenCalledTimes(1)
  })
  it('拒绝混入其他单据、来源或已删除的引用', async () => {
    api.listRefs.mockResolvedValue({ data: [refs()[0], { ...refs()[1], sourceId: 9 }, { ...refs()[1], sourceType: 'sales_order' }, { ...refs()[1], deleted: true }] })
    const { view } = setup()
    await flushPromises()
    expect(view.traceOnlyRows.value.map(row => row.id)).toEqual([213])
  })
})

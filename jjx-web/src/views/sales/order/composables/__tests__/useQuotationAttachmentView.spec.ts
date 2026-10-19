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
    expect(view.refRows).toBe(session.refRows)
    expect(view.getChanges()).toEqual({ uploads: [], updates: [] })
    expect(session.refRows.value).toEqual([])
  })
  it('普通新增及已保存订单不查询来源附件', async () => {
    setup(0)
    setup(2, 99)
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
  it('订单保存后清除来源行，忽略保存前的回包', async () => {
    const old = deferred()
    api.listRefs.mockReturnValue(old.promise)
    const { orderId, view } = setup()
    orderId.value = 99
    await nextTick()
    old.resolve({ data: refs() })
    await flushPromises()
    expect(view.traceOnlyRows.value).toEqual([])
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

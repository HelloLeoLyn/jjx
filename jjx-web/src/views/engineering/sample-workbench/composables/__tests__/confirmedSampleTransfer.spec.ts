import { expect, it, vi } from 'vitest'
import { useSampleTransferStore } from '../useConfirmedSampleTransfer'
const api = vi.hoisted(() => ({ getRounds: vi.fn(), previewConfirmedRound: vi.fn(), confirmArchivedScheme: vi.fn() }))
vi.mock('@/api/sales/sampleOrder', () => ({ sampleOrderApi: api }))
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn() } }))
it('requires explicit confirmed archive and submits preview identity', async () => {
  api.getRounds.mockResolvedValue({ data: [{ roundId: 1, result: 'confirmed' }, { roundId: 2, result: 'confirmed' }, { roundId: 3, result: 'pending' }] })
  const store = useSampleTransferStore()
  await store.loadRounds(8)
  expect(store.confirmedRounds.value.map(r => r.roundId)).toEqual([1, 2])
  expect(api.previewConfirmedRound).not.toHaveBeenCalled()
  await store.confirmTransfer()
  expect(api.confirmArchivedScheme).not.toHaveBeenCalled()
  store.selectedRoundId.value = 1
  api.previewConfirmedRound.mockResolvedValue({ data: { orderId: 8, roundId: 1, confirmedSchemeId: 1, sampleProcesses: [{ matched: true }], sampleMaterials: [] } })
  await store.loadPreview(8)
  api.confirmArchivedScheme.mockResolvedValue({ data: {} })
  await store.confirmTransfer()
  expect(api.previewConfirmedRound).toHaveBeenCalledWith(8, 1)
  expect(api.confirmArchivedScheme.mock.calls[0][0]).toMatchObject({ orderId: 8, roundId: 1, confirmedSchemeId: 1, requestId: expect.any(String) })
})

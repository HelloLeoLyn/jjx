import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Print from '../print.vue'
const state = vi.hoisted(() => ({
  query: {} as Record<string, string>,
  suggestions: vi.fn(),
  log: vi.fn(),
}))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: state.query }),
  useRouter: () => ({ back: vi.fn() }),
}))
vi.mock('@/api/purchase/order', () => ({
  getPlanSuggestions: state.suggestions,
  logPlanPrint: state.log,
}))
beforeEach(() => {
  sessionStorage.clear()
  state.query = { snapshot: 'purchase-plan-print-test' }
  state.log.mockReset()
  state.log.mockResolvedValue({})
  state.suggestions.mockReset()
})
describe('采购计划打印承接当前计划', () => {
  it('读取当前计划快照，不重新加载建议或丢掉手工采购单位', async () => {
    sessionStorage.setItem(
      state.query.snapshot,
      JSON.stringify([
        {
          物料编码: 'RM000350',
          原始需求: 300,
          基本单位: '㎡',
          计划采购量: 3,
          采购单位: '卷',
          折合基本数量: 366,
          采购说明: '余料交回',
        },
      ])
    )
    const w = shallowMount(Print)
    await flushPromises()
    const vm = w.vm as any
    expect(vm.rows[0]).toMatchObject({
      原始需求: 300,
      计划采购量: 3,
      采购单位: '卷',
      折合基本数量: 366,
    })
    expect(state.suggestions).not.toHaveBeenCalled()
    expect(sessionStorage.getItem(state.query.snapshot)).toBeNull()
    w.unmount()
  })
  it('失效快照明确报错，不偷换成另一份采购建议', async () => {
    const w = shallowMount(Print)
    await flushPromises()
    expect((w.vm as any).loadError).toContain('失效')
    expect(state.suggestions).not.toHaveBeenCalled()
    w.unmount()
  })
  it('打印留痕失败不执行打印', async () => {
    sessionStorage.setItem(state.query.snapshot, JSON.stringify([{ 物料编码: 'AUX000106' }]))
    state.log.mockRejectedValueOnce(new Error('offline'))
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    const w = shallowMount(Print)
    await flushPromises()
    await (w.vm as any).print()
    expect(print).not.toHaveBeenCalled()
    print.mockRestore()
    w.unmount()
  })
})

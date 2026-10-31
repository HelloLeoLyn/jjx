import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import SupplierSelector from '../Selector/SupplierSelector.vue'

const api = vi.hoisted(() => ({ active: vi.fn(), list: vi.fn(), get: vi.fn() }))
vi.mock('@/api/purchase/supplier', () => ({
  getActiveSuppliers: api.active,
  listSupplier: api.list,
  getSupplier: api.get,
}))

// el-select / el-option 用轻量替身：只关心传给 el-select 的 remote-method 与渲染出的选项
const ElSelectStub = defineComponent({
  name: 'ElSelect',
  props: ['modelValue', 'placeholder', 'disabled', 'clearable', 'filterable', 'remote', 'remoteMethod', 'loading'],
  template: '<div class="el-select-stub"><slot /></div>',
})
const ElOptionStub = defineComponent({
  name: 'ElOption',
  props: ['label', 'value', 'disabled'],
  template: '<div class="el-option-stub">{{ label }}</div>',
})

const mountSelector = (props: Record<string, unknown> = {}) =>
  mount(SupplierSelector, {
    props: { remote: true, modelValue: undefined, ...props },
    global: { stubs: { ElSelect: ElSelectStub, ElOption: ElOptionStub } },
  })

const remoteMethodOf = (wrapper: ReturnType<typeof mountSelector>) =>
  wrapper.findComponent(ElSelectStub).props('remoteMethod') as (query: string) => void

const optionLabels = (wrapper: ReturnType<typeof mountSelector>) =>
  wrapper.findAll('.el-option-stub').map((node) => node.text())

beforeEach(() => {
  vi.useFakeTimers()
  api.active.mockReset().mockResolvedValue({ data: [] })
  api.list.mockReset().mockResolvedValue({ data: { records: [] } })
  api.get.mockReset().mockResolvedValue({ data: null })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('SupplierSelector 远程搜索', () => {
  it('挂载时加载一次全量活跃供应商（不带关键字）', async () => {
    mountSelector()
    await flushPromises()
    expect(api.active).toHaveBeenCalledTimes(1)
    expect(api.active.mock.calls[0][0]).toBeUndefined()
  })

  it('连续输入只发一次请求，且用最后一次的关键字', async () => {
    const wrapper = mountSelector()
    await flushPromises()
    const remote = remoteMethodOf(wrapper)

    remote('深')
    remote('深圳')
    remote('深圳艾')
    expect(api.active).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(299)
    expect(api.active).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(1)
    expect(api.active).toHaveBeenCalledTimes(2)
    expect(api.active).toHaveBeenLastCalledWith('深圳艾')
  })

  it('关键字透传给接口并渲染返回结果', async () => {
    api.active.mockResolvedValue({ data: [{ supplierId: 1, supplierName: '深圳市艾高科电子材料有限公司' }] })
    const wrapper = mountSelector()
    await flushPromises()

    remoteMethodOf(wrapper)('艾高')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(api.active).toHaveBeenLastCalledWith('艾高')
    expect(optionLabels(wrapper)).toContain('深圳市艾高科电子材料有限公司')
  })

  it('清空关键字时回落到全量列表', async () => {
    const wrapper = mountSelector()
    await flushPromises()

    const remote = remoteMethodOf(wrapper)
    remote('艾高')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    remote('')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(api.active).toHaveBeenLastCalledWith(undefined)
  })

  it('过期响应不覆盖后发请求的结果', async () => {
    const wrapper = mountSelector()
    await flushPromises()

    let resolveFirst: (value: unknown) => void = () => {}
    const first = new Promise((resolve) => {
      resolveFirst = resolve
    })
    api.active.mockReturnValueOnce(first)
    api.active.mockResolvedValueOnce({ data: [{ supplierId: 2, supplierName: '后发请求结果' }] })

    const remote = remoteMethodOf(wrapper)
    remote('旧')
    await vi.advanceTimersByTimeAsync(300)
    remote('新')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    expect(optionLabels(wrapper)).toContain('后发请求结果')

    // 旧请求晚到，列表必须保持后发请求的结果
    resolveFirst({ data: [{ supplierId: 3, supplierName: '过期结果' }] })
    await flushPromises()
    expect(optionLabels(wrapper)).toContain('后发请求结果')
    expect(optionLabels(wrapper)).not.toContain('过期结果')
  })

  it('debounceDelay=0 时不防抖，立即请求', async () => {
    const wrapper = mountSelector({ debounceDelay: 0 })
    await flushPromises()

    remoteMethodOf(wrapper)('立即')
    await flushPromises()
    expect(api.active).toHaveBeenLastCalledWith('立即')
  })
})

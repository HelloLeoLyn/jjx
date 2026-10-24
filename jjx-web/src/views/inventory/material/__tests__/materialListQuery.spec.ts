import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import MaterialList from '../index.vue'
import { MaterialClassStatusEnum } from '@/enums/inventory/MaterialClassEnum'

vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/card/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/pagination/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('vue-router', () => ({ useRouter: () => ({}) }))
vi.mock('@/utils/request', () => ({ default: {} }))
vi.mock('@/components/common-ui/SearchForm.vue', () => ({
  default: { name: 'SearchForm', props: ['modelValue', 'fields'], template: '<div />' },
}))
vi.mock('@/components/common-ui/Toolbar.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/TagQuerySelect.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/inventory/MaterialFormDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/inventory/MaterialDetailDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/inventory/MaterialRegenerateDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/inventory/MaterialClassifyDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/inventory/MaterialClassifyRuleDrawer.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/inventory/MaterialMasterImportDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/inventory/MaterialMasterSettingsDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/api/inventory/classify', () => ({ materialClassifyApi: {} }))
vi.mock('@/api/inventory/materialMaster', () => ({
  materialMasterApi: {
    categories: async () => ({ data: [] }),
    dictionaries: async () => ({ data: {} }),
  },
}))

const { page } = vi.hoisted(() => ({ page: vi.fn() }))
vi.mock('@/api/inventory/material', () => ({ materialApi: { page } }))

let wrapper: ReturnType<typeof shallowMount> | undefined
afterEach(() => {
  wrapper?.unmount()
  page.mockReset()
})

describe('物料列表默认查询与归类筛选', () => {
  it('首屏和重置不携带旧档案范围，三态选择原样提交', async () => {
    const requests: Record<string, unknown>[] = []
    page.mockImplementation(async (params) => {
      requests.push({ ...params })
      return { data: { records: [], total: 1606 } }
    })
    wrapper = shallowMount(MaterialList, {
      global: { directives: { hasPermi: {}, loading: {} } },
    })
    await flushPromises()
    expect(requests).toHaveLength(1)
    expect(requests[0]).not.toHaveProperty('masterDataOnly')
    expect(requests[0].classStatus).toBe('')
    expect(requests[0].pageSize).toBe(50)

    const search = wrapper.findComponent({ name: 'SearchForm' })
    const fields = search.props('fields') as Array<{ prop: string; options?: unknown[] }>
    expect(fields.some((field) => field.prop === 'masterDataOnly')).toBe(false)
    expect(fields.find((field) => field.prop === 'classStatus')?.options).toEqual(
      MaterialClassStatusEnum.items.map(({ value, label }) => ({ value, label }))
    )
    for (const { value } of MaterialClassStatusEnum.items) {
      search.props('modelValue').classStatus = value
      search.vm.$emit('search')
      await flushPromises()
      expect(requests.at(-1)?.classStatus).toBe(value)
      expect(requests.at(-1)).not.toHaveProperty('masterDataOnly')
    }
    search.vm.$emit('reset')
    await flushPromises()
    expect(requests.at(-1)?.classStatus).toBe('')
    expect(requests.at(-1)).not.toHaveProperty('masterDataOnly')
  })
})

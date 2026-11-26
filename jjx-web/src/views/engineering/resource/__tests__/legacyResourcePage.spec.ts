import { describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import ResourcePage from '../index.vue'

const api = vi.hoisted(() => ({
  frames: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }),
  dies: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }),
}))
const route = vi.hoisted(() => ({ name: 'EngineeringScreenResource' }))
vi.mock('vue-router', async (original) => ({ ...(await original<typeof import('vue-router')>()), useRoute: () => route }))
vi.mock('@/utils/format', () => ({ download: vi.fn() }))
vi.mock('@/api/engineering/resource', () => ({ engineeringResourceApi: api }))
vi.mock('@/api/product', () => ({ searchProduct: vi.fn() }))

function render(name: string) {
  route.name = name
  return shallowMount(ResourcePage, {
    global: {
      renderStubDefaultSlot: true,
      directives: { hasPermi: () => {}, loading: () => {} },
      stubs: {
        ElCard: { template: '<section><slot name="header"/><slot/></section>' },
        ElTable: { template: '<table/> ' }, ElDialog: { template: '<aside/>' }, Pagination: true, TableActionColumn: true,
      },
    },
  })
}

describe('工程资源历史台账与刀模入口', () => {
  it('旧网框直达页只读，不再提供按框号导入和新增网框入口', () => {
    const wrapper = render('EngineeringScreenResource')
    expect(api.frames).toHaveBeenCalled()
    expect(wrapper.text()).toContain('历史网版台账')
    expect(wrapper.text()).not.toMatch(/导入网版|新增网框|下载导入模板/)
    wrapper.unmount()
  })

  it('刀模直达页继续提供新增及导入', () => {
    const wrapper = render('EngineeringDieResource')
    expect(api.dies).toHaveBeenCalled()
    expect(wrapper.text()).toContain('新增刀模')
    expect(wrapper.text()).toContain('导入刀模')
    wrapper.unmount()
  })
})

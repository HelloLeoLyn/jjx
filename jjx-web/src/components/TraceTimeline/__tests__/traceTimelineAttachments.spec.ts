vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/overlay/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/theme-chalk/base.css', () => ({}))
vi.mock('element-plus/theme-chalk/el-drawer.css', () => ({}))
vi.mock('element-plus/theme-chalk/el-table.css', () => ({}))
vi.mock('element-plus/es/components/drawer/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/pagination/style/css', () => ({}))
vi.mock('element-plus/es/components/image/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import TraceTimeline from '../index.vue'
import request from '@/utils/request'

vi.mock('@/utils/request', () => ({ default: { get: vi.fn() } }))
vi.mock('@/api/system/attachment', () => ({ attachmentApi: { downloadUrl: (id: number) => `/file/${id}` } }))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: { downloadUrlByRef: (id: number) => `/ref/${id}` } }))

const get = vi.mocked(request.get)
let wrapper: ReturnType<typeof mount> | null = null

beforeEach(() => {
  vi.clearAllMocks()
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  get.mockImplementation((url: string) => {
    if (url === '/api/trace/events') {
      return Promise.resolve({
        data: {
          total: 1,
          records: [
            {
              eventId: 'oper-11',
              time: '2026-10-20 10:10:53',
              action: '审核报价单',
              businessType: 6,
              bizType: 'quotation',
              bizId: '1',
              operatorName: '系统管理员',
              result: 1,
              detail: JSON.stringify({
                changes: ['新增附件（整单）：「客户需求.jpg」'],
                attachments: [{ id: 239, refId: 2, fileName: '客户需求.jpg' }],
              }),
            },
          ],
        },
      }) as any
    }
    if (url === '/api/trace/reviews') {
      return Promise.resolve({
        data: [
          {
            createTime: '2026-10-20 10:10:53',
            actionCode: 'APPROVE',
            attachmentRefs: [{ refId: 2, fileName: '客户需求.jpg' }],
          },
        ],
      }) as any
    }
    return Promise.resolve({ data: {} }) as any
  })
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('TraceTimeline 证据附件', () => {
  it('审核流水回填同一附件时不重复显示附件名', async () => {
    wrapper = mount(TraceTimeline, {
      props: { modelValue: true, traceId: 'trace-1' },
      global: {
        stubs: {
          ElDrawer: defineComponent({
            name: 'ElDrawerStub',
            emits: ['open'],
            template: '<div class="drawer-stub"><slot /></div>',
          }),
        },
      },
    })
    const drawer = wrapper.findComponent({ name: 'ElDrawerStub' })
    drawer.vm.$emit('open')
    await flushPromises()

    expect(get).toHaveBeenCalledWith('/api/trace/events', expect.anything())
    const actionLink = wrapper.findAll('.el-link').find((item) => item.text().includes('审核报价单'))
    expect(actionLink).toBeTruthy()
    await actionLink!.trigger('click')
    await flushPromises()

    // 客户需求.jpg 是图片附件，详情区用 el-image 展示；同一 refId 只出现一张
    expect(wrapper.findAll('.attachment-image')).toHaveLength(1)
    expect(wrapper.text()).toContain('📎 1')
  })
})

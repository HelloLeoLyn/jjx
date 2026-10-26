vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))

import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import OperationPreviewDialog from '../index.vue'
import { quotationOperations } from '../registry'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import { useAttachmentRefs } from '@/composables/useAttachmentRefs'
import request from '@/utils/request'

vi.mock('@/utils/request', () => ({ default: vi.fn(() => Promise.resolve({ data: [] })) }))
vi.mock('@/router', () => ({ default: { push: vi.fn() } }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ userId: 7 }) }))
vi.mock('@/composables/usePermission', () => ({ usePermission: () => ({ hasPermission: () => true }) }))
vi.mock('@/api/system/event-config', () => ({ eventConfigApi: { list: async () => ({ data: [] }) } }))
vi.mock('@/api/system/role', () => ({ roleApi: { list: async () => ({ data: [] }) } }))
vi.mock('@/components/attachment/AttachmentCenter.vue', () => ({ default: {
  name: 'EvidenceStub', props: ['operationGroupId'], emits: ['bound'],
  template: '<div class="evidence-center" />',
} }))

const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => { wrappers.splice(0).forEach((w) => w.unmount()); vi.clearAllMocks(); localStorage.clear() })
function dialog(api = vi.fn(async () => {})) {
  const operation = { ...quotationOperations.find((op) => op.key === 'quotation.approve')!, api }
  const wrapper = mount(OperationPreviewDialog, {
    props: { modelValue: true, bizId: 1, operation },
    global: { stubs: {
      ElDialog: { template: '<div><slot/><slot name="footer"/></div>' },
      ElButton: { template: '<button><slot/></button>' },
      ElForm: { template: '<div><slot/></div>' },
      ElFormItem: { template: '<div><slot/></div>' },
      ElInput: true, ElTag: true, ElIcon: true, ElAlert: true,
    } },
  })
  wrappers.push(wrapper)
  return wrapper
}
async function confirm(wrapper: ReturnType<typeof mount>) {
  await wrapper.findAll('button').at(-1)!.trigger('click')
  await flushPromises()
}

describe('报价审核弹窗操作组', () => {
  it('附件与确认共享同一ID，失败重试保留ID和证据，重开生成新ID', async () => {
    const api = vi.fn().mockRejectedValueOnce(new Error('状态冲突')).mockResolvedValueOnce({})
    const wrapper = dialog(api)
    await flushPromises()
    const evidence = wrapper.findComponent({ name: 'EvidenceStub' })
    const group = evidence.props('operationGroupId')
    expect(group).toMatch(/^[a-f0-9-]{36}$/)
    evidence.vm.$emit('bound', { refId: 2 })
    await confirm(wrapper)
    expect(wrapper.emitted('success')).toBeUndefined()
    await confirm(wrapper)
    expect(api).toHaveBeenCalledTimes(2)
    for (const [ctx] of api.mock.calls) {
      expect(ctx).toMatchObject({ operationGroupId: group, attachmentRefIds: [2] })
    }
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    const nextGroup = wrapper.findComponent({ name: 'EvidenceStub' }).props('operationGroupId')
    expect(nextGroup).not.toBe(group)
    expect(wrapper.emitted('success')).toHaveLength(1)
  })
  it('取消不调用审核，另一个弹窗独立分组', async () => {
    const api = vi.fn(async () => {})
    const first = dialog(api)
    const second = dialog()
    await flushPromises()
    expect(first.findComponent({ name: 'EvidenceStub' }).props('operationGroupId'))
      .not.toBe(second.findComponent({ name: 'EvidenceStub' }).props('operationGroupId'))
    first.findComponent({ name: 'EvidenceStub' }).vm.$emit('bound', { refId: 2 })
    await first.findAll('button')[0].trigger('click')
    expect(api).not.toHaveBeenCalled()
    expect(first.emitted('update:modelValue')?.at(-1)).toEqual([false])
  })
  it('局域网HTTP没有randomUUID时仍生成独立操作组', async () => {
    const uuid = vi.spyOn(globalThis.crypto, 'randomUUID').mockImplementation(() => undefined as any)
    try {
      const first = dialog()
      const second = dialog()
      await flushPromises()
      const group = first.findComponent({ name: 'EvidenceStub' }).props('operationGroupId')
      expect(group).toMatch(/^op-/)
      expect(group.length).toBeLessThanOrEqual(64)
      expect(group).not.toBe(second.findComponent({ name: 'EvidenceStub' }).props('operationGroupId'))
    } finally { uuid.mockRestore() }
  })
  it('提交审核、审核通过与驳回均透传操作组，普通附件请求不串组', async () => {
    const group = 'review-group'
    for (const key of ['quotation.submitReview', 'quotation.approve', 'quotation.reject']) {
      await quotationOperations.find((op) => op.key === key)!.api({
        bizId: 1, values: { remark: '意见' }, attachmentIds: [], attachmentRefIds: [2], operationGroupId: group,
      })
      expect(vi.mocked(request).mock.calls.at(-1)![0]).toMatchObject({
        headers: { 'X-Request-Id': group }, params: { attachmentRefIds: '2' },
      })
    }
    await attachmentRefApi.createRef({ attachmentId: 282, sourceType: 'quotation', sourceId: 1 })
    expect(vi.mocked(request).mock.calls.at(-1)![0].headers).toBeUndefined()
  })
  it('真实附件composable把实例组传到上传和绑定请求，不使用全局组', async () => {
    vi.mocked(request).mockImplementation(async (config: any) => ({
      data: config.url.endsWith('/temp') ? 282 : config.url === '/system/attachment/refs' && config.method === 'post' ? 2 : [],
    }) as any)
    const group = ref<string | undefined>('review-attachment-group')
    let attachments!: ReturnType<typeof useAttachmentRefs>
    const host = mount(defineComponent({ setup() {
      attachments = useAttachmentRefs({ sourceType: 'quotation', sourceId: 1, operationGroupId: group, autoLoad: false })
      return () => null
    } }))
    wrappers.push(host)
    await attachments.uploadTemp(new File(['evidence'], '需求.jpg'))
    const entry = attachments.pending.value[0]
    entry.documentType = 'SALES_QUOTATION_REVIEW_EVIDENCE'
    await attachments.bindPendingOne(entry)
    const writes = vi.mocked(request).mock.calls.map(([config]) => config).filter((c) => c.method === 'post')
    expect(writes).toHaveLength(2)
    expect(writes.every((c) => c.headers?.['X-Request-Id'] === group.value)).toBe(true)
  })
})

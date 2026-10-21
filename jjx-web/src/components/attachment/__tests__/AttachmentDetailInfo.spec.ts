import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AttachmentDetailInfo from '../AttachmentDetailInfo.vue'
import { attachmentRefApi, type AttachmentRefActions } from '@/api/system/attachmentRef'

vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: { actions: vi.fn() } }))
const actions = vi.mocked(attachmentRefApi.actions)
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => { actions.mockReset() })
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()) })

function summary(refId: number, names: string[] = []): AttachmentRefActions {
  return {
    refId, uploadedBy: '原文件上传人', uploadedAt: '2026-10-21T09:00:00',
    joinedBy: '加入操作人', joinedAt: '2026-10-21T10:00:00',
    businessActions: names.map((actionName, index) => ({
      eventKey: `flow:${index}`, actionName, actionTime: '2026-10-21T11:00:00', operatorName: null,
    })),
    referenceEvents: [{ eventKey: 'log:1', actionName: '附件新增', actionTime: '2026-10-21T10:00:00', operatorName: '加入操作人' }],
  }
}

function deferred() {
  let resolve!: (value: any) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<any>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function setup(props: Record<string, unknown> = {}) {
  const w = mount(AttachmentDetailInfo, {
    props: { row: { refId: 1, attachmentId: 100, fileName: 'drawing.pdf', createBy: '引用创建人' }, ...props },
    global: { stubs: {
      ElDescriptions: { template: '<section><slot /></section>' },
      ElDescriptionsItem: { props: ['label'], template: '<div><b>{{ label }}</b><slot /></div>' },
    } },
  })
  wrappers.push(w)
  return w
}

describe('附件来源动作详情', () => {
  it('隐藏时不查，打开只查一次；关闭后重开刷新', async () => {
    actions.mockResolvedValue({ data: summary(1) } as any)
    const w = setup({ visible: false })
    expect(actions).not.toHaveBeenCalled()
    await w.setProps({ visible: true })
    await flushPromises()
    expect(actions).toHaveBeenCalledExactlyOnceWith(1)
    await w.setProps({ visible: false })
    expect(actions).toHaveBeenCalledTimes(1)
    await w.setProps({ visible: true })
    await flushPromises()
    expect(actions).toHaveBeenCalledTimes(2)
  })

  it('文件ID与引用ID同号也不推断；旧入口可显式传引用ID', async () => {
    const w = setup({ row: { id: 1, attachmentId: 100, fileName: 'legacy.pdf' } })
    expect(actions).not.toHaveBeenCalled()
    actions.mockResolvedValue({ data: summary(7) } as any)
    await w.setProps({ refId: 7 })
    await flushPromises()
    expect(actions).toHaveBeenCalledExactlyOnceWith(7)
  })

  it('分开原文件上传、加入本单和多个业务动作；不冒充系统操作人', async () => {
    actions.mockResolvedValue({ data: summary(1, ['审核通过', '客户确认报价']) } as any)
    const w = setup()
    await flushPromises()
    expect(w.text()).toContain('原文件上传人')
    expect(w.text()).toContain('加入操作人')
    expect(w.text()).not.toContain('引用创建人')
    expect(w.text()).toContain('2026-10-21 09:00')
    expect(w.text()).toContain('2026-10-21 10:00')
    expect(w.text()).toContain('审核通过')
    expect(w.text()).toContain('客户确认报价')
    expect(w.text()).toContain('操作人未记录')
    expect(w.find('summary').text()).toBe('查看附件变更流水')
  })

  it('失败与无记录分开，重试成功才显示空态', async () => {
    actions.mockRejectedValueOnce(new Error('forbidden'))
    const empty = { ...summary(1), joinedAt: null, joinedBy: null, referenceEvents: [] }
    actions.mockResolvedValueOnce({ data: empty } as any)
    const w = setup()
    await flushPromises()
    expect(w.find('[role=alert]').text()).toContain('加载失败或当前无查看权限')
    expect(w.text()).not.toContain('未查到关联业务动作记录')
    await w.find('button').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('未查到关联业务动作记录')
    expect(w.text()).toContain('未查到正式加入记录')
    expect(w.find('[role=alert]').exists()).toBe(false)
  })

  it('快速切换后旧成功响应不会覆盖新附件', async () => {
    const old = deferred()
    actions.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ data: summary(2, ['新附件动作']) } as any)
    const w = setup()
    await w.setProps({ row: { refId: 2, attachmentId: 101 } })
    await flushPromises()
    old.resolve({ data: summary(1, ['旧附件动作']) })
    await flushPromises()
    expect(w.text()).toContain('新附件动作')
    expect(w.text()).not.toContain('旧附件动作')
  })

  it('旧请求失败不影响新附件；切到无引用附件不残留动作', async () => {
    const old = deferred()
    actions.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ data: summary(2, ['当前动作']) } as any)
    const w = setup()
    await w.setProps({ row: { refId: 2, attachmentId: 101 } })
    await flushPromises()
    old.reject(new Error('old request failed'))
    await flushPromises()
    expect(w.text()).toContain('当前动作')
    expect(w.find('[role=alert]').exists()).toBe(false)
    await w.setProps({ row: { id: 2, attachmentId: 102 } })
    expect(w.text()).not.toContain('当前动作')
    expect(actions).toHaveBeenCalledTimes(2)
  })

  it('后端返回不匹配引用时不展示动作', async () => {
    actions.mockResolvedValue({ data: summary(999, ['错误来源动作']) } as any)
    const w = setup()
    await flushPromises()
    expect(w.find('[role=alert]').exists()).toBe(true)
    expect(w.text()).not.toContain('错误来源动作')
  })
})

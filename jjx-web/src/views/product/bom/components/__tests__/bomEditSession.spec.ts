/**
 * BOM 编辑占用 composable（dev-20261111-018）。
 * 只覆盖前端状态机与请求契约；真实 Redis/MySQL 并发与浏览器多标签页未在本文件中验证。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  acquireBomEditSession: vi.fn(),
  renewBomEditSession: vi.fn(),
  releaseBomEditSession: vi.fn(),
  describeBomEditSession: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
}))

vi.mock('@/api/product/bom', () => ({
  productBomApi: {
    acquireBomEditSession: mocks.acquireBomEditSession,
    renewBomEditSession: mocks.renewBomEditSession,
    releaseBomEditSession: mocks.releaseBomEditSession,
    describeBomEditSession: mocks.describeBomEditSession,
  },
}))

vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: {
    success: mocks.success,
    warning: mocks.warning,
    error: vi.fn(),
    info: vi.fn(),
  },
}))

import {
  bizCodeOf,
  bizMessageCode,
  isContentConflict,
  isLeaseConflict,
  useBomEditSession,
} from '../../composables/useBomEditSession'

const granted = (token = 'token-1', bomId = 100) => ({
  data: {
    leaseAvailable: true,
    acquired: true,
    occupied: false,
    ownerDisplayName: '张三',
    ownerIsSameUser: true,
    ownerIsSameClient: true,
    expiresAt: Date.now() + 90_000,
    token: `${token}-${bomId}`,
  },
})

function useComposable(bomId: number | null = 100) {
  let api!: ReturnType<typeof useBomEditSession>
  const Comp = defineComponent({
    setup() {
      api = useBomEditSession(() => bomId)
      return () => null
    },
  })
  const wrapper = mount(Comp)
  return { api, wrapper }
}

let wrappers: ReturnType<typeof mount>[] = []

beforeEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  window.sessionStorage.clear()
  wrappers = []
})

afterEach(() => {
  wrappers.forEach((wrapper) => wrapper.unmount())
})

function mountIn(bomId: number | null = 100) {
  const { api, wrapper } = useComposable(bomId)
  wrappers.push(wrapper)
  return api
}

describe('业务码解析：409 用 bizCode，不解析提示文案', () => {
  it('保留 request.ts 附加的 bizCode，并区分内容冲突与租约冲突', () => {
    const content = Object.assign(new Error('BOM_CONTENT_STALE：BOM 已被其他人保存'), { bizCode: 409 })
    const lease = Object.assign(new Error('BOM_EDIT_LEASE_LOST：编辑权已失效'), { bizCode: 409 })
    expect(bizCodeOf(content)).toBe(409)
    expect(bizMessageCode(content)).toBe('BOM_CONTENT_STALE')
    expect(isContentConflict(content)).toBe(true)
    expect(isContentConflict(lease)).toBe(false)
    expect(isLeaseConflict(lease)).toBe(true)
  })

  it('无 bizCode 时回退 HTTP status', () => {
    expect(bizCodeOf({ response: { status: 409 } })).toBe(409)
    // 没有 bizCode 时仍能按 HTTP 409 + 前缀码识别内容冲突（兼容未升级的调用方）
    expect(isContentConflict({ response: { status: 409 }, message: 'BOM_CONTENT_STALE' })).toBe(true)
  })
})

describe('编辑占用状态机', () => {
  it('取得编辑权后允许保存，并在关闭时按 token 释放', async () => {
    mocks.acquireBomEditSession.mockResolvedValue(granted())
    mocks.releaseBomEditSession.mockResolvedValue({ data: true })
    const api = mountIn(100)

    expect(await api.acquire(100)).toBe(true)
    expect(api.state.value).toBe('granted')
    expect(api.token.value).toBe('token-1-100')
    expect(api.canSave.value).toBe(true)
    expect(api.ensureEditable()).toBe(true)

    await api.release()
    expect(mocks.releaseBomEditSession).toHaveBeenCalledWith(100, 'token-1-100')
    expect(api.readOnly.value).toBe(true)
    expect(api.state.value).toBe('idle')
  })

  it('已被其他窗口占用：只读、无 token、禁止保存', async () => {
    mocks.acquireBomEditSession.mockResolvedValue({
      data: {
        leaseAvailable: true,
        acquired: false,
        occupied: true,
        ownerDisplayName: '李四',
        ownerIsSameUser: false,
        message: '该BOM正由 李四 编辑，本窗口为只读',
      },
    })
    const api = mountIn(100)
    expect(await api.acquire(100)).toBe(false)
    expect(api.readOnly.value).toBe(true)
    expect(api.token.value).toBeNull()
    expect(api.ownerName.value).toBe('李四')
    expect(api.ensureEditable()).toBe(false)
    expect(mocks.warning).toHaveBeenCalled()
  })

  it('同账号另一窗口同样只读（不默认接管）', async () => {
    mocks.acquireBomEditSession.mockResolvedValue({
      data: {
        leaseAvailable: true,
        acquired: false,
        occupied: true,
        ownerDisplayName: '张三',
        ownerIsSameUser: true,
        message: '该BOM正在你的另一个窗口编辑，本窗口为只读；请关闭另一个窗口后再申请编辑权',
      },
    })
    const api = mountIn(100)
    expect(await api.acquire(100)).toBe(false)
    expect(api.readOnly.value).toBe(true)
    expect(api.token.value).toBeNull()
    // 没有后台接管：不再发出任何 acquire 之外的写请求
    expect(mocks.acquireBomEditSession).toHaveBeenCalledTimes(1)
  })

  it('占用服务不可用（leaseAvailable=false）时只读', async () => {
    mocks.acquireBomEditSession.mockResolvedValue({
      data: { leaseAvailable: false, acquired: false, occupied: true, message: '占用服务暂不可用' },
    })
    const api = mountIn(100)
    expect(await api.acquire(100)).toBe(false)
    expect(api.readOnly.value).toBe(true)
    expect(api.state.value).toBe('unavailable')
    expect(api.message.value).toContain('占用服务暂不可用')
  })

  it('Redis 故障（503）fail-closed：只读且不保存', async () => {
    mocks.acquireBomEditSession.mockRejectedValue(
      Object.assign(new Error('EDIT_LEASE_UNAVAILABLE：占用服务暂不可用，请稍后重试'), { bizCode: 503 })
    )
    const api = mountIn(100)
    expect(await api.acquire(100)).toBe(false)
    expect(api.state.value).toBe('unavailable')
    expect(api.ensureEditable()).toBe(false)
  })

  it('心跳失败立即只读并提示重新申请，不自动续期/接管', async () => {
    mocks.acquireBomEditSession.mockResolvedValue(granted())
    mocks.renewBomEditSession.mockRejectedValue(
      Object.assign(new Error('BOM_EDIT_LEASE_LOST：编辑权已失效'), { bizCode: 409 })
    )
    const api = mountIn(100)
    await api.acquire(100)
    expect(api.state.value).toBe('granted')

    expect(await api.renew()).toBe(false)
    expect(api.state.value).toBe('lost')
    expect(api.readOnly.value).toBe(true)
    expect(api.message.value).toContain('草稿')
    expect(mocks.warning).toHaveBeenCalled()
    // 心跳失败后不再自动重试续租
    expect(mocks.renewBomEditSession).toHaveBeenCalledTimes(1)
    expect(mocks.acquireBomEditSession).toHaveBeenCalledTimes(1)
  })

  it('显式重新申请后才恢复可编辑', async () => {
    mocks.acquireBomEditSession.mockResolvedValueOnce({
      data: { leaseAvailable: true, acquired: false, occupied: true, message: '已被占用' },
    })
    const api = mountIn(100)
    expect(await api.acquire(100)).toBe(false)
    expect(api.readOnly.value).toBe(true)

    mocks.acquireBomEditSession.mockResolvedValueOnce(granted('token-2'))
    expect(await api.reacquire()).toBe(true)
    expect(api.readOnly.value).toBe(false)
    expect(api.token.value).toBe('token-2-100')
    expect(mocks.success).toHaveBeenCalled()
  })

  it('新建 BOM（无 bomId）不占用，取得 bomId 后才占用', async () => {
    const api = mountIn(null)
    expect(await api.acquire(null)).toBe(true)
    expect(api.state.value).toBe('idle')
    expect(mocks.acquireBomEditSession).not.toHaveBeenCalled()

    mocks.acquireBomEditSession.mockResolvedValue(granted('token-9', 9))
    expect(await api.acquire(9)).toBe(true)
    expect(mocks.acquireBomEditSession).toHaveBeenCalledWith(9, expect.any(String))
  })

  it('保存失败映射：内容冲突与租约冲突都转为只读，草稿由调用方保留', async () => {
    mocks.acquireBomEditSession.mockResolvedValue(granted())
    const api = mountIn(100)
    await api.acquire(100)

    expect(
      api.handleRequestError(Object.assign(new Error('BOM_CONTENT_STALE：BOM 已被其他人保存'), { bizCode: 409 }))
    ).toBe('contentConflict')
    expect(api.readOnly.value).toBe(true)

    const api2 = mountIn(100)
    mocks.acquireBomEditSession.mockResolvedValue(granted())
    await api2.acquire(100)
    expect(
      api2.handleRequestError(Object.assign(new Error('BOM_EDIT_LEASE_EXPIRED：编辑占用已过期'), { bizCode: 409 }))
    ).toBe('leaseLost')
    expect(api2.readOnly.value).toBe(true)
  })

  it('保存成功不释放租约；同一 bomId 重复 acquire 幂等，不换 token', async () => {
    mocks.acquireBomEditSession.mockResolvedValue(granted())
    const api = mountIn(100)
    expect(await api.acquire(100)).toBe(true)
    const token = api.token.value

    // 保存成功：只刷新指纹，不 release（关闭/取消才释放）
    api.applyRevision('rev-2')
    expect(mocks.releaseBomEditSession).not.toHaveBeenCalled()
    expect(api.token.value).toBe(token)
    expect(api.boundBomId.value).toBe(100)

    // 保存后补充 acquire（制造方案首保存路径）不应重复申请、也不应替换 token
    expect(await api.acquire(100)).toBe(true)
    expect(mocks.acquireBomEditSession).toHaveBeenCalledTimes(1)
    expect(api.token.value).toBe(token)
  })

  it('切换 bomId 后 boundBomId 跟随，释放后清空', async () => {
    mocks.acquireBomEditSession.mockResolvedValueOnce(granted('token-1', 100))
    mocks.acquireBomEditSession.mockResolvedValueOnce(granted('token-2', 200))
    const api = mountIn(100)
    await api.acquire(100)
    expect(api.boundBomId.value).toBe(100)

    expect(await api.acquire(200)).toBe(true)
    expect(api.boundBomId.value).toBe(200)
    expect(api.token.value).toBe('token-2-200')

    await api.release()
    expect(api.boundBomId.value).toBeNull()
    expect(mocks.releaseBomEditSession).toHaveBeenCalledWith(200, 'token-2-200')
  })

  it('复制 sessionStorage 的窗口与同页面不同编辑实例也不共享身份', async () => {
    window.sessionStorage.setItem('jjx:bom:edit:client-id', 'copied-window-id')
    const first = mountIn(100)
    const second = mountIn(100)
    expect(first.clientId).not.toBe(second.clientId)
    expect(first.clientId).not.toBe('copied-window-id')
    mocks.acquireBomEditSession.mockResolvedValue(granted())
    await first.acquire()
    expect(mocks.acquireBomEditSession).toHaveBeenCalledWith(100, first.clientId)
  })

  it('已有 BOM 等待申请期间禁止编辑保存', async () => {
    let resolve!: (value: ReturnType<typeof granted>) => void
    mocks.acquireBomEditSession.mockReturnValue(new Promise(done => { resolve = done }))
    const api = mountIn(100)
    expect(api.canSave.value).toBe(false)
    const acquiring = api.acquire()
    expect(api.state.value).toBe('acquiring')
    expect(api.ensureEditable()).toBe(false)
    resolve(granted())
    expect(await acquiring).toBe(true)
    expect(api.ensureEditable()).toBe(true)
  })

  it('关闭后迟到的申请按原BOM释放，不恢复编辑权或心跳', async () => {
    let resolve!: (value: ReturnType<typeof granted>) => void
    mocks.acquireBomEditSession.mockReturnValue(new Promise(done => { resolve = done }))
    const api = mountIn(100)
    const pending = api.acquire()
    await api.release()
    resolve(granted())
    expect(await pending).toBe(false)
    expect(api.token.value).toBeNull()
    expect(api.boundBomId.value).toBeNull()
    expect(api.state.value).toBe('idle')
    expect(mocks.releaseBomEditSession).toHaveBeenCalledWith(100, 'token-1-100')
  })

  it('切换BOM后旧申请成功不得把旧token绑定到新BOM', async () => {
    let resolve!: (value: ReturnType<typeof granted>) => void
    mocks.acquireBomEditSession.mockReturnValueOnce(new Promise(done => { resolve = done }))
    mocks.acquireBomEditSession.mockResolvedValueOnce(granted('new', 200))
    const api = mountIn(100)
    const old = api.acquire(100)
    expect(await api.acquire(200)).toBe(true)
    resolve(granted('old', 100))
    expect(await old).toBe(false)
    expect(api.boundBomId.value).toBe(200)
    expect(api.token.value).toBe('new-200')
    expect(mocks.releaseBomEditSession).toHaveBeenCalledWith(100, 'old-100')
    expect(mocks.releaseBomEditSession).not.toHaveBeenCalledWith(200, 'old-100')
  })

  it('旧申请失败与旧心跳失败都不覆盖切换后的新编辑权', async () => {
    let reject!: (error: Error) => void
    mocks.acquireBomEditSession.mockReturnValueOnce(new Promise((_done, fail) => { reject = fail }))
    mocks.acquireBomEditSession.mockResolvedValueOnce(granted('new', 200))
    const api = mountIn(100)
    const old = api.acquire(100)
    await api.acquire(200)
    reject(new Error('old acquire failed'))
    expect(await old).toBe(false)
    expect(api.state.value).toBe('granted')
    mocks.renewBomEditSession.mockReturnValueOnce(new Promise((_done, fail) => { reject = fail }))
    const heartbeat = api.renew()
    await api.release()
    mocks.acquireBomEditSession.mockResolvedValueOnce(granted('newest', 300))
    await api.acquire(300)
    reject(new Error('old heartbeat failed'))
    expect(await heartbeat).toBe(false)
    expect(api.token.value).toBe('newest-300')
    expect(api.state.value).toBe('granted')
  })

  it('续租响应不含有效凭证时失去编辑权', async () => {
    mocks.acquireBomEditSession.mockResolvedValue(granted())
    const api = mountIn(100)
    await api.acquire()
    mocks.renewBomEditSession.mockResolvedValue({ data: { leaseAvailable: false } })
    expect(await api.renew()).toBe(false)
    expect(api.canSave.value).toBe(false)
  })

  it('组件卸载后迟到申请被释放，不能重新启动会话', async () => {
    let resolve!: (value: ReturnType<typeof granted>) => void
    mocks.acquireBomEditSession.mockReturnValueOnce(new Promise(done => { resolve = done }))
    const api = mountIn(100)
    const pending = api.acquire()
    wrappers[0].unmount()
    resolve(granted())
    expect(await pending).toBe(false)
    expect(api.token.value).toBeNull()
    expect(mocks.releaseBomEditSession).toHaveBeenCalledWith(100, 'token-1-100')
    expect(await api.acquire()).toBe(false)
  })
})

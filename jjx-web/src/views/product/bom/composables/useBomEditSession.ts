/**
 * BOM 编辑占用会话（dev-20261111-018）。
 *
 * 规则（用户口径，不得降级）：
 * 1）同一 bomId 同一版本只允许一个编辑会话；其他窗口（含同账号另一窗口）一律只读。
 * 2）Redis 异常 fail-closed：获取失败即只读，没有有效 token 的保存由后端拒绝。
 * 3）心跳失败立即只读、禁止保存、保留草稿，提示重新申请；不后台自动接管、不静默续期。
 * 4）关闭/取消时 compare-token 释放；保存成功不释放（保持到关闭/取消）。
 * 5）租约不替代审核权限，也不替代服务端内容指纹（contentRevision）的权威校验。
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { productBomApi } from '@/api/product/bom'

/** 心跳间隔：租约 TTL 90 秒，30 秒续租。 */
export const BOM_LEASE_HEARTBEAT_MS = 30_000

export type BomLeaseState = 'idle' | 'acquiring' | 'granted' | 'readonly' | 'lost' | 'unavailable'

/** 每个编辑实例独立 UUID；复制窗口会复制 sessionStorage，不能用它共享编辑身份。 */
export function bomClientId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `editor-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

type BizError = Error & { bizCode?: number; data?: unknown }

/** 业务码：request.ts 已保留 res.code，不用解析提示文案。 */
export function bizCodeOf(error: unknown): number | undefined {
  const candidate = error as BizError | undefined
  if (candidate && typeof candidate.bizCode === 'number') return candidate.bizCode
  const status = (error as { response?: { status?: number } } | undefined)?.response?.status
  return typeof status === 'number' ? status : undefined
}

/** 后端错误前缀码（如 BOM_CONTENT_STALE），仅用于区分同类 409 的不同处理，不依赖中文文案。 */
export function bizMessageCode(error: unknown): string {
  const text = String((error as Error | undefined)?.message || '')
  const index = text.indexOf('：')
  return index > 0 ? text.slice(0, index) : text
}

export function isContentConflict(error: unknown): boolean {
  return bizCodeOf(error) === 409 && bizMessageCode(error).startsWith('BOM_CONTENT')
}

export function isLeaseConflict(error: unknown): boolean {
  return bizCodeOf(error) === 409 && bizMessageCode(error).startsWith('BOM_EDIT_LEASE')
}

/**
 * 编辑占用状态机。source 返回当前正在编辑的 bomId（新建未保存时为空 → idle 不受限）。
 */
export function useBomEditSession(source: () => number | null | undefined) {
  const clientId = bomClientId()
  const state = ref<BomLeaseState>('idle')
  const token = ref<string | null>(null)
  const message = ref('')
  const ownerName = ref('')
  const expiresAt = ref<number | null>(null)
  let timer: ReturnType<typeof setInterval> | null = null
  let generation = 0
  let disposed = false
  /** 当前已申请/持有的 bomId（暴露给调用方判断“保存后已绑定，无需再释放重取”）。 */
  const boundBomId = ref<number | null>(null)

  /** 已存 BOM 取得凭证前一律只读，包括请求等待期间；未保存的新建草稿可编辑。 */
  const readOnly = computed(() =>
    state.value === 'acquiring' || state.value === 'readonly' || state.value === 'lost' ||
    state.value === 'unavailable' ||
    (state.value === 'idle' && Boolean(boundBomId.value || source())),
  )
  const canSave = computed(() => !readOnly.value)

  function stopHeartbeat() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function startHeartbeat() {
    stopHeartbeat()
    if (disposed || state.value !== 'granted') return
    timer = setInterval(() => {
      void renew()
    }, BOM_LEASE_HEARTBEAT_MS)
  }

  function markReadOnly(next: BomLeaseState, text: string, owner?: string | null) {
    state.value = next
    message.value = text
    if (owner !== undefined) ownerName.value = owner || ''
    stopHeartbeat()
  }

  /** 只释放指定请求的凭证；不改变当前窗口可能已切到另一 BOM 的状态。 */
  async function releaseToken(id: number, heldToken: string | null): Promise<void> {
    if (!id || !heldToken) return
    try {
      await productBomApi.releaseBomEditSession(id, heldToken)
    } catch (error) {
      console.warn('释放BOM编辑权失败:', error)
    }
  }

  /** 获取编辑权。关闭、切换、重新申请后的迟到响应不得恢复旧会话。 */
  async function acquire(bomId?: number | null): Promise<boolean> {
    if (disposed) return false
    const id = Number(bomId ?? source() ?? 0)
    if (id && boundBomId.value === id && state.value === 'granted' && token.value) return true
    const previousId = boundBomId.value
    const previousToken = token.value
    const requestGeneration = ++generation
    stopHeartbeat()
    token.value = null
    boundBomId.value = id || null
    expiresAt.value = null
    ownerName.value = ''
    message.value = ''
    if (previousId && previousId !== id) void releaseToken(previousId, previousToken)
    if (!id) {
      state.value = 'idle'
      return true
    }
    state.value = 'acquiring'
    try {
      const response = await productBomApi.acquireBomEditSession(id, clientId)
      const lease = response?.data
      if (disposed || requestGeneration !== generation || boundBomId.value !== id) {
        // 同目标的新请求可能正持有同一个 token；仅在窗口关闭/切到别的 BOM 时释放旧结果。
        if (lease?.acquired && lease.token && (disposed || boundBomId.value !== id)) {
          await releaseToken(id, lease.token)
        }
        return false
      }
      if (!lease?.leaseAvailable) {
        markReadOnly('unavailable', lease?.message || '占用服务暂不可用，请稍后重试')
        return false
      }
      if (!lease.acquired || !lease.token) {
        markReadOnly('readonly',
          lease.message || `该BOM正由 ${lease.ownerDisplayName || '其他会话'} 编辑，本窗口为只读`,
          lease.ownerDisplayName)
        return false
      }
      token.value = lease.token
      ownerName.value = lease.ownerDisplayName || ''
      expiresAt.value = lease.expiresAt ?? null
      state.value = 'granted'
      startHeartbeat()
      return true
    } catch (error) {
      if (disposed || requestGeneration !== generation || boundBomId.value !== id) return false
      const code = bizCodeOf(error)
      const text = code === 503 ? '占用服务暂不可用，请稍后重试'
        : String((error as Error)?.message || '获取编辑权失败，请稍后重试')
      markReadOnly(code === 503 ? 'unavailable' : 'readonly', text)
      return false
    }
  }

  async function renew(): Promise<boolean> {
    const id = Number(boundBomId.value || 0)
    const heldToken = token.value
    const requestGeneration = generation
    if (disposed || !id || !heldToken || state.value !== 'granted') return false
    const stillCurrent = () => !disposed && requestGeneration === generation &&
      boundBomId.value === id && token.value === heldToken && state.value === 'granted'
    try {
      const response = await productBomApi.renewBomEditSession(id, clientId, heldToken)
      if (!stillCurrent()) return false
      const lease = response?.data
      if (!lease?.leaseAvailable || !lease.acquired || lease.token !== heldToken) {
        throw new Error('编辑占用续租未返回有效凭证')
      }
      expiresAt.value = lease.expiresAt ?? null
      return true
    } catch {
      if (!stillCurrent()) return false
      markReadOnly('lost', '编辑权已失效；当前草稿已保留，请重新申请编辑权后再保存')
      ElMessage.warning('编辑权已失效，草稿仍保留在当前窗口；请重新申请编辑权后再保存')
      return false
    }
  }

  /** 显式重新申请（用户主动点击，不做后台接管）。 */
  async function reacquire(): Promise<boolean> {
    const ok = await acquire(boundBomId.value ?? source())
    if (ok) ElMessage.success('已重新取得编辑权，请核对内容后再保存')
    return ok
  }

  /** 关闭/取消立即使所有在途请求失效，再按原 id/token 释放。 */
  async function release(): Promise<void> {
    ++generation
    stopHeartbeat()
    const id = Number(boundBomId.value || 0)
    const current = token.value
    token.value = null
    boundBomId.value = null
    state.value = 'idle'
    message.value = ''
    ownerName.value = ''
    expiresAt.value = null
    await releaseToken(id, current)
  }

  /** 保存前门禁：只读时拒绝保存。 */
  function ensureEditable(): boolean {
    if (!canSave.value) {
      ElMessage.warning(message.value || '当前为只读状态，不能保存')
      return false
    }
    if ((boundBomId.value || source()) && (!token.value || state.value !== 'granted' ||
        (source() && Number(source()) !== boundBomId.value))) {
      markReadOnly('lost', '缺少编辑占用凭证，请重新申请编辑权后再保存')
      ElMessage.warning(message.value)
      return false
    }
    return true
  }

  /**
   * 保存/审批失败后的统一处理：
   * 409 内容冲突 → 只读并提示重新加载（调用方保留草稿并回读最新内容）；
   * 409 租约冲突 / 503 → 只读。
   */
  function handleRequestError(error: unknown): 'contentConflict' | 'leaseLost' | 'unavailable' | 'other' {
    const code = bizCodeOf(error)
    if (isContentConflict(error)) {
      markReadOnly('lost', 'BOM 已被其他人保存，请重新加载后再提交（当前草稿已保留）')
      return 'contentConflict'
    }
    if (isLeaseConflict(error)) {
      markReadOnly('lost', '编辑权已失效，请重新申请编辑权后再保存（当前草稿已保留）')
      return 'leaseLost'
    }
    if (code === 503) {
      markReadOnly('unavailable', '占用服务暂不可用，请稍后重试（当前草稿已保留）')
      return 'unavailable'
    }
    return 'other'
  }

  /** 保存成功后由后端返回的新指纹；保持编辑权，不释放租约。 */
  function applyRevision(_revision?: string | null) {
    if (state.value === 'granted') startHeartbeat()
  }

  onBeforeUnmount(() => {
    disposed = true
    void release()
  })

  return {
    clientId,
    boundBomId,
    state,
    token,
    message,
    ownerName,
    expiresAt,
    readOnly,
    canSave,
    acquire,
    reacquire,
    renew,
    release,
    ensureEditable,
    handleRequestError,
    applyRevision,
    startHeartbeat,
    stopHeartbeat,
  }
}

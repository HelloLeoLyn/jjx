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

const CLIENT_ID_KEY = 'jjx:bom:edit:client-id'
/** 心跳间隔：租约 TTL 90 秒，30 秒续租。 */
export const BOM_LEASE_HEARTBEAT_MS = 30_000

export type BomLeaseState = 'idle' | 'acquiring' | 'granted' | 'readonly' | 'lost' | 'unavailable'

/** 每个浏览器标签页一个稳定 UUID（不是 userId，也不是 token）。 */
export function bomClientId(): string {
  try {
    const existing = window.sessionStorage.getItem(CLIENT_ID_KEY)
    if (existing) return existing
    const created =
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `tab-${Date.now()}-${Math.random().toString(16).slice(2)}`
    window.sessionStorage.setItem(CLIENT_ID_KEY, created)
    return created
  } catch {
    // sessionStorage 不可用（隐私模式）：退化为内存级标识，仍能区分本窗口
    return `mem-${Date.now()}-${Math.random().toString(16).slice(2)}`
  }
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
  /** 当前已申请/持有的 bomId（暴露给调用方判断“保存后已绑定，无需再释放重取”）。 */
  const boundBomId = ref<number | null>(null)

  /** 只读：无法编辑或已失去编辑权。新建未保存（idle）不受限。 */
  const readOnly = computed(() =>
    state.value === 'readonly' || state.value === 'lost' || state.value === 'unavailable',
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

  /** 获取编辑权。返回是否可编辑（true 才允许保存）。 */
  async function acquire(bomId?: number | null): Promise<boolean> {
    const id = Number(bomId ?? source() ?? 0)
    if (!id) {
      // 新建 BOM 还没有 bomId：不占用，首次保存后再获取
      boundBomId.value = null
      token.value = null
      state.value = 'idle'
      message.value = ''
      return true
    }
    // 已为本 bomId 持有编辑权：幂等返回，避免保存后的补充 acquire 把刚拿到的 token 换掉
    if (boundBomId.value === id && state.value === 'granted' && token.value) return true
    boundBomId.value = id
    state.value = 'acquiring'
    try {
      const response = await productBomApi.acquireBomEditSession(id, clientId)
      const lease = response?.data
      if (!lease?.leaseAvailable) {
        markReadOnly('unavailable', lease?.message || '占用服务暂不可用，请稍后重试')
        return false
      }
      if (!lease.acquired || !lease.token) {
        markReadOnly(
          'readonly',
          lease.message || `该BOM正由 ${lease.ownerDisplayName || '其他会话'} 编辑，本窗口为只读`,
          lease.ownerDisplayName,
        )
        return false
      }
      token.value = lease.token
      ownerName.value = lease.ownerDisplayName || ''
      expiresAt.value = lease.expiresAt ?? null
      state.value = 'granted'
      message.value = ''
      startHeartbeat()
      return true
    } catch (error) {
      const code = bizCodeOf(error)
      const text =
        code === 503
          ? '占用服务暂不可用，请稍后重试'
          : String((error as Error)?.message || '获取编辑权失败，请稍后重试')
      markReadOnly(code === 503 ? 'unavailable' : 'readonly', text)
      return false
    }
  }

  async function renew(): Promise<boolean> {
    const id = Number(boundBomId.value || source() || 0)
    if (!id || !token.value || state.value !== 'granted') return false
    try {
      const response = await productBomApi.renewBomEditSession(id, clientId, token.value)
      const lease = response?.data
      if (lease?.token) token.value = lease.token
      expiresAt.value = lease?.expiresAt ?? null
      return true
    } catch {
      // 心跳失败：立即只读，保留草稿，不后台接管、不静默续期
      markReadOnly('lost', '编辑权已失效（可能已在其他窗口重新申请）；当前草稿已保留，请重新申请编辑权后再保存')
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

  /** 关闭/取消时 compare-token 释放。保存成功后不调用。 */
  async function release(): Promise<void> {
    stopHeartbeat()
    const id = Number(boundBomId.value || source() || 0)
    const current = token.value
    token.value = null
    boundBomId.value = null
    state.value = 'idle'
    message.value = ''
    if (!id || !current) return
    try {
      await productBomApi.releaseBomEditSession(id, current)
    } catch (error) {
      // 释放失败不影响关闭；租约到期自动释放，不做后台接管
      console.warn('释放BOM编辑权失败:', error)
    }
  }

  /** 保存前门禁：只读时拒绝保存。 */
  function ensureEditable(): boolean {
    if (!canSave.value) {
      ElMessage.warning(message.value || '当前为只读状态，不能保存')
      return false
    }
    if (boundBomId.value && !token.value) {
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
    stopHeartbeat()
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

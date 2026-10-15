import { computed, onScopeDispose, ref, watch } from 'vue'
import { attachmentApi, type AttachmentSourceItem } from '@/api/system/attachment'
import { attachmentRefApi, type AttachmentDocumentTypeItem, type AttachmentRefItem } from '@/api/system/attachmentRef'
import { AttachmentDocumentStatusEnum } from '@/enums/system/AttachmentEnum'
import type { AttachmentRow } from './useAttachmentRowText'

export interface AttachmentDocQuery {
  sourceType?: string
  sourceNo?: string
  sourceId?: number
  traceId?: string
  productId?: number
  includeLegacy?: boolean | null
}

/**
 * 具名查询范围：调用方只能声明这几种（可组合 = 并集），不得自由拼参数隐式扩大可见范围。
 * - own：本单（来源类型 + 内部ID 或 单号，二选一）
 * - chain：关联文档（同链路 traceId）
 * - product：产品（productId）
 * - legacy：历史附件（需要来源类型 + 可解析的内部ID，且显式声明才查）
 */
export type AttachmentDocScope =
  | { kind: 'own'; sourceType: string; sourceId?: number; sourceNo?: string }
  | { kind: 'chain'; traceId: string }
  | { kind: 'product'; productId: number }
  | { kind: 'legacy'; sourceType: string; sourceId: number }

/** 具名范围 → 查询参数的唯一映射点（各调用方不再各拼一套） */
export function docQueryOf(...scopes: AttachmentDocScope[]): AttachmentDocQuery {
  const query: AttachmentDocQuery = {}
  for (const scope of scopes) {
    if (scope.kind === 'own') {
      query.sourceType = scope.sourceType
      if (scope.sourceId != null) query.sourceId = scope.sourceId
      const sourceNo = scope.sourceNo?.trim()
      if (sourceNo) query.sourceNo = sourceNo
    } else if (scope.kind === 'chain') {
      query.traceId = scope.traceId
    } else if (scope.kind === 'product') {
      query.productId = scope.productId
    } else {
      query.sourceType = scope.sourceType
      query.sourceId = scope.sourceId
      query.includeLegacy = true
    }
  }
  return query
}

export interface AttachmentDocRow extends AttachmentRow {
  /** 预览器与表格使用文件ID，引用ID单独保留，避免两种主键碰撞。 */
  id: number
  attachmentId: number
  refId?: number | null
  status: string
}

type FileRow = AttachmentSourceItem

function responseRows<T>(response: { data: T[] }): T[] {
  if (!Array.isArray(response.data)) throw new Error('文档返回格式异常')
  return response.data
}

function fromRef(row: AttachmentRefItem): AttachmentDocRow {
  return { ...row, id: row.attachmentId, refId: row.id, status: AttachmentDocumentStatusEnum.BOUND.value }
}

export function attachmentDocFromFile(row: FileRow, status: string): AttachmentDocRow {
  return {
    ...row, attachmentId: row.id, sourceType: row.bizType,
    sourceTypeName: row.bizTypeName, status,
  }
}

/** 参数只启用对应查询；合并时去重，不追加产品/链路等前端过滤。
 *  options.typesOnly：受控行模式——不按参数查询，只加载资料类型字典（供标签渲染）。 */
export function useAttachmentDocList(
  query: () => AttachmentDocQuery,
  options: { typesOnly?: () => boolean } = {}
) {
  const rows = ref<AttachmentDocRow[]>([])
  const loading = ref(false)
  const messages = ref<string[]>([])
  const documentTypes = ref<AttachmentDocumentTypeItem[]>([])
  let requestId = 0
  let disposed = false
  const hasQuery = computed(() => {
    const q = query()
    return Boolean((q.sourceType && (q.sourceId != null || q.sourceNo?.trim())) || q.traceId || q.productId != null)
  })

  async function reload() {
    const current = ++requestId
    const q = { ...query() }
    const typesOnly = options.typesOnly?.() === true
    rows.value = []
    messages.value = []
    loading.value = false
    if ((!hasQuery.value && !typesOnly) || disposed) return
    if (!typesOnly) loading.value = true
    const segments: AttachmentDocRow[][] = [[], [], [], []]
    const failures: string[] = []
    const isCurrent = () => !disposed && current === requestId
    const publish = () => {
      if (!isCurrent()) return
      const seen = new Set<number>()
      rows.value = segments.flat().filter(row => {
        if (seen.has(row.attachmentId)) return false
        seen.add(row.attachmentId)
        return true
      })
      messages.value = [...failures]
    }
    const run = async (index: number, name: string, load: () => Promise<AttachmentDocRow[]>) => {
      try { segments[index] = await load() }
      catch { failures.push(`${name}：来源不可见、不存在或加载失败`) }
      publish()
    }
    const requests: Promise<void>[] = []
    if (q.sourceType && (q.sourceId != null || q.sourceNo?.trim())) {
      requests.push(run(0, '本单文档', async () => {
        if (q.sourceId != null && q.sourceNo?.trim()) throw new Error('来源ID和单号须二选一')
        const refs = responseRows(await attachmentRefApi.listRefs(q.sourceType!, q.sourceId, q.sourceNo))
        segments[0] = refs.map(fromRef)
        publish()
        // 旧接口仅支持内部ID。只有明确传true时才加载历史段，不隐含启用。
        if (q.includeLegacy === true && isCurrent()) {
          const sourceId = q.sourceId ?? refs[0]?.sourceId
          if (sourceId != null) {
            await run(3, '历史文档', async () => responseRows<FileRow>(await attachmentApi.list(q.sourceType!, sourceId))
              .map(row => attachmentDocFromFile(row, AttachmentDocumentStatusEnum.LEGACY.value)))
          } else {
            failures.push('历史文档：当前单号没有可解析ID的引用，请传 sourceId 查询历史附件')
          }
        }
        return refs.map(fromRef)
      }))
    } else if (q.includeLegacy === true) {
      failures.push('历史文档：请提供来源类型和来源单号或ID')
    }
    if (q.traceId) requests.push(run(1, '同链路文档', async () =>
      responseRows<FileRow>(await attachmentApi.listByTrace(q.traceId!))
        .map(row => attachmentDocFromFile(row, AttachmentDocumentStatusEnum.TRACE.value))))
    if (q.productId != null) requests.push(run(2, '产品文档', async () =>
      responseRows(await attachmentRefApi.refsByProduct(q.productId!)).map(fromRef)))
    // 字典失败只影响分类标签，不影响三个文档段。
    if (!documentTypes.value.length) requests.push((async () => {
      try {
        const types = responseRows(await attachmentRefApi.documentTypes())
        if (isCurrent()) documentTypes.value = types
      } catch {
        failures.push('资料类型加载失败，暂显示类型编码')
      }
      publish()
    })())
    await Promise.all(requests)
    if (isCurrent()) { publish(); loading.value = false }
  }

  watch(() => {
    const q = query()
    return [q.sourceType, q.sourceNo, q.sourceId, q.traceId, q.productId, q.includeLegacy]
  }, reload, { immediate: true })
  onScopeDispose(() => { disposed = true; requestId++ })
  return { rows, loading, messages, documentTypes, hasQuery, reload }
}

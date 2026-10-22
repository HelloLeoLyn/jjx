import request from '@/utils/request'
import { BomSaveLogResult, type BomOriginalValues } from '@/types/product/bom'

interface SaveEvent { action?: string; result?: number; detail?: unknown }
interface TracePage { records?: SaveEvent[]; total?: number; totalPages?: number }

export function originalValuesFromDetail(detail: unknown): BomOriginalValues {
  const parsed = typeof detail === 'string' ? JSON.parse(detail) : detail as any
  const values: BomOriginalValues = {}
  if (!Array.isArray(parsed?.itemChanges)) return values
  for (const change of parsed.itemChanges) {
    if (!Number.isSafeInteger(change.itemId) || change.itemId <= 0
      || !['materialName', 'specification'].includes(change.field)) continue
    if (change.oldValue !== null && typeof change.oldValue !== 'string') continue
    const entry = values[String(change.itemId)] ||= {}
    entry[change.field as 'materialName' | 'specification'] = change.oldValue
  }
  return values
}

/** 流水接口升序分页，从末页向前查最近成功保存；该次没有结构化旧值时返回空，不追溯更早保存。 */
export async function loadBomOriginalValues(bomId: number): Promise<BomOriginalValues> {
  const pageSize = 100
  const fetchPage = async (pageNum: number): Promise<TracePage> => {
    const response: any = await request.get('/api/trace/events', { params: { bizType: 'bom', bizId: bomId, pageNum, pageSize } })
    return response?.data || {}
  }
  const first = await fetchPage(1)
  const lastPage = Math.max(1, Number(first.totalPages) || Math.ceil(Number(first.total || 0) / pageSize))
  for (let page = lastPage; page >= 1; page--) {
    const data = page === 1 ? first : await fetchPage(page)
    const latest = [...(data.records || [])].reverse().find(event =>
      event.action === '修改BOM' && event.result === BomSaveLogResult.SUCCESS)
    if (latest) return originalValuesFromDetail(latest.detail)
  }
  return {}
}

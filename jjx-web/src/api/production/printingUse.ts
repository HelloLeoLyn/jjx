import request from '@/utils/request'
import type { R } from '@/types'

/** 生产实际用版段（P10，dev-20261125-017） */
export interface PrintUseSegment {
  segmentId: number
  executionId: number
  versionId?: number | null
  plateId?: number | null
  plateNo?: string | null
  frameNo?: string | null
  artworkNo?: string | null
  versionNo?: string | null
  startTime?: string | null
  endTime?: string | null
  operatorName?: string | null
  reason?: string | null
}

export const printingUseApi = {
  /** 某道工序的用版历史（按开始时间） */
  listByExecution(executionId: number) {
    return request.get<R<PrintUseSegment[]>>('/production/printing/uses', { params: { executionId } })
  },
  /** 开工/换版：选定实体网版 */
  start(data: { requestId: string; executionId: number; plateId: number; versionId?: number; reason?: string }) {
    return request.post<R<Record<string, unknown>>>('/production/printing/uses', data)
  },
  /** 结束当前用版段 */
  end(executionId: number, reason?: string) {
    return request.post<R<Record<string, unknown>>>('/production/printing/uses/end', null, {
      params: { executionId, reason },
    })
  },
}

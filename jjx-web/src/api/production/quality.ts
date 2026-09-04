import request from '@/utils/request'
import type { PageResult, R } from '@/types'

export interface QualityQuery {
  pageNum: number
  pageSize: number
  inspectionNo?: string
  inspectionType?: string
  orderId?: number
  /** P3-B：按工序执行过滤 */
  executionId?: number
  /** P3-B：按报工过滤 */
  workReportId?: number
  result?: string
}

export interface InspectionItemVO {
  itemId?: number
  checkItem: string
  standard?: string
  actualValue?: string
  result?: string
  remark?: string
}

export interface QualityVO {
  inspectionId: number
  inspectionNo: string
  inspectionType: string
  inspectionTypeName: string
  orderId?: number
  orderNo?: string
  /** P3-B：关联工序执行 */
  executionId?: number
  /** P3-D：工序名称（展示） */
  processName?: string
  /** P3-B：关联报工 */
  workReportId?: number
  materialName?: string
  productName?: string
  inspector?: string
  inspectTime?: string
  result: string
  resultName: string
  /** P3-B：数量 DECIMAL(18,4)，支持小数 */
  totalQty?: number
  passQty?: number
  failQty?: number
  defectDesc?: string
  remark?: string
  createTime?: string
  items?: InspectionItemVO[]
}

/** P3-C：判定入参（正式质量动作，不走 legacy PUT） */
export interface QualityJudgePayload {
  result: 'PASS' | 'FAIL'
  totalQty?: number
  passQty?: number
  failQty?: number
  defectDesc?: string
  remark?: string
}

/** P3-C：创建质检（人工创建 IPQC）入参 */
export interface QualityCreatePayload {
  inspectionType: string
  orderId?: number
  /** IPQC/FQC：关联工序执行 */
  executionId?: number
  /** IPQC：可选关联报工（后端反查校验一致性） */
  workReportId?: number
  materialId?: number
  productId?: number
  inspector?: string
  remark?: string
}

export const qualityApi = {
  page(params: QualityQuery) {
    return request.get<R<PageResult<QualityVO>>>('/production/quality/page', { params })
  },
  getById(id: number) {
    return request.get<R<QualityVO>>(`/production/quality/${id}`)
  },
  create(data: any) {
    return request.post<R<number>>('/production/quality', data)
  },
  /** P3-C：创建质检（createInspection，workReportId 非空时后端反查校验） */
  createInspection(data: QualityCreatePayload) {
    return request.post<R<number>>('/production/quality/inspection', data)
  },
  /** P3-C：判定 PASS/FAIL（正式质量动作） */
  judge(id: number, data: QualityJudgePayload) {
    return request.post<R<QualityVO>>(`/production/quality/${id}/judge`, data)
  },
  /** P3-C：复检（新建 PENDING 记录，不覆盖历史） */
  reinspect(id: number) {
    return request.post<R<number>>(`/production/quality/${id}/reinspect`)
  },
  update(data: any) {
    return request.put<R<void>>('/production/quality', data)
  },
  remove(id: number) {
    return request.delete<R<void>>(`/production/quality/${id}`)
  },
  getStatistics() {
    return request.get<R<any>>('/production/quality/statistics')
  },
  exportExcel(id: number) {
    return request.get(`/production/quality/export-excel/${id}`, { responseType: 'blob' })
  },
}

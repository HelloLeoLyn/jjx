import type { PageResult, R } from '@/types'
import { InspectionResult } from '@/enums/quality'
import { qualityLotApi, toQualityLotView, type QualityLotItem } from '@/api/quality/lot'

export interface QualityQuery {
  pageNum: number
  pageSize: number
  inspectionNo?: string
  inspectionType?: string
  sourceType?: string
  sourceId?: number
  sourceItemId?: number
  orderId?: number
  /** P3-B：按工序执行过滤 */
  executionId?: number
  /** P3-B：按报工过滤 */
  workReportId?: number
  result?: string
  reviewStatus?: string
}

export interface InspectionItemVO {
  itemId?: number
  checkItem: string
  standard?: string
  inspectionMethod?: string
  equipment?: string
  actualValue?: string
  result?: string
  crQuantity?: number
  maQuantity?: number
  miQuantity?: number
  remark?: string
}

export interface QualityVO {
  inspectionId: number
  inspectionNo: string
  inspectionType: string
  inspectionTypeName: string
  sourceType?: string
  sourceId?: number
  sourceItemId?: number
  batchNo?: string
  previousInspectionId?: number
  inspectionVersion?: number
  disposition?: string
  orderId?: number
  orderNo?: string
  /** P3-B：关联工序执行 */
  executionId?: number
  /** P3-D：工序名称（展示） */
  processName?: string
  /** P3-B：关联报工 */
  workReportId?: number
  materialCode?: string
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
  remainingFailQty?: number
  defectDesc?: string
  remark?: string
  reviewerId?: number
  reviewerName?: string
  reviewTime?: string
  reviewStatus?: string
  reviewRemark?: string
  createTime?: string
  items?: InspectionItemVO[]
}

export interface QualityJudgePayload {
  result: typeof InspectionResult.PASS | typeof InspectionResult.FAIL
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
  async page(params: QualityQuery): Promise<R<PageResult<QualityVO>>> {
    const response = await qualityLotApi.page({
      pageNum: params.pageNum,
      pageSize: params.pageSize,
      lotNo: params.inspectionNo,
      lotType: params.inspectionType,
      sourceType: params.sourceType,
      sourceId: params.workReportId ?? params.sourceId,
      sourceItemId: params.sourceItemId,
      orderId: params.orderId,
      executionId: params.executionId,
      result: params.result,
      reviewStatus: params.reviewStatus,
    })
    const page = response.data
    const records = (page?.records || []).map((lot) => toQualityVO(lot, []))
    return {
      ...response,
      data: {
        total: page?.total || 0,
        records,
        pageNum: params.pageNum,
        pageSize: params.pageSize,
        totalPages: Math.ceil((page?.total || 0) / params.pageSize),
      },
    }
  },
  async getById(id: number): Promise<R<QualityVO>> {
    const [lotResponse, itemResponse] = await Promise.all([
      qualityLotApi.detail(id),
      qualityLotApi.items(id),
    ])
    const lot = lotResponse.data
    if (!lot) throw new Error('检验批不存在')
    return {
      ...lotResponse,
      data: toQualityVO(lot, itemResponse.data || []),
    }
  },
}

function toQualityVO(lot: import('@/api/quality/lot').QualityLot, items: QualityLotItem[]): QualityVO {
  const view = toQualityLotView(lot)
  return {
    ...view,
    result: view.result || InspectionResult.PENDING,
    sourceType: lot.sourceType,
    sourceId: lot.sourceId,
    sourceItemId: lot.sourceItemId,
    batchNo: lot.batchNo,
    previousInspectionId: lot.parentLotId,
    inspectionVersion: lot.version,
    materialCode: lot.materialCode,
    reviewStatus: lot.reviewStatus,
    reviewerId: lot.reviewerId,
    reviewerName: lot.reviewerName,
    reviewTime: lot.reviewTime,
    reviewRemark: lot.reviewRemark,
    defectDesc: lot.defectReason,
    remainingFailQty: Math.max(0, Number(lot.failQuantity || 0) - Number(lot.disposedQuantity || 0)),
    items: items.map((item) => ({ ...item })),
  }
}

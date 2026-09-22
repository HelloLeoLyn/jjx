import { createEnum } from '../base'

export const InspectionType = {
  FQC: 'FQC',
  IPQC: 'IPQC',
  IQC: 'IQC',
  OQC: 'OQC',
} as const

export const InspectionTypeEnum = createEnum<string>({
  items: [
    { value: InspectionType.FQC, label: 'FQC 完工检验', tagProps: { type: 'danger' } },
    { value: InspectionType.IPQC, label: 'IPQC 过程检验', tagProps: { type: 'warning' } },
    { value: InspectionType.IQC, label: 'IQC 来料检验', tagProps: { type: 'info' } },
    { value: InspectionType.OQC, label: 'OQC 出货检验', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

export const InspectionResult = {
  PENDING: 'pending',
  PASS: 'pass',
  FAIL: 'fail',
} as const

export const InspectionResultEnum = createEnum<string>({
  items: [
    { value: InspectionResult.PENDING, label: '待检', tagProps: { type: 'info' } },
    { value: InspectionResult.PASS, label: '合格', tagProps: { type: 'success' } },
    { value: InspectionResult.FAIL, label: '不合格', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/** quality_lot.status：统一检验批流程状态。 */
export const QualityLotStatus = {
  PENDING: 'PENDING',
  INSPECTING: 'INSPECTING',
  JUDGED: 'JUDGED',
  CLOSED: 'CLOSED',
} as const

export const QualityLotStatusEnum = createEnum<string>({
  items: [
    { value: QualityLotStatus.PENDING, label: '待检', tagProps: { type: 'info' } },
    { value: QualityLotStatus.INSPECTING, label: '检验中', tagProps: { type: 'warning' } },
    { value: QualityLotStatus.JUDGED, label: '已判定', tagProps: { type: 'success' } },
    { value: QualityLotStatus.CLOSED, label: '已关闭', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

export const QualityDisposition = {
  INTERNAL_SORT: 'INTERNAL_SORT',
  SCRAP: 'SCRAP',
} as const

export type FqcDisposition = typeof QualityDisposition[keyof typeof QualityDisposition]

export const QualityDispositionEnum = createEnum<string>({
  items: [
    { value: QualityDisposition.INTERNAL_SORT, label: '内部返工', tagProps: { type: 'warning' } },
    { value: QualityDisposition.SCRAP, label: '报废', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

export const QualityReviewStatus = {
  DRAFT: 'DRAFT',
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
} as const

export const QualityReviewStatusEnum = createEnum<string>({
  items: [
    { value: QualityReviewStatus.DRAFT, label: '待提交', tagProps: { type: 'info' } },
    { value: QualityReviewStatus.PENDING, label: '待审核', tagProps: { type: 'warning' } },
    { value: QualityReviewStatus.APPROVED, label: '已审核', tagProps: { type: 'success' } },
    { value: QualityReviewStatus.REJECTED, label: '已驳回', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

export const InspectionEnum = {
  type: InspectionTypeEnum,
  result: InspectionResultEnum,
  lotStatus: QualityLotStatusEnum,
  disposition: QualityDispositionEnum,
  reviewStatus: QualityReviewStatusEnum,
}

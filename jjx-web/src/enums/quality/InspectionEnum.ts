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
  reviewStatus: QualityReviewStatusEnum,
}

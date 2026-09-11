import { createNamedEnum } from '../base'

export const ArchiveRecognitionStatusEnum = createNamedEnum({
  PENDING: { value: 0, label: '待识别', tagProps: { type: 'info' } },
  RECOGNIZING: { value: 1, label: '识别中', tagProps: { type: 'warning' } },
  REVIEW: { value: 2, label: '待确认', tagProps: { type: 'primary' } },
  GENERATED: { value: 3, label: '已生成草稿', tagProps: { type: 'success' } },
  FAILED: { value: 4, label: '识别失败', tagProps: { type: 'danger' } },
}, { type: 'info' })

export const IconConfirmStatusEnum = createNamedEnum({
  PENDING: { value: 0, label: '待确认', tagProps: { type: 'warning' } },
  CONFIRMED: { value: 1, label: '已确认', tagProps: { type: 'success' } },
  IGNORED: { value: 2, label: '已忽略', tagProps: { type: 'info' } },
}, { type: 'info' })

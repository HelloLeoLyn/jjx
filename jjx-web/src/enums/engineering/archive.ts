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

export const ArchiveCellContentTypeEnum = createNamedEnum({
  EMPTY: { value: 'EMPTY', label: '空白', tagProps: { type: 'info' } },
  TEXT_ONLY: { value: 'TEXT_ONLY', label: '纯文本', tagProps: { type: 'primary' } },
  ICON_ONLY: { value: 'ICON_ONLY', label: '纯图标', tagProps: { type: 'warning' } },
  MIXED: { value: 'MIXED', label: '图文混合', tagProps: { type: 'success' } },
  UNKNOWN: { value: 'UNKNOWN', label: '待判断', tagProps: { type: 'danger' } },
}, { type: 'info' })

export const ArchiveProcessStructureEnum = createNamedEnum({
  EMPTY: { value: 'EMPTY', label: '空白', tagProps: { type: 'info' } },
  SINGLE: { value: 'SINGLE', label: '单工序', tagProps: { type: 'primary' } },
  COMPOSITE: { value: 'COMPOSITE', label: '复合工序', tagProps: { type: 'warning' } },
  DEPENDENCY: { value: 'DEPENDENCY', label: '跨组依赖', tagProps: { type: 'success' } },
  UNDECIDED: { value: 'UNDECIDED', label: '待判断', tagProps: { type: 'danger' } },
}, { type: 'info' })

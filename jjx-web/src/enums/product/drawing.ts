import { createNamedEnum } from '@/enums/base'

/** 产品工程图纸标记，对应 sys_attachment。 */
export const DrawingCurrentFlagEnum = createNamedEnum({
  NOT_CURRENT: { value: 0, label: '非现行', tagProps: { type: 'info' } },
  CURRENT: { value: 1, label: '现行', tagProps: { type: 'success' } },
}, { type: 'info' })
export const DrawingReleaseFlagEnum = createNamedEnum({
  NOT_RELEASED: { value: 0, label: '未下发', tagProps: { type: 'info' } },
  RELEASED: { value: 1, label: '已下发', tagProps: { type: 'success' } },
}, { type: 'info' })
export const DrawingControlledFlagEnum = createNamedEnum({
  NOT_CONTROLLED: { value: 0, label: '非受控', tagProps: { type: 'info' } },
  CONTROLLED: { value: 1, label: '受控', tagProps: { type: 'warning' } },
}, { type: 'info' })
export const DrawingFileRoleEnum = createNamedEnum({
  ORIGINAL: { value: 'ORIGINAL', label: '工程原稿', tagProps: { type: 'info' } },
  PRINT: { value: 'PRINT', label: '预览打印件', tagProps: { type: 'primary' } },
}, { type: 'info' })
export const DrawingViewEnum = createNamedEnum({
  ALL: { value: 'all', label: '全部版本', tagProps: { type: 'info' } },
  CURRENT: { value: 'current', label: '现行版本', tagProps: { type: 'success' } },
  HISTORY: { value: 'history', label: '历史／待定版本', tagProps: { type: 'info' } },
  UNBOUND: { value: 'unbound', label: '待归集文件', tagProps: { type: 'warning' } },
}, { type: 'info' })
export const DrawingUploadStatusEnum = createNamedEnum({
  QUEUED: { value: 'queued', label: '待上传', tagProps: { type: 'info' } },
  UPLOADING: { value: 'uploading', label: '上传中', tagProps: { type: 'primary' } },
  SUCCESS: { value: 'success', label: '已上传', tagProps: { type: 'success' } },
  FAILED: { value: 'failed', label: '失败，可重试', tagProps: { type: 'danger' } },
}, { type: 'info' })

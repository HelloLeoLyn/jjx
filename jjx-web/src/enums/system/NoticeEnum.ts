// src/enums/system/NoticeEnum.ts
import { createEnum } from '../base'

/**
 * 通知类型枚举
 */
export const NoticeTypeEnum = createEnum({
  items: [
    { value: 'system', label: '系统通知', tagProps: { type: 'primary' } },
    { value: 'approval', label: '审批通知', tagProps: { type: 'warning' } },
    { value: 'reminder', label: '提醒通知', tagProps: { type: 'info' } },
    { value: 'alert', label: '预警通知', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 通知状态枚举
 */
export const NoticeStatusEnum = createEnum({
  items: [
    { value: 'unread', label: '未读', tagProps: { type: 'warning' } },
    { value: 'read', label: '已读', tagProps: { type: 'info' } },
    { value: 'archived', label: '已归档', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 通知级别枚举
 */
export const NoticeLevelEnum = createEnum({
  items: [
    { value: 'info', label: '一般', tagProps: { type: 'info' } },
    { value: 'warning', label: '重要', tagProps: { type: 'warning' } },
    { value: 'urgent', label: '紧急', tagProps: { type: 'danger' } },
    { value: 'critical', label: '严重', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 通知相关枚举统一导出
 */
export const NoticeEnum = {
  type: NoticeTypeEnum,
  status: NoticeStatusEnum,
  level: NoticeLevelEnum,
}

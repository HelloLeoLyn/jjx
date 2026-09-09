import request from '@/utils/request'

export interface NotificationQuery {
  receiverId?: number
  notificationType?: string
  bizType?: string
  isRead?: number
  pageNum?: number
  pageSize?: number
}

export interface NotificationCreateDTO {
  title: string
  content?: string
  notificationType: string
  bizType?: string
  bizId?: string
  senderId?: number
  senderName?: string
  receiverId: number
  receiverName?: string
  priority?: string
}

export interface NotificationVO {
  notificationId: number
  title: string
  content: string
  notificationType: string
  bizType: string
  bizId: string
  senderId: number
  senderName: string
  receiverId: number
  receiverName: string
  isRead: number
  readTime: string
  priority: string
  status: string
  sendTime: string
  createTime: string
}

export function getNotificationPage(params: NotificationQuery) {
  return request({ url: '/notification/page', method: 'get', params })
}

export function getUnreadList(receiverId: number) {
  return request({ url: `/notification/unread/${receiverId}`, method: 'get' })
}

export function getUnreadCount(receiverId: number) {
  return request({ url: `/notification/unread-count/${receiverId}`, method: 'get' })
}

export function markAsRead(id: number) {
  return request({ url: `/notification/read/${id}`, method: 'put' })
}

export function markAllAsRead(receiverId: number) {
  return request({ url: `/notification/read-all/${receiverId}`, method: 'put' })
}

export function deleteNotification(id: number) {
  return request({ url: `/notification/${id}`, method: 'delete' })
}

// ============ 通用催办（2026-09-09 dev-20260909-002） ============

export interface NotifyTaskDTO {
  title: string
  content?: string
  // 目标角色 role_key 列表；不传时后端取系统参数 notify_task_default_roles（默认 production:all,admin）
  roleKeys?: string[]
  bizType?: string
  bizId?: number
  priority?: string
  kanbanModule?: string
}

// 通用：按角色发通知 + 建待办任务（供"请先启动工单"等催办场景及其他业务复用）
export function createNotifyTask(data: NotifyTaskDTO) {
  return request({ url: '/common/notify-task', method: 'post', data })
}

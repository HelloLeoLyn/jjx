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

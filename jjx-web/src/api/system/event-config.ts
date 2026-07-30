import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { SysEventConfig } from '@/types/system'

// 事件配置管理API
export const eventConfigApi = {
  // 分页列表
  page(params?: Partial<SysEventConfig> & { pageNum?: number; pageSize?: number }) {
    return request.get<R<PageResult<SysEventConfig>>>('/system/event-config/page', { params })
  },
  // 全量列表
  list(params?: Partial<SysEventConfig>) {
    return request.get<R<SysEventConfig[]>>('/system/event-config/list', { params })
  },
  // 详情
  getInfo(eventId: number) {
    return request.get<R<SysEventConfig>>(`/system/event-config/${eventId}`)
  },
  // 新增
  add(data: Partial<SysEventConfig>) {
    return request.post<R>('/system/event-config', data)
  },
  // 编辑
  update(data: Partial<SysEventConfig>) {
    return request.put<R>('/system/event-config', data)
  },
  // 删除
  remove(eventIds: number[]) {
    return request.delete<R>(`/system/event-config/${eventIds}`)
  },
}

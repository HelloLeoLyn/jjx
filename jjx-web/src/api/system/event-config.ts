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
  metadata(eventCode: string) {
    return request.get<
      R<{
        variables: Array<{ key: string; description: string; example: string }>
        latest?: {
          title?: string
          content?: string
          receiverName?: string
          sendTime?: string
        }
        /** 最近一次真实 payload（2026-09-23 dev-20260921-014）——试渲染用 */
        lastPayload?: Record<string, unknown> | null
        lastPayloadTime?: string | null
        /** lastEvent = 用真实 payload；sample = 回落样例值 */
        payloadSource?: 'lastEvent' | 'sample'
      }>
    >(`/system/event-config/${encodeURIComponent(eventCode)}/metadata`)
  },
  // 新增（data.warnings：后端模板键名校验告警，不阻断保存）
  add(data: Partial<SysEventConfig>) {
    return request.post<R<{ warnings?: string[] }>>('/system/event-config', data)
  },
  // 编辑
  update(data: Partial<SysEventConfig>) {
    return request.put<R<{ warnings?: string[] }>>('/system/event-config', data)
  },
  // 删除
  remove(eventIds: number[]) {
    return request.delete<R>(`/system/event-config/${eventIds}`)
  },
}

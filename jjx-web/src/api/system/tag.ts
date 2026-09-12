import request from '@/utils/request'
import type { R } from '@/types'
import type { SysTag } from '@/types/system/tag'

/**
 * 系统标签 API（通用标签体系，dev-20260911-007）
 */
export const tagApi = {
  /** 标签列表（可按分组/关键字/状态） */
  list(params?: { tagGroup?: string; keyword?: string; status?: number }) {
    return request.get<R<SysTag[]>>('/system/tag/list', { params })
  },

  /** 新增标签 */
  add(data: SysTag) {
    return request.post<R<SysTag>>('/system/tag', data)
  },

  /** 修改标签 */
  update(tagId: number, data: SysTag) {
    return request.put<R<void>>(`/system/tag/${tagId}`, data)
  },

  /** 删除标签（批量） */
  remove(tagIds: number[]) {
    return request.delete<R<void>>(`/system/tag/${tagIds.join(',')}`)
  },

  /** 查询某业务对象已挂标签 */
  getBizTags(bizType: string, bizId: number | string) {
    return request.get<R<SysTag[]>>('/system/tag/rel', { params: { bizType, bizId } })
  },

  /** 重设某业务对象标签（全量替换，传空数组=清空） */
  setBizTags(bizType: string, bizId: number | string, tagIds: number[]) {
    return request.post<R<void>>('/system/tag/rel', tagIds, { params: { bizType, bizId } })
  },

  /** 按标签反查业务ID（列表按标签筛选用） */
  getBizIdsByTag(bizType: string, tagId: number) {
    return request.get<R<number[]>>('/system/tag/biz-ids', { params: { bizType, tagId } })
  },

  /**
   * 标签查询辅助（facets）：标签 + 当前选中组合下的关联数量（dev-20260912-004）
   * @param tagIds 已选标签（参与「与」收窄，用于动态计数）
   */
  facets(params: { bizType: string; tagIds?: number[]; keyword?: string; matchMode?: 'AND' | 'OR' }) {
    const { bizType, tagIds, keyword, matchMode } = params
    return request.get<R<TagFacet[]>>('/system/tag/facets', {
      params: {
        bizType,
        keyword,
        matchMode,
        // Spring 以逗号串绑定 List<Long>，避免 axios 数组序列化成 tagIds[]
        tagIds: tagIds && tagIds.length ? tagIds.join(',') : undefined,
      },
    })
  },
}

/** 标签查询辅助项（含当前条件下计数） */
export interface TagFacet {
  tagId: number
  tagCode?: string
  tagName: string
  tagGroup: string
  count: number
  selected?: boolean
}

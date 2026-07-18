import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { SysDict, SysDictDTO, SysDictItem, SysDictItemDTO } from '@/types/system/dict'

/** 字典管理API */
export const dictApi = {
  // ==================== 字典类型 ====================

  /** 分页查询字典类型列表 */
  list(params: SysDictDTO & { pageNum?: number; pageSize?: number }) {
    return request.get<R<PageResult<SysDict>>>('/system/dict/list', { params })
  },

  /** 查询所有字典类型列表 */
  getAll() {
    return request.get<R<SysDict[]>>('/system/dict/all')
  },

  /** 获取字典类型详情 */
  getById(dictId: number) {
    return request.get<R<SysDict>>(`/system/dict/${dictId}`)
  },

  /** 新增字典类型 */
  add(data: SysDictDTO) {
    return request.post<R<void>>('/system/dict', data)
  },

  /** 修改字典类型 */
  update(dictId: number, data: SysDictDTO) {
    return request.put<R<void>>(`/system/dict/${dictId}`, data)
  },

  /** 删除字典类型 */
  remove(dictIds: number[]) {
    return request.delete<R<void>>(`/system/dict/${dictIds.join(',')}`)
  },

  /** 启用/禁用字典类型 */
  changeStatus(dictId: number, isActive: number) {
    return request.put<R<void>>(`/system/dict/${dictId}/status`, null, {
      params: { isActive },
    })
  },

  // ==================== 字典项 ====================

  /** 根据字典编码获取字典项列表 */
  getItems(dictCode: string) {
    return request.get<R<SysDictItem[]>>(`/system/dict/code/${dictCode}`)
  },

  /** 新增字典项 */
  addItem(data: SysDictItemDTO) {
    return request.post<R<void>>('/system/dict/item', data)
  },

  /** 修改字典项 */
  updateItem(itemId: number, data: SysDictItemDTO) {
    return request.put<R<void>>(`/system/dict/item/${itemId}`, data)
  },

  /** 删除字典项 */
  removeItem(itemIds: number[]) {
    return request.delete<R<void>>(`/system/dict/item/${itemIds.join(',')}`)
  },

  /** 启用/禁用字典项 */
  changeItemStatus(itemId: number, isActive: number) {
    return request.put<R<void>>(`/system/dict/item/${itemId}/status`, null, {
      params: { isActive },
    })
  },
}

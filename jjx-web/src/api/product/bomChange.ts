import request from '@/utils/request'
import type { R } from '@/types'

/** BOM 变更单（ECN-lite，dev-20261112-009）。 */
export interface BomChangeItem {
  itemId?: number
  changeId?: number
  bomId: number
  bomVersion?: string | null
  targetEffectiveDate?: string | null
  validateResult?: string | null
}

export interface BomChangeRequest {
  changeId: number
  changeNo: string
  title: string
  effectiveDate?: string | null
  status: string
  createdBy?: string
  createdTime?: string
  approvedBy?: string
  approvedTime?: string
  activatedBy?: string
  activatedTime?: string
  remark?: string
}

export interface BomChangePage {
  records: BomChangeRequest[]
  total: number
}

export const bomChangeApi = {
  page(params: { pageNum: number; pageSize: number; status?: string }) {
    return request.get<R<BomChangePage>>('/product/bom-change/page', { params })
  },
  detail(changeId: number) {
    return request.get<R<BomChangeRequest>>(`/product/bom-change/${changeId}`)
  },
  items(changeId: number) {
    return request.get<R<BomChangeItem[]>>(`/product/bom-change/${changeId}/items`)
  },
  create(data: {
    title: string
    effectiveDate?: string | null
    remark?: string
    items: Array<{ bomId: number; bomVersion?: string | null; targetEffectiveDate?: string | null }>
  }) {
    return request.post<R<number>>('/product/bom-change', data)
  },
  submit(changeId: number) {
    return request.post(`/product/bom-change/${changeId}/submit`)
  },
  approve(changeId: number) {
    return request.post(`/product/bom-change/${changeId}/approve`)
  },
  activate(changeId: number, force = false) {
    return request.post(`/product/bom-change/${changeId}/activate`, null, { params: { force } })
  },
  cancel(changeId: number) {
    return request.post(`/product/bom-change/${changeId}/cancel`)
  },
}

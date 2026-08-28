import request from '@/utils/request'

export interface QualityTemplateQuery {
  pageNum: number
  pageSize: number
  recordNo?: string
  recordName?: string
  ownerDept?: string
  category?: string
  status?: number
}

export interface QualityTemplate {
  id?: number
  recordNo: string
  recordName: string
  version: string
  ownerDept?: string
  retentionYears: number
  category: string
  bizType?: string
  fileId?: number
  status?: number
  remark?: string
}

export const getQualityTemplatePage = (params: QualityTemplateQuery) =>
  request({ url: '/production/quality-template/page', method: 'get', params })
export const getQualityTemplate = (id: number) =>
  request({ url: `/production/quality-template/${id}`, method: 'get' })
export const createQualityTemplate = (data: QualityTemplate) =>
  request({ url: '/production/quality-template', method: 'post', data })
export const updateQualityTemplate = (data: QualityTemplate) =>
  request({ url: '/production/quality-template', method: 'put', data })
export const changeQualityTemplateStatus = (id: number, status: number) =>
  request({ url: `/production/quality-template/${id}/status`, method: 'put', data: { status } })
export const deleteQualityTemplate = (id: number) =>
  request({ url: `/production/quality-template/${id}`, method: 'delete' })

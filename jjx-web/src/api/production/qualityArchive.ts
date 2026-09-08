import request from '@/utils/request'

export type QualityArchiveExpiryState = 'overdue' | 'soon'

export interface QualityArchiveQuery {
  pageNum: number
  pageSize: number
  templateId?: number
  ownerDept?: string
  archived?: boolean
  expiryState?: QualityArchiveExpiryState
  recordNo?: string
}

export interface QualityArchiveRow {
  printLogId: number
  recordNo: string
  recordName?: string
  ownerDept?: string
  retentionYears?: number
  bizType?: string
  bizId?: number
  operatorName?: string
  printTime: string
  expiryDate?: string
  archived: boolean
}

export const getQualityArchivePage = (params: QualityArchiveQuery) =>
  request({ url: '/production/quality-archive/page', method: 'get', params })

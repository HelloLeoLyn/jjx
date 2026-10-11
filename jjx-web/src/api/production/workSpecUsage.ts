import request from '@/utils/request'

/** 工单换版执行区间（dev-20261011-013） */
export interface WorkSpecUsageVO {
  id: number
  workOrderId: number
  specVersionId: number
  versionNo?: string
  versionStatus?: string
  qtyFrom?: number | null
  qtyTo?: number | null
  startTime?: string | null
  endTime?: string | null
  changeReason?: string
  approvedBy?: string
  approvedAt?: string
  createBy?: string
  createTime?: string
}

export interface WorkSpecUsageRegisterDTO {
  workOrderId: number
  specVersionId: number
  qtyFrom?: number | null
  qtyTo?: number | null
  startTime?: string | null
  endTime?: string | null
  changeReason?: string
  approvedBy?: string
}

export const workSpecUsageApi = {
  register: (data: WorkSpecUsageRegisterDTO) =>
    request.post('/production/work-spec-usage', data),
  list: (workOrderId: number) =>
    request.get('/production/work-spec-usage', { params: { workOrderId } }),
  remove: (id: number) => request.delete('/production/work-spec-usage/' + id),
}

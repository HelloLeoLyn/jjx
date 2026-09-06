import request from '@/utils/request'
import type { R, PageResult } from '@/types'
import type {
  InboundQueryParams,
  InboundVO,
  InboundCreateParams,
  InboundUpdateParams,
  InboundStatusUpdateParams,
  InboundApproveParams,
  InboundRejectParams,
  InboundDashboardData,
  InboundInspectionSubmitParams,
} from '@/types/inventory/inbound'

// 入库管理API
export const inboundApi = {
  // 分页查询入库单列表
  list(params: InboundQueryParams) {
    return request.get<R<PageResult<InboundVO>>>('/inventory/inbound/list', {
      params,
    })
  },

  // 获取入库单详情
  getById(inboundId: string) {
    return request.get<R<InboundVO>>(`/inventory/inbound/${inboundId}`)
  },

  // 创建入库单
  create(data: InboundCreateParams) {
    return request.post<R<{ inboundId: string }>>('/inventory/inbound/create', data)
  },

  // 更新入库单
  update(data: InboundUpdateParams) {
    return request.put<R<boolean>>('/inventory/inbound/update', data)
  },

  // 确认入库
  confirm(inboundId: string, operatorId: string, operatorName: string) {
    return request.post<R<boolean>>(`/inventory/inbound/confirm/${inboundId}`, null, {
      params: { operatorId, operatorName },
    })
  },

  // 取消入库单
  cancel(inboundId: string, reason: string) {
    return request.post<R<boolean>>(`/inventory/inbound/cancel/${inboundId}`, null, {
      params: { reason },
    })
  },

  // 提交审批
  submitApprove(inboundId: string, data?: InboundInspectionSubmitParams) {
    return request.post<R<boolean>>(`/inventory/inbound/submit-approve/${inboundId}`, data)
  },

  approveInspectionItem(itemId: string, data: Omit<InboundApproveParams, 'inboundId'>) {
    return request.post<R<boolean>>(`/inventory/inbound/inspection-item/${itemId}/approve`, data)
  },

  rejectInspectionItem(itemId: string, data: Omit<InboundRejectParams, 'inboundId'>) {
    return request.post<R<boolean>>(`/inventory/inbound/inspection-item/${itemId}/reject`, data)
  },

  reinspectItem(itemId: string) {
    return request.post<R<number>>(`/inventory/inbound/inspection-item/${itemId}/reinspect`)
  },
  listQuarantine(inboundId: string) {
    return request.get<R<any[]>>(`/inventory/inbound/${inboundId}/iqc-quarantine`)
  },
  handleQuarantine(quarantineId: string, data: any) {
    return request.post<R<boolean>>(`/inventory/inbound/iqc-quarantine/${quarantineId}/action`, data)
  },
  listDispositionOrders(inboundId: string) {
    return request.get<R<any[]>>(`/inventory/inbound/${inboundId}/iqc-disposition-orders`)
  },
  listAllQuarantine(status?: string) {
    return request.get<R<any[]>>('/inventory/inbound/iqc-quarantine/list', { params: { status } })
  },
  listAllDispositionOrders(action?: string) {
    return request.get<R<any[]>>('/inventory/inbound/iqc-disposition-orders/list', { params: { action } })
  },
  getDispositionOrder(dispositionId: string) {
    return request.get<R<any>>(`/inventory/inbound/iqc-disposition-orders/${dispositionId}`)
  },
  listIqcReturnOrders(inboundId: string) {
    return request.get<R<any[]>>(`/inventory/inbound/${inboundId}/iqc-return-orders`)
  },

  // 审批通过
  approve(data: InboundApproveParams) {
    return request.post<R<boolean>>(`/inventory/inbound/approve/${data.inboundId}`, null, {
      params: {
        approverId: data.approverId,
        approverName: data.approverName,
        remark: data.remark,
      },
    })
  },

  // 审批驳回
  reject(data: InboundRejectParams) {
    return request.post<R<boolean>>(`/inventory/inbound/reject/${data.inboundId}`, null, {
      params: {
        approverId: data.approverId,
        approverName: data.approverName,
        remark: data.remark,
      },
    })
  },

  // 从采购订单创建入库单
  createFromPurchase(purchaseOrderId: string) {
    return request.post<R<{ inboundId: string }>>(
      `/inventory/inbound/create-from-purchase/${purchaseOrderId}`
    )
  },

  // 从生产工单创建入库单
  createFromProduction(workOrderId: string) {
    return request.post<R<{ inboundId: string }>>(
      `/inventory/inbound/create-from-production/${workOrderId}`
    )
  },

  // 查询待审批的入库单
  getPendingApproval() {
    return request.get<R<InboundVO[]>>('/inventory/inbound/pending-approval')
  },

  // 查询日期范围内的入库单
  getByDateRange(startDate: string, endDate: string) {
    return request.get<R<InboundVO[]>>('/inventory/inbound/date-range', {
      params: { startDate, endDate },
    })
  },

  // 根据来源单据查询入库单
  getBySource(sourceType: string, sourceId: string) {
    return request.get<R<InboundVO>>('/inventory/inbound/source', {
      params: { sourceType, sourceId },
    })
  },

  // 更新入库单状态
  updateStatus(data: InboundStatusUpdateParams) {
    return request.post<R<boolean>>(`/inventory/inbound/update-status/${data.inboundId}`, null, {
      params: { status: data.status },
    })
  },

  // 获取入库仪表板数据
  getDashboard() {
    return request.get<R<InboundDashboardData>>('/inventory/inbound/dashboard')
  },

  // 导出入库单
  export(params: InboundQueryParams) {
    return request.get('/inventory/inbound/export', {
      params,
      responseType: 'blob',
    })
  },
}

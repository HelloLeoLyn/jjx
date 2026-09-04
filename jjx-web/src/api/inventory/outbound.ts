import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type {
  OutboundQueryParams,
  OutboundVO,
  OutboundCreateParams,
  OutboundUpdateParams,
  OutboundStatusUpdateParams,
  OutboundApproveParams,
  OutboundRejectParams,
  OutboundDashboardData,
  PickPreviewRow,
  PickRemainingRow,
  PickItemPayload,
} from '@/types/inventory/outbound'

// 出库管理API
export const outboundApi = {
  // 分页查询出库单列表
  list(params: OutboundQueryParams) {
    return request.get<R<PageResult<OutboundVO>>>('/inventory/outbound/list', {
      params,
    })
  },

  // 获取出库单详情
  getById(outboundId: string) {
    return request.get<R<OutboundVO>>(`/inventory/outbound/${outboundId}`)
  },

  // 创建出库单
  create(data: OutboundCreateParams) {
    return request.post<R<{ outboundId: string }>>(
      '/inventory/outbound/create',
      data,
    )
  },

  // 更新出库单
  update(data: OutboundUpdateParams) {
    return request.put<R<boolean>>('/inventory/outbound/update', data)
  },

  // 确认出库
  confirm(outboundId: string, operatorId: string, operatorName: string) {
    return request.post<R<boolean>>(
      `/inventory/outbound/confirm/${outboundId}`,
      null,
      {
        params: { operatorId, operatorName },
      },
    )
  },

  // 取消出库单
  cancel(outboundId: string, reason: string) {
    return request.post<R<boolean>>(
      `/inventory/outbound/cancel/${outboundId}`,
      null,
      {
        params: { reason },
      },
    )
  },

  // 提交审批
  submitApprove(outboundId: string) {
    return request.post<R<boolean>>(
      `/inventory/outbound/submit-approve/${outboundId}`,
    )
  },

  // 审批通过
  approve(data: OutboundApproveParams) {
    return request.post<R<boolean>>(
      `/inventory/outbound/approve/${data.outboundId}`,
      null,
      {
        params: {
          approverId: data.approverId,
          approverName: data.approverName,
          remark: data.remark,
        },
      },
    )
  },

  // 审批驳回
  reject(data: OutboundRejectParams) {
    return request.post<R<boolean>>(
      `/inventory/outbound/reject/${data.outboundId}`,
      null,
      {
        params: {
          approverId: data.approverId,
          approverName: data.approverName,
          remark: data.remark,
        },
      },
    )
  },

  // 从销售订单创建出库单
  createFromSales(salesOrderId: string) {
    return request.post<R<{ outboundId: string }>>(
      `/inventory/outbound/create-from-sales/${salesOrderId}`,
    )
  },

  // 从生产工单创建出库单
  createFromProduction(workOrderId: string) {
    return request.post<R<{ outboundId: string }>>(
      `/inventory/outbound/create-from-production/${workOrderId}`,
    )
  },

  // 生产领料预览（BOM展开+可用量+替代料）
  pickPreview(workOrderId: number) {
    return request.get<R<PickPreviewRow[]>>(
      `/inventory/outbound/pick-preview/${workOrderId}`,
    )
  },

  // 工单剩余可领料量
  pickRemaining(workOrderId: number) {
    return request.get<R<PickRemainingRow[]>>(
      `/inventory/outbound/pick-remaining/${workOrderId}`,
    )
  },

  // 追加领料（多次领料）
  createProductionPick(workOrderId: number, items: PickItemPayload[]) {
    return request.post<R<{ outboundId: string }>>(
      `/inventory/outbound/create-production-pick/${workOrderId}`,
      items,
    )
  },

  // 查询待审批的出库单
  getPendingApproval() {
    return request.get<R<OutboundVO[]>>('/inventory/outbound/pending-approval')
  },

  // 查询日期范围内的出库单
  getByDateRange(startDate: string, endDate: string) {
    return request.get<R<OutboundVO[]>>('/inventory/outbound/date-range', {
      params: { startDate, endDate },
    })
  },

  // 根据来源单据查询出库单
  getBySource(sourceType: string, sourceId: string) {
    return request.get<R<OutboundVO>>('/inventory/outbound/source', {
      params: { sourceType, sourceId },
    })
  },

  // 更新出库单状态
  updateStatus(data: OutboundStatusUpdateParams) {
    return request.post<R<boolean>>(
      `/inventory/outbound/update-status/${data.outboundId}`,
      null,
      {
        params: { status: data.status },
      },
    )
  },

  // 获取出库仪表板数据
  getDashboard() {
    return request.get<R<OutboundDashboardData>>(
      '/inventory/outbound/dashboard',
    )
  },

  // 导出出库单
  export(params: OutboundQueryParams) {
    return request.get('/inventory/outbound/export', {
      params,
      responseType: 'blob',
    })
  },
}

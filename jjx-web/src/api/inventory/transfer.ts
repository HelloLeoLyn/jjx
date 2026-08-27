import request from '@/utils/request'
import type { PageResult, R } from '@/types'

// 调拨单查询参数
export interface TransferQueryParams {
  current?: number
  pageSize?: number
  transferNo?: string
  transferType?: string
  fromWarehouseId?: string
  toWarehouseId?: string
  orderStatus?: string
  approveStatus?: string
}

// 调拨单明细
export interface TransferItemVO {
  transferItemId?: string
  materialId?: string
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  quantity?: number
  unitCost?: number
  amount?: number
  batchNo?: string
  sortOrder?: number
  remark?: string
}

// 调拨单VO
export interface TransferVO {
  transferId: string
  transferNo?: string
  transferType?: string
  transferTypeName?: string
  fromWarehouseId?: string
  fromWarehouseName?: string
  fromLocationId?: string
  fromLocationName?: string
  toWarehouseId?: string
  toWarehouseName?: string
  toLocationId?: string
  toLocationName?: string
  transferDate?: string
  expectedDate?: string
  actualDate?: string
  totalQuantity?: number
  totalAmount?: number
  orderStatus?: number
  orderStatusName?: string
  approveStatus?: number
  approveStatusName?: string
  approverName?: string
  approveTime?: string
  outOperator?: string
  outTime?: string
  inOperator?: string
  inTime?: string
  remark?: string
  createBy?: string
  createTime?: string
  items?: TransferItemVO[]
}

// 调拨单API
export const transferApi = {
  // 分页查询
  list(params: TransferQueryParams) {
    return request.get<R<PageResult<TransferVO>>>('/inventory/transfer/list', { params })
  },

  // 详情
  getById(transferId: string) {
    return request.get<R<TransferVO>>(`/inventory/transfer/${transferId}`)
  },

  // 新建
  create(data: Record<string, unknown>) {
    return request.post<R<string>>('/inventory/transfer/create', data)
  },

  // 提交审批
  submitApprove(transferId: string) {
    return request.post<R<boolean>>(`/inventory/transfer/submit-approve/${transferId}`)
  },

  // 审批
  approve(transferId: string, remark?: string) {
    return request.post<R<boolean>>(`/inventory/transfer/approve/${transferId}`, null, {
      params: { remark },
    })
  },

  // 驳回
  reject(transferId: string, remark?: string) {
    return request.post<R<boolean>>(`/inventory/transfer/reject/${transferId}`, null, {
      params: { remark },
    })
  },

  // 确认调出（扣减源仓库存）
  confirmOut(transferId: string) {
    return request.post<R<boolean>>(`/inventory/transfer/confirm-out/${transferId}`)
  },

  // 确认调入（增加目标仓库库存）
  confirmIn(transferId: string) {
    return request.post<R<boolean>>(`/inventory/transfer/confirm-in/${transferId}`)
  },

  // 取消
  cancel(transferId: string, remark?: string) {
    return request.post<R<boolean>>(`/inventory/transfer/cancel/${transferId}`, null, {
      params: { remark },
    })
  },
}

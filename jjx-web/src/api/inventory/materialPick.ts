import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

// 领料单（生产出库）
export const materialPickApi = {
  // 从生产工单创建领料单（2026-08-18：支持预览调整明细，仅可少领）
  createFromProduction(workOrderId: number, adjustedItems?: any[]): AxiosPromise<number> {
    return request({
      url: `/inventory/outbound/create-from-production/${workOrderId}`,
      method: 'post',
      data: adjustedItems || [],
    })
  },

  // 领料预览（2026-08-18：BOM展开+可用量+替代料，生成前确认）
  pickPreview(workOrderId: number): AxiosPromise<any[]> {
    return request({
      url: `/inventory/outbound/pick-preview/${workOrderId}`,
      method: 'get',
    })
  },

  // 追加领料（2026-08-18：按剩余可领量补领，Σ累计领料≤BOM需求量）
  createProductionPick(workOrderId: number, items: any[]): AxiosPromise<number> {
    return request({
      url: `/inventory/outbound/create-production-pick/${workOrderId}`,
      method: 'post',
      data: items,
    })
  },

  // 查询工单剩余可领料量（需求-已领）
  getPickRemaining(workOrderId: number): AxiosPromise<any[]> {
    return request({
      url: `/inventory/outbound/pick-remaining/${workOrderId}`,
      method: 'get',
    })
  },
}

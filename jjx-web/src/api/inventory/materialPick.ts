import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

// 领料单（生产出库）
export const materialPickApi = {
  // 从生产工单创建领料单
  createFromProduction(workOrderId: number): AxiosPromise<number> {
    return request({
      url: `/inventory/outbound/create-from-production/${workOrderId}`,
      method: 'post',
    })
  },
}

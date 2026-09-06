import request from '@/utils/request'
import type { R } from '@/types'

export const iqcApi = {
  listReturnOrders(inboundId: string) { return request.get<R<any[]>>(`/inventory/inbound/${inboundId}/iqc-return-orders`) },
  listReworkOrders(inboundId: string) { return request.get<R<any[]>>(`/inventory/inbound/${inboundId}/iqc-rework-orders`) },
  listScrapOrders(inboundId: string) { return request.get<R<any[]>>(`/inventory/inbound/${inboundId}/iqc-scrap-orders`) },
  approveScrap(scrapId: string, data: { approverId: string; approverName: string; approved: boolean; remark?: string }) {
    return request.post<R<boolean>>(`/inventory/inbound/iqc-scrap-orders/${scrapId}/approve`, data)
  },
  completeRework(reworkId: string) {
    return request.post<R<number>>(`/inventory/inbound/iqc-rework-orders/${reworkId}/complete`)
  },
}

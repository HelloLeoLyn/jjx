import request from '@/utils/request'

export interface ArchiveImportRecord {
  archiveId: number
  fileName: string
  recognizeStatus: number
  recognizeMessage?: string
  productName?: string
  productCode?: string
  extractedJson?: string
  productId?: number
  bomId?: number
  routingId?: number
  createTime: string
}

export interface IconSample {
  sampleId: number
  processId?: number
  workflowType: string
  stepNo?: number
  perceptualHash: string
  matchScore?: number
  confirmStatus: number
  previewBase64?: string
}

export const archiveImportApi = {
  page: (params: { pageNum: number; pageSize: number }) => request.get('/engineering/archive-imports', { params }),
  ocrHealth: () => request.get('/engineering/archive-imports/ocr-health'),
  upload: (file: File) => {
    const data = new FormData()
    data.append('file', file)
    return request.post('/engineering/archive-imports/upload', data, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  retry: (id: number) => request.post(`/engineering/archive-imports/${id}/retry`),
  updateResult: (id: number, data: unknown) => request.put(`/engineering/archive-imports/${id}/result`, data),
  generate: (id: number) => request.post(`/engineering/archive-imports/${id}/generate`),
  samples: (archiveId: number) => request.get('/engineering/archive-imports/icon-samples', { params: { archiveId } }),
  confirmSample: (sampleId: number, processId: number) => request.put(`/engineering/archive-imports/icon-samples/${sampleId}/confirm`, null, { params: { processId } }),
}

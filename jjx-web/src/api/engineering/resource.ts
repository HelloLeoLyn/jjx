import request from '@/utils/request'

const base = '/engineering/resources'

export const engineeringResourceApi = {
  frames: (params?: Record<string, unknown>) => request.get(`${base}/screen-frames`, { params }),
  saveFrame: (data: Record<string, unknown>) => request.post(`${base}/screen-frames`, data),
  createPlate: (data: Record<string, unknown>) => request.post(`${base}/screen-plates`, data),
  washFrame: (id: number, description?: string) => request.post(`${base}/screen-frames/${id}/wash`, { description }),
  frameAction: (id: number, data: Record<string, unknown>) => request.post(`${base}/screen-frames/${id}/actions`, data),
  dies: (params?: Record<string, unknown>) => request.get(`${base}/dies`, { params }),
  saveDie: (data: Record<string, unknown>) => request.post(`${base}/dies`, data),
  dieAction: (id: number, data: Record<string, unknown>) => request.post(`${base}/dies/${id}/actions`, data),
  /** 网版导入：下载模板（2026-09-21） */
  frameImportTemplate: () => request.get(`${base}/screen-frames/import-template`, { responseType: 'blob' }),
  /** 网版导入：上传 Excel（网框 + 当前版面，按网框编号 upsert） */
  importFrames: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return request.post(`${base}/screen-frames/import`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  /** 刀模导入：下载模板（2026-09-21） */
  dieImportTemplate: () => request.get(`${base}/dies/import-template`, { responseType: 'blob' }),
  /** 刀模导入：上传 Excel（按刀模编号 upsert） */
  importDies: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return request.post(`${base}/dies/import`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  products: (type: string, id: number) => request.get(`${base}/${type}/${id}/products`),
  /** 反查：某产品关联的工程资源（目前支持 DIE 刀模；dev-20260929-023） */
  byProduct: (type: string, productId: number) => request.get(`${base}/${type}/by-product/${productId}`),
  replaceProducts: (type: string, id: number, productIds: number[], purpose?: string) =>
    request.put(`${base}/${type}/${id}/products`, { productIds, purpose }),
  maintenance: (type: string, id: number) => request.get(`${base}/${type}/${id}/maintenance`),

  // ===== P04 网框/网版（dev-20261125-013）=====
  listPrintFrames: (params: { keyword?: string; status?: string; pageNum?: number; pageSize?: number }) =>
    request.get('/engineering/printing/frames', { params }),
  getPrintFrame: (id: number) => request.get(`/engineering/printing/frames/${id}`),
  createPrintFrame: (data: any) => request.post('/engineering/printing/frames', data),
  editPrintFrame: (id: number, data: any) => request.put(`/engineering/printing/frames/${id}`, data),
  editPrintFrameRemark: (id: number, data: { requestId: string; expectedRevision: number; remark: string }) =>
    request.put(`/engineering/printing/frames/${id}/remark`, data),
  stretchPrintFrame: (id: number, data: any) => request.post(`/engineering/printing/frames/${id}/stretch`, data),
  maintainPrintFrame: (id: number, data: any) => request.post(`/engineering/printing/frames/${id}/maintain`, data),
  resumePrintFrame: (id: number, data: any) => request.post(`/engineering/printing/frames/${id}/resume`, data),
  scrapPrintFrame: (id: number, data: any) => request.post(`/engineering/printing/frames/${id}/scrap`, data),
  cleanPrintFrame: (id: number, data: any) => request.post(`/engineering/printing/frames/${id}/clean`, data),
  getPrintPlate: (id: number) => request.get(`/engineering/printing/plates/${id}`),
  createPrintPlate: (data: any) => request.post('/engineering/printing/plates', data),
  releasePrintPlate: (id: number, data: any) => request.post(`/engineering/printing/plates/${id}/release`, data),
  failPrintPlate: (id: number, data: any) => request.post(`/engineering/printing/plates/${id}/fail`, data),
  washPrintPlate: (id: number, data: any) => request.post(`/engineering/printing/plates/${id}/wash`, data),
  scrapPrintPlate: (id: number, data: any) => request.post(`/engineering/printing/plates/${id}/scrap`, data),
}

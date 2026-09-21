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
  replaceProducts: (type: string, id: number, productIds: number[], purpose?: string) =>
    request.put(`${base}/${type}/${id}/products`, { productIds, purpose }),
  maintenance: (type: string, id: number) => request.get(`${base}/${type}/${id}/maintenance`),
}

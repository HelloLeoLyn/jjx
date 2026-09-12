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
  products: (type: string, id: number) => request.get(`${base}/${type}/${id}/products`),
  replaceProducts: (type: string, id: number, productIds: number[], purpose?: string) =>
    request.put(`${base}/${type}/${id}/products`, { productIds, purpose }),
  maintenance: (type: string, id: number) => request.get(`${base}/${type}/${id}/maintenance`),
}

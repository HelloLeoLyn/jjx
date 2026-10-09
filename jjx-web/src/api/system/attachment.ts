import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

export interface DrawingMetadata {
  drawingNo: string
  drawingName?: string
  version: string
  fileRole: string
  category?: string
}
export interface ProductDrawingFile extends Partial<DrawingMetadata> {
  id: number
  fileName: string
  category: string
  fileType?: string
  fileSize?: number
  isCurrent?: number
  isControlled?: number
  released?: number
  releasedAt?: string
  releasedBy?: string
  createBy?: string
  createTime?: string
}

// 通用附件API
export const attachmentApi = {
  // 上传附件
  upload(
    file: File,
    bizType: string,
    bizId: number,
    remark?: string,
    traceId?: string,
    category?: string
  ): AxiosPromise<number> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('bizType', bizType)
    formData.append('bizId', String(bizId))
    if (remark) formData.append('remark', remark)
    if (traceId) formData.append('traceId', traceId)
    if (category) formData.append('category', category)
    return request({
      url: '/system/attachment/upload',
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  // 获取附件列表
  list(bizType: string, bizId: number): AxiosPromise<any[]> {
    return request({
      url: '/system/attachment/list',
      method: 'get',
      params: { bizType, bizId },
    })
  },

  // 按链路追踪ID获取附件（含来源单据文档）
  listByTrace(traceId: string): AxiosPromise<any[]> {
    return request({
      url: `/system/attachment/by-trace/${traceId}`,
      method: 'get',
    })
  },

  // 删除附件
  remove(id: number): AxiosPromise<boolean> {
    return request({
      url: `/system/attachment/${id}`,
      method: 'delete',
    })
  },

  // 客供资料归集：某产品关联的询价/报价附件（只读；dev-20260929-023）
  customerDocs(productId: number): AxiosPromise<any[]> {
    return request({
      url: `/system/attachment/customer-docs/${productId}`,
      method: 'get',
    })
  },

  // 按图纸编号切换现行版本；同版本原稿和打印件同步
  setCurrent(id: number): AxiosPromise<boolean> {
    return request({
      url: `/system/attachment/${id}/set-current`,
      method: 'post',
    })
  },

  // 图纸/工程文件下发或撤回（受控文件；dev-20261009-023）
  release(id: number, released = true): AxiosPromise<boolean> {
    return request({
      url: `/system/attachment/${id}/release`,
      method: 'post',
      params: { released },
    })
  },

  // 下载/预览附件
  downloadUrl(id: number): string {
    const base = (import.meta.env.VITE_BASE_API || '/api') as string
    return `${base}/system/attachment/download/${id}`
  },

  // 上传产品工程文件（产品文件库）
  uploadProductFile(file: File, productCode: string, category: string, version?: string,
    drawing?: DrawingMetadata, onProgress?: (percent: number) => void): AxiosPromise<number> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('productCode', productCode)
    formData.append('category', category)
    if (version) formData.append('version', version)
    if (drawing) {
      formData.append('drawingNo', drawing.drawingNo)
      formData.append('drawingName', drawing.drawingName || '')
      formData.set('version', drawing.version)
      formData.append('fileRole', drawing.fileRole)
    }
    return request({
      url: '/system/attachment/upload-product',
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
      onUploadProgress: (event) => {
        if (event.total) onProgress?.(Math.round(event.loaded * 100 / event.total))
      },
    })
  },

  updateDrawingMetadata(id: number, data: DrawingMetadata): AxiosPromise<boolean> {
    return request({ url: `/system/attachment/${id}/drawing-metadata`, method: 'put', data })
  },

  // 获取产品文件库（按产品编码）
  productFiles(productCode: string): AxiosPromise<any[]> {
    return request({
      url: `/system/attachment/product/${encodeURIComponent(productCode)}`,
      method: 'get',
    })
  },

  // 回收站列表
  recycleList(): AxiosPromise<any[]> {
    return request({
      url: '/system/attachment/recycle-list',
      method: 'get',
    })
  },

  // 恢复附件
  restore(id: number): AxiosPromise<boolean> {
    return request({
      url: `/system/attachment/restore/${id}`,
      method: 'post',
    })
  },

  // 彻底删除（回收站）
  permanent(id: number): AxiosPromise<boolean> {
    return request({
      url: `/system/attachment/permanent/${id}`,
      method: 'delete',
    })
  },

  // 清理回收站过期附件
  permanentExpired(days = 30): AxiosPromise<number> {
    return request({
      url: '/system/attachment/permanent-expired',
      method: 'post',
      params: { days },
    })
  },
}

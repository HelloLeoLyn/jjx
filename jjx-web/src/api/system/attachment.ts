import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

// 通用附件API
export const attachmentApi = {
  // 上传附件
  upload(file: File, bizType: string, bizId: number, remark?: string): AxiosPromise<number> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('bizType', bizType)
    formData.append('bizId', String(bizId))
    if (remark) formData.append('remark', remark)
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

  // 下载/预览附件
  downloadUrl(id: number): string {
    const base = (import.meta.env.VITE_BASE_API || '/api') as string
    return `${base}/system/attachment/download/${id}`
  },

  // 上传产品工程文件（产品文件库）
  uploadProductFile(file: File, productCode: string, category: string, version?: string): AxiosPromise<number> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('productCode', productCode)
    formData.append('category', category)
    if (version) formData.append('version', version)
    return request({
      url: '/system/attachment/upload-product',
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  // 获取产品文件库（按产品编码）
  productFiles(productCode: string): AxiosPromise<any[]> {
    return request({
      url: `/system/attachment/product/${productCode}`,
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

import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

// 通用附件API
export const attachmentApi = {
  // 上传附件
  upload(file: File, bizType: string, bizId: number): AxiosPromise<number> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('bizType', bizType)
    formData.append('bizId', String(bizId))
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
}

import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

// 产品配置模型API
export const configModelApi = {
  // 列表
  list(): AxiosPromise<any[]> {
    return request({ url: '/engineering/config', method: 'get' })
  },

  // 详情（含选项）
  detail(modelId: number): AxiosPromise<any> {
    return request({ url: `/engineering/config/${modelId}`, method: 'get' })
  },

  // 创建
  create(data: any): AxiosPromise<number> {
    return request({ url: '/engineering/config', method: 'post', data })
  },

  // 更新
  update(data: any): AxiosPromise<void> {
    return request({ url: '/engineering/config', method: 'put', data })
  },

  // 删除
  remove(modelId: number): AxiosPromise<void> {
    return request({ url: `/engineering/config/${modelId}`, method: 'delete' })
  },

  // 设置默认
  setDefault(modelId: number): AxiosPromise<void> {
    return request({ url: `/engineering/config/${modelId}/default`, method: 'put' })
  },

  // 启用/停用
  changeStatus(modelId: number, status: number): AxiosPromise<void> {
    return request({ url: `/engineering/config/${modelId}/status/${status}`, method: 'put' })
  },
}

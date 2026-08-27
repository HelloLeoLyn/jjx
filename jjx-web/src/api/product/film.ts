import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

// 菲林/薄膜管理API（对接 /engineering/films）
export interface EngineeringFilm {
  filmId?: number
  filmCode?: string
  filmName?: string
  filmType?: string
  filmTypeName?: string
  productId?: number
  productCode?: string
  productName?: string
  version?: string
  isCurrent?: number
  isCurrentName?: string
  parentFilmId?: number
  filmSize?: string
  filmThickness?: number
  filmMaterial?: string
  color?: string
  technicalSpec?: string
  designNotes?: string
  remark?: string
  status?: number
  statusName?: string
  createTime?: string
}

export const filmApi = {
  // 根据产品ID获取菲林列表
  getByProductId(productId: number): AxiosPromise<EngineeringFilm[]> {
    return request.get(`/engineering/films/product/${productId}`)
  },

  // 获取菲林详情
  getById(filmId: number): AxiosPromise<EngineeringFilm> {
    return request.get(`/engineering/films/${filmId}`)
  },

  // 创建菲林
  create(data: Partial<EngineeringFilm>): AxiosPromise<EngineeringFilm> {
    return request.post('/engineering/films', data)
  },

  // 更新菲林
  update(filmId: number, data: Partial<EngineeringFilm>): AxiosPromise<EngineeringFilm> {
    return request.put(`/engineering/films/${filmId}`, data)
  },

  // 删除菲林
  remove(filmId: number): AxiosPromise<void> {
    return request.delete(`/engineering/films/${filmId}`)
  },

  // 提交审批
  submitApprove(filmId: number): AxiosPromise<void> {
    return request.post(`/engineering/films/${filmId}/submit`)
  },

  // 审批通过
  approve(filmId: number): AxiosPromise<void> {
    return request.put(`/engineering/films/${filmId}/approve`)
  },

  // 审批驳回
  reject(filmId: number): AxiosPromise<void> {
    return request.put(`/engineering/films/${filmId}/reject`)
  },

  // 创建新版本
  newVersion(filmId: number): AxiosPromise<EngineeringFilm> {
    return request.post(`/engineering/films/${filmId}/new-version`)
  },

  // 设为当前版本
  setCurrent(filmId: number): AxiosPromise<void> {
    return request.put(`/engineering/films/${filmId}/set-current`)
  },

  // 下发生产
  release(filmId: number): AxiosPromise<void> {
    return request.put(`/engineering/films/${filmId}/release`)
  },
}

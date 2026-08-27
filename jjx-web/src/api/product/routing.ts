import request from '@/utils/request'

import type {
  EngineeringRoutingItemVO,
  EngineeringRoutingVO,
  ProductRouteQueryParams,
  ProductRouteFormData,
} from '@/types/product/routing'
import type { PageResult, R } from '@/types'
// ==================== 工艺路线API ====================
// 菜单管理API
export const productRouteApi = {
  /**
   * 分页查询工艺路线
   * 对应后端 GET /engineering/routings/page
   */
  listProductRoute(params: ProductRouteQueryParams) {
    return request.get<R<PageResult<EngineeringRoutingVO>>>('/engineering/routings/page', {
      params,
    })
  },

  /**
   * 获取工艺路线详情
   * 对应后端 GET /engineering/routings/{routingId}
   */
  getProductRouteInfo(routingId: number) {
    return request.get<R<EngineeringRoutingVO>>(`/engineering/routings/${routingId}`)
  },

  /**
   * 创建工艺路线
   * 对应后端 POST /product/routings
   */
  addProductRoute(data: ProductRouteFormData) {
    return request.post<R<EngineeringRoutingVO>>('/engineering/routings', data)
  },

  /**
   * 更新工艺路线
   * 对应后端 PUT /engineering/routings/{routingId}
   */
  editProductRoute(routingId: number, data: ProductRouteFormData) {
    return request.put<R<EngineeringRoutingVO>>(`/engineering/routings/${routingId}`, data)
  },
  /**
   * 删除工艺路线
   * 对应后端 DELETE /engineering/routings/{routingId}
   */
  removeProductRoute(routingId: number) {
    return request.delete(`/engineering/routings/${routingId}`)
  },
  /**
   * 复制为新版本
   * 对应后端 POST /engineering/routings/{routingId}/copy?newVersion={newVersion}
   */
  copyProductRoute(routingId: number, newVersion: string) {
    return request.post<R<EngineeringRoutingVO>>(`/engineering/routings/${routingId}/copy`, null, {
      params: { newVersion },
    })
  },

  /**
   * 设置当前版本
   * 对应后端 PUT /engineering/routings/{routingId}/set-current
   */
  setCurrentProductRoute(routingId: number) {
    return request.put(`/engineering/routings/${routingId}/set-current`)
  },

  /**
   * 获取产品所有版本
   * 对应后端 GET /engineering/routings/product/{productId}/versions
   */
  getProductRouteVersions(productId: number) {
    return request.get<R<EngineeringRoutingVO[]>>(`/engineering/routings/product/${productId}/versions`)
  },

  /**
   * 获取产品当前版本
   * 对应后端 GET /engineering/routings/product/{productId}/current
   */
  getCurrentProductRoute(productId: number) {
    return request.get<R<EngineeringRoutingVO>>(`/engineering/routings/product/${productId}/current`)
  },

  /**
   * 提交审批
   * 对应后端 POST /engineering/routings/{routingId}/submit
   */
  submitProductRoute(routingId: number) {
    return request.post(`/engineering/routings/${routingId}/submit`)
  },

  /**
   * 审批通过
   * 对应后端 PUT /engineering/routings/{routingId}/approve
   */
  approveProductRoute(routingId: number, remark?: string) {
    return request.put(`/engineering/routings/${routingId}/approve`, null, {
      params: { remark },
    })
  },

  /**
   * 审批驳回
   * 对应后端 PUT /engineering/routings/{routingId}/reject
   */
  rejectProductRoute(routingId: number, remark: string) {
    return request.put(`/engineering/routings/${routingId}/reject`, null, {
      params: { remark },
    })
  },

  /**
   * 重新计算工时
   * 对应后端 POST /engineering/routings/{routingId}/calculate-hours
   */
  calculateProductRouteHours(routingId: number) {
    return request.post(`/engineering/routings/${routingId}/calculate-hours`)
  },

  /**
   * 验证工艺路线
   * 对应后端 GET /engineering/routings/{routingId}/validate
   */
  validateProductRoute(routingId: number) {
    return request.get<boolean>(`/engineering/routings/${routingId}/validate`)
  },

  /**
   * 获取启用的标准工序列表
   * 对应后端 GET /engineering/standard-processes/enabled
   */
  getEnabledProcesses() {
    return request.get('/engineering/standard-processes/enabled')
  },

  /**
   * 获取产品已审批的工艺路线列表
   */
  getApprovedRouteList(productId: number) {
    return request.get(`/engineering/routings/product/${productId}/approved`)
  },
}

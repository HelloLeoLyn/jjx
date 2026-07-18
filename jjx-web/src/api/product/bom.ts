import request from '@/utils/request'
import type {
  ProductBomQueryParams,
  ProductBom,
  ProductBomFormData,
  ProductBomItem,
  BomSimpleVo,
} from '@/types/product/bom'
import type { PageResult, R } from '@/types'
// ==================== BomAPI ====================
// 菜单管理API
export const productBomApi = {
  // ==================== BOM管理API ====================

  /**
   * 获取BOM列表
   */
  listProductBom(params: ProductBomQueryParams) {
    return request.get<R<PageResult<ProductBom>>>('/product/bom/page', {
      params,
    })
  },
  /**
   * 获取BOM列表
   */
  getApprovedBomByProductId(prodcutId: number) {
    return request.get<R<BomSimpleVo[]>>(`/product/bom/approved/${prodcutId}`)
  },
  /**
   * 获取BOM详情
   */
  getProductBomInfo(bomId: number) {
    return request.get(`/product/bom/${bomId}`)
  },

  /**
   * 新增BOM
   */
  addProductBom(data: ProductBomFormData) {
    return request.post('/product/bom', data)
  },

  /**
   * 修改BOM
   */
  editProductBom(data: ProductBomFormData) {
    return request.put('/product/bom', data)
  },

  /**
   * 删除BOM
   */
  removeProductBom(bomId: number) {
    return request.delete(`/product/bom/${bomId}`)
  },

  /**
   * 获取BOM明细列表
   */
  listProductBomItem(bomId: number) {
    return request.get(`/product/bom/items/${bomId}`)
  },

  /**
   * 新增BOM明细
   */
  addProductBomItem(bomId: number, data: ProductBomItem) {
    return request.post(`/product/bom/${bomId}/items`, data)
  },

  /**
   * 修改BOM明细
   */
  editProductBomItem(itemId: number, data: ProductBomItem) {
    return request.put(`/product/bom/items/${itemId}`, data)
  },

  /**
   * 删除BOM明细
   */
  removeProductBomItem(itemId: number) {
    return request.delete(`/product/bom/items/${itemId}`)
  },

  /**
   * 审批BOM
   */
  approveProductBom(bomId: number, remark?: string) {
    return request.put(`/product/bom/approve/${bomId}`, { bomId, remark })
  },

  /**
   * 驳回BOM
   */
  rejectProductBom(bomId: number, remark: string) {
    return request.put(`/product/bom/reject/${bomId}`, { bomId, remark })
  },

  /**
   * 设置默认BOM（对应后端 PUT /product/bom/setDefault/{bomId}）
   */
  setCurrentProductBom(bomId: number) {
    return request.put(`/product/bom/setDefault/${bomId}`)
  },

  /**
   * 计算BOM成本（对应后端 POST /product/bom/calculateCost/{bomId}）
   */ calculateBomCost(bomId: number) {
    return request.post(`/product/bom/calculateCost/${bomId}`)
  },

  /**
   * 导出BOM数据
   */
  exportProductBom(params: ProductBomQueryParams) {
    return request.get('/product/bom/export', {
      params,
      responseType: 'blob',
    })
  },
}

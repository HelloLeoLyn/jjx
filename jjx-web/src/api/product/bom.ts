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
    return request.get<R<PageResult<ProductBom>>>('/engineering/bom/page', {
      params,
    })
  },
  /**
   * 获取BOM列表
   */
  getApprovedBomByProductId(prodcutId: number) {
    return request.get<R<BomSimpleVo[]>>(`/engineering/bom/approved/${prodcutId}`)
  },
  /**
   * 获取BOM详情
   */
  getProductBomInfo(bomId: number) {
    return request.get(`/engineering/bom/${bomId}`)
  },

  /**
   * 新增BOM
   */
  addProductBom(data: ProductBomFormData) {
    return request.post('/engineering/bom', data)
  },

  /**
   * 修改BOM
   */
  editProductBom(data: ProductBomFormData) {
    return request.put('/engineering/bom', data)
  },

  /**
   * 删除BOM
   */
  removeProductBom(bomId: number) {
    return request.delete(`/engineering/bom/${bomId}`)
  },

  /**
   * 获取BOM明细列表
   */
  listProductBomItem(bomId: number) {
    return request.get(`/engineering/bom/items/${bomId}`)
  },

  /**
   * 审批BOM
   */
  approveProductBom(bomId: number, remark?: string) {
    return request.put(`/engineering/bom/approve/${bomId}`, { bomId, remark })
  },

  /**
   * 驳回BOM
   */
  rejectProductBom(bomId: number, remark: string) {
    return request.put(`/engineering/bom/reject/${bomId}`, { bomId, remark })
  },

  /**
   * 设置默认BOM（对应后端 PUT /engineering/bom/setDefault/{bomId}）
   */
  setCurrentProductBom(bomId: number) {
    return request.put(`/engineering/bom/setDefault/${bomId}`)
  },

  /**
   * 计算BOM成本（对应后端 POST /engineering/bom/calculateCost/{bomId}）
   */ calculateBomCost(bomId: number) {
    return request.post(`/engineering/bom/calculateCost/${bomId}`)
  },

  /**
   * 导出BOM数据
   */
  exportProductBom(params: ProductBomQueryParams) {
    return request.get('/engineering/bom/export', {
      params,
      responseType: 'blob',
    })
  },
}

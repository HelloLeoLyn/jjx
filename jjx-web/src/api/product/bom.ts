import request from '@/utils/request'
import type {
  EngineeringBomQueryParams,
  EngineeringBom,
  EngineeringBomFormData,
  EngineeringBomItem,
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
  listEngineeringBom(params: EngineeringBomQueryParams) {
    return request.get<R<PageResult<EngineeringBom>>>('/engineering/bom/page', {
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
  getEngineeringBomInfo(bomId: number) {
    return request.get(`/engineering/bom/${bomId}`)
  },

  /**
   * 新增BOM
   */
  addEngineeringBom(data: EngineeringBomFormData) {
    return request.post('/engineering/bom', data)
  },

  /**
   * 修改BOM
   */
  editEngineeringBom(data: EngineeringBomFormData) {
    return request.put('/engineering/bom', data)
  },

  /**
   * 删除BOM
   */
  removeEngineeringBom(bomId: number) {
    return request.delete(`/engineering/bom/${bomId}`)
  },

  /**
   * 获取BOM明细列表
   */
  listEngineeringBomItem(bomId: number) {
    return request.get(`/engineering/bom/items/${bomId}`)
  },

  /**
   * 提交BOM审核（草稿→审核中）
   */
  submitEngineeringBom(bomId: number) {
    return request.put(`/engineering/bom/submit/${bomId}`)
  },

  /**
   * 审批BOM
   */
  approveEngineeringBom(bomId: number, remark?: string) {
    return request.put(`/engineering/bom/approve/${bomId}`, { bomId, remark })
  },

  /**
   * 驳回BOM
   */
  rejectEngineeringBom(bomId: number, remark: string) {
    return request.put(`/engineering/bom/reject/${bomId}`, { bomId, remark })
  },

  /**
   * 设置默认BOM（对应后端 PUT /engineering/bom/setDefault/{bomId}）
   */
  setCurrentEngineeringBom(bomId: number) {
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
  exportEngineeringBom(params: EngineeringBomQueryParams) {
    return request.get('/engineering/bom/export', {
      params,
      responseType: 'blob',
    })
  },
}

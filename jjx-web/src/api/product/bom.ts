import request from '@/utils/request'
import type {
  EngineeringBomQueryParams,
  EngineeringBom,
  EngineeringBomFormData,
  EngineeringBomItem,
  BomSimpleVo,
  BomCheckIssue,
  BomMaterialMatchItem,
  BomMaterialMatchResult,
  BomItemReviewRequest,
  BomItemBatchReviewRequest,
  BomItemBatchReviewResult,
  BomMaterialBatchResult,
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
  getBomVersions(bomCode: string) {
    return request.get<R<EngineeringBom[]>>('/engineering/bom/versions', { params: { bomCode } })
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
   * BOM 物料全量同步：批量按「名称+规格」匹配物料库（只读）
   * 2026-10-10 dev-20261010-002
   */
  matchMaterials(items: BomMaterialMatchItem[]) {
    return request.post<R<BomMaterialMatchResult[]>>('/engineering/bom/match-materials', items)
  },
  /** BOM 批量关联物料（dev-20261024-020）：逐行确认后一次提交 */
  batchLinkMaterials(payload: { bomId: number; links: Array<{ itemId: number; materialId: number }> }) {
    return request.post<R<BomMaterialBatchResult>>('/engineering/bom/batch-link-materials', payload)
  },

  /**
   * 提交审核前数据完整性体检（只读预检）
   * 2026-10-07 dev-20261007-006
   */
  checkEngineeringBom(bomId: number) {
    return request.get<R<BomCheckIssue[]>>(`/engineering/bom/${bomId}/check`)
  },

  /**
   * BOM 批准前体检（行状态/待移除确认/物料关联）
   * 2026-10-21 dev-20261020-029
   */
  checkBomForApprove(bomId: number) {
    return request.get<R<BomCheckIssue[]>>(`/engineering/bom/${bomId}/approve-check`)
  },

  /**
   * BOM 明细行级审核：通过/驳回/确认移除/关联物料（逐行写流水）
   * 2026-10-21 dev-20261020-029
   */
  reviewBomItemsBatch(data: BomItemBatchReviewRequest) {
    return request.post<R<BomItemBatchReviewResult>>('/engineering/bom/item/review/batch', data)
  },

  reviewBomItem(data: BomItemReviewRequest) {
    return request.put('/engineering/bom/item/review', data)
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
   * 复制为新版本（DEV-619：版本号递增、明细复制、isCurrent=false）
   */
  copyEngineeringBom(bomId: number, newVersion: string) {
    return request.post(`/engineering/bom/${bomId}/copy`, null, {
      params: { newVersion },
    })
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

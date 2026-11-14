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
  ManufacturingOutputType,
  ManufacturingScheme,
  ManufacturingBomDetail,
  ManufacturingBomSavePayload,
  ManufacturingSchemeChoice,
  BomEditSession,
  ProductBomRow,
  BomReviewPackage,
  BomReviewPackageRequest,
  BomReviewBatchResult,
  BomReferenceImpact,
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
  /** 产品/材料BOM页签聚合行（产品页默认 PRODUCT；材料页传 MATERIAL）。 */
  getProductBomPage(params: EngineeringBomQueryParams) {
    return request.get<R<PageResult<ProductBomRow>>>('/engineering/bom/product-page', { params })
  },
  /** 整套范围预览：只读展示每个产品实际引用的方案版本/状态/明细/问题。 */
  planReviewPackages(action: 'SUBMIT' | 'APPROVE' | 'REJECT', rootBomIds: number[]) {
    return request.post<R<BomReviewPackage[]>>('/engineering/bom/review/package/plan', rootBomIds, {
      params: { action },
    })
  },
  /** 产品侧整套送审（草稿/已驳回根方案 + 本次实际引用的未批准下层方案）。 */
  submitReviewPackages(data: BomReviewPackageRequest) {
    return request.post<R<BomReviewBatchResult>>('/engineering/bom/review/package/submit', data)
  },
  /** 产品侧整套通过（一次确认待审明细与本次待审方案）。 */
  approveReviewPackages(data: BomReviewPackageRequest) {
    return request.post<R<BomReviewBatchResult>>('/engineering/bom/review/package/approve', data)
  },
  /** 产品侧整套驳回（只退回该产品根方案）。 */
  rejectReviewPackages(data: BomReviewPackageRequest) {
    return request.post<R<BomReviewBatchResult>>('/engineering/bom/review/package/reject', data)
  },
  /** 材料/公共子方案被驳回或独立审批前的影响范围。 */
  getBomReferenceImpact(bomId: number) {
    return request.get<R<BomReferenceImpact[]>>(`/engineering/bom/${bomId}/references`)
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
   * 审批BOM（expectedContentRevision：详情加载时的服务端内容指纹，内容被保存改动后返回 409）
   */
  approveEngineeringBom(bomId: number, remark?: string, expectedContentRevision?: string | null) {
    return request.put(`/engineering/bom/approve/${bomId}`, { bomId, remark, expectedContentRevision })
  },

  /**
   * 驳回BOM（同样要求服务端内容指纹，避免驳回旧内容）
   */
  rejectEngineeringBom(bomId: number, remark: string, expectedContentRevision?: string | null) {
    return request.put(`/engineering/bom/reject/${bomId}`, { bomId, remark, expectedContentRevision })
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

  // ==================== 制造BOM方案（dev-20261111-004 后端接口，dev-20261111-005 前端接入） ====================

  /** 某产出对象的候选制造方案（含草稿标记；productId 用于回显本产品主方案）。 */
  manufacturingOptions(params: {
    outputType: ManufacturingOutputType
    outputId: number
    productId?: number | null
  }) {
    return request.get<R<ManufacturingScheme[]>>('/engineering/bom/manufacturing/options', { params })
  },

  /** 新建制造方案并保存明细；不自动发布、不改父 BOM。 */
  saveManufacturingBom(data: ManufacturingBomSavePayload) {
    return request.post<R<ManufacturingBomDetail>>('/engineering/bom/manufacturing', data)
  },

  /** 编辑已保存制造方案；产出身份不可原地改变。 */
  updateManufacturingBom(bomId: number, data: ManufacturingBomSavePayload) {
    return request.put<R<ManufacturingBomDetail>>(`/engineering/bom/manufacturing/${bomId}`, data)
  },

  /** 生成采用 token；不落库、不设主方案，父 BOM 保存时才持久化关联。 */
  adoptManufacturingBom(bomId: number) {
    return request.post<R<ManufacturingScheme>>(`/engineering/bom/manufacturing/${bomId}/adopt`, null)
  },

  /** 查看父产品 BOM 已保存的方案选择（非产品 BOM 返回空列表）。 */
  getBomSchemeChoices(bomId: number) {
    return request.get<R<ManufacturingSchemeChoice[]>>(`/engineering/bom/${bomId}/scheme-choices`)
  },

  // ==================== 编辑占用会话（dev-20261111-018） ====================

  /** 查询当前占用；leaseAvailable=false 或 occupied 时本窗口只能只读。 */
  describeBomEditSession(bomId: number, clientId: string) {
    return request.get<R<BomEditSession>>(`/engineering/bom/${bomId}/edit-session`, {
      params: { clientId },
    })
  },

  /** 获取编辑权（SET NX）；已被占用时返回占用者信息，不返回他人 token。 */
  acquireBomEditSession(bomId: number, clientId: string) {
    return request.post<R<BomEditSession>>(`/engineering/bom/${bomId}/edit-session`, { clientId })
  },

  /** 心跳续租（compare token）；token 失效返回 409，前端立即只读并保留草稿。 */
  renewBomEditSession(bomId: number, clientId: string, token: string) {
    return request.put<R<BomEditSession>>(`/engineering/bom/${bomId}/edit-session`, { clientId, token })
  },

  /** 关闭/取消时 compare-token 释放；保存成功不自动释放。 */
  releaseBomEditSession(bomId: number, token: string) {
    return request.delete<R<boolean>>(`/engineering/bom/${bomId}/edit-session`, {
      data: { token },
    })
  },
}

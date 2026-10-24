export type MaterialClassStatusCode = 'unclassified' | 'rule_unconfirmed' | 'confirmed'
export type MaterialClassMatchField = 'NAME' | 'SPEC' | 'ANY'

/** 归类规则（inventory_material_classify_rule） */
export interface MaterialClassifyRule {
  id?: number
  keyword: string
  matchField: MaterialClassMatchField
  categoryCode: string
  priority?: number
  enabled?: boolean
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

export interface ClassifyBatchRequest {
  materialIds: number[]
  categoryId: number
}

export interface ClassifyItem {
  materialId: number
  success: boolean
  message?: string
}

export interface ClassifyBatchResult {
  successCount: number
  failCount: number
  details: ClassifyItem[]
}

export interface ApplyRuleChange {
  materialId: number
  materialName: string
  rule: {
    ruleId?: number
    keyword?: string
    matchField?: MaterialClassMatchField
    categoryId?: number
    categoryCode?: string
  }
}

export interface ApplyRulesResult {
  dryRun: boolean
  changes: ApplyRuleChange[]
  failures: ClassifyItem[]
}

import request from '@/utils/request'
import type { R } from '@/types'
import type { RequirementPreviewRequest, RequirementSummary } from '@/types/production/requirementPlan'

/** 多层净需求只读试算：服务端读库存/供给，客户端只提交需求条件。 */
export function previewRequirement(data: RequirementPreviewRequest) {
  return request.post<R<RequirementSummary>>('/production/requirement/preview', data)
}

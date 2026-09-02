import request from '@/utils/request'

/** 分页查询需求单 */
export function pageRequirement(params?: Record<string, unknown>) {
  return request({
    url: '/biz/requirement/page',
    method: 'get',
    params,
  })
}

/** 需求单详情 */
export function getRequirement(requirementId: number) {
  return request({
    url: `/biz/requirement/${requirementId}`,
    method: 'get',
  })
}

/** 新增需求单 */
export function createRequirement(data: Record<string, unknown>) {
  return request({
    url: '/biz/requirement',
    method: 'post',
    data,
  })
}

/** 修改需求单 */
export function updateRequirement(data: Record<string, unknown>) {
  return request({
    url: '/biz/requirement',
    method: 'put',
    data,
  })
}

/** 删除需求单 */
export function removeRequirement(requirementIds: number | number[]) {
  return request({
    url: `/biz/requirement/${requirementIds}`,
    method: 'delete',
  })
}

/** 提交评审 */
export function submitRequirement(requirementId: number) {
  return request({
    url: `/biz/requirement/submit/${requirementId}`,
    method: 'put',
  })
}

/** 审核（通过/驳回） */
export function reviewRequirement(requirementId: number, approved: boolean, remark?: string) {
  return request({
    url: `/biz/requirement/review/${requirementId}`,
    method: 'put',
    params: { approved, remark },
  })
}

/** 需求类型选项 */
export function listRequirementTypes() {
  return request({
    url: '/biz/requirement/type-options',
    method: 'get',
  })
}

/** 状态选项 */
export function listRequirementStatuses() {
  return request({
    url: '/biz/requirement/status-options',
    method: 'get',
  })
}

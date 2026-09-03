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

/** 四部门会签（同意/不同意+意见） */
export function signApproval(requirementId: number, role: string, approved: boolean, comment?: string) {
  return request({
    url: `/biz/requirement/approval/${requirementId}`,
    method: 'put',
    params: { role, approved, comment },
  })
}

/** 会签记录（全部轮次） */
export function listApprovals(requirementId: number) {
  return request({
    url: `/biz/requirement/approvals/${requirementId}`,
    method: 'get',
  })
}

/** 开始执行（审核通过后） */
export function executeRequirement(requirementId: number) {
  return request({
    url: `/biz/requirement/execute/${requirementId}`,
    method: 'put',
  })
}

/** 关闭（登记执行结果） */
export function closeRequirement(requirementId: number, result?: string) {
  return request({
    url: `/biz/requirement/close/${requirementId}`,
    method: 'put',
    params: { result },
  })
}

/** 变更升版（关联产品 → 复制 BOM/工艺路线新版本） */
export function upgradeRequirement(requirementId: number, newVersion: string) {
  return request({
    url: `/biz/requirement/upgrade/${requirementId}`,
    method: 'put',
    params: { newVersion },
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

/** 公共审核表单的结果；由业务调用方映射到各模块动作。 */
export const ApprovalResultEnum = {
  APPROVE: 'approve',
  REJECT: 'reject',
} as const

export type ApprovalResult = (typeof ApprovalResultEnum)[keyof typeof ApprovalResultEnum]

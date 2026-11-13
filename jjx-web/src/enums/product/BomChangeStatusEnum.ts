/** BOM 变更单状态枚举（dev-20261112-009）：页面与测试的唯一来源。 */
export const BomChangeStatusEnum = {
  DRAFT: { value: 'DRAFT', label: '草稿' },
  PENDING: { value: 'PENDING', label: '待审批' },
  APPROVED: { value: 'APPROVED', label: '已批准' },
  EFFECTIVE: { value: 'EFFECTIVE', label: '已生效' },
  CANCELLED: { value: 'CANCELLED', label: '已取消' },
} as const

export type BomChangeStatus = (typeof BomChangeStatusEnum)[keyof typeof BomChangeStatusEnum]['value']

export const BOM_CHANGE_STATUS_OPTIONS = Object.values(BomChangeStatusEnum)

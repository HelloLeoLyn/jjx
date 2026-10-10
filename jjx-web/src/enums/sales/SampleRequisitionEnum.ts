/** 样品需求单会签 / 打样不良记录 相关枚举（dev-20261010-028） */

/** 样品需求单签字位（对应后端 SalesSampleRequisitionSignRole） */
export const SampleRequisitionSignRoleEnum = {
  SALES: 'SALES',
  APPROVE: 'APPROVE',
  DEPT: 'DEPT',
} as const

export type SampleRequisitionSignRole = (typeof SampleRequisitionSignRoleEnum)[keyof typeof SampleRequisitionSignRoleEnum]

/** 三个签字位：展示名 + 各自权限点（权限点由「角色管理」分配） */
export const SAMPLE_REQUISITION_SIGN_ROLES: {
  role: SampleRequisitionSignRole
  label: string
  permission: string
}[] = [
  { role: SampleRequisitionSignRoleEnum.SALES, label: '业务', permission: 'sales:sample:reqsign:sales' },
  { role: SampleRequisitionSignRoleEnum.APPROVE, label: '核准', permission: 'sales:sample:reqsign:approve' },
  { role: SampleRequisitionSignRoleEnum.DEPT, label: '部门主管', permission: 'sales:sample:reqsign:dept' },
]

/** 制样类别（对应后端 SalesSampleCraftType）：QR-065 两栏制样记录 */
export const SampleCraftTypeEnum = {
  PRINT: 'PRINT',
  PUNCH: 'PUNCH',
} as const

export type SampleCraftType = (typeof SampleCraftTypeEnum)[keyof typeof SampleCraftTypeEnum]

export const SAMPLE_CRAFT_TYPES: { value: SampleCraftType; label: string }[] = [
  { value: SampleCraftTypeEnum.PRINT, label: '印刷' },
  { value: SampleCraftTypeEnum.PUNCH, label: '加工冲型' },
]

export const SAMPLE_DEFECT_RECORD_PERMISSION = 'engineering:sample:defect:record'

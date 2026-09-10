import { createNamedEnum } from '../base'

/**
 * 在职状态（人事模块，与字典 hr_employment_status 对齐）
 * 1 试用 / 2 正式 / 3 停薪留职 / 9 离职
 */
export const EmploymentStatusEnum = createNamedEnum(
  {
    PROBATION: { value: 1, label: '试用', tagProps: { type: 'warning' } },
    REGULAR: { value: 2, label: '正式', tagProps: { type: 'success' } },
    SUSPENDED: { value: 3, label: '停薪留职', tagProps: { type: 'warning' } },
    RESIGNED: { value: 9, label: '离职', tagProps: { type: 'info' } },
  },
  { type: 'info' },
)

/** 岗位状态：0 停用 / 1 启用 */
export const PositionStatusEnum = createNamedEnum(
  {
    DISABLED: { value: 0, label: '停用', tagProps: { type: 'info' } },
    ENABLED: { value: 1, label: '启用', tagProps: { type: 'success' } },
  },
  { type: 'info' },
)

/** 性别：1 男 / 2 女 */
export const SexEnum = createNamedEnum(
  {
    MALE: { value: 1, label: '男', tagProps: { type: 'info' } },
    FEMALE: { value: 2, label: '女', tagProps: { type: 'info' } },
  },
  { type: 'info' },
)

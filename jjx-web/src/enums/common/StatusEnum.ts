// src/enums/common/StatusEnum.ts
import { createNamedEnum } from '../base'

/**
 * 通用状态枚举
 * 对应后端 common StatusEnum（NORMAL=1正常 / DISABLE=0停用 / DELETED=2删除）
 * 2026-09-05（任务1426）：createEnum 列表式改为 createNamedEnum 具名式，供网版启停等通用状态比较使用
 */
export const CommonStatusEnum = createNamedEnum(
  {
    NORMAL: { value: 1, label: '正常', tagProps: { type: 'success' } },
    DISABLED: { value: 0, label: '停用', tagProps: { type: 'danger' } },
    DELETED: { value: 2, label: '删除', tagProps: { type: 'info' } },
  },
  { type: 'info' }
)

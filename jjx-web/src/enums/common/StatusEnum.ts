// src/enums/common/StatusEnum.ts
import { createEnum } from '../base'

/**
 * 通用状态枚举
 * 对应后端 common StatusEnum（NORMAL=1正常 / DISABLE=0停用 / DELETED=2删除）
 */
export const CommonStatusEnum = createEnum<number>({
  items: [
    { value: 1, label: '正常', tagProps: { type: 'success' } },
    { value: 0, label: '停用', tagProps: { type: 'danger' } },
    { value: 2, label: '删除', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

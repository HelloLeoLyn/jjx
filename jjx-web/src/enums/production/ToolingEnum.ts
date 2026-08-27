// src/enums/production/ToolingEnum.ts
import { createEnum } from '../base'

/**
 * 工装模具类型枚举
 * 对应后端 ToolingTypeEnum：SCREEN=网框 DIE=刀模
 */
export const ToolingTypeEnum = createEnum<string>({
  items: [
    { value: 'SCREEN', label: '网框', tagProps: { type: 'primary' } },
    { value: 'DIE', label: '刀模', tagProps: { type: 'warning' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 工装模具状态枚举
 * 对应后端 ToolingStatusEnum：0在库 1使用中 2清洗/保养中 3维修中 4报废
 */
export const ToolingStatusEnum = createEnum<number>({
  items: [
    { value: 0, label: '在库', tagProps: { type: 'success' } },
    { value: 1, label: '使用中', tagProps: { type: 'primary' } },
    { value: 2, label: '清洗/保养中', tagProps: { type: 'warning' } },
    { value: 3, label: '维修中', tagProps: { type: 'danger' } },
    { value: 4, label: '报废', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

// src/enums/product/process.ts
import { createEnum } from '../base'

/**
 * 工序类型枚举
 * 对应后端 ProcessTypeEnum
 */
export const ProcessTypeEnum = createEnum<string>({
  items: [
    { value: 'MAIN_PAD', label: '面板', tagProps: { type: 'primary' } },
    { value: 'UP_LINE', label: '上线', tagProps: { type: 'primary' } },
    { value: 'DOWN_LINE', label: '下线', tagProps: { type: 'primary' } },
    { value: 'PRINTING', label: '印刷', tagProps: { type: 'primary' } },
    { value: 'CUTTING', label: '模切', tagProps: { type: 'success' } },
    { value: 'LAMINATING', label: '贴合', tagProps: { type: 'warning' } },
    { value: 'TESTING', label: '测试', tagProps: { type: 'info' } },
    { value: 'PACKAGING', label: '包装', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 工序类别枚举
 * 对应后端 ProcessCategoryEnum
 */
export const ProcessCategoryEnum = createEnum<string>({
  items: [
    { value: 'PREPARATION', label: '准备', tagProps: { type: 'info' } },
    { value: 'MAIN', label: '主要', tagProps: { type: 'primary' } },
    { value: 'FINISHING', label: '后处理', tagProps: { type: 'warning' } },
    { value: 'QUALITY', label: '质量', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

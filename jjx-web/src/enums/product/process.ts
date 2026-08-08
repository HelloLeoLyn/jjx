// src/enums/product/process.ts
import { createEnum } from '../base'

/**
 * 工序类型枚举
 * 对应后端 ProcessTypeEnum，与字典 process_type 保持一致
 */
export const ProcessTypeEnum = createEnum<string>({
  items: [
    { value: 'PRINTING', label: '印刷', tagProps: { type: 'primary' } },
    { value: 'PUNCH_HOLE', label: '冲孔', tagProps: { type: 'primary' } },
    { value: 'PUNCH_SHAPE', label: '冲型', tagProps: { type: 'primary' } },
    { value: 'LAMINATING', label: '贴合', tagProps: { type: 'warning' } },
    { value: 'CUTTING', label: '裁切', tagProps: { type: 'success' } },
    { value: 'GASKET', label: '垫片', tagProps: { type: 'info' } },
    { value: 'PROTECTIVE_FILM', label: '保护膜', tagProps: { type: 'info' } },
    { value: 'SPACER', label: '隔片', tagProps: { type: 'info' } },
    { value: 'CLEANING', label: '清洁', tagProps: { type: 'info' } },
    { value: 'FILM_APPLY', label: '贴膜', tagProps: { type: 'primary' } },
    { value: 'FILM_REMOVE', label: '撕膜', tagProps: { type: 'warning' } },
    { value: 'RESISTOR', label: '电阻', tagProps: { type: 'danger' } },
    { value: 'CONNECTOR', label: '连接器', tagProps: { type: 'primary' } },
    { value: 'QC', label: '品检', tagProps: { type: 'success' } },
    { value: 'PANEL', label: '面板', tagProps: { type: 'primary' } },
    { value: 'UP_LINE', label: '上线', tagProps: { type: 'primary' } },
    { value: 'DOWN_LINE', label: '下线', tagProps: { type: 'primary' } },
    { value: 'OTHER', label: '其他', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 工序类别枚举
 * 对应后端 ProcessCategoryEnum，与字典 process_category 保持一致
 */
export const ProcessCategoryEnum = createEnum<string>({
  items: [
    { value: 'PANEL', label: '面板', tagProps: { type: 'primary' } },
    { value: 'UP_LINE', label: '上线', tagProps: { type: 'primary' } },
    { value: 'DOWN_LINE', label: '下线', tagProps: { type: 'primary' } },
    { value: 'OTHER', label: '其他', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

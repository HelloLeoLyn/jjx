import { createEnum } from '@/enums/base'

/**
 * 采购票据类型枚举
 */
export const DocumentTypeEnum = createEnum({
  items: [
    { value: 'invoice', label: '发票', tagProps: { type: 'primary' } },
    { value: 'receipt', label: '收据', tagProps: { type: 'info' } },
    { value: 'contract', label: '合同', tagProps: { type: 'warning' } },
    { value: 'quotation', label: '报价单', tagProps: { type: 'success' } },
    { value: 'delivery_note', label: '送货单', tagProps: { type: 'info' } },
    { value: 'other', label: '其他', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

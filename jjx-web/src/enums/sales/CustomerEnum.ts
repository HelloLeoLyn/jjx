import { createEnum } from '../base'
/**客户状态 (1: 潜在客户, 2: 正式客户, 3: 暂停合作, 4: 终止合作) */
export const CustomerStatusEnum = createEnum({
  items: [
    { value: 1, label: '潜在客户', tagProps: { type: 'warning' } },
    { value: 2, label: '正式客户', tagProps: { type: 'info' } },
    { value: 3, label: '暂停合作', tagProps: { type: 'warning' } },
    { value: 4, label: '终止合作', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

export const CustomerTypeEnum = createEnum<number>({
  items: [
    { value: 1, label: '终端客户', tagProps: { type: 'primary', color: '#0099DD' } },
    { value: 2, label: '代理商', tagProps: { type: 'success', color: '#00ABBD' } },
    { value: 3, label: '经销商', tagProps: { type: 'info', color: '#FF9933' } },
  ],
  defaultTag: { type: 'info' },
})

export const CustomerLevelEnum = createEnum<number>({
  items: [
    { value: 1, label: 'A级', tagProps: { type: 'warning', color: '#d81fb9' } },
    { value: 2, label: 'B级', tagProps: { type: 'info', color: '#b11111' } },
    { value: 3, label: 'C级', tagProps: { type: 'success', color: '#07b17e' } },
  ],
  defaultTag: { type: 'info' },
})

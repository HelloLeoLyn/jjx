// views/system/user/config.ts
import type { SearchOptions } from '@/components/common-ui/type'

// 搜索配置
export const searchConfig: SearchOptions[] = [
  {
    prop: 'customerCode',
    label: '客户编码',
    type: 'input',
  },
  {
    prop: 'customerName',
    label: '客户名称',
    type: 'input',
  },
  {
    prop: 'customerType',
    label: '状态',
    type: 'select',
    options: [
      { value: 1, label: '终端客户' },
      { value: 2, label: '代理商' },
      { value: 3, label: '经销商' },
    ],
  },
  {
    prop: 'customerStatus',
    label: '客户状态',
    type: 'select',
    options: [
      { value: 1, label: '潜在客户' },
      { value: 2, label: '正式客户' },
      { value: 3, label: '暂停合作' },
      { value: 4, label: '终止合作' },
    ],
  },
]

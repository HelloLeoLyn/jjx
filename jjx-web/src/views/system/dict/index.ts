import type { SearchOptions, ToolbarOptions, TableOptions } from '@/components/common-ui/type'

// 搜索表单配置
const searchOptions: SearchOptions[] = [
  { prop: 'dictCode', label: '字典编码', type: 'input' },
  { prop: 'dictName', label: '字典名称', type: 'input' },
  {
    prop: 'isActive',
    label: '状态',
    type: 'select',
    options: [
      { value: 1, label: '启用' },
      { value: 0, label: '禁用' },
    ],
  },
]

// 工具栏配置
const toolbarOptions: ToolbarOptions[] = [
  {
    key: 'add',
    label: '新增',
    type: 'primary',
    icon: 'Plus',
    permission: 'system:dict:add',
  },
]

// 表格列配置
const tableOptions: TableOptions[] = [
  { label: '字典编码', prop: 'dictCode', width: 150 },
  { label: '字典名称', prop: 'dictName', width: 180 },
  { label: '排序', prop: 'sortOrder', width: 80, align: 'center' },
  {
    label: '状态',
    prop: 'isActive',
    width: 100,
    slot: 'isActive',
    align: 'center',
  },
  {
    label: '创建时间',
    prop: 'createTime',
    width: 180,
    slot: 'createTime',
  },
]

export { searchOptions, toolbarOptions, tableOptions }

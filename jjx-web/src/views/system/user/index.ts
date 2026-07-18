import type { SearchOptions, ToolbarOptions, TableOptions } from '@/components/common-ui/type'

// 搜索表单配置
export const searchOptions: SearchOptions[] = [
  { prop: 'userName', label: '用户名称', type: 'input' },
  { prop: 'deptId', label: '部门名称', type: 'tree' },
  { prop: 'phone', label: '手机号码', type: 'input' },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { value: '0', label: '正常' },
      { value: '1', label: '停用' },
    ],
  },
]

// 工具栏配置
export const toolbarOptions: ToolbarOptions[] = [
  {
    key: 'add',
    label: '新增',
    type: 'primary',
    icon: 'Plus',
    permission: 'system:user:add',
  },
  {
    key: 'export',
    label: '导出',
    type: 'warning',
    icon: 'Download',
    permission: 'system:user:export',
  },
]

// 表格列配置
export const tableOptions: TableOptions[] = [
  { label: '用户名称', prop: 'userName', width: 120 },
  { label: '用户昵称', prop: 'nickName', width: 120 },
  { label: '部门', prop: 'deptId', width: 120, slot: 'deptId' },
  { label: '邮箱', prop: 'email' },
  { label: '手机号码', prop: 'phone', width: 120 },
  {
    label: '状态',
    prop: 'status',
    width: 100,
    slot: 'status',
    align: 'center',
  },
  {
    label: '创建时间',
    prop: 'createTime',
    width: 180,
    slot: 'createTime',
  },
]

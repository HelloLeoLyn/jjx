import type {
  SearchOptions,
  ToolbarOptions,
  FormOptions,
  TableOptions,
} from '@/components/common-ui/type'

// 搜索配置
export const searchOptions: SearchOptions[] = [
  {
    prop: 'roleName',
    label: '角色名称',
    type: 'input',
  },
  {
    prop: 'roleKey',
    label: '权限字符',
    type: 'input',
  },
  {
    prop: 'status',
    label: '状态',
    type: 'select',
    options: [
      { value: '0', label: '正常' },
      { value: '1', label: '禁用' },
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
    permission: 'system:role:add',
  },
  {
    key: 'export',
    label: '导出',
    type: 'warning',
    icon: 'Download',
    permission: 'system:role:export',
  },
]
// 表格列配置
export const tableOptions: TableOptions[] = [
  { prop: 'roleId', label: '角色编号', width: 100, align: 'center' },
  { prop: 'roleName', label: '角色名称', minWidth: 170 },
  { prop: 'roleKey', label: '权限字符', minWidth: 160 },
  {
    label: '状态',
    prop: 'status',
    width: 90,
    slot: 'status',
    align: 'center',
  },
  {
    label: '备注',
    prop: 'remark',
    minWidth: 150,
    align: 'center',
  },
  {
    label: '创建时间',
    prop: 'createTime',
    width: 170,
    slot: 'createTime',
    align: 'center',
  },
]

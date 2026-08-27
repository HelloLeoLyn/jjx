// views/system/dept/index.ts
import type {
  SearchOptions,
  ToolbarOptions,
  FormOptions,
  TableOptions,
} from '@/components/common-ui/type'

// 搜索配置
export const searchOptions: SearchOptions[] = [
  {
    prop: 'deptName',
    label: '部门名称',
    type: 'input',
  },
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
    permission: 'system:dept:add',
  },
  {
    key: 'export',
    label: '导出',
    type: 'warning',
    icon: 'Download',
    permission: 'system:dept:export',
  },
]

// 表单配置
export const formOptions: FormOptions[] = [
  {
    prop: 'parentId',
    label: '上级部门',
    type: 'select',
    options: [],
    span: 24,
  },
  {
    prop: 'deptName',
    label: '部门名称',
    type: 'input',
    required: true,
    maxlength: 30,
    span: 12,
  },
  {
    prop: 'orderNum',
    label: '显示排序',
    type: 'number',
    min: 0,
    span: 12,
  },
  {
    prop: 'leader',
    label: '负责人',
    type: 'input',
    maxlength: 20,
    span: 12,
  },
  {
    prop: 'phone',
    label: '联系电话',
    type: 'input',
    maxlength: 11,
    span: 12,
  },
  {
    prop: 'email',
    label: '邮箱',
    type: 'input',
    maxlength: 50,
    span: 12,
  },
  {
    prop: 'status',
    label: '状态',
    type: 'radio',
    options: [
      { value: '0', label: '正常' },
      { value: '1', label: '停用' },
    ],
    span: 12,
  },
]

// 表格列配置
export const tableOptions: TableOptions[] = [
  { label: '部门编号', prop: 'deptId', width: 80, align: 'center' },
  { label: '部门名称', prop: 'deptName', width: 200 },
  { label: '排序', prop: 'orderNum', width: 80, align: 'center' },
  { label: '负责人', prop: 'leader', width: 120 },
  { label: '联系电话', prop: 'phone', width: 120 },
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
    align: 'center',
  },
]

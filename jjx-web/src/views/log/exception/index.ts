import type {
  SearchOptions,
  ToolbarOptions,
  FormOptions,
  TableOptions,
} from '@/components/common-ui/type'

// 处理状态字典
const handleStatusMap: Record<number, string> = {
  0: '未处理',
  1: '已处理',
  2: '忽略',
}

const handleStatusOptions = Object.entries(handleStatusMap).map(([value, label]) => ({
  value: Number(value),
  label,
}))

// 搜索表单配置（对齐后端 OperLogController 异常日志查询参数）
const searchOptions: SearchOptions[] = [
  { prop: 'exceptionName', label: '异常名称', type: 'input' },
  { prop: 'requestUrl', label: '请求URL', type: 'input' },
  {
    prop: 'handleStatus',
    label: '处理状态',
    type: 'select',
    options: handleStatusOptions,
  },
]

// 工具栏配置
const toolbarOptions: ToolbarOptions[] = [
  {
    key: 'export',
    label: '导出',
    type: 'warning',
    icon: 'Download',
    permission: 'system:log:exception:export',
  },
]

// 表单配置 - 查看详情使用（对齐后端 SysErrorLog 字段）
const getFormOptions = () => {
  const formOptions: FormOptions[] = [
    {
      prop: 'exceptionName',
      label: '异常名称',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'username',
      label: '用户名',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'userId',
      label: '用户ID',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'clientIp',
      label: 'IP地址',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'requestUrl',
      label: '请求URL',
      type: 'input',
      readonly: true,
      span: 24,
    },
    {
      prop: 'requestMethod',
      label: '请求方法',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'triggerTime',
      label: '异常时间',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'exceptionMsg',
      label: '异常信息',
      type: 'textarea',
      readonly: true,
      rows: 3,
      span: 24,
    },
    {
      prop: 'requestParams',
      label: '请求参数',
      type: 'textarea',
      readonly: true,
      rows: 4,
      span: 24,
    },
    {
      prop: 'handleStatus',
      label: '处理状态',
      type: 'select',
      options: handleStatusOptions,
      readonly: true,
      span: 12,
    },
    {
      prop: 'handleBy',
      label: '处理人',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'handleTime',
      label: '处理时间',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'handleRemark',
      label: '处理备注',
      type: 'textarea',
      readonly: true,
      rows: 2,
      span: 24,
    },
  ]
  return formOptions
}

// 表格列配置（对齐后端 SysErrorLog 字段）
const tableOptions: TableOptions[] = [
  { label: '跟踪ID', prop: 'traceId', width: 200, align: 'center' },
  { label: '异常名称', prop: 'exceptionName', width: 180 },
  { label: '用户名', prop: 'username', width: 100 },
  { label: 'IP地址', prop: 'clientIp', width: 120 },
  { label: '请求URL', prop: 'requestUrl', width: 200 },
  { label: '请求方法', prop: 'requestMethod', width: 90 },
  {
    label: '处理状态',
    prop: 'handleStatus',
    width: 100,
    align: 'center',
    formatter: (row: any) => handleStatusMap[row.handleStatus] ?? row.handleStatus,
  },
  {
    label: '异常时间',
    prop: 'triggerTime',
    width: 170,
    formatter: (row: any) => {
      if (!row.triggerTime) return ''
      return new Date(row.triggerTime).toLocaleString('zh-CN')
    },
  },
]

export { searchOptions, toolbarOptions, getFormOptions, tableOptions }

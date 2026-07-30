import type {
  SearchOptions,
  ToolbarOptions,
  FormOptions,
  TableOptions,
} from '@/components/common-ui/type'

// 登录类型字典（常见值映射，后端按需扩展）
const loginTypeMap: Record<string, string> = {
  PASSWORD: '密码登录',
  SMS: '短信登录',
  WECHAT: '微信登录',
  TOKEN: 'Token验证',
}

// 搜索表单配置（对齐后端 OperLogController 登录日志查询参数）
const searchOptions: SearchOptions[] = [
  { prop: 'username', label: '用户账号', type: 'input' },
  {
    prop: 'loginType',
    label: '登录类型',
    type: 'select',
    options: Object.entries(loginTypeMap).map(([value, label]) => ({
      value,
      label,
    })),
  },
  {
    prop: 'status',
    label: '登录状态',
    type: 'select',
    options: [
      { value: 1, label: '成功' },
      { value: 0, label: '失败' },
    ],
  },
]

// 工具栏配置
const toolbarOptions: ToolbarOptions[] = [
  {
    key: 'export',
    label: '导出',
    type: 'warning',
    icon: 'Download',
    permission: 'system:log:login:export',
  },
]

// 表单配置 - 查看详情使用（对齐后端 SysLoginLog 字段）
const getFormOptions = () => {
  const formOptions: FormOptions[] = [
    {
      prop: 'username',
      label: '用户账号',
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
      prop: 'loginType',
      label: '登录类型',
      type: 'select',
      options: Object.entries(loginTypeMap).map(([value, label]) => ({
        value,
        label,
      })),
      readonly: true,
      span: 12,
    },
    {
      prop: 'loginIp',
      label: '登录IP',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'loginLocation',
      label: '登录地点',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'userAgent',
      label: '用户代理',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'status',
      label: '登录状态',
      type: 'select',
      options: [
        { value: 0, label: '成功' },
        { value: 1, label: '失败' },
      ],
      readonly: true,
      span: 12,
    },
    {
      prop: 'failReason',
      label: '提示消息',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'loginTime',
      label: '登录时间',
      type: 'input',
      readonly: true,
      span: 12,
    },
  ]
  return formOptions
}

// 表格列配置（对齐后端 SysLoginLog 字段）
const tableOptions: TableOptions[] = [
  { label: '用户账号', prop: 'username', width: 120 },
  { label: '用户ID', prop: 'userId', width: 80, align: 'center' },
  {
    label: '登录类型',
    prop: 'loginType',
    width: 120,
    align: 'center',
    formatter: (row: any) => loginTypeMap[row.loginType] ?? row.loginType,
  },
  { label: '登录IP', prop: 'loginIp', width: 120 },
  { label: '登录地点', prop: 'loginLocation', width: 150 },
  { label: '用户代理', prop: 'userAgent', width: 220 },
  {
    label: '登录状态',
    prop: 'status',
    width: 100,
    align: 'center',
    formatter: (row: any) => {
      if (row.status === 1) return '成功'
      if (row.status === 0) return '失败'
      return row.status
    },
  },
  { label: '提示消息', prop: 'failReason', width: 150 },
  {
    label: '登录时间',
    prop: 'loginTime',
    width: 170,
    formatter: (row: any) => {
      if (!row.loginTime) return ''
      return new Date(row.loginTime).toLocaleString('zh-CN')
    },
  },
]

export { searchOptions, toolbarOptions, getFormOptions, tableOptions }

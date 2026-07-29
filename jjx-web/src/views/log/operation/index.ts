import type {
  SearchOptions,
  ToolbarOptions,
  FormOptions,
  TableOptions,
} from '@/components/common-ui/type'

// 搜索表单配置
const searchOptions: SearchOptions[] = [
  { prop: 'module', label: '模块', type: 'input' },
  {
    prop: 'businessType',
    label: '业务类型',
    type: 'select',
    options: [
      { value: '0', label: '其它' },
      { value: '1', label: '新增' },
      { value: '2', label: '修改' },
      { value: '3', label: '删除' },
      { value: '4', label: '授权' },
      { value: '5', label: '导出' },
      { value: '6', label: '导入' },
      { value: '7', label: '强退' },
      { value: '8', label: '生成代码' },
      { value: '9', label: '清空数据' },
    ],
  },
  { prop: 'operatorName', label: '操作人员', type: 'input' },
  {
    prop: 'status',
    label: '操作状态',
    type: 'select',
    options: [
      { value: '0', label: '成功' },
      { value: '1', label: '失败' },
    ],
  },
  { prop: 'time', label: '开始时间', type: 'daterange' },
]

// 工具栏配置
const toolbarOptions: ToolbarOptions[] = [
  {
    key: 'export',
    label: '导出',
    type: 'warning',
    icon: 'Download',
    permission: 'system:log:operation:export',
  },
]

// 表单配置 - 查看详情使用
const getFormOptions = () => {
  const formOptions: FormOptions[] = [
    {
      prop: 'title',
      label: '日志标题',
      type: 'input',
      readonly: true,
      span: 24,
    },
    {
      prop: 'businessType',
      label: '业务类型',
      type: 'select',
      options: [
        { value: '0', label: '其它' },
        { value: '1', label: '新增' },
        { value: '2', label: '修改' },
        { value: '3', label: '删除' },
        { value: '4', label: '授权' },
        { value: '5', label: '导出' },
        { value: '6', label: '导入' },
        { value: '7', label: '强退' },
        { value: '8', label: '生成代码' },
        { value: '9', label: '清空数据' },
      ],
      readonly: true,
      span: 12,
    },
    {
      prop: 'operatorName',
      label: '操作人员',
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
      label: '请求方式',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'requestIp',
      label: '请求IP',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'requestLocation',
      label: '操作地点',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'status',
      label: '操作状态',
      type: 'select',
      options: [
        { value: '0', label: '成功' },
        { value: '1', label: '失败' },
      ],
      readonly: true,
      span: 12,
    },
    {
      prop: 'operationTime',
      label: '操作时间',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'costTime',
      label: '耗时(ms)',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'requestParam',
      label: '请求参数',
      type: 'textarea',
      readonly: true,
      rows: 4,
      span: 24,
    },
    {
      prop: 'jsonResult',
      label: '返回结果',
      type: 'textarea',
      readonly: true,
      rows: 4,
      span: 24,
    },
    {
      prop: 'errorMsg',
      label: '错误信息',
      type: 'textarea',
      readonly: true,
      rows: 3,
      span: 24,
    },
  ]
  return formOptions
}

// 表格列配置
const tableOptions: TableOptions[] = [
  { label: '日志ID', prop: 'logId', width: 80, align: 'center' },
  { label: '日志标题', prop: 'title', width: 180 },
  {
    label: '业务类型',
    prop: 'businessType',
    width: 100,
    formatter: (row: any) => {
      const map: Record<string, string> = {
        '0': '其它',
        '1': '新增',
        '2': '修改',
        '3': '删除',
        '4': '授权',
        '5': '导出',
        '6': '导入',
        '7': '强退',
        '8': '生成代码',
        '9': '清空数据',
      }
      return map[row.businessType] || row.businessType
    },
  },
  { label: '操作人员', prop: 'operatorName', width: 120 },
  { label: '请求IP', prop: 'requestIp', width: 120 },
  {
    label: '操作状态',
    prop: 'status',
    width: 100,
    align: 'center',
    formatter: (row: any) => {
      return row.status === 0 ? '成功' : '失败'
    },
  },
  {
    label: '操作时间',
    prop: 'operationTime',
    width: 180,
    formatter: (row: any) => {
      if (!row.operationTime) return ''
      return new Date(row.operationTime).toLocaleString('zh-CN')
    },
  },
  { label: '耗时(ms)', prop: 'costTime', width: 100, align: 'center' },
]

export { searchOptions, toolbarOptions, getFormOptions, tableOptions }

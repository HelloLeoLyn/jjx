import type {
  SearchOptions,
  ToolbarOptions,
  FormOptions,
  TableOptions,
} from '@/components/common-ui/type'
import { BusinessTypeEnum } from '@/enums/system/LogEnum'

const businessTypeMap: Record<number, string> = Object.fromEntries(
  BusinessTypeEnum.items.map((item) => [item.value, item.label])
) as Record<number, string>

const businessTypeOptions = BusinessTypeEnum.items.map((item) => ({
  value: item.value,
  label: item.label,
}))

// 搜索表单配置（对齐后端 OperLogController 查询参数）
const searchOptions: SearchOptions[] = [
  { prop: 'module', label: '模块', type: 'input' },
  {
    prop: 'bizType',
    label: '业务类型标识',
    type: 'input',
  },
  {
    prop: 'status',
    label: '操作状态',
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
    permission: 'system:log:operation:export',
  },
]

// 表单配置 - 查看详情使用
const getFormOptions = () => {
  const formOptions: FormOptions[] = [
    {
      prop: 'module',
      label: '操作内容',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'businessType',
      label: '操作类型',
      type: 'select',
      options: businessTypeOptions,
      readonly: true,
      span: 12,
    },
    {
      prop: 'username',
      label: '操作人员',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'realName',
      label: '真实姓名',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'operUrl',
      label: '操作接口',
      type: 'input',
      readonly: true,
      span: 24,
    },
    {
      prop: 'operIp',
      label: '请求IP',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'status',
      label: '操作状态',
      type: 'select',
      options: [
        { value: 0, label: '失败' },
        { value: 1, label: '成功' },
      ],
      readonly: true,
      span: 12,
    },
    {
      prop: 'bizStatus',
      label: '业务状态',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'createTime',
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
      prop: 'bizId',
      label: '关联业务ID',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'traceId',
      label: '跟踪ID',
      type: 'input',
      readonly: true,
      span: 12,
    },
    {
      prop: 'operParam',
      label: '请求参数',
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
    {
      prop: 'userAgent',
      label: '用户代理',
      type: 'input',
      readonly: true,
      span: 24,
    },
  ]
  return formOptions
}

// 表格列配置（对齐后端 SysOperLog 字段）
const tableOptions: TableOptions[] = [
  { label: 'ID', prop: 'id', width: 60, align: 'center' },
  {
    label: '操作内容',
    prop: 'module',
    width: 200,
    formatter: (row: any) => {
      const bt = businessTypeMap[row.businessType] ?? ''
      const mod = (row.module || '').replace(/管理$/, '')
      return mod + ' - ' + bt
    },
  },
  { label: '操作人员', prop: 'username', width: 100 },
  { label: '请求IP', prop: 'operIp', width: 120 },
  {
    label: '操作状态',
    prop: 'status',
    width: 100,
    align: 'center',
    formatter: (row: any) => {
      if (row.status === 1) return '成功'
      if (row.status === 0) return '失败'
      return row.status
    },
  },
  {
    label: '操作时间',
    prop: 'createTime',
    width: 170,
    formatter: (row: any) => {
      if (!row.createTime) return ''
      return new Date(row.createTime).toLocaleString('zh-CN')
    },
  },
  {
    label: '业务状态',
    prop: 'bizStatus',
    width: 100,
    formatter: (row: any) => {
      const mod = row.module || ''
      // 按模块匹配对应状态字典
      if (mod.includes('询价')) {
        const inquiryMap: Record<number, string> = { 0:'草稿', 1:'待处理', 2:'已发送', 3:'已转报价', 4:'已确认', 5:'已拒绝', 6:'已过期' }
        return inquiryMap[row.bizStatus] ?? ''
      }
      if (mod.includes('报价')) {
        const quoteMap: Record<number, string> = { 0:'草稿', 1:'待处理', 2:'已发送', 3:'已报价', 4:'已确认', 5:'已拒绝', 6:'已过期' }
        return quoteMap[row.bizStatus] ?? ''
      }
      if (mod.includes('订单') || mod.includes('销售')) {
        const orderMap: Record<number, string> = { 0:'待确认', 1:'已确认', 2:'生产中', 3:'已完成', 4:'已取消' }
        return orderMap[row.bizStatus] ?? ''
      }
      return String(row.bizStatus ?? '')
    },
  },
  { label: '追踪ID', prop: 'traceId', width: 180 },
  { label: '耗时(ms)', prop: 'costTime', width: 90, align: 'center' },
]

export { searchOptions, toolbarOptions, getFormOptions, tableOptions }

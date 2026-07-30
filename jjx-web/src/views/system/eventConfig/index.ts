import type {
  SearchOptions,
  ToolbarOptions,
  TableOptions,
} from '@/components/common-ui/type'

// 事件类型选项
const eventTypeOptions = [
  { value: 'notification', label: '通知' },
  { value: 'task', label: '任务' },
  { value: 'both', label: '通知+任务' },
]

// 启用状态选项
const enabledOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' },
]

// 搜索配置
export const searchOptions: SearchOptions[] = [
  {
    prop: 'eventCode',
    label: '事件编码',
    type: 'input',
  },
  {
    prop: 'eventName',
    label: '事件名称',
    type: 'input',
  },
  {
    prop: 'eventType',
    label: '类型',
    type: 'select',
    options: eventTypeOptions,
  },
  {
    prop: 'isEnabled',
    label: '状态',
    type: 'select',
    options: enabledOptions,
  },
]

// 工具栏配置
export const toolbarOptions: ToolbarOptions[] = [
  {
    key: 'add',
    label: '新增',
    type: 'primary',
    icon: 'Plus',
    permission: 'system:eventConfig:add',
  },
]

// 表格列配置
export const tableOptions: TableOptions[] = [
  { prop: 'eventCode', label: '事件编码', width: 180 },
  { prop: 'eventName', label: '事件名称', width: 150 },
  {
    prop: 'eventType',
    label: '类型',
    width: 100,
    align: 'center',
  },
  {
    prop: 'targetRole',
    label: '目标角色',
    width: 120,
    align: 'center',
  },
  { prop: 'title', label: '标题', minWidth: 200 },
  {
    prop: 'excludeTrigger',
    label: '排除触发者',
    width: 100,
    align: 'center',
  },
  {
    prop: 'isEnabled',
    label: '状态',
    width: 80,
    align: 'center',
  },
  {
    label: '创建时间',
    prop: 'createTime',
    width: 180,
    align: 'center',
  },
]

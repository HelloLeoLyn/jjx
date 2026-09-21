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

// 看板模块选项（2026-09-21 统一取值，与看板页签一致：业务/生产/开发任务）
const kanbanModuleOptions = [
  { value: 'biz', label: '业务' },
  { value: 'prod', label: '生产' },
  { value: 'dev', label: '开发任务' },
]

// 历史值 → 展示用别名（仅列表渲染旧数据，下拉不再提供；取值统一见迁移 144）
const kanbanModuleLegacyLabels: Record<string, string> = {
  office: '业务（旧值 office）',
  emergency: '业务（旧值 emergency）',
  production: '生产（旧值 production）',
}

// 业务模块选项（与编辑弹窗保持一致）
const bizModuleOptions = [
  { value: 'sales', label: '销售' },
  { value: 'purchase', label: '采购' },
  { value: 'production', label: '生产' },
  { value: 'product', label: '产品工程' },
  { value: 'inventory', label: '库存' },
  { value: 'quality', label: '品质' },
  { value: 'biz', label: '业务需求' },
]

// 任务优先级选项
const priorityOptions = [
  { value: 'urgent', label: '紧急' },
  { value: 'high', label: '高' },
  { value: 'normal', label: '普通' },
  { value: 'low', label: '低' },
]

// 启用状态选项
const enabledOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' },
]

// 搜索配置
export const searchOptions: SearchOptions[] = [
  {
    prop: 'bizModule',
    label: '业务模块',
    type: 'select',
    options: bizModuleOptions,
  },
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
    prop: 'bizModule',
    label: '业务模块',
    width: 100,
    align: 'center',
    formatter: (row: any) => bizModuleOptions.find(o => o.value === row.bizModule)?.label ?? row.bizModule ?? '-',
  },
  {
    prop: 'eventType',
    label: '类型',
    width: 100,
    align: 'center',
    slot: 'eventType',
  },
  {
    prop: 'kanbanModule',
    label: '看板模块',
    width: 100,
    align: 'center',
    formatter: (row: any) => kanbanModuleOptions.find(o => o.value === row.kanbanModule)?.label ?? kanbanModuleLegacyLabels[row.kanbanModule] ?? row.kanbanModule ?? '-',
  },
  {
    prop: 'priority',
    label: '优先级',
    width: 80,
    align: 'center',
    formatter: (row: any) => priorityOptions.find(o => o.value === row.priority)?.label ?? row.priority ?? '-',
  },
  {
    prop: 'targetRole',
    label: '目标角色',
    width: 120,
    align: 'center',
    slot: 'targetRole',
  },
  { prop: 'title', label: '标题', minWidth: 200 },
  {
    prop: 'excludeTrigger',
    label: '排除触发者',
    width: 100,
    align: 'center',
    slot: 'excludeTrigger',
  },
  {
    prop: 'isEnabled',
    label: '状态',
    width: 80,
    align: 'center',
    slot: 'isEnabled',
  },
  {
    label: '创建时间',
    prop: 'createTime',
    width: 180,
    align: 'center',
    slot: 'createTime',
  },
]

import type { OrderStatus, OrderType, PlanType, Priority } from '@/types/production/order'

/**
 * 订单状态选项
 */
export const ORDER_STATUS_OPTIONS = [
  { label: '草稿', value: 'draft' as OrderStatus },
  { label: '待审批', value: 'pending_approval' as OrderStatus },
  { label: '已批准', value: 'approved' as OrderStatus },
  { label: '已排程', value: 'scheduled' as OrderStatus },
  { label: '进行中', value: 'in_progress' as OrderStatus },
  { label: '已完成', value: 'completed' as OrderStatus },
  { label: '已取消', value: 'cancelled' as OrderStatus },
]

/**
 * 订单类型选项
 */
export const ORDER_TYPE_OPTIONS = [
  { label: '生产计划', value: 'plan' as OrderType },
  { label: '生产工单', value: 'work_order' as OrderType },
]

/**
 * 计划类型选项
 */
export const PLAN_TYPE_OPTIONS = [
  { label: '月计划', value: 'monthly' as PlanType },
  { label: '周计划', value: 'weekly' as PlanType },
  { label: '日计划', value: 'daily' as PlanType },
  { label: '专项计划', value: 'special' as PlanType },
]

/**
 * 优先级选项
 */
export const PRIORITY_OPTIONS = [
  { label: '低', value: 'low' as Priority },
  { label: '中', value: 'medium' as Priority },
  { label: '高', value: 'high' as Priority },
  { label: '紧急', value: 'urgent' as Priority },
]

/**
 * 审批状态选项
 */
export const APPROVAL_STATUS_OPTIONS = [
  { label: '待审批', value: 'pending' },
  { label: '已批准', value: 'approved' },
  { label: '已拒绝', value: 'rejected' },
  { label: '已取消', value: 'cancelled' },
]

/**
 * 执行状态选项
 */
export const EXECUTION_STATUS_OPTIONS = [
  { label: '未开始', value: 'not_started' },
  { label: '进行中', value: 'in_progress' },
  { label: '已完成', value: 'completed' },
  { label: '已暂停', value: 'paused' },
  { label: '已取消', value: 'cancelled' },
]

/**
 * 分页配置
 */
export const PAGINATION_CONFIG = {
  pageSizes: [10, 20, 50, 100],
  layout: 'total, sizes, prev, pager, next, jumper',
  defaultPageSize: 20,
  defaultPageNum: 1,
}

/**
 * 表格列配置
 */
export const TABLE_COLUMNS_CONFIG = [
  { prop: 'orderNo', label: '订单编号', width: 150, sortable: true },
  { prop: 'productName', label: '产品信息', minWidth: 200 },
  { prop: 'quantity', label: '数量', width: 120 },
  { prop: 'progress', label: '进度', width: 120 },
  { prop: 'time', label: '时间', width: 180 },
  { prop: 'status', label: '状态', width: 120 },
  { prop: 'priority', label: '优先级', width: 80 },
  { prop: 'actions', label: '操作', width: 200, fixed: 'right' },
]

/**
 * 搜索表单默认值
 */
export const SEARCH_FORM_DEFAULTS = {
  pageNum: 1,
  pageSize: 20,
  orderType: 'all' as 'all' | OrderType,
  orderStatus: '',
  approvalStatus: '',
  executionStatus: '',
  planType: undefined as PlanType | undefined,
  orderNo: '',
  productName: '',
  productCode: '',
  salesOrderNo: '',
  planDateStart: '',
  planDateEnd: '',
  createTimeStart: '',
  createTimeEnd: '',
  sortField: '',
  sortOrder: undefined as 'asc' | 'desc' | undefined,
}

/**
 * 视图类型
 */
export const VIEW_TYPES = {
  PLAN: 'plan' as const,
  WORK_ORDER: 'work_order' as const,
  ALL: 'all' as const,
  GANTT: 'gantt' as const,
}

/**
 * 视图标题映射
 */
export const VIEW_TITLE_MAP = {
  [VIEW_TYPES.PLAN]: '生产计划列表',
  [VIEW_TYPES.WORK_ORDER]: '生产工单列表',
  [VIEW_TYPES.ALL]: '全部生产订单',
  [VIEW_TYPES.GANTT]: '生产计划甘特图',
}

/**
 * 根据视图类型过滤状态选项
 */
export function getFilteredStatusOptions(viewType: string) {
  switch (viewType) {
    case VIEW_TYPES.PLAN:
      return ORDER_STATUS_OPTIONS.filter((status) =>
        ['draft', 'pending_approval', 'approved', 'cancelled'].includes(status.value)
      )
    case VIEW_TYPES.WORK_ORDER:
      return ORDER_STATUS_OPTIONS.filter((status) =>
        ['scheduled', 'in_progress', 'completed', 'cancelled'].includes(status.value)
      )
    case VIEW_TYPES.ALL:
      return ORDER_STATUS_OPTIONS
    default:
      return []
  }
}

/**
 * 订单编号前缀
 */
export const ORDER_NO_PREFIX = {
  PLAN: 'PLAN',
  WORK_ORDER: 'WO',
}

/**
 * 默认表单值
 */
export const DEFAULT_FORM_VALUES = {
  orderType: 'plan' as OrderType,
  priority: 'medium' as Priority,
  planType: 'monthly' as PlanType,
  plannedQuantity: 100,
  productUnit: 'PCS',
}

/**
 * 验证规则
 */
export const VALIDATION_RULES = {
  required: { required: true, message: '此项为必填项', trigger: 'blur' },
  number: { type: 'number', message: '必须为数字', trigger: 'blur' },
  minQuantity: { type: 'number', min: 1, message: '数量必须大于0', trigger: 'blur' },
  date: { type: 'date', message: '必须为有效日期', trigger: 'change' },
}

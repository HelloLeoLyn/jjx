import type { StatusOption } from '@/types/purchase/order'

/**
 * 采购订单常量定义
 */

// 合并后的审批状态选项（1草稿/2已取消/3待审批/4已批准/5已拒绝）
export const APPROVAL_STATUS_OPTIONS: StatusOption[] = [
  { label: '草稿', value: '1' },
  { label: '已取消', value: '2' },
  { label: '待审批', value: '3' },
  { label: '已批准', value: '4' },
  { label: '已拒绝', value: '5' },
]

// 收货状态选项
export const RECEIPT_STATUS_OPTIONS: StatusOption[] = [
  { label: '待收货', value: '0' },
  { label: '部分收货', value: '1' },
  { label: '已收货', value: '2' },
]

// 付款状态选项
export const PAYMENT_STATUS_OPTIONS: StatusOption[] = [
  { label: '待付款', value: '0' },
  { label: '部分付款', value: '1' },
  { label: '已付款', value: '2' },
]

// 订单类型选项
export const ORDER_TYPE_OPTIONS: StatusOption[] = [
  { label: '普通订单', value: '0' },
  { label: '紧急订单', value: '1' },
]

// 币种选项
export const CURRENCY_OPTIONS: StatusOption[] = [
  { label: '人民币', value: 'CNY' },
  { label: '美元', value: 'USD' },
  { label: '欧元', value: 'EUR' },
]

// 状态标签类型映射
export const STATUS_TAG_TYPES = {
  approval: {
    '1': 'info', // 草稿
    '2': 'danger', // 已取消
    '3': 'warning', // 待审批
    '4': 'success', // 已批准
    '5': 'danger', // 已拒绝
  },
  receipt: {
    '0': 'warning',
    '1': 'primary',
    '2': 'success',
  },
  payment: {
    '0': 'warning',
    '1': 'primary',
    '2': 'success',
  },
}

// 状态图标映射
export const STATUS_ICONS = {
  '1': 'Document', // 草稿
  '2': 'Close', // 已取消
  '3': 'Clock', // 待审批
  '4': 'CircleCheck', // 已批准
  '5': 'Close', // 已拒绝
}

// 状态颜色映射
export const STATUS_COLORS = {
  '1': '#909399', // 草稿
  '2': '#f56c6c', // 已取消
  '3': '#e6a23c', // 待审批
  '4': '#67c23a', // 已批准
  '5': '#f56c6c', // 已拒绝
}

// 默认分页参数
export const DEFAULT_PAGE_SIZE = 20
export const DEFAULT_PAGE_NUM = 1

// 表格列定义
export const TABLE_COLUMNS = [
  { prop: 'orderNo', label: '订单号', width: 160 },
  { prop: 'supplierName', label: '供应商名称', width: 180 },
  { prop: 'orderDate', label: '订单日期', width: 120 },
  { prop: 'expectedDeliveryDate', label: '交货日期', width: 120 },
  { prop: 'approvalStatus', label: '审批状态', width: 120 },
  { prop: 'receiptStatus', label: '收货状态', width: 120 },
  { prop: 'paymentStatus', label: '付款状态', width: 120 },
  { prop: 'currency', label: '币种', width: 80 },
  { prop: 'orderTotalAmount', label: '总金额', width: 120 },
  { prop: 'urgentFlag', label: '紧急', width: 80 },
  { prop: 'createTime', label: '创建时间', width: 180 },
]

// 搜索字段定义
export const SEARCH_FIELDS = [
  { prop: 'orderNo', label: '订单号', placeholder: '请输入订单号', type: 'input' },
  { prop: 'supplierName', label: '供应商名称', placeholder: '请输入供应商名称', type: 'input' },
  {
    prop: 'approvalStatus',
    label: '审批状态',
    placeholder: '请选择审批状态',
    type: 'select',
    options: APPROVAL_STATUS_OPTIONS,
  },
  {
    prop: 'receiptStatus',
    label: '收货状态',
    placeholder: '请选择收货状态',
    type: 'select',
    options: RECEIPT_STATUS_OPTIONS,
  },
  {
    prop: 'paymentStatus',
    label: '付款状态',
    placeholder: '请选择付款状态',
    type: 'select',
    options: PAYMENT_STATUS_OPTIONS,
  },
  { prop: 'urgentFlag', label: '紧急订单', placeholder: '请选择紧急状态', type: 'switch' },
  {
    prop: 'orderType',
    label: '订单类型',
    placeholder: '请选择订单类型',
    type: 'select',
    options: ORDER_TYPE_OPTIONS,
  },
]

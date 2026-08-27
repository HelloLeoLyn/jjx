import request from '@/utils/request'
import type {
  ProductionOrderVO,
  ProductionOrderQuery,
  ProductionOrderCreateDTO,
  ProductionOrderUpdateDTO,
  OrderStatusUpdateDTO,
  ConvertPlanToWorkOrdersDTO,
  ProductionOrderStats,
  ProductionOrderExportData,
  OrderType,
  OrderStatus,
  ApprovalStatus,
  PlanType,
  Priority,
} from '@/types/production/order'

// 获取生产订单列表
export function getProductionOrderList(params: ProductionOrderQuery) {
  return request({
    url: '/production/order/list',
    method: 'get',
    params,
  })
}

// 获取生产订单详情
export function getProductionOrderDetail(orderId: string, orderType?: OrderType) {
  return request({
    url: `/production/order/${orderId}`,
    method: 'get',
    params: { orderType },
  })
}

// 创建生产订单
export function createProductionOrder(data: ProductionOrderCreateDTO) {
  return request({
    url: '/production/order',
    method: 'post',
    data,
  })
}

// 更新生产订单
export function updateProductionOrder(data: ProductionOrderUpdateDTO) {
  return request({
    url: '/production/order',
    method: 'put',
    data,
  })
}

// 删除生产订单
export function deleteProductionOrder(orderId: string, orderType?: OrderType) {
  return request({
    url: `/production/order/${orderId}`,
    method: 'delete',
    params: { orderType },
  })
}

// 更新订单状态（后端为 @RequestParam query 参数，2026-08-11 修复：原放 body 导致 orderId 缺失）
export function updateOrderStatus(data: OrderStatusUpdateDTO) {
  return request({
    url: '/production/order/status',
    method: 'put',
    params: data,
  })
}

// 批量更新订单状态
export function batchUpdateOrderStatus(data: {
  orderIds: string[]
  orderStatus: OrderStatus
  remark?: string
}) {
  return request({
    url: '/production/order/batch-status',
    method: 'put',
    data,
  })
}

// 计划转工单
export function convertPlanToWorkOrders(data: ConvertPlanToWorkOrdersDTO) {
  return request({
    url: '/production/order/convert-plan-to-work-orders',
    method: 'post',
    data,
  })
}

// 获取生产订单统计信息
export function getProductionOrderStats(params?: {
  startDate?: string
  endDate?: string
  orderType?: OrderType
}) {
  return request({
    url: '/production/order/statistics',
    method: 'get',
    params,
  })
}

// 导出生产订单数据
export function exportProductionOrder(params: ProductionOrderQuery) {
  return request({
    url: '/production/order/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

// 导出生产工单PDF（单张表单）
export function exportProductionOrderPdf(orderId: number) {
  return request({
    url: `/production/order/export-pdf/${orderId}`,
    method: 'get',
    responseType: 'blob',
  })
}

// 导入生产订单数据
export function importProductionOrder(file: File) {
  const formData = new FormData()
  formData.append('file', file)

  return request({
    url: '/production/order/import',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

// 获取审批记录
export function getApprovalRecords(orderId: string) {
  return request({
    url: `/production/order/${orderId}/approval-records`,
    method: 'get',
  })
}

// 提交审批
export function submitApproval(
  orderId: string,
  data: {
    approvalStatus: ApprovalStatus
    approvalRemark?: string
  }
) {
  return request({
    url: `/production/order/${orderId}/submit-approval`,
    method: 'post',
    data,
  })
}

// 获取执行记录
export function getExecutionRecords(orderId: string) {
  return request({
    url: `/production/order/${orderId}/execution-records`,
    method: 'get',
  })
}

// 开始执行
export function startExecution(
  orderId: string,
  data?: {
    actualStartTime?: string
    operatorId?: string
    equipmentId?: string
    remark?: string
  }
) {
  return request({
    url: `/production/order/${orderId}/start`,
    method: 'put',
    data,
  })
}

// 完成执行
export function completeExecution(
  orderId: string,
  data: {
    actualEndTime?: string
    completedQuantity: number
    qualityResult?: string
    remark?: string
  }
) {
  return request({
    url: `/production/order/${orderId}/complete`,
    method: 'put',
    data,
  })
}

// 暂停执行
export function pauseExecution(orderId: string, remark?: string) {
  return request({
    url: `/production/order/${orderId}/pause`,
    method: 'put',
    data: { remark },
  })
}

// 恢复执行
export function resumeExecution(orderId: string, remark?: string) {
  return request({
    url: `/production/order/${orderId}/resume`,
    method: 'post',
    data: { remark },
  })
}

// 获取关联工单（针对计划）
export function getRelatedWorkOrders(planId: string) {
  return request({
    url: `/production/order/${planId}/related-work-orders`,
    method: 'get',
  })
}

// 获取关联计划（针对工单）
export function getRelatedPlan(workOrderId: string) {
  return request({
    url: `/production/order/${workOrderId}/related-plan`,
    method: 'get',
  })
}

// 获取产品选项
export function getProductOptions(params?: {
  keyword?: string
  categoryId?: string
  enabled?: boolean
}) {
  return request({
    url: '/production/order/product-options',
    method: 'get',
    params,
  })
}

// 获取操作员选项
export function getOperatorOptions(params?: {
  departmentId?: string
  skillLevel?: string
  available?: boolean
}) {
  return request({
    url: '/production/order/operator-options',
    method: 'get',
    params,
  })
}

// 获取设备选项
export function getEquipmentOptions(params?: {
  equipmentType?: string
  equipmentStatus?: string
  available?: boolean
}) {
  return request({
    url: '/production/order/equipment-options',
    method: 'get',
    params,
  })
}

// 获取工序选项
export function getStepOptions(params?: {
  stepType?: string
  stepCategory?: string
  enabled?: boolean
}) {
  return request({
    url: '/production/order/step-options',
    method: 'get',
    params,
  })
}

// 获取审批人选项
export function getApproverOptions(params?: { departmentId?: string; roleCode?: string }) {
  return request({
    url: '/production/order/approver-options',
    method: 'get',
    params,
  })
}

// 验证订单编号唯一性
export function checkOrderNoUnique(orderNo: string, excludeOrderId?: string) {
  return request({
    url: '/production/order/check-order-no-unique',
    method: 'get',
    params: { orderNo, excludeOrderId },
  })
}

// 获取甘特图数据
export function getGanttData(params?: {
  startDate?: string
  endDate?: string
  productId?: string
  orderType?: OrderType
}) {
  return request({
    url: '/production/order/schedule/gantt',
    method: 'get',
    params,
  })
}

// 更新甘特图数据（拖拽调整）
export function updateGanttData(data: {
  orderId: string
  orderType: OrderType
  planStartDate: string
  planEndDate: string
  remark?: string
}) {
  return request({
    url: '/production/order/schedule/gantt',
    method: 'put',
    data,
  })
}

// 获取资源负载数据
export function getResourceLoadData(params?: {
  startDate?: string
  endDate?: string
  resourceType?: 'operator' | 'equipment'
}) {
  return request({
    url: '/production/order/resource-load-data',
    method: 'get',
    params,
  })
}

// 获取交期预警数据
export function getDeliveryWarningData(params?: { daysThreshold?: number; orderType?: OrderType }) {
  return request({
    url: '/production/order/delivery-warning',
    method: 'get',
    params,
  })
}

// 获取生产进度数据
export function getProductionProgressData(params?: {
  planId?: string
  productId?: string
  startDate?: string
  endDate?: string
}) {
  return request({
    url: '/production/order/production-progress',
    method: 'get',
    params,
  })
}

// 获取成本分析数据
export function getCostAnalysisData(params?: {
  startDate?: string
  endDate?: string
  productId?: string
  orderType?: OrderType
}) {
  return request({
    url: '/production/order/cost-analysis',
    method: 'get',
    params,
  })
}

// 获取质量分析数据
export function getQualityAnalysisData(params?: {
  startDate?: string
  endDate?: string
  productId?: string
  operatorId?: string
}) {
  return request({
    url: '/production/order/quality-analysis',
    method: 'get',
    params,
  })
}

// 获取生产效率数据
export function getEfficiencyAnalysisData(params?: {
  startDate?: string
  endDate?: string
  operatorId?: string
  equipmentId?: string
}) {
  return request({
    url: '/production/order/efficiency-analysis',
    method: 'get',
    params,
  })
}

// 复制生产订单
export function copyProductionOrder(
  orderId: string,
  data?: {
    newOrderNo?: string
    copyRelations?: boolean
  }
) {
  return request({
    url: `/production/order/${orderId}/copy`,
    method: 'post',
    data,
  })
}

// 批量删除生产订单
export function batchDeleteProductionOrders(orderIds: string[]) {
  return request({
    url: '/production/order/batch-delete',
    method: 'delete',
    data: { orderIds },
  })
}

// 批量导出生产订单
export function batchExportProductionOrders(orderIds: string[]) {
  return request({
    url: '/production/order/batch-export',
    method: 'post',
    data: { orderIds },
    responseType: 'blob',
  })
}

// 获取操作历史
export function getOperationHistory(
  orderId: string,
  params?: {
    operationType?: string
    startTime?: string
    endTime?: string
  }
) {
  return request({
    url: `/production/order/${orderId}/operation-history`,
    method: 'get',
    params,
  })
}

// 获取版本历史
export function getVersionHistory(orderId: string) {
  return request({
    url: `/production/order/${orderId}/version-history`,
    method: 'get',
  })
}

// 恢复到指定版本
export function restoreToVersion(orderId: string, versionId: string) {
  return request({
    url: `/production/order/${orderId}/restore-to-version`,
    method: 'post',
    data: { versionId },
  })
}

// 获取打印模板
export function getPrintTemplate(orderId: string, templateType: string) {
  return request({
    url: `/production/order/${orderId}/print-template`,
    method: 'get',
    params: { templateType },
    responseType: 'blob',
  })
}

// 发送通知
export function sendNotification(
  orderId: string,
  data: {
    notificationType: string
    recipientIds: string[]
    message?: string
  }
) {
  return request({
    url: `/production/order/${orderId}/send-notification`,
    method: 'post',
    data,
  })
}

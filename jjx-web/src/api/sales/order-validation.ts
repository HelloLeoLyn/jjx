// src/api/sales/order-validation.ts

import request from '@/utils/request'
import type { R } from '@/types'
import type {
  OrderReviewValidationRequestDTO,
  OrderReviewValidationResponseVO,
  FixValidationIssueRequestDTO,
  FixValidationIssueResponseVO,
  BatchValidationRequestDTO,
  BatchValidationResponseVO,
  ValidationHistoryRecord,
  ValidationConfig,
} from '@/types/sales/order-validation'

/**
 * 订单验证API
 * 提供销售订单提交审核前的验证功能
 */
export const orderValidationApi = {
  // ==================== 订单验证 ====================

  /**
   * 验证订单是否可以提交审核
   */
  validateOrderForReview(data: OrderReviewValidationRequestDTO) {
    return request.post<R<OrderReviewValidationResponseVO>>(
      '/sales/orders/validate-for-review',
      data
    )
  },

  /**
   * 快速验证订单（简化版）
   */
  quickValidateOrder(orderId: number) {
    return request.get<R<OrderReviewValidationResponseVO>>(
      `/sales/orders/${orderId}/quick-validate`
    )
  },

  /**
   * 批量验证订单
   */
  batchValidateOrders(data: BatchValidationRequestDTO) {
    return request.post<R<BatchValidationResponseVO>>('/sales/orders/batch-validate', data)
  },

  // ==================== 验证历史 ====================

  /**
   * 获取订单验证历史
   */
  getOrderValidationHistory(orderId: number) {
    return request.get<R<ValidationHistoryRecord[]>>(`/sales/orders/${orderId}/validation-history`)
  },

  /**
   * 获取验证历史详情
   */
  getValidationHistoryDetail(recordId: number) {
    return request.get<R<ValidationHistoryRecord>>(`/sales/orders/validation-history/${recordId}`)
  },

  /**
   * 删除验证历史记录
   */
  deleteValidationHistory(recordId: number) {
    return request.delete<R<void>>(`/sales/orders/validation-history/${recordId}`)
  },

  /**
   * 批量删除验证历史记录
   */
  batchDeleteValidationHistory(recordIds: number[]) {
    return request.delete<R<void>>('/sales/orders/validation-history/batch', { data: recordIds })
  },

  // ==================== 问题修复 ====================

  /**
   * 修复验证问题
   */
  fixValidationIssue(data: FixValidationIssueRequestDTO) {
    return request.post<R<FixValidationIssueResponseVO>>('/sales/orders/fix-issue', data)
  },

  /**
   * 批量修复验证问题
   */
  batchFixValidationIssues(orderId: number, issueCodes: string[]) {
    return request.post<R<FixValidationIssueResponseVO[]>>(
      `/sales/orders/${orderId}/batch-fix-issues`,
      { issueCodes }
    )
  },

  /**
   * 获取修复历史
   */
  getFixHistory(orderId: number) {
    return request.get<R<FixValidationIssueResponseVO[]>>(`/sales/orders/${orderId}/fix-history`)
  },

  /**
   * 撤销修复
   */
  undoFix(orderId: number, issueCode: string) {
    return request.post<R<void>>(`/sales/orders/${orderId}/undo-fix`, { issueCode })
  },

  // ==================== 验证配置 ====================

  /**
   * 获取验证配置列表
   */
  getValidationConfigs() {
    return request.get<R<ValidationConfig[]>>('/sales/orders/validation-configs')
  },

  /**
   * 获取验证配置详情
   */
  getValidationConfig(configId: number) {
    return request.get<R<ValidationConfig>>(`/sales/orders/validation-configs/${configId}`)
  },

  /**
   * 创建验证配置
   */
  createValidationConfig(data: ValidationConfig) {
    return request.post<R<ValidationConfig>>('/sales/orders/validation-configs', data)
  },

  /**
   * 更新验证配置
   */
  updateValidationConfig(configId: number, data: ValidationConfig) {
    return request.put<R<ValidationConfig>>(`/sales/orders/validation-configs/${configId}`, data)
  },

  /**
   * 删除验证配置
   */
  deleteValidationConfig(configId: number) {
    return request.delete<R<void>>(`/sales/orders/validation-configs/${configId}`)
  },

  /**
   * 启用/禁用验证配置
   */
  toggleValidationConfig(configId: number, enabled: boolean) {
    return request.patch<R<void>>(`/sales/orders/validation-configs/${configId}/toggle`, {
      enabled,
    })
  },

  // ==================== 验证报告 ====================

  /**
   * 生成验证报告
   */
  generateValidationReport(orderId: number) {
    return request.get(`/sales/orders/${orderId}/validation-report`, {
      responseType: 'blob',
    })
  },

  /**
   * 批量生成验证报告
   */
  batchGenerateValidationReports(orderIds: number[]) {
    return request.post(
      '/sales/orders/batch-validation-reports',
      { orderIds },
      {
        responseType: 'blob',
      }
    )
  },

  /**
   * 获取验证统计
   */
  getValidationStatistics() {
    return request.get<R<any>>('/sales/orders/validation-statistics')
  },

  /**
   * 获取验证趋势数据
   */
  getValidationTrend(params: any) {
    return request.get<R<any>>('/sales/orders/validation-statistics/trend', { params })
  },

  // ==================== 集成验证 ====================

  /**
   * 验证并提交审核
   */
  validateAndSubmitReview(orderId: number, validateOptions?: OrderReviewValidationRequestDTO) {
    return request.post<R<void>>(`/sales/orders/${orderId}/validate-and-submit`, validateOptions)
  },

  /**
   * 验证并确认订单
   */
  validateAndConfirmOrder(orderId: number, validateOptions?: OrderReviewValidationRequestDTO) {
    return request.post<R<void>>(`/sales/orders/${orderId}/validate-and-confirm`, validateOptions)
  },

  /**
   * 验证并开始生产
   */
  validateAndStartProduction(orderId: number, validateOptions?: OrderReviewValidationRequestDTO) {
    return request.post<R<void>>(
      `/sales/orders/${orderId}/validate-and-start-production`,
      validateOptions
    )
  },

  // ==================== 实时验证 ====================

  /**
   * 开始实时验证（WebSocket）
   */
  startRealTimeValidation(orderId: number) {
    return request.post<R<{ sessionId: string; wsUrl: string }>>(
      `/sales/orders/${orderId}/real-time-validation/start`
    )
  },

  /**
   * 停止实时验证
   */
  stopRealTimeValidation(sessionId: string) {
    return request.post<R<void>>(`/sales/orders/real-time-validation/${sessionId}/stop`)
  },

  /**
   * 获取实时验证状态
   */
  getRealTimeValidationStatus(sessionId: string) {
    return request.get<R<any>>(`/sales/orders/real-time-validation/${sessionId}/status`)
  },

  // ==================== 工具函数 ====================

  /**
   * 获取验证错误代码说明
   */
  getValidationErrorCodes() {
    return request.get<R<Record<string, string>>>('/sales/orders/validation-error-codes')
  },

  /**
   * 获取修复建议模板
   */
  getFixSuggestionTemplates() {
    return request.get<R<Record<string, string>>>('/sales/orders/fix-suggestion-templates')
  },

  /**
   * 测试验证规则
   */
  testValidationRule(ruleCode: string, testData: any) {
    return request.post<R<any>>(`/sales/orders/test-validation-rule/${ruleCode}`, testData)
  },
}

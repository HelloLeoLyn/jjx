// src/services/sales/order-validation.service.ts

import type {
  OrderReviewValidationRequestDTO,
  OrderReviewValidationResponseVO,
} from '@/types/sales/order-validation'

import { orderApi } from '@/api/sales/order'
import type { OrderReferValidationVO } from '@/types/sales/order'
/**
 * 订单验证服务
 * 提供销售订单提交审核前的验证功能
 */
export class OrderValidationService {
  private static instance: OrderValidationService

  /**
   * 获取单例实例
   */
  public static getInstance(): OrderValidationService {
    if (!OrderValidationService.instance) {
      OrderValidationService.instance = new OrderValidationService()
    }
    return OrderValidationService.instance
  }

  /**
   * 验证订单是否可以提交审核
   */
  async validateOrderForReview(
    request: OrderReviewValidationRequestDTO
  ): Promise<OrderReferValidationVO | null> {
    try {
      // 验证订单基本信息
      const orderInfo = await orderApi.getOrderValidationInfo(request.orderId)

      // 汇总验证结果（2026-08-11 修复：不再强制 canSubmit=true，以后端实际校验结果为准）
      const validationResult = orderInfo.data
      if (validationResult) {
        const items = validationResult.items || []
        const hasError = items.some(
          (it: any) => it.status === 'error' || it.status === 'INVALID' || it.valid === false
        )
        validationResult.canSubmit = !hasError
        if (validationResult.errorCount === undefined) {
          validationResult.errorCount = items.filter(
            (it: any) => it.status === 'error' || it.status === 'INVALID' || it.valid === false
          ).length
        }
      }
      return validationResult
    } catch (error) {
      console.error('订单验证失败:', error)
      throw new Error(`订单验证失败: ${error instanceof Error ? error.message : String(error)}`)
    }
  }
}

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

      // 汇总验证结果
      const validationResult = orderInfo.data
      if (validationResult) {
        validationResult.canSubmit = true
      }
      return validationResult
    } catch (error) {
      console.error('订单验证失败:', error)
      throw new Error(`订单验证失败: ${error instanceof Error ? error.message : String(error)}`)
    }
  }
}

// src/examples/order-validation-demo.ts

import { OrderValidationService } from '@/services/sales/order-validation.service'
import type { OrderReviewValidationRequestDTO } from '@/types/sales/order-validation'

/**
 * 订单验证演示
 * 展示如何使用订单验证服务
 */
export async function runOrderValidationDemo() {
  console.log('=== 订单验证演示开始 ===')

  try {
    // 1. 获取验证服务实例
    const validationService = OrderValidationService.getInstance()

    // 2. 创建验证请求
    const validationRequest: OrderReviewValidationRequestDTO = {
      orderId: 123,
      validateProducts: true,
      validateBom: true,
      validateRouting: true,
      validateCapacity: true,
      validateCost: true,
      options: {
        strictMode: true,
        includeWarnings: true,
      },
    }

    console.log('验证请求:', JSON.stringify(validationRequest, null, 2))

    // 3. 执行验证
    console.log('正在验证订单...')
    const validationResult = await validationService.validateOrderForReview(validationRequest)

    // 4. 显示验证结果
    console.log('\n=== 验证结果 ===')
    console.log(`订单编号: ${validationResult.orderNo}`)
    console.log(`是否有效: ${validationResult.isValid ? '✅ 是' : '❌ 否'}`)
    console.log(`是否可以提交审核: ${validationResult.canSubmit ? '✅ 是' : '❌ 否'}`)
    console.log(`验证状态: ${validationResult.validationStatusLabel}`)

    // 5. 显示验证摘要
    const summary = validationResult.summary
    console.log('\n=== 验证摘要 ===')
    console.log(`总错误数: ${summary.totalErrors}`)
    console.log(`总警告数: ${summary.totalWarnings}`)
    console.log(`总提示数: ${summary.totalInfos}`)
    console.log(`验证耗时: ${summary.validationDuration}ms`)

    // 6. 显示产品验证结果
    if (validationResult.productValidations.length > 0) {
      console.log('\n=== 产品验证结果 ===')
      validationResult.productValidations.forEach((product, index) => {
        console.log(`\n产品 ${index + 1}: ${product.productCode} - ${product.productName}`)
        console.log(`  状态: ${product.statusLabel}`)
        console.log(`  是否有效: ${product.isValid ? '✅' : '❌'}`)
        console.log(`  有有效BOM: ${product.hasValidBom ? '✅' : '❌'}`)
        console.log(`  有有效工艺路线: ${product.hasValidRouting ? '✅' : '❌'}`)

        if (product.errors.length > 0) {
          console.log(`  错误:`)
          product.errors.forEach((error) => {
            console.log(`    - ${error.message}`)
          })
        }

        if (product.warnings.length > 0) {
          console.log(`  警告:`)
          product.warnings.forEach((warning) => {
            console.log(`    - ${warning.message}`)
          })
        }
      })
    }

    // 7. 显示BOM验证结果
    if (validationResult.bomValidations.length > 0) {
      console.log('\n=== BOM验证结果 ===')
      validationResult.bomValidations.forEach((bom, index) => {
        console.log(`\nBOM ${index + 1}: ${bom.bomCode} - ${bom.bomName}`)
        console.log(`  审批状态: ${bom.approveStatusLabel}`)
        console.log(`  是否有效: ${bom.isValid ? '✅' : '❌'}`)
        console.log(`  是否当前版本: ${bom.isCurrent ? '✅' : '❌'}`)
        console.log(`  物料数量: ${bom.materialCount}`)
        console.log(`  缺失物料: ${bom.missingMaterialCount}`)
      })
    }

    // 8. 显示工艺路线验证结果
    if (validationResult.routingValidations.length > 0) {
      console.log('\n=== 工艺路线验证结果 ===')
      validationResult.routingValidations.forEach((routing, index) => {
        console.log(`\n工艺路线 ${index + 1}: ${routing.routingCode} - ${routing.routingName}`)
        console.log(`  审批状态: ${routing.approveStatusLabel}`)
        console.log(`  是否有效: ${routing.isValid ? '✅' : '❌'}`)
        console.log(`  是否当前版本: ${routing.isCurrent ? '✅' : '❌'}`)
        console.log(`  工序数量: ${routing.processCount}`)
      })
    }

    // 9. 显示生产能力验证结果
    if (validationResult.capacityValidations.length > 0) {
      const capacity = validationResult.capacityValidations[0]
      console.log('\n=== 生产能力验证结果 ===')
      console.log(`是否有效: ${capacity.isValid ? '✅' : '❌'}`)
      console.log(`计划开始日期: ${capacity.planStartDate}`)
      console.log(`计划结束日期: ${capacity.planEndDate}`)
      console.log(`所需产能: ${capacity.requiredCapacity}`)
      console.log(`可用产能: ${capacity.availableCapacity}`)
      console.log(`产能利用率: ${capacity.capacityUtilization.toFixed(2)}%`)
      console.log(`是否超负荷: ${capacity.isOverload ? '❌ 是' : '✅ 否'}`)
    }

    // 10. 显示成本验证结果
    if (validationResult.costValidations.length > 0) {
      const cost = validationResult.costValidations[0]
      console.log('\n=== 成本验证结果 ===')
      console.log(`是否有效: ${cost.isValid ? '✅' : '❌'}`)
      console.log(`材料成本: ¥${cost.materialCost.toLocaleString()}`)
      console.log(`人工成本: ¥${cost.laborCost.toLocaleString()}`)
      console.log(`制造费用: ¥${cost.manufacturingCost.toLocaleString()}`)
      console.log(`总成本: ¥${cost.totalCost.toLocaleString()}`)
      console.log(`销售价格: ¥${cost.salePrice.toLocaleString()}`)
      console.log(`毛利率: ${cost.grossMargin}%`)
      console.log(`是否低于最低毛利率: ${cost.isBelowMinMargin ? '❌ 是' : '✅ 否'}`)
    }

    // 11. 显示错误消息
    if (summary.totalErrors > 0) {
      console.log('\n=== 错误消息 ===')
      summary.errorMessages.forEach((message, index) => {
        console.log(`${index + 1}. ${message}`)
      })
    }

    // 12. 显示警告消息
    if (summary.totalWarnings > 0) {
      console.log('\n=== 警告消息 ===')
      summary.warningMessages.forEach((message, index) => {
        console.log(`${index + 1}. ${message}`)
      })
    }

    // 13. 最终建议
    console.log('\n=== 最终建议 ===')
    if (validationResult.canSubmit) {
      console.log('✅ 订单验证通过，可以提交审核！')
    } else {
      console.log('❌ 订单验证失败，请修复以下问题后再提交审核：')
      if (summary.totalErrors > 0) {
        console.log(`  1. 修复 ${summary.totalErrors} 个错误`)
      }
      if (summary.totalWarnings > 0) {
        console.log(`  2. 处理 ${summary.totalWarnings} 个警告`)
      }
    }

    console.log('\n=== 订单验证演示结束 ===')
    return validationResult
  } catch (error) {
    console.error('订单验证演示失败:', error)
    throw error
  }
}

// 如果直接运行此文件，则执行演示
if (import.meta.env?.DEV) {
  runOrderValidationDemo().catch(console.error)
}

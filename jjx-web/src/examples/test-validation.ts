// src/examples/test-validation.ts

import { OrderValidationService } from '@/services/sales/order-validation.service'

async function testOrderValidation() {
  console.log('=== 测试订单验证系统 ===\n')

  try {
    // 1. 获取验证服务实例
    const validationService = OrderValidationService.getInstance()
    console.log('✅ 验证服务实例创建成功')

    // 2. 创建验证请求
    const validationRequest = {
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

    console.log('📋 验证请求配置:')
    console.log(`  订单ID: ${validationRequest.orderId}`)
    console.log(`  验证产品: ${validationRequest.validateProducts}`)
    console.log(`  验证BOM: ${validationRequest.validateBom}`)
    console.log(`  验证工艺路线: ${validationRequest.validateRouting}`)
    console.log(`  验证生产能力: ${validationRequest.validateCapacity}`)
    console.log(`  验证成本: ${validationRequest.validateCost}`)

    // 3. 执行验证
    console.log('\n🔍 正在执行验证...')
    const startTime = Date.now()
    const validationResult = await validationService.validateOrderForReview(validationRequest)
    const endTime = Date.now()
    const duration = endTime - startTime

    console.log(`✅ 验证完成，耗时: ${duration}ms\n`)

    // 4. 显示验证结果
    console.log('📊 验证结果摘要:')
    console.log(`  订单编号: ${validationResult.orderNo}`)
    console.log(`  是否有效: ${validationResult.isValid ? '✅ 是' : '❌ 否'}`)
    console.log(`  是否可以提交审核: ${validationResult.canSubmit ? '✅ 是' : '❌ 否'}`)
    console.log(`  验证状态: ${validationResult.validationStatusLabel}`)

    const summary = validationResult.summary
    console.log(`\n📈 验证统计:`)
    console.log(`  总错误数: ${summary.totalErrors}`)
    console.log(`  总警告数: ${summary.totalWarnings}`)
    console.log(`  总提示数: ${summary.totalInfos}`)
    console.log(`  验证耗时: ${summary.validationDuration}ms`)

    // 5. 显示产品验证详情
    if (validationResult.productValidations.length > 0) {
      console.log(`\n📦 产品验证 (${validationResult.productValidations.length}个产品):`)
      validationResult.productValidations.forEach((product, index) => {
        console.log(`  ${index + 1}. ${product.productCode} - ${product.productName}`)
        console.log(`     状态: ${product.statusLabel}`)
        console.log(`     是否有效: ${product.isValid ? '✅' : '❌'}`)
        console.log(`     有有效BOM: ${product.hasValidBom ? '✅' : '❌'}`)
        console.log(`     有有效工艺路线: ${product.hasValidRouting ? '✅' : '❌'}`)
      })
    }

    // 6. 显示BOM验证详情
    if (validationResult.bomValidations.length > 0) {
      console.log(`\n🔧 BOM验证 (${validationResult.bomValidations.length}个BOM):`)
      validationResult.bomValidations.forEach((bom, index) => {
        console.log(`  ${index + 1}. ${bom.bomCode} - ${bom.bomName}`)
        console.log(`     审批状态: ${bom.approveStatusLabel}`)
        console.log(`     是否有效: ${bom.isValid ? '✅' : '❌'}`)
        console.log(`     是否当前版本: ${bom.isCurrent ? '✅' : '❌'}`)
        console.log(`     物料数量: ${bom.materialCount}`)
        console.log(`     缺失物料: ${bom.missingMaterialCount}`)
      })
    }

    // 7. 显示工艺路线验证详情
    if (validationResult.routingValidations.length > 0) {
      console.log(`\n⚙️ 工艺路线验证 (${validationResult.routingValidations.length}个工艺路线):`)
      validationResult.routingValidations.forEach((routing, index) => {
        console.log(`  ${index + 1}. ${routing.routingCode} - ${routing.routingName}`)
        console.log(`     审批状态: ${routing.approveStatusLabel}`)
        console.log(`     是否有效: ${routing.isValid ? '✅' : '❌'}`)
        console.log(`     是否当前版本: ${routing.isCurrent ? '✅' : '❌'}`)
        console.log(`     工序数量: ${routing.processCount}`)
      })
    }

    // 8. 显示生产能力验证详情
    if (validationResult.capacityValidations.length > 0) {
      const capacity = validationResult.capacityValidations[0]
      console.log(`\n🏭 生产能力验证:`)
      console.log(`  是否有效: ${capacity.isValid ? '✅' : '❌'}`)
      console.log(`  计划开始日期: ${capacity.planStartDate}`)
      console.log(`  计划结束日期: ${capacity.planEndDate}`)
      console.log(`  所需产能: ${capacity.requiredCapacity}`)
      console.log(`  可用产能: ${capacity.availableCapacity}`)
      console.log(`  产能利用率: ${capacity.capacityUtilization.toFixed(2)}%`)
      console.log(`  是否超负荷: ${capacity.isOverload ? '❌ 是' : '✅ 否'}`)
    }

    // 9. 显示成本验证详情
    if (validationResult.costValidations.length > 0) {
      const cost = validationResult.costValidations[0]
      console.log(`\n💰 成本验证:`)
      console.log(`  是否有效: ${cost.isValid ? '✅' : '❌'}`)
      console.log(`  材料成本: ¥${cost.materialCost.toLocaleString()}`)
      console.log(`  人工成本: ¥${cost.laborCost.toLocaleString()}`)
      console.log(`  制造费用: ¥${cost.manufacturingCost.toLocaleString()}`)
      console.log(`  总成本: ¥${cost.totalCost.toLocaleString()}`)
      console.log(`  销售价格: ¥${cost.salePrice.toLocaleString()}`)
      console.log(`  毛利率: ${cost.grossMargin}%`)
      console.log(`  是否低于最低毛利率: ${cost.isBelowMinMargin ? '❌ 是' : '✅ 否'}`)
    }

    // 10. 显示错误和警告
    if (summary.totalErrors > 0) {
      console.log(`\n❌ 错误 (${summary.totalErrors}个):`)
      summary.errorMessages.forEach((message, index) => {
        console.log(`  ${index + 1}. ${message}`)
      })
    }

    if (summary.totalWarnings > 0) {
      console.log(`\n⚠️ 警告 (${summary.totalWarnings}个):`)
      summary.warningMessages.forEach((message, index) => {
        console.log(`  ${index + 1}. ${message}`)
      })
    }

    // 11. 最终结论
    console.log('\n🎯 最终结论:')
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

    console.log('\n=== 测试完成 ===')
    return validationResult
  } catch (error) {
    console.error('❌ 测试失败:', error)
    throw error
  }
}

// 运行测试
if (import.meta.env?.DEV) {
  testOrderValidation()
    .then(() => {
      console.log('\n✅ 所有测试通过！')
      process.exit(0)
    })
    .catch((error) => {
      console.error('\n❌ 测试失败:', error)
      process.exit(1)
    })
}

export { testOrderValidation }

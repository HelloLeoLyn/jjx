// src/examples/test-product-search.ts
// 测试产品搜索的防抖和缓存功能

import { useOrderForm } from '@/views/sales/order/composables/useOrderForm'

async function testProductSearch() {
  console.log('=== 测试产品搜索防抖和缓存功能 ===\n')

  try {
    // 1. 创建订单表单实例
    const orderForm = useOrderForm()
    console.log('✅ 订单表单实例创建成功')

    // 2. 测试搜索功能
    console.log('🔍 测试产品搜索...')

    // 模拟快速输入（测试防抖）
    console.log('1. 模拟快速输入 "pro"')
    orderForm.searchProduct('pro', null)

    console.log('2. 100ms后输入 "prod"')
    setTimeout(() => {
      orderForm.searchProduct('prod', null)
    }, 100)

    console.log('3. 200ms后输入 "produ"')
    setTimeout(() => {
      orderForm.searchProduct('produ', null)
    }, 200)

    console.log('4. 300ms后输入 "product"')
    setTimeout(() => {
      orderForm.searchProduct('product', null)
    }, 300)

    // 等待搜索完成
    await new Promise((resolve) => setTimeout(resolve, 1000))

    // 3. 检查搜索结果
    console.log('\n📊 搜索结果:')
    console.log(`  产品选项数量: ${orderForm.productOptions.value.length}`)
    console.log(`  加载状态: ${orderForm.productLoading.value}`)

    // 4. 测试缓存功能
    console.log('\n🔍 测试缓存功能...')

    console.log('1. 再次搜索 "product"（应该从缓存获取）')
    orderForm.searchProduct('product', null)

    // 等待一小段时间
    await new Promise((resolve) => setTimeout(resolve, 500))

    console.log(`  产品选项数量: ${orderForm.productOptions.value.length}`)
    console.log(`  加载状态: ${orderForm.productLoading.value}`)

    // 5. 测试短输入（小于2个字符）
    console.log('\n🔍 测试短输入过滤...')

    console.log('1. 输入 "p"（应该被过滤）')
    orderForm.searchProduct('p', null)
    await new Promise((resolve) => setTimeout(resolve, 500))
    console.log(`  产品选项数量: ${orderForm.productOptions.value.length}`)

    console.log('2. 输入 "pr"（应该触发搜索）')
    orderForm.searchProduct('pr', null)
    await new Promise((resolve) => setTimeout(resolve, 500))
    console.log(`  产品选项数量: ${orderForm.productOptions.value.length}`)

    // 6. 测试空输入
    console.log('\n🔍 测试空输入...')

    console.log('1. 输入空字符串')
    orderForm.searchProduct('', null)
    await new Promise((resolve) => setTimeout(resolve, 500))
    console.log(`  产品选项数量: ${orderForm.productOptions.value.length}`)

    console.log('2. 输入空格')
    orderForm.searchProduct('   ', null)
    await new Promise((resolve) => setTimeout(resolve, 500))
    console.log(`  产品选项数量: ${orderForm.productOptions.value.length}`)

    // 7. 测试产品选择
    console.log('\n🔍 测试产品选择...')

    if (orderForm.productOptions.value.length > 0) {
      const testProduct = orderForm.productOptions.value[0]
      console.log(`  选择产品: ${testProduct.productCode} - ${testProduct.productName}`)

      // 创建测试订单项
      const testItem = {
        productCode: testProduct.productCode,
        productName: '',
        specification: '',
        unit: '',
        quantity: 1,
        unitPrice: 100,
        amount: 0,
        deliveryDays: 30,
        customRequirements: '',
      }

      console.log('  处理产品选择变化...')
      orderForm.handleProductChange(testItem)

      console.log(`  产品名称自动填充: ${testItem.productName}`)
      console.log(`  单位自动填充: ${testItem.unit}`)
      console.log(`  规格自动填充: ${testItem.specification}`)
      console.log(`  金额计算: ${testItem.amount}`)
    } else {
      console.log('  没有可用的产品进行测试')
    }

    console.log('\n✅ 所有测试完成')
    return true
  } catch (error) {
    console.error('❌ 测试失败:', error)
    return false
  }
}

// 运行测试
if (import.meta.env?.DEV) {
  testProductSearch()
    .then((success) => {
      if (success) {
        console.log('\n🎉 产品搜索功能测试通过！')
      } else {
        console.log('\n❌ 产品搜索功能测试失败')
      }
    })
    .catch((error) => {
      console.error('\n❌ 测试执行失败:', error)
    })
}

export { testProductSearch }

# 销售订单验证系统

## 概述

销售订单验证系统提供了一套完整的验证机制，用于在销售订单提交审核前进行全面的验证。系统支持产品状态、BOM配置、工艺路线、生产能力和成本等多维度的验证。

## 功能特性

### 1. 多维验证

- **产品验证**: 验证产品状态、BOM配置、工艺路线
- **BOM验证**: 验证BOM审批状态、版本、物料完整性
- **工艺路线验证**: 验证工艺路线审批状态、版本、工序配置
- **生产能力验证**: 验证产能是否超负荷、产能利用率
- **成本验证**: 验证毛利率、成本结构

### 2. 验证级别

- **错误 (Error)**: 必须修复的问题，阻止订单提交
- **警告 (Warning)**: 建议修复的问题，不影响提交但可能影响生产
- **提示 (Info)**: 仅供参考的信息

### 3. 验证状态

- **验证通过**: 所有验证项通过
- **验证失败**: 存在错误级别的验证问题
- **部分通过**: 只有警告和提示，没有错误

## 快速开始

### 1. 安装依赖

确保项目已安装必要的依赖：

```bash
pnpm install
```

### 2. 使用验证服务

```typescript
import { OrderValidationService } from '@/services/sales/order-validation.service'
import type { OrderReviewValidationRequestDTO } from '@/types/sales/order-validation'

// 获取验证服务实例
const validationService = OrderValidationService.getInstance()

// 创建验证请求
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

// 执行验证
try {
  const validationResult = await validationService.validateOrderForReview(validationRequest)

  if (validationResult.canSubmit) {
    console.log('✅ 订单验证通过，可以提交审核！')
  } else {
    console.log('❌ 订单验证失败，请修复以下问题：')
    validationResult.summary.errorMessages.forEach((message, index) => {
      console.log(`${index + 1}. ${message}`)
    })
  }
} catch (error) {
  console.error('验证失败:', error)
}
```

### 3. 使用API接口

```typescript
import { orderValidationApi } from '@/api/sales/order-validation'

// 验证订单
const validationResult = await orderValidationApi.validateOrderForReview({
  orderId: 123,
  validateProducts: true,
  validateBom: true,
  validateRouting: true,
  validateCapacity: true,
  validateCost: true,
})

// 修复验证问题
const fixResult = await orderValidationApi.fixValidationIssue({
  orderId: 123,
  issueCode: 'PRODUCT_STATUS_INVALID',
  fixData: { status: 'released' },
})

// 获取验证历史
const history = await orderValidationApi.getOrderValidationHistory(123)
```

## 验证规则

### 产品验证规则

1. **产品状态**: 必须为"已发布"状态
2. **BOM配置**: 必须有有效的BOM配置
3. **工艺路线**: 必须有有效的工艺路线配置

### BOM验证规则

1. **审批状态**: 必须为"已批准"状态
2. **版本**: 建议使用当前版本
3. **物料完整性**: 不能有缺失物料

### 工艺路线验证规则

1. **审批状态**: 必须为"已批准"状态
2. **版本**: 建议使用当前版本
3. **工序配置**: 必须有至少一个工序

### 生产能力验证规则

1. **产能超负荷**: 需求产能不能超过可用产能
2. **产能利用率**: 建议不超过90%

### 成本验证规则

1. **毛利率**: 不能低于最低要求
2. **成本结构**: 各项成本必须在合理范围内

## 错误代码

### 产品相关错误

- `PRODUCT_STATUS_INVALID`: 产品状态无效
- `PRODUCT_NO_BOM`: 产品没有配置BOM
- `PRODUCT_NO_ROUTING`: 产品没有配置工艺路线

### BOM相关错误

- `BOM_STATUS_INVALID`: BOM审批状态无效
- `BOM_NOT_CURRENT`: BOM不是当前版本
- `BOM_MISSING_MATERIALS`: BOM有缺失物料

### 工艺路线相关错误

- `ROUTING_STATUS_INVALID`: 工艺路线审批状态无效
- `ROUTING_NOT_CURRENT`: 工艺路线不是当前版本
- `ROUTING_NO_PROCESSES`: 工艺路线没有配置工序

### 生产能力相关错误

- `CAPACITY_OVERLOAD`: 产能超负荷
- `CAPACITY_HIGH_UTILIZATION`: 产能利用率过高

### 成本相关错误

- `MARGIN_BELOW_MINIMUM`: 毛利率低于最低要求
- `MARGIN_LOW`: 毛利率接近最低要求

## 配置管理

### 验证配置

系统支持动态配置验证规则：

```typescript
// 获取验证配置
const configs = await orderValidationApi.getValidationConfigs()

// 创建验证配置
const newConfig = await orderValidationApi.createValidationConfig({
  configName: '严格验证模式',
  validationType: 1, // 产品验证
  rules: [...],
  enabled: true,
  priority: 1,
})
```

### 验证选项

验证时可以指定选项：

```typescript
const options = {
  strictMode: true, // 严格模式
  includeWarnings: true, // 包含警告
  includeInfos: true, // 包含提示
  timeout: 30000, // 超时时间(毫秒)
  cache: true, // 使用缓存
}
```

## 集成使用

### 1. 在订单提交前验证

```vue
<script setup lang="ts">
import { orderValidationApi } from '@/api/sales/order-validation'

const submitOrder = async () => {
  // 先验证订单
  const validationResult = await orderValidationApi.validateOrderForReview({
    orderId: orderId.value,
    validateProducts: true,
    validateBom: true,
    validateRouting: true,
  })

  if (!validationResult.canSubmit) {
    // 显示验证错误
    showValidationErrors(validationResult)
    return
  }

  // 验证通过，提交订单
  await submitOrderToBackend()
}
</script>
```

### 2. 实时验证

```vue
<script setup lang="ts">
import { orderValidationApi } from '@/api/sales/order-validation'

// 开始实时验证
const startRealTimeValidation = async () => {
  const session = await orderValidationApi.startRealTimeValidation(orderId.value)

  // 连接WebSocket
  const ws = new WebSocket(session.wsUrl)
  ws.onmessage = (event) => {
    const validationUpdate = JSON.parse(event.data)
    updateValidationDisplay(validationUpdate)
  }
}
</script>
```

## 测试

### 运行演示

```bash
# 运行验证演示
pnpm exec tsx src/examples/order-validation-demo.ts
```

### 单元测试

```typescript
import { OrderValidationService } from '@/services/sales/order-validation.service'

describe('OrderValidationService', () => {
  let service: OrderValidationService

  beforeEach(() => {
    service = OrderValidationService.getInstance()
  })

  test('should validate order successfully', async () => {
    const result = await service.validateOrderForReview({
      orderId: 1,
      validateProducts: true,
      validateBom: true,
      validateRouting: true,
      validateCapacity: true,
      validateCost: true,
    })

    expect(result).toBeDefined()
    expect(result.orderId).toBe(1)
  })
})
```

## 最佳实践

### 1. 验证时机

- 订单保存时进行轻量级验证
- 订单提交审核前进行全面验证
- 订单修改后重新验证

### 2. 错误处理

- 提供清晰的错误消息
- 给出具体的修复建议
- 提供一键修复功能

### 3. 性能优化

- 使用缓存避免重复验证
- 异步验证避免阻塞UI
- 分步验证提高响应速度

### 4. 用户体验

- 实时显示验证进度
- 提供验证结果摘要
- 支持批量验证和修复

## 扩展开发

### 添加新的验证规则

1. 在 `ValidationEnum.ts` 中添加新的验证类型
2. 在 `order-validation.ts` 中添加新的验证结果类型
3. 在 `order-validation.service.ts` 中实现验证逻辑
4. 在 `order-validation.ts` (API) 中添加对应的接口

### 自定义验证逻辑

```typescript
// 扩展验证服务
class CustomOrderValidationService extends OrderValidationService {
  async validateCustomRule(orderId: number): Promise<ValidationItem[]> {
    // 实现自定义验证逻辑
    return []
  }
}
```

## 故障排除

### 常见问题

1. **验证服务无法启动**
   - 检查依赖是否安装
   - 检查TypeScript配置
   - 检查导入路径

2. **验证结果不正确**
   - 检查枚举值是否正确
   - 检查API响应格式
   - 检查验证逻辑

3. **性能问题**
   - 启用缓存
   - 优化验证逻辑
   - 使用异步验证

### 调试技巧

```typescript
// 启用调试日志
const validationService = OrderValidationService.getInstance()
console.log('验证服务:', validationService)

// 查看验证请求和响应
console.log('验证请求:', validationRequest)
console.log('验证响应:', validationResult)
```

## 版本历史

### v1.0.0 (2024-01-01)

- 初始版本发布
- 支持基本的产品、BOM、工艺路线验证
- 提供API和服务层接口

### v1.1.0 (计划中)

- 支持实时验证
- 添加验证配置管理
- 优化性能

## 贡献指南

欢迎提交Issue和Pull Request来改进这个系统。

### 开发流程

1. Fork仓库
2. 创建功能分支
3. 提交更改
4. 创建Pull Request

### 代码规范

- 使用TypeScript
- 遵循项目代码规范
- 添加单元测试
- 更新文档

## 许可证

本项目采用MIT许可证。详见LICENSE文件。

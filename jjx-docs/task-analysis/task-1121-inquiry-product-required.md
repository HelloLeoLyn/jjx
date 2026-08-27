# 任务 1121 实现方案（修订版）：标准品询价必须选产品 + 只能选该客户的产品

> 任务：dev-1787735758688（kanban_module=dev）
> 优先级：normal | 状态：未开始(0) | 负责人：未分配 | 截止：2026-08-26
> 方案日期：2026-08-26（v2 修订）| 方案人：大黄 | 状态：待确认（未写代码）

---

## 一、业务口径（v2 修订确认）

- **公司产品均为定制产品，无通用产品** → 每个产品都有固定客户（`product.customer_id` 必填）
- 因此"只能选择该客户的产品" = **严格按 `customer_id = X` 过滤**（口径 B，不做"专属+公共"兼容）

## 二、公共组件盘点（优先复用）

| 组件 | 位置 | 现状 |
|---|---|---|
| ProductSelector | `src/components/Selector/ProductSelector.vue` | 远程搜索（`productApi.search`），无 customerId 支持 |
| ProductPicker | `src/components/Selector/ProductPicker.vue` | 聚焦自动加载 + 远程搜索（2026-08-11），无 customerId 支持 |
| 后端 | `GET /product/search`、`GET /product/list` | 均不支持按客户过滤（ProductQuery 无 customerId 字段） |

结论：**没有现成的"按客户过滤产品"能力**，需要给公共组件/接口补上，且向后兼容（不传 customerId 行为不变，其他页面无感）。

## 三、修改方案

### 后端（能力补全，公共组件/接口统一受益）

1. **`ProductQuery` 增加 `customerId` 字段**；`ProductServiceImpl.buildWrapper` 支持：customerId 非空 → `eq(Product::getCustomerId, customerId)`（口径 B）
2. **`GET /product/search` 支持 customerId 参数**：`searchProducts(keyword, customerId)` → customerId 非空时 `eq(Product::getCustomerId, customerId)`
   - 顺带修正：现有 keyword 条件 `like(code).or().like(name)` 与 `eq(status)` 混连，建议包成 `.and(w -> w.like(code).or().like(name))`，避免 OR 优先级问题
3. **兜底校验**：`insertInquiry` / `updateInquiry` 标准品（inquiryType=1）时 `productId` 必填，否则报"标准品询价必须选择产品"

### 前端（公共组件加 customerId prop，询价表单换用公共组件）

4. **`searchProduct(keyword, customerId?)`**（api/product/index.ts）、**`ProductQueryParams` 加 `customerId?`**（types/product/index.ts）
5. **`ProductSelector.vue` / `ProductPicker.vue` 增加可选 `customerId` prop**：
   - 内部搜索统一传 customerId（`productApi.search(keyword, customerId)` / `listProduct({...customerId})`）
   - 不传时行为完全不变（向后兼容，销售订单/报价单/BOM 等现有使用方无感）
6. **询价表单（inquiry/index.vue）标准品产品选择改换公共组件**（推荐 ProductPicker：聚焦即加载该客户产品列表）：
   - 绑定 `:customer-id="form.customerId"`，`change` 后沿用现有 `onProductSelect` 回填逻辑（描述/编码/名称/反解编码要素）
   - 未选客户时禁用产品选择并提示"请先选择客户"（标准品必须先选客户）
   - 切换客户时清空已选产品并重校验
7. **rules 动态必填**：`inquiryType === 1` 时 productId 必填（自定义 validator 判断类型），触发时机 change/blur

## 四、改动清单预估

| 文件 | 改动 |
|---|---|
| `ProductQuery.java`（后端） | +customerId |
| `ProductServiceImpl.java`（后端） | buildWrapper + customerId 过滤；searchProducts 支持 customerId + 修正 OR 分组 |
| `ProductController.java`（后端） | /search 透传 customerId |
| `InquiryServiceImpl.java`（后端） | insert/update 标准品 productId 必填兜底 |
| `src/api/product/index.ts` | searchProduct + customerId 可选参 |
| `src/types/product/index.ts` | ProductQueryParams + customerId? |
| `ProductSelector.vue` / `ProductPicker.vue` | +customerId prop（可选，向后兼容） |
| `inquiry/index.vue` | 换公共组件 + 动态必填 + 切客户清空 + 未选客户禁用提示 |

## 五、边界

- 其他页面（销售订单/报价单/BOM/工艺路线）继续全库搜索，不受影响（customerId 可选）
- 样品（inquiryType=2）走编码生成器，不涉及产品选择，不动
- 后端兜底保证 API 直调也不能绕过"标准品必须选产品"

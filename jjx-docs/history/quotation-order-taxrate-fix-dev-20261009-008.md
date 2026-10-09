# 报价税率注解归位 + 报价转订单税率透传（去掉 ÷100）— dev-20261009-008

- 日期：2026-10-09
- 任务码：dev-20261009-008
- 状态：待审核(2)
- 家族：dev-20261008-026（注解挂错字段：折扣金额被误挂折扣率校验）

## 背景

用户报「新增报价单后端报『税率不能大于100』」，并追问是否与「报价转订单税率疑点」同因。核查结论：**两个独立问题**（一个存不进去、一个算错），本次一并修。

## 问题一：报价新增报「税率不能大于100」

- 全仓仅 `SalesOrderAddDTO` / `SalesOrderEditDTO` / `SalesQuotationAddDTO` 三处使用该文案。
- 前者挂在 `taxRate` 上（正确）；**`SalesQuotationAddDTO` 把它挂在 `taxAmount`（税额）上**：

```java
// 修前
@Schema(description = "税率") @DecimalMin("0"...) private BigDecimal taxRate;          // 无上限
@Schema(description = "税额") @DecimalMax(value="100", message="税率不能大于100")
private BigDecimal taxAmount;                                                          // ← 错位
```

- 后果：新增报价单时税额 > 100 元（常态）即 400 报错。
- 修法：`@DecimalMax(100,"税率不能大于100")` 归位到 `taxRate`（与订单 DTO 对齐），`taxAmount` 去上限。

## 问题二：报价转订单税率被 ÷100

- `QuotationServiceImpl.convertToOrder` 原写：`orderDTO.setTaxRate(quotation.getTaxRate().divide(100))`，注释称"换算成订单小数口径"。
- 但**订单侧是百分数口径**：`SalesOrderCalculator.fillFromItems` 用 `税 = 未税小计 × tax_rate ÷ 100`；`SalesOrderEditDTO` 明确 `税率(百分比，如 13 表示 13%)`；前端 `OrderForm` 税率 `:max=100`。
- 后果：报价 13% → 订单 `tax_rate=0.13` → 税额少 100 倍，`报价最终金额 ≠ 转单后应付`。
- 修法：**直接透传** `orderDTO.setTaxRate(quotation.getTaxRate())`；移除无效的 `setTaxAmount`（`fillFromItems` 本就按税率重算税额）。

## 改动清单

- `SalesQuotationAddDTO.java`：税率上限校验归位（税额字段去 `@DecimalMax`）。
- `QuotationServiceImpl.java`：`convertToOrder` 去掉 ÷100，直接透传税率；注释同步。

## 效果

修复后「报价 → 订单」金额闭环成立（同明细/税率/折扣/运费）：
```
订单未税小计 = 报价 subtotal_amount
订单 tax_amount = subtotal × rate ÷ 100 = 报价 tax_amount
订单 total_amount = 未税小计 + 税 + 运费 = 报价 total_amount
订单 final_amount = total − 折扣 = 报价 final_amount
```

## 验证（静态自查）

- `mvn -o compile` → EXIT=0（见提交记录）。

## 遗留 / 待办

1. 后端需**重启**生效。
2. 建议补单测：报价（含运费）→ 转订单，应付与税额一致。
3. 前端报价表单税率本就是 `:max="100"`（与本次后端口径一致），无需改。

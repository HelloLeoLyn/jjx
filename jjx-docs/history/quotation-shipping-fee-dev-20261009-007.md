# 报价单补运费字段（对齐销售订单金额口径）— dev-20261009-007

- 日期：2026-10-09
- 任务码：dev-20261009-007
- 状态：待审核(2)
- 关联：dev-20261008-002（销售订单加 shipping_fee）

## 背景

金额组成分析发现：四类单据（报价单 / 销售订单 / 采购订单 / 样品单）的金额构成是各自演进的，未统一。

- **销售订单**：未税小计 + 税额 + **运费（单列不计税）** − 折扣 = 应付总额（`shipping_fee`，2026-10-08 dev-20261008-002 才补）。
- **报价单**：小计(未税) → 税 → 总金额(含税) → 最终金额(总金额−折扣)，**无运费字段**。
- **采购订单**：合计(不含税) + 税额 = 价税合计，不单列运费（合同口径「运费由供方承担」，合理）。
- **样品单**：只有明细 amount，无单头汇总。

问题：报价是对客户的外对口径，却无运费字段；而订单已有运费。一旦成交要收运费，**「报价最终金额 ≠ 转单后应付」**，业务上易扯皮。

## 方案（本任务范围：仅第 1 项）

给报价单补运费字段，金额口径对齐销售订单：

```
subtotal_amount  = Σ 明细行金额（未税）
tax_amount       = subtotal_amount × tax_rate / 100     （税率存百分数）
total_amount     = subtotal_amount + tax_amount + shipping_fee   （含税，含运费）
final_amount     = total_amount − discount_amount        （≥ 0，客户最终应付）
```

- 运费**单列、不计税**，与订单 `SalesOrderCalculator` 一致。
- 转订单时透传运费（`orderDTO.setShippingFee(quotation.getShippingFee())`）。

## 改动清单

### DB
- 迁移 `jjx-docs/sql/migrations/248_sales_quotation_shipping_fee.sql`：`sales_quotation` 加列
  `shipping_fee DECIMAL(15,2) NULL DEFAULT NULL COMMENT '运费（单列，不计税）' AFTER total_amount`（幂等）。
- 与订单口径一致：列可空（历史单为 NULL，重算按 0 处理）。
- **执行由用户手工完成**（仓库规范：agent 不自动建库表）：
  `bash scripts/db-migrate.sh 248_sales_quotation_shipping_fee.sql --yes --task dev-20261009-007`

### 后端
- `SalesQuotation` 实体：新增 `shippingFee`。
- `SalesQuotationAddDTO`：新增 `shippingFee`（`@DecimalMin(0)`）。
- `QuotationServiceImpl`：
  - `addQuotation`：`shippingFee` 缺省补 0。
  - `recalcQuotationAmounts`：`total = subtotal + tax + 运费`（运费取表头值，空按 0）；`final` 口径不变。
  - `convertToOrder`：透传 `shippingFee` 到订单 DTO。
  - `buildQuotationChanges`：变更记录增列「运费」。
  - `buildQuotationExcel`：汇总区增「运费」行。
  - `copy`：复制时带上 `shippingFee`。

### 前端
- `api/sales/quotation.ts`：`QuotationBase` 增 `shippingFee?`。
- `useQuotation.ts`：表单默认值 + reset 增 `shippingFee: 0`。
- `QuotationFormDialog.vue`：金额汇总增「运费(元，不计税)」输入；`calculateTotalAmount` 计入运费。
- `QuotationDetailDialog.vue`：金额汇总增「运费」统计项。
- `print.vue`：金额汇总增「运费」行。

## 验证（静态自查）

- `mvn -o compile`：见提交记录。
- `npx vue-tsc --noEmit`：见提交记录。
- `node scripts/check-docs.mjs`：见提交记录。

## 遗留 / 待办

1. **迁移未执行**：`248` 需用户手工跑；跑完重启后端才生效。
2. **转订单税率换算疑点（独立核实，本次未动）**：`convertToOrder` 把报价税率 `÷100` 再写入订单
   （`orderDTO.setTaxRate(quotation.getTaxRate().divide(100))`）。但订单侧税率看起来是**百分数口径**
   （`SalesOrderCalculator`：`tax = subtotal × rate ÷ 100`；`OrderForm` 亦为百分数）→ 变成 ×0.01 后税额可能算错 100 倍。
   库里当前无转单数据，无实证，故本次**未改**，单独立项核实。
3. **建议 2（未做）**：`《单据金额口径规范》`文档。
4. **建议 3（未做）**：展示标签统一（小计→未税小计 / 应付 / 价税合计等三种叫法）。

## 口径备注（现状，非本任务定案）

- 报价「最终金额」、订单「应付总额」、采购「价税合计」——同一「客户最终应付」三种叫法，待规范统一。

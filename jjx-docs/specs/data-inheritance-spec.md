# 🔗 数据传承规则（单据转换）

> 版本: v1.0 | 最后更新: 2026-08-01
> 适用范围: 所有单据转换点（询价→报价→样品→订单→生产）
> 目的: 单据转换时数据不丢、可追溯、有据可依

---

## 一、核心原则

1. **客户信息必传承**：customerId/customerName/contactPerson/contactPhone 全带
2. **traceId 链贯穿**：所有转换点继承来源单的 traceId，保证流水可追溯
3. **明细必传承**：产品明细（productId/code/name/quantity/price）转换时复制
4. **附件随 traceId 可见**：不物理复制附件，靠 traceId 关联查询（DEV-440 方案）
5. **来源引用必记录**：目标单记录 sourceId（如 quotationId/orderId/convertedOrderId）

---

## 二、转换点清单

### 1. 询价单 → 报价单（inquiry.converted）

| 传承项 | 来源字段 | 目标字段 | 状态 |
|---|---|---|---|
| 客户 | customerId/Name | customerId/Name | ✅ |
| 联系人 | contactPerson/Phone | contactPerson/Phone | ✅ |
| 类型 | inquiryType | quotationType | ✅ |
| 需求描述 | productDescription | remark | ⚠️ 需确认 |
| traceId | traceId | traceId | ✅ |
| 图纸附件 | — | 靠 traceId 关联 | ✅ (DEV-440) |

**缺口**：报价单无 sourceInquiryId 字段（只能靠 traceId 反查）

### 2. 报价单 → 样品单（sample.created）

| 传承项 | 来源字段 | 目标字段 | 状态 |
|---|---|---|---|
| 客户 | customerId/Name | customerId/Name | ✅ |
| 报价单引用 | quotationId | quotationId | ✅ |
| 产品明细 | sales_quotation_item | sales_order_product | ✅ |
| traceId | traceId | traceId | ✅ |
| 工艺参数 | engineeringNote | engineeringNote（工程接单后填）| ✅ |

### 3. 报价单 → 标准订单（quotation.converted）

| 传承项 | 来源字段 | 目标字段 | 状态 |
|---|---|---|---|
| 客户 | customerId/Name | customerId/Name | ✅ |
| 订单号 | generateOrderNo | orderNo | ✅ |
| 明细 | quotation_item | sales_order_product | ✅ (DEV-426 修复) |
| traceId | traceId | traceId | ✅ |
| remark | quotationNo 引用 | remark | ✅ |

### 4. 样品单 → 标准订单（sample.converted）

| 传承项 | 来源字段 | 目标字段 | 状态 |
|---|---|---|---|
| 客户 | customerId/Name | customerId/Name | ✅ |
| 金额 | totalAmount/finalAmount | totalAmount/finalAmount | ✅ |
| 明细 | sales_order_product | sales_order_product | ✅ |
| 工艺参数 | engineeringNote | remark【工艺参数传承】 | ✅ |
| 打样成本/工时 | sampleCost/workHours | remark【打样成本/工时】 | ✅ |
| **BOM** | sales_sample_bom | engineering_bom（草稿）| ✅ (DEV-457) |
| traceId | traceId | traceId | ✅ |

### 5. 标准订单 → 生产工单（order.production_started）

| 传承项 | 来源字段 | 目标字段 | 状态 |
|---|---|---|---|
| 销售订单 | orderId/orderNo | salesOrderId/No | ✅ (DEV-470) |
| 产品 | productId/code/name | productId/code/name | ✅ |
| 数量 | quantity | plannedQuantity | ✅ |
| **BOM** | 已批准BOM校验 | bomId/bomCode | ✅ (DEV-470) |
| **工艺路线** | 已批准路线校验 | routingId/routingCode | ✅ (DEV-470) |
| traceId | traceId | traceId | ✅ |

---

## 三、字段传承对照表（模板）

新单据转换必须填写此表：

```markdown
| 传承项 | 来源字段 | 目标字段 | 转换规则 |
|---|---|---|---|
| 客户ID | xxxId | yyyId | 直接复制 |
| 明细 | xxx_item | yyy_item | 逐行复制（清空新主键）|
| traceId | traceId | traceId | 直接复制（无则生成）|
| 附件 | — | — | 不复制，靠 traceId 关联 |
```

---

## 四、traceId 规则

1. **来源**：单据创建时生成（无则继承来源单）
2. **传播**：每次转换继承来源单 traceId（`target.setTraceId(source.getTraceId())`）
3. **查询**：`GET /system/attachment/by-trace/{traceId}` 查全部关联附件
4. **回填**：历史数据无 traceId 时，操作日志按"来源单号"反查回填（DEV-411/440）

---

## 五、已知缺口

| 缺口 | 影响 | 建议 |
|---|---|---|
| 报价单无 sourceInquiryId | 报价单看不出自哪个询价 | 加字段或靠 traceId 查询 |
| 样品→量产不传承工艺路线 | 转量产只建 BOM，路线需手动 | DEV-457 只提醒，待补自动生成 |
| 附件不物理复制 | 新单看不到附件（需点追溯）| 靠 AttachmentPanel traceId 展示（已做）|

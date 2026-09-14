# 后端枚举冗余审计报告

> 审计日期：2026-08-30 ｜ 范围：jjx-server 60 个 public 枚举
> 前置：字段命名审计见 enum-field-naming-audit-20260830.md；本报告聚焦"重复定义/同名冲突/语义重叠"

---

## 一、结论摘要

**存在 4 组明确冗余 + 5 组同名冲突 + 1 组语义重叠**，其中最严重的是 purchase 模块的"双轨制"（同一状态定义两遍，String code 版 + Integer code 版并存）。

## 二、🔴 明确冗余（同一语义定义两遍，应合并）

### 1. purchase 模块 3 组"双胞胎"枚举 ⭐最严重

| 成对 | String code 版 | Integer code 版 | 常量对比 |
|---|---|---|---|
| 付款状态 | `PaymentStatus`（pending/partially_paid/paid/completed） | `PaymentStatusEnum`（0/1/2） | 常量一致（Integer 版少 PAID） |
| 收货状态 | `ReceiptStatus`（pending/partially_received/completed） | `ReceiptStatusEnum`（0/1/2） | **完全一致** |
| 采购单状态 | `PurchaseOrderStatus`（draft/inquiry/comparing/submitted/approved/in_progress/completed/closed/cancelled） | `PurchaseOrderStatusEnum`（0/1/2/3/4/5/6/7/8） | **完全一致**（9 个常量） |

同一状态两套枚举并存，**code 类型不同、语义相同**——典型的历史演进残留（先写 String 版，后统一 Integer 时新增 Enum 版未删旧的）。合并方案：保留 Integer 版（`PurchaseOrderStatusEnum` 等），删除 String 版，调用点全局替换。

### 2. 审批状态两套

| 枚举 | 常量 |
|---|---|
| `ApproveStatusEnum`（common） | DRAFT(1)/PENDING(2)/APPROVED(3)/REJECTED(4) |
| `ApprovalStatusEnum`（purchase） | DRAFT(1)/CANCELLED(2)/PENDING(3)/APPROVED(4)/REJECTED(5) |

common 已有通用审批状态，purchase 又自定义一份（多 CANCELLED，code 还错位：PENDING=3 而非 2）。建议：统一用 common 版（补 CANCELLED 常量），删 purchase 版。

## 三、🟠 同名不同义（不冗余但高风险，应改名）

| 枚举名 | 出现位置 | 语义差异 |
|---|---|---|
| `InquiryStatus` | purchase（4 常量：PENDING/INQUIRED/COMPARING/SELECTED） | 采购询价 |
| `InquiryStatus` | sales（7 常量：DRAFT/PENDING/SENT/CONVERTED/ACCEPTED/REJECTED/EXPIRED） | 销售询价 |
| `PaymentStatusEnum` | purchase（3 常量：PENDING/PARTIALLY_PAID/COMPLETED） | 采购付款 |
| `PaymentStatusEnum` | sales（5 常量：UNPAID/PAYING/PAID/PARTIAL_PAID/REFUNDED） | 销售回款 |
| `OrderStatusEnum` | **inventory / production / sales 三处** | 库存单据/生产工单/销售订单，状态流转完全不同 |
| `OrderTypeEnum` | production（8 常量：PLAN/ORDER/TRIAL/REWORK/SAMPLE/REPAIR/SPARE/URGENT） | 生产类型 |
| `OrderTypeEnum` | sales（2 常量：STANDARD/SAMPLE） | 订单类型 |

同名类在不同模块 import 极易拿错（IDE 自动补全会导入错的那个）。建议：改名加模块前缀（如 `PurchaseInquiryStatus`/`SalesInquiryStatus`、`ProdOrderStatusEnum`/`SalesOrderStatusEnum`），或至少统一放 `common/enums` 并在类注释标注适用范围。

## 四、🟡 语义重叠（部分冗余，建议统一）

| 枚举 | 位置 | 常量 |
|---|---|---|
| `InspectionResult` | purchase | PASSED / FAILED |
| `QualityInspectionResultEnum` | production | PENDING / PASS / FAIL |

质检结果两个模块各定义一份（常量名还不同：PASSED vs PASS）。建议统一为一个质检结果枚举（含 PENDING），放 common。

## 五、⚠️ 附带风险（非冗余但易踩坑）

### 异常枚举 code 语义重复
- `BusinessExceptionEnum`（common）：ORDER_NOT_FOUND=2001、ORDER_STATUS_ERROR=2002、ORDER_ALREADY_CANCELLED=2010……
- `PurchaseExceptionEnum`（purchase）：ORDER_NOT_FOUND=10000、ORDER_STATUS_ERROR=10100、ORDER_ALREADY_CANCELLED=10106……

**同语义常量名在两处定义、code 段不同**——调用方若引错枚举，错误码体系会乱。建议：异常枚举收敛到 common（或按模块分区但统一命名前缀，如 PURCHASE_ORDER_NOT_FOUND）。

### MaterialEnums 聚合文件（合理，仅提示）
`MaterialEnums` 内含 Type（物料类型）/Status（物料状态）/BatchControl（批次控制）3 个内部枚举——聚合模式本身没问题，但与全仓"一枚举一文件"风格不一致，新人不一定找得到。

## 六、处理建议（按优先级）

| 优先级 | 项 | 工作量 |
|---|---|---|
| P0 | purchase 3 组双胞胎合并（删 String 版） | 中（需查调用点） |
| P0 | ApproveStatusEnum vs ApprovalStatusEnum 统一 | 小 |
| P1 | InquiryStatus / PaymentStatusEnum 改名区分 | 中 |
| P1 | InspectionResult 质检结果统一 | 小 |
| P2 | OrderStatusEnum ×3 / OrderTypeEnum ×2 改名加前缀 | 大（调用点多） |
| P2 | 异常枚举命名规范（前缀化） | 中 |

> 全部为静态重构，IDE 全局重命名 + mvn compile 可兜底，建议作为独立专项排期，与字段命名统一（见上一份报告）合并成一个"枚举治理"专项一次做完。

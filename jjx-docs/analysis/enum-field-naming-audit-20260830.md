# 后端枚举字段命名统一性审计报告

> 审计日期：2026-08-30 ｜ 审计范围：jjx-server 全部 60 个 public 枚举
> 背景：用户反馈枚举展示字段命名混乱（label/name/desc 混用），本报告量化现状并给出统一方案

---

## 一、结论摘要

**确认混乱，且比预想严重**：同一语义（"短展示文本"，即界面显示用的中文标签）在 60 个枚举里用了 **4 种字段名**：

| 字段名 | 枚举数 | 主要分布 |
|---|---|---|
| `label` | 34 | inventory（库存）为主、production 部分、product 内部枚举 |
| `name` | 16 | common、product、sales、production 部分 |
| `desc` | 7 | sales 部分、system、common、framework |
| `description` | 23 | **purchase 全部 16 个**、production 部分、sales 部分 |

另外还有第 5 种隐藏变体：部分枚举用 `value` 存展示文本（20 处，与 code 混用语义）。

## 二、全量清单（60 个枚举的展示字段）

| 枚举 | 模块 | 展示字段 | code 类型 |
|---|---|---|---|
| ApproveStatusEnum | common | name | Integer |
| UnitEnum | common | name | String |
| YesNoEnum | common | desc | Integer |
| BizCode | framework | desc | String |
| AlertLevelEnum | inventory | label | Integer |
| AlertTypeEnum | inventory | label | Integer |
| InboundTypeEnum | inventory | label | Integer |
| OrderStatusEnum | inventory | label | Integer |
| OutboundTypeEnum | inventory | label | Integer |
| ProcessGroup | inventory | name | String |
| StockItemStatusEnum | inventory | label | Integer |
| StockStatusEnum | inventory | label | Integer |
| TransactionTypeEnum | inventory | label | String |
| FilmTypeEnum | product | name | String |
| ProcessCategoryEnum | product | name | String |
| ProcessTypeEnum | product | name | String |
| ProcessTypeEnum 内部 Type | product | label | String |
| ExecutionStatusEnum | production | name + description | String |
| OrderStatusEnum | production | name + description | Integer |
| OrderTypeEnum | production | name + description | String |
| ProductionTaskStatus | production | description | String |
| QualityInspectionResultEnum | production | label | Integer |
| QualityInspectionTypeEnum | production | label | Integer |
| QualityTemplateStatusEnum | production | label | Integer |
| RecordTypeEnum | production | name + description | String |
| ToolingStatusEnum | production | label | Integer |
| ToolingTypeEnum | production | label | Integer |
| WorkReportStatusEnum | production | label | String |
| ApprovalStatusEnum 等 **16 个 purchase 枚举** | purchase | **description** | Integer/String |
| InquiryStatus | sales | name | Integer |
| OperationResultEnum | sales | name | Integer |
| OperationTypeEnum | sales | name | String |
| OrderStatusEnum | sales | name + description | Integer |
| OrderTypeEnum | sales | desc | Integer |
| PaymentStatusEnum | sales | desc | Integer |
| ProdStatusEnum | sales | desc | Integer |
| QuotationStatus | sales | name | Integer |
| SampleOrderStatusEnum | sales | name + description | Integer |
| BusinessType | system | desc | String |
| UserType | system | desc | Integer |

## 三、混乱根源分析

1. **模块各自为政**：每个模块开发时按自己习惯命名——inventory 用 label、purchase 用 description、sales 用 name/desc，无全局规范约束。
2. **purchase 模块语义偏差**：16 个枚举把"短标签"直接命名为 `description`，与 production/sales 里"name（短标签）+ description（详细说明）"并存的双字段模式冲突——同样的 `getDescription()`，有的返回"待处理"（短标签），有的返回"已提交审核，等待审核人审核"（长说明），**同名不同义，这是最危险的不一致**。
3. **desc 是缩写**：desc 与 description 极易混淆，sales 的 OrderTypeEnum/PaymentStatusEnum 用 desc，而 purchase 用 description。
4. **value 语义漂移**：20 个枚举用 value 字段，部分（如 transaction 类）把中文文本放 value 而非 code——调用方 `getValue()` 拿到的可能是"值"也可能是"描述"。
5. **code 类型不统一**（附带发现）：Integer（1/2/3）与 String（"DRAFT"/"PRINTING"）并存，前端展示/字典映射需分别处理。

## 四、影响面

- **后端 getter 调用点**：getLabel 10 + getName 36 + getDesc 7 + getDescription 15 = **68 处**
- **前端属性引用**：.label 183 + .name 119 + .desc 13 + .description 38 = **353 处**（含表单字段名，非全枚举）
- 主要风险：`getDescription()` 同名不同义——新接手的开发者极易拿错文案；字典/下拉/表格列取值需按枚举逐个适配。

## 五、统一方案

### 方案 A（推荐）：统一为 `label`（短标签）+ 保留 `description`（长说明）

- 以 Element Plus / RuoYi 生态惯例 `label` 为唯一短标签名，现有 34 个 label 枚举不动，改动量最小
- `name`（16 个）→ `label`：影响 getter 调用点约 36 处
- `desc`（7 个）→ `label`：影响约 7 处
- `description`（23 个）按语义拆分：
  - purchase 16 个（description 实为短标签）→ `label`
  - production/sales 中"name + description"双字段的保留 description（长说明）
- 总量：约 60+ 处后端改动 + 前端对应适配；可用 IDE 全局重命名 + 编译兜底

### 方案 B：统一为 `name`

- 与 JavaBean/实体惯例更贴合，但需改 34 个 label 枚举（inventory 全模块），改动面更大，不推荐

### 方案 C：暂不重构，只立规范

- 在项目规范文档中规定新枚举一律 `code + label`（+可选 description），存量不动
- 零风险，但存量混乱持续，且 `getDescription()` 同名不同义问题仍在

### 附带建议

- 顺带统一 `code` 类型规范：业务状态用 Integer，字典类用 String，写入规范
- `value` 字段语义收敛：只做"值"，展示文本一律走 label

## 六、工作量估算（方案 A）

| 项 | 量 | 说明 |
|---|---|---|
| 枚举类字段改名 | ~26 个文件 | name→label 16、desc→label 7、description→label 16（purchase）去重后约 26 |
| 后端 getter 调用 | ~60 处 | IDE rename + mvn compile 兜底 |
| 前端引用 | ~200 处 | 需区分枚举属性与表单字段 |
| 回归验证 | 全模块下拉/字典/表格 | 影响所有状态展示 |

预计 1 个专项（约 0.5~1 天），建议作为独立任务排期，不与日常开发混做。

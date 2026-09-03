# Engineering Sample Workbench Source Documents Step 1 Report

> 日期：2026-08-26
> 范围：工程打样工作台"来源单据"展示与查看入口（仅此一件事）

## 一、修改文件

### 后端（2 个）
- `jjx-server/.../sales/domain/entity/SalesOrder.java` — 新增 3 个非表字段：`quotationNo`、`inquiryId`、`inquiryNo`（`@TableField(exist=false)`，仅标量，未塞入 Inquiry/Quotation 整对象）
- `jjx-server/.../sales/service/impl/SampleOrderServiceImpl.java` — `selectById()` 增加 `fillSourceDocNos()` 最小补充；新增 `SalesInquiryMapper` 注入

### 前端（4 个改 + 2 个新增）
- 改 `src/views/engineering/sample-workbench/components/SampleInfoCard.vue` — 新增"来源单据"区 + `viewInquiry`/`viewQuotation` 事件
- 改 `src/views/engineering/sample-workbench/workbench.vue` — 挂载两个共享详情弹窗 + 打开函数
- 改 `src/views/sales/quotation/index.vue` — 内联详情弹窗替换为共享组件（行为不变，提交审核模式保留）
- 改 `src/views/sales/inquiry/index.vue` — 内联详情弹窗替换为共享组件
- 新增 `src/views/sales/quotation/components/QuotationDetailDialog.vue`（从报价单页抽取）
- 新增 `src/views/sales/inquiry/components/InquiryDetailDialog.vue`（从询价单页抽取）

## 二、来源字段从哪里取得

链路：`样品单.quotation_id → sales_quotation → sales_inquiry.converted_quotation_id`

- 工作台原本数据（`GET /sales/sample-order/{orderId}` → SalesOrder）**只有 quotationId**，无单号
- 后端 `fillSourceDocNos()` 在 `selectById()` 返回前做最小补充：
  - `quotationNo` ← `sales_quotation.quotation_no`（按 quotation_id）
  - `inquiryId` / `inquiryNo` ← `sales_inquiry`（按 converted_quotation_id = 该 quotation_id）
- 不存在的来源保持 null → 前端按现有空值风格显示 `-`，不伪造单号
- 无报价单（quotationId 为空）→ 整段显示 `-`

## 三、是否复用现有询价/报价详情组件

是，**抽取复用，未新建第二套**：
- 原询价/报价详情弹窗是各自列表页内联代码，已抽取为共享组件 `InquiryDetailDialog.vue` / `QuotationDetailDialog.vue`
- 原列表页（inquiry/index.vue、quotation/index.vue）改为使用同一组件，行为不变（报价单保留 view / submitReview 两种模式）
- 工作台"查看"直接挂这两个组件 + 现有 `inquiryApi.getInfo` / `quotationApi.getInfo`，弹窗内查看，不跳转离开工作台，关闭后停留原工作台

## 四、工作台展示位置

`SampleInfoCard.vue`"样品单信息"卡片内，**接单状态区之后、图纸/工艺文件区之前**（符合 基本信息 → 统计 → 接单状态 → 来源单据 → 图纸 顺序）

- 紧凑行式：`询价单 INQxxxx  [查看]` / `报价单 QTxxxx  [查看]`，无新增大卡片
- 视觉为辅助上下文（灰色 key + 小号链接按钮），不压过单号/工程执行信息层级
- Round / 冲型组装 / 印刷 / 作业项目 / 工序计划 / BOM 等主体区域未动

## 五、FAST 结果

| 检查 | 结果 |
|---|---|
| `npx vue-tsc --noEmit` | ✅ 通过（EXIT 0） |
| `mvn -o compile`（后端有改动） | ✅ 通过（EXIT 0） |
| `git diff --check` | ✅ 通过（EXIT 0） |

说明：`SalesOrder.java` 原为仓库内 CRLF 孤立文件（其余代码多为 LF），本次已归一化为 LF，`git diff --check` 才通过；该文件 diff 显示 360/341 行变动为换行符归一化噪声，实际内容仅新增 3 个字段。

未新增测试类；未 commit / push。

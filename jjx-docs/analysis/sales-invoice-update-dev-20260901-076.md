# 1261 实施：销售发票补 update 端点（dev-20260901-076）

依据：sales-gap-analysis-20260902.md §4；任务描述（Controller 补 PUT 暴露 update，激活事件）。

## 现状（已核实）

- SalesInvoiceController：GET page/detail、POST create、DELETE delete、POST print-log、GET export
  —— 无 PUT；create/delete 的 @Log 带 bizType="'invoice'" 但**无 bizStatus**
- 隐患：aspect 规则（e33d5f2）——bizType 非空的 @Log 在操作成功且 bizStatus 为空时抛
  IllegalStateException。sales_invoice 当前 0 行（create 从未成功过），一旦有数据，
  create/delete 也会踩雷。本次 PUT 的 @Log 必须带有效 bizStatus，不能照抄 create 的写法。
- SalesInvoiceServiceImpl 已有：`@Override public boolean update(SalesInvoice invoice)`
  （updateById，含 @Event sales.invoice.updated，bizType='sales'）—— 死路径待激活
- 前端 views/sales/invoice/index.vue 为只读列表+详情，无新增/编辑按钮（模板注释"扩展位"），
  api/sales/invoice.ts 无 update 调用方 —— 本次任务后端范围，UI 入口另议

## 改动点（后端，1 个新文件 + 2 处小改）

### 1. 新建状态枚举（供 @Log bizStatus 取值）
com.jjx.sales.enums.SalesInvoiceStatusEnum implements com.jjx.common.enums.BizStatusEnum：
- NORMAL(1, "正常") / CANCELLED(0, "作废")（与 Controller.statusText 语义一致；
  value/label 字段 + getByValue 静态查找，照 18 单据状态枚举的现行模板写）

### 2. SalesInvoiceController 补 PUT
- `@PutMapping("/{invoiceId}")`，入参 @PathVariable Long invoiceId + @RequestBody
  SalesInvoice invoice（前端传全量；service 的 updateById 需 entity 主键匹配——
  若 body 无 invoiceId，则 set 后调用；以 service 现有 update(SalesInvoice) 为准，
  控制器负责保证 invoice.invoiceId 与路径一致）
- @Log(module="销售发票", businessType=BusinessType.UPDATE, bizType="'invoice'",
  bizId="#invoice.invoiceId",
  bizStatus="T(com.jjx.sales.enums.SalesInvoiceStatusEnum).getByValue(#invoice.status)?.label")
  —— 照仓库既有 getByValue+?.label 模式（腿B'），非三元，启动校验可通过
- @SaCheckPermission("sales:order:edit")（与 create 一致）
- 返回 Result<Void>；invoiceService.update 返回 false → Result.error()

### 3. （可选顺手，禁止扩大范围）现有 create/delete 的 @Log 补 bizStatus
不补：避免本次改动扩大风险面，另登记（见下）。如 Codex 判断一行可安全补齐且不动行为，
写入报告即可，不实施。

## 明确不做

- ❌ 前端发票页新增/编辑 UI（页面只读是独立缺口，建议另登记任务：发票新增/编辑入口）
- ❌ 发票状态机/作废流程设计（本次仅"更正开票信息"通道）
- ❌ 不动 create/delete 既有注解；不新建 VO/表/迁移
- ❌ 触碰工作区其他在改文件（并行会话施工中：biz/requirement 模块、
  SalesQuotationAddDTO、BoardTaskController、OperationPreviewDialog、WebConfig 等一律不碰）
- ❌ git commit

## 验证

1. mvn -o clean compile（主代码）；如顺带改到测试相关则 test-compile
2. 手测清单（交付说明给用户）：
   - 发票列表页外调用 PUT /sales/invoice/{id}（body 全量+修改字段）→ 更新成功
   - sys_oper_log 出现 销售发票/UPDATE/biz_status=正常 的行（验证不 500、bizStatus 落库）
   - @Event sales.invoice.updated 触发（若事件配置存在，产生通知/任务）
   - create/delete 未受影响
3. 已知遗留：前端无编辑入口（报告里点名，建议另登记 UI 任务）

## 关联
- sys_task 1261 dev-20260901-076 P1 待开始 → 实施中 → 待审核

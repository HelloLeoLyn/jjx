# TraceTimeline 流水组件 · 设计方案（统一事件流 v3：主表查询 + 前端解析 + 按需加载）

> 版本：v3（2026-08-28 定稿并实施）
> 状态：已实施（后端 TraceServiceImpl 简化 + 前端 TraceTimeline 数据逻辑重构；UI 设定未动）
> 关联任务：dev-20260827-025

## 一、背景与问题（v2 及以前）

原 TraceTimeline 的问题（v1 日志表格 + 审核履历折叠区）：
1. 定位模糊：展示"哪些模块写了日志"，不是业务用户关心的"这张单经历了什么"。
2. 技术痕迹重：traceId、module、businessType、bizStatus 数字映射对业务人员无意义。
3. 数据源分裂：同一件事（审核通过）散在 @Log 行、review_flow 行、附件行，用户要自己拼。
4. 附件归属不清、审核意见不可见、事件多了整页堆叠。

v2 方案（2026-08-28 早）曾做"三表聚合 + 语义/时间窗合并"（/api/trace/events 按
bizType+bizId 查 sys_oper_log + review_flow + sales_quotation_flow + sys_attachment，
后端合并成统一事件流）。实施后发现：后端聚合逻辑重（代码量大、查两张附加表）、
前端依赖后端合并结果，复杂且不易维护。

v3 决定**砍掉聚合**：列表只查主表 sys_oper_log，detail 原文透传，前端解析标志、
点击按需加载内容。

## 二、v3 设计目标

- 查询键：**trace_id**（sys_oper_log 主键查询，挂载点只传 traceId，与现状一致）。
- 列表接口只查主表 + 分页，不查 review_flow / sys_attachment，不做聚合合并。
- 后端解析 actionTitle（业务化操作名）保留；detail/operParam/operUrl 原文透传。
- 前端解析 detail 给标志（hasChanges/hasAttachments/hasReview），驱动表格徽标与
  可点击性；点击行才按需加载审核意见/独立附件。
- UI 设定（表格列、详情区布局、技术详情开关等）保持 v2 定稿不变，仅数据逻辑重构。

## 三、后端设计

### GET /api/trace/events（列表，查询键 trace_id）
```
@RequestParam String traceId, int pageNum, int pageSize
```
- 只查 sys_oper_log（trace_id = 参数），create_time 正序，MyBatis-Plus 数据库分页。
- 返回 UnifiedTraceEventVO：eventId / time / bizStatus / actionTitle / operatorName /
  result / traceId / module / bizType / bizId / businessType / operUrl / operParam / detail。
- actionTitle 沿用关键词语义（SUBMIT/APPROVE/REJECT/CONFIRM/SEND/CANCEL）+ businessType
  映射（创建/修改/删除/导出/导入/审批/转换），不依赖任何附加表。
- detail/operParam/operUrl 原文透传，**标志由前端解析**（后端不解析 detail）。

### GET /api/trace/reviews（按需，审核流水）
```
@RequestParam String bizType, @RequestParam Long bizId
```
- 统一返回 TraceReviewVO：flowId / roundNo / actionCode / actionName / fromStatus /
  toStatus / operatorName / comment / attachmentIds / createTime。
- 分流：bizType=quotation → sales_quotation_flow（selectByQuotationId + 轮次归一化：
  驳回后再次提交轮次+1）；其他 → review_flow（bizType 映射 order→sales_order、
  purchase_order、bom→engineering_bom、film→engineering_film）；无映射返回空。
- 前端在点击审核行时用**该行日志自身的 bizType/bizId** 调用，页面无需额外传参。
- 不影响报价模块：QuotationController.flowRecords 与 QuotationServiceImpl 的
  写入/删除逻辑原样保留。

### 删除（无用的旧逻辑）
- GET /api/trace/{traceId}（legacy 节点树）、GET /api/trace/search（bizId 反查）。
- aggregateEvents 三表聚合/语义+时间窗合并/附件挂载/独立附件成行等全部逻辑。
- UnifiedTraceEventVO 的 changes/attachments/reviewHistory/comment/roundNo/actionCode
  字段；TraceAttachmentVO、TraceReviewHistoryVO 两个类。

## 四、前端设计（TraceTimeline/index.vue）

- 挂载点不变：全部页面只传 traceId（订单/报价/样品/询价/入库/出库/生产/采购）。
- 数据流：/api/trace/events → 每行 enrich：
  - parseDetail(detail) → changes[] / attachments[]（detail JSON 约定
    {"changes":[...],"attachments":[{id,fileName}]}，OperLogDetailBuilder 统一写入）；
  - semanticAction(operUrl, operParam) → actionCode + isReview（前端语义关键词识别）；
  - 表格徽标（N项变更 / 📎）/ 可点击性 = 前端标志驱动。
- 点击行 selectEvent → loadRowContent 按需加载（缓存 per bizType:bizId）：
  - 审核行：/api/trace/reviews 拉整单审核流水，按动作语义 + 时间最近匹配该行，
    回填 comment / roundNo / actionCode（详情区"审核意见/驳回原因/第N轮"展示）；
  - 附件：detail 内附件 id 直接下载，另调 attachmentApi.list(bizType, bizId) 拉
    独立附件（如询价图纸）与行内附件去重合并。
- 分页：pageSize 10/20/50 可调（DB 分页）。
- OperationLogPanel（BizFlowDetail 操作流水 Tab）：改走 /api/trace/events（traceId），
  删除 legacy /api/trace/{traceId} 与 /api/trace/search 调用。

## 五、实施记录

- 后端：TraceServiceImpl 重写（~230 行，原 442 行聚合版删除）、TraceController 增
  /reviews、删 /{traceId} 与 /search、VO 精简 + 新增 TraceReviewVO。
- 前端：TraceTimeline script 重构（模板/样式不动）、OperationLogPanel 改事件流。
- 测试：TraceServiceImplTest 重写 5 用例（traceId 分页查询、detail 透传、review_flow
  映射、报价轮次归一化、无审核模块返回空）全部通过；vue-tsc + check:status-enums 通过。

## 六、已知边界

- 样品/询价/入库/出库/生产：review_flow 无数据（审核过程仅体现在日志状态流转），
  点击审核行只显示变更/附件，无意见区——与 v2 行为一致。
- 历史数据若日志缺 trace_id / bizType / bizId，流水查不到或按需加载为空；
  现状所有 @Log 均带 trace_id 与 bizType+bizId（数字ID）。

# dev-20260828-048 操作弹窗凭证附件收口（送样看不到附件 + 同类 11 处）

## 触发现象（实测）

样品单送样后流水看不到附件：
- 附件表 id=5「样品送样登记.png」biz_type=sample_order / biz_id=1 / trace_id=**NULL** / 17:59:46
- 对应日志 sys_oper_log id=20（/sales/sample-order/send-sample/1，17:59:53）detail=**NULL**

对照报价单能看到附件，是因为它走 `@Log(detail = "#attachmentIds")`，把附件 id 直接写进
detail，不依赖 bizType/traceId。

## 根因（三层，前两层是主因）

1. **前端注册表丢了 attachmentIds**：
   `jjx-web/src/components/OperationPreviewDialog/registry.ts` 里 `sample.sendSample`(:272)
   写了 `evidence: true`（弹窗允许上传凭证），但 `api` 回调没接收/透传 attachmentIds：
   ```ts
   api: ({ bizId, values }) => sampleOrderApi.sendSample(bizId, values.trackingNo || '')
   ```
   正确写法见同文件 `sample.rejectReview`(:252)：
   ```ts
   api: ({ bizId, values, attachmentIds }) =>
     sampleOrderApi.rejectReview(bizId, values.remark,
       attachmentIds?.length ? attachmentIds.join(',') : undefined)
   ```
2. **后端接口没有 attachmentIds 参数、注解没有 detail**：
   `SampleOrderController.java:154` sendSample 入参只有 trackingNo，`@Log` 无 detail。
   正确写法见同文件 `submit-request`(:107)：`@RequestParam(required=false) String attachmentIds`
   + `@Log(..., detail = "#attachmentIds")`。
3. **bizType 两套并存 + 附件 trace_id 为空**（次要，导致兜底路径也失效）：
   页面上传/查列表用 `sample_order`（sample-order/index.vue:1506、:1560，registry bizType 也是
   sample_order），但 AttachmentPanel / AttachmentUploader 用 `sample`（index.vue:468、:577），
   日志 biz_type 又是 `sample`；附件表 id=2/3/4/5 的 trace_id 全为 NULL。

## 全量清单（已扫，registry.ts 中 evidence:true 但 api 不传 attachmentIds = 11 处）

| 模块 | key | 后端现状 |
|---|---|---|
| 样品单 | sample.sendSample | SampleOrderController:154 无 attachmentIds 参数、无 detail |
| 样品单 | sample.confirm | 同上（confirm 方法） |
| 样品单 | sample.rejectSample | 需核实对应后端方法 |
| 采购单 | purchase.approve | PurchaseOrderController:154 无 attachmentIds、无 detail |
| 采购单 | purchase.reject | 走同一个 approve 接口（dto.approved 判分支），需核实 |
| 采购单 | purchase.cancel | PurchaseOrderController:106 无 attachmentIds、无 detail |
| 生产工单 | production.approve | ProductionOrderController 无 attachmentIds、无 detail |
| 生产工单 | production.reject | 同上 |
| 生产工单 | production.start | :107 startOrder 无 attachmentIds、无 detail |
| 生产工单 | production.complete | :125 completeOrder 无 attachmentIds、无 detail |
| 生产工单 | production.cancel | :142 cancelOrder 无 attachmentIds、无 detail |

已正确的参照组（不要改）：quotation.* 全部 7 处、sample.approve、sample.rejectReview、
sample.submitRequest。

## 实施要求

### 1. 前端 registry.ts

把上表 11 处的 `api` 回调统一改成接收并透传 attachmentIds，写法与 `sample.rejectReview`
完全一致（`attachmentIds?.length ? attachmentIds.join(',') : undefined`）。
不要改 evidence 标记，不要改 fromStatus/toStatus/events。

### 2. 对应 api 方法签名

`jjx-web/src/api/**` 里这些方法（sampleOrderApi.sendSample/confirm/... 、采购、生产对应方法）
补一个可选的 attachmentIds 参数并作为 query 参数传给后端，与已有的 rejectReview 保持同一风格。

### 3. 后端接口 + 注解

对上表涉及的后端方法：
- 增加 `@RequestParam(required = false) String attachmentIds`（方法体不使用该参数，仅供
  `@Log` SpEL 取值——与 submit-request 现有做法一致，注意加注释说明用途，避免被当成无用参数删掉）；
- `@Log` 增加 `detail = "#attachmentIds"`，其余属性（module/businessType/bizType/bizId/bizStatus）
  一律不动；
- 若某动作是复用同一个接口按 DTO 分支（如采购 approve/reject 共用 `/approve`），
  则在该接口上加一次即可，并在 registry 两个 key 上都透传。

### 4. bizType 统一（样品单）

样品单附件的 bizType 统一为 **sample_order**（库中已有数据是这个值，改动最小）：
- `views/sales/sample-order/index.vue:468`、`:577` 的 `biz-type="sample"` 改为 `sample_order`；
- 检查 TraceTimeline 行点击拉附件的 bizType 映射，确保 `sample`（日志里的 bizType）能映射到
  附件的 `sample_order`，或统一两端取值；
- **不要**批量改库里已有附件数据，只改代码取值口径。

## 验收（用户自行验证）

1. 送样登记时上传一张凭证 → 新日志 detail 形如
   `{"attachments":[{"id":6,"fileName":"xxx.png"}]}`，流水节点能看到附件。
2. 客户确认 / 退回修改 / 采购审核 / 采购作废 / 生产审核 / 开工 / 完工 / 作废 同样能看到。
3. 不上传凭证时 detail 为 NULL（不产生空 attachments）。
4. 样品单详情页「图纸 / 工艺文件」面板仍能正常列出附件（bizType 改动后不掉列表）。

## 硬约束

- 不改 quotation 模块（已正确），不改 evidence 之外的注册表字段。
- 编译：`cd jjx-server && mvn -o clean test-compile`、`cd jjx-web && npm run validate` 必须通过；
  **禁止跑后端全量测试套件**，只跑受影响的测试类（若有）。
- 不要 git commit。不要触碰工作区中用户正在改的销售/报价重构相关文件
  （OrderController.java、OrderServiceImpl.java、api/sales/order.ts、OrderForm.vue、
  quotation/ 下新增组件、TraceTimeline/index.vue、main.ts、components.d.ts、plugins/、
  page-skeleton/、index.back）——若与它们冲突，先停下并报告，不要自行合并。

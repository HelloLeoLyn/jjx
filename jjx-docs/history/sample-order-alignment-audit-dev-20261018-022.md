# 样品单对齐报价 P3：新建产品残留与复制重复核查

任务码：dev-20261018-022
日期：2026-10-18
执行者：dahuang
性质：只读核查（未改业务代码、未改库、未调写接口、未重启服务）
基线：HEAD bd601244；工作区含 Codex dev-20261018-016 的未提交 9 行改动（位于转量产校验 ~L3760，不在本卡审计方法内），本报告结论不依赖该草稿。

## 0. 结论速览

1）核查项 A「新建产品旧数据残留」：**不存在**（主路径）。另有 1 处相邻边界（多产品历史单编辑早退不重置表单），非本卡目标，仅记录。
2）核查项 A「保存归属混用」：**不存在**（界面路径）。但存在「整单级证据未回填产品 + 后端缺 productId/productCode 一致性校验」的实现缺口。
3）核查项 B「复制重复落库」：**不存在**（代码层）；运行时样本为 0，单次复制的真实落库行为属**证据不足**。

## 1. 范围与方法

1）只读代码：
- jjx-web/src/views/sales/sample-order/components/SampleForm.vue
- jjx-web/src/components/ProductCodeGenDialog/index.vue
- jjx-web/src/views/sales/sample-order/index.vue
- jjx-web/src/composables/useAttachmentSession.ts
- jjx-server/src/main/java/com/jjx/sales/service/impl/SampleOrderServiceImpl.java
- jjx-server/src/main/java/com/jjx/sales/controller/SampleOrderController.java
- jjx-server/src/main/java/com/jjx/product/service/impl/ProductServiceImpl.java
2）只读查库：sales_order、sales_sample_order、sales_order_product、sys_attachment_ref（root 只读查询）。
3）对照：报价 dev-20261018-003（新建产品旧数据残留/保存归属）、dev-20261017-012（复制重复落库）。

## 2. 核查项 A：新建产品残留 / 保存归属混用

### 2.1 结论

- 旧数据残留：**不存在**。新建产品弹窗每次打开重置；确认后整体替换产品对象；客户/来源变化与新建初始化都会清空产品选择。
- 保存归属混用：界面路径**不存在**（样品单一成品 + 附件默认整单级，不存在跨产品归属）；但后端未做一致性校验，且「整单级按唯一产品回填」的注释与实现不符。
- 相邻边界（记录、不判定为本卡缺陷）：多产品历史单编辑时 fillFromOrder 早退，不重置表单产品。

### 2.2 触发步骤（复核用）

1）新建样品单 → 选客户 → 点「新建产品」→ 四选生成编码 → 确定 → 保存：编码/名称/规格参数均来自本次弹窗。
2）打开「新建产品」→ 取消 → 再次打开：弹窗字段为空（无上次参数）。
3）选客户 A → 新建产品 → 改选客户 B（或改来源类型）：产品选择与「新产品」态被清空。
4）新建产品 A 后点「重选」→ 选择已有产品 B：行内只保留 B 的编码/名称/单位，无 A 的编码参数残留。

### 2.3 代码证据

1）弹窗打开即重置：ProductCodeGenDialog/index.vue:101-113 —— watch(modelValue) 中 codeState 四项全部取 initState 或空串，codeParams=null，nameTouched=false，productName 取 initProductName 或空。SampleForm 未传 initState/initProductName（SampleForm.vue:199-206），即每次全空。
2）确认后整体替换产品：SampleForm.vue:625-641 —— form.product 赋值为全新对象（productId: undefined，productCode/名称/结构参数均为本次值），旧 ID/名称/规格不保留。
3）客户/来源变化清空：SampleForm.vue:505-526 —— onCustomerChange/onSourceTypeChange 调 resetProductSelection，后者置 productIsNew=false、form.product=emptyProduct()、清空 productOptions/报价错误。
4）新建初始化重置：SampleForm.vue:689-696 —— initForCreate → resetForm → resetProductSelection。
5）编辑态不提供新建产品：SampleForm.vue:471-474 —— canCreateProduct 要求 !props.editId，编辑时不会走新建建档分支。
6）后端建档幂等：SampleOrderServiceImpl.java:853 调 productService.ensureDraftProduct；ProductServiceImpl.ensureDraftProductWithSpec 先按 product_code 查存在（存在则复用/补客户/补 spec_json），不存在才 insert，不会重复建档。

### 2.4 为什么同源代码不会触发报价 003 的残留（反例）

报价 003 的根因是两点：弹窗保留上次参数、确认时不清旧 ID/名称/规格。样品单：
- 弹窗在 open 时重置（证据 1），不存在上次参数带入；
- 确认时是整体替换对象（证据 2），不存在旧 ID/名称残留；
- 新建态下产品下拉被隐藏（showProductSelect = !productIsNew，SampleForm.vue:473-475 附近），要改选已有产品必须先「重选」，而重选会清空对象（证据 3）。
因此旧值没有进入保存 payload 的路径。

### 2.5 保存归属核查

1）前端 payload：SampleForm.vue:392-405 —— attachPayload 的 productId 只从 ownershipKey「product:<id>」解析；productCode 取待保存条目的 productCode。
2）样品单附件默认整单级：SampleForm.vue:187-195 的 AttachmentManager 未传 ownerships；useAttachmentSession.ts:311-313 ownerships 恒含 order；AttachmentUpload.vue:83 ownershipValue 默认 orderOwnership()。即样品单证据上传归属为「整单」，productId/productCode 通常为 null。
3）后端落定：SampleOrderServiceImpl.java:614-666 bindSampleAttachments —— attachmentId/documentType 必填；productId 为空且 productCode 非空时按「已保存明细编码→产品ID」回填；两者都空则保持 NULL；**没有** productId 与 productCode 的一致性校验（报价 003 已加，样品单未加）。
4）方法注释写「整单级不带产品时由后端按来源唯一产品回填」，但代码没有该回填分支（只按 productCode 反查），注释与实现不一致。
5）库内证据：sys_attachment_ref 中 source_type='sample' 仅 1 行（id=11），source_id/product_id/product_code/document_type 全为 NULL，是未绑定的草稿行；没有已绑定的样品证据行可用于运行时验证。

### 2.6 影响面

- 样品单证据以整单级落库（product_id/product_code 为 NULL）：对「一单一成品」影响有限，但按产品检索附件会查不到，且与注释口径不符。
- 接口层若传入不一致的 productId/productCode，后端会原样落库；界面路径不会产生该组合（见 2.5-1）。

### 2.7 建议（不在本卡实施）

1）在 bindSampleAttachments 增加一致性校验：productId 非空时按 productId 取产品编码，与 productCode 不一致则抛业务异常（整单回滚），对齐报价 003。
2）实现「整单级唯一产品回填」或删掉该注释口径，避免文档与实现不符。

### 2.8 相邻边界（非本卡目标）

fillFromOrder 在 products.length > 1 时先提示错误再 return（SampleForm.vue:667-671），早于 resetProductSelection；同一弹窗实例上一次的 form.product 会残留。updateSampleOrder 以单产品全量替换明细（validateSingleSampleProduct 通过），理论上可把多产品历史单改成单产品。当前库内只有 1 张单产品样品单，无法用真实数据复核该边界；如需处理建议另立卡。

## 3. 核查项 B：复制重复落库

### 3.1 结论

- 明细重复插入：**不存在**。
- 附件重复/错挂：**不存在**（复制完全不复制附件）。
- 工艺资料重复：**不存在**（复制不写 sales_sample_process / sales_sample_bom）。
- 运行时证据：库内复制单 0 张，属证据不足；判定依据为代码路径。

### 3.2 触发步骤（复核用）

样品单列表 → 行操作「复制」→ 确认 → 提示复制成功，列表出现一张新的 CREATED 样品单。

### 3.3 代码证据

1）后端唯一入口：SampleOrderController.java:129-132 POST /sales/sample-order/copy/{orderId} → service.copySampleOrder。
2）copySampleOrder（SampleOrderServiceImpl.java:359-497）：
- orderMapper.insert(copy) 单次（L405）；
- upsertSampleOrderProfile(copy) 单次（L406），内部 selectByOrderId 判空后 insert/update（不会插两条 profile）；
- 产品明细 batchAdd(dtos) 单次（L430），dtos 逐条带新 orderId；
- @Transactional(rollbackFor = Exception.class)，任一失败整体回滚。
3）前端：index.vue:1297-1316 handleCopySample 只调一次 sampleOrderApi.copy(orderId)，成功后 getList()；**没有**「清空 id 再走新增保存」的第二段逻辑。
4）不复制附件：copySampleOrder 全段没有 attachmentRef/sys_attachment 写入；新单无证据引用，不会错挂源单。
5）不复制工艺资料：无 sample_process/sample_bom 写入；新单 sample_round=1、工艺为空档。
6）单号：redisSequenceService.generateBusinessNumberByType("sample_order","SP","yyMMdd",3)（L376 附近），调用一次；order_no 唯一索引兜底。

### 3.4 为什么同源代码不会触发报价 012 的重复落库（反例）

报价 012 的根因是「复制接口先预插一张表头 + 前端又清 quotationId 走新增保存」，一次操作落两张。样品单复制接口返回的是**已插入的完整新单**（copy 对象），前端只刷新列表、不调 create，因此不存在第二条插入路径；附件/工艺资料不在复制范围，也不存在重复插入或错挂。

### 3.5 影响面与建议

- 影响面：复制单为空档（无附件、无工艺资料、无轮次快照），若业务期望「复制即可继续打样的完整草稿」，属功能缺失而非重复缺陷。
- 建议：1）复制按钮加 loading/幂等（防误双击产生两张，非报价 012 同款）；2）如需复制附件/工艺资料，另立卡先定口径（引用原附件还是复制文件）。

## 4. 只读 SQL 与结果

1）样品证据引用分布：
SELECT source_type, COUNT(*) total, SUM(product_id IS NULL) null_pid, SUM(product_code IS NULL OR product_code='') null_pcode
FROM sys_attachment_ref WHERE source_type='sample' AND deleted=0 GROUP BY source_type;
→ sample｜1｜1｜1（仅未绑定草稿行 id=11，source_id 亦为 NULL）

2）复制单样本：
SELECT p.order_id,o.order_no,p.source_type,p.source_no,p.sample_status,...
FROM sales_sample_order p JOIN sales_order o ON o.order_id=p.order_id
WHERE p.source_type='SAMPLE_ORDER' OR o.remark LIKE '复制自样品单%';
→ 0 行

3）现有样品单：
SELECT order_id,order_no,order_type FROM sales_order;
→ 1｜SP261018001｜2（SAMPLE）；profile source_type=QUOTATION/source_no=QT261018001；明细 1 行（YL001MEOO×5）

## 5. 未验证 / 证据不足

1）B 的「单次复制只落一张」无真实复制样本（0 行），本次未执行写操作，属证据不足；结论为代码层判定。
2）A 的界面往返（多次开关弹窗、改客户/来源）未实机点验，结论基于代码分支与重置逻辑。
3）A 的归属回填需真实上传+保存后查 sys_attachment_ref；当前库内无已绑定 sample 证据行，未验证。
4）多产品历史单边界（2.8）无真实数据，未验证。
5）本报告读取的是工作区当前代码；Codex dev-20261018-016 的未提交改动仅在转量产校验段（~L3760），不影响本报告涉及的 copySampleOrder/createSample/updateSampleOrder/bindSampleAttachments 结论；其提交后无需重核本卡结论。

## 6. 后续建议

1）另立卡：样品单附件归属对齐报价 003（一致性校验 + 唯一产品回填口径）。
2）另立卡：复制样品单是否应携带附件/工艺资料（业务口径）。
3）相邻边界（多产品历史单编辑残留）如需处理，另行评估。

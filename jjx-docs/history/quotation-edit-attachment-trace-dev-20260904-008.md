# 报价修改流水挂附件：明细行产品文件库上传归属该次修改操作行（dev-20260904-008）

状态：⏳待实施（2026-09-04，实证 traceId=c31dd9d078524263 / QT2609040002）

## 现象（实测证据）

- 报价 QT2609040002（quotation_id=1）修改流水行（sys_oper_log id=6，11:35:25）：
  detail={"changes":["税率:0.00→13.00","明细[JST002MEOO]数量:5→2","明细[JST002MEOO]单价:100.00→150.00"],"attachments":[]}
- 附件 id=3（"ChatGPT Image….png"，类别 客供稿，11:35:17 上传，即修改保存前 8 秒）：
  biz_type=product、biz_id=2（JST002MEOO）、trace_id=NULL → 挂在产品文件库，不上任何操作行。
- 上传 UI = 报价修改弹窗→明细行📎→产品文件库（views/sales/quotation/components/QuotationFormDialog.vue:296-304，
  ProductFileLibrary.vue，按 productCode 绑定）；上传走 axios 封装 attachmentApi.uploadProductFile
  （api/system/attachment.ts:53），文件确实落库成功。
- 报价修改请求体（oper_param 全文）无 attachmentIds；后端 @Log
  （QuotationController.java:88-90）detail=#result.data.detailMessage；
  QuotationServiceImpl.updateQuotation 约 :423 detailMessage=changeRecorder.toDetailJson(changes) → attachments 恒 []。
- 与询价修改同款缺口（询价已修 dev-20260903-116/commit d33f732，本单照其方案复刻）。

## 产品口径（用户拍板，2026-09-04）

报价修改会话内经产品文件库上传的资料，归属到"该次报价修改"流水行（与询价口径一致）。

## 改动清单

### 后端 jjx-server
1. `sales/domain/entity/SalesQuotation.java`：加 transient 字段
   `@TableField(exist = false) private List<Long> attachmentIds;`（实体现用作 edit @RequestBody，
   已有 @TableField 用法，风格一致；非表列）。
2. `sales/service/impl/QuotationServiceImpl.java`：注入
   `com.jjx.system.mapper.SysAttachmentMapper`（@RequiredArgsConstructor 加 final 字段）；
   updateQuotation 组装 detailMessage 处（现 :423 附近，先 snapshot 旧单再 diff 的既有顺序不许动）：
   attachmentIds 非空 → attachmentMapper.selectByIds(ids) 取 {id,fileName}，
   `OperLogDetailBuilder.build(changes, attList)`（system/utils/OperLogDetailBuilder.java:25 已有）；
   为空 → 维持 changeRecorder.toDetailJson(changes)。
3. @Log 注解、OperLogAspect、其它实体一律不动（action 铺码已完成，勿重排 88-90 多行注解）。

### 前端 jjx-web
4. `components/product/ProductFileLibrary.vue`：defineEmits 加 `success:[id:number]`；
   doUpload 成功分支（:189-193）ElMessage.success 后 `emit('success', Number(res.data))`。
   组件是纯展示+上传，加性改动；其它挂载点（ProductDetail.vue:271）不受影响。
5. `views/sales/quotation/components/QuotationFormDialog.vue`：
   - 收集本次会话上传 id：`const uploadedAttachmentIds = ref<number[]>([])`，
     ProductFileLibrary 加 `@success="onFileLibUpload"`（去重 push）；
   - 弹窗打开/关闭处（handleClose 等）重置为 []，防跨会话串单；
   - 提交链路：找到本组件 emit('submit')/父页 quotation/index.vue 调 quotationApi edit 的地方，
     把 `attachmentIds` 写进提交对象（formData.attachmentIds = [...uploadedAttachmentIds.value]，
     仅修改分支有实际效果；新增分支后端忽略，不动 create 的 flushPending 链路）。
6. 若前端类型定义（quotation api 的 data 类型）标注了字段，同步加 `attachmentIds?: number[]`。

## 明确不做

- 不做产品资料页（ProductDetail）文件库上传的流水归属（另一语境，另行讨论）。
- 不新建 DTO/VO（实体 transient 字段足够）；不改 trace_id 回填机制；不动 ProductFileLibrary 的
  展示/删除逻辑；删除边缘（上传后又删）由后端 selectByIds 天然过滤，无需前端处理。
- 不登记额外 sys_task 子项（本任务即 dev-20260904-008）。

## 验证

Codex：`mvn -o clean test-compile` + `npx vue-tsc --noEmit`（只报本次相关错误；仓库既有无关报错
列出不修）；不 git commit；工作区其它脏文件（dashboard/PrintProcessPanel/SampleOrder*/ISampleOrderService/
LogBizStatusValidator 等并行会话产物）禁止触碰。
人工验收（用户）：修改报价单→明细📎→产品文件库上传一张图→保存→该修改流水行出现该附件；
纯改字段不传附件 → attachments 仍为 []；老数据不受影响。

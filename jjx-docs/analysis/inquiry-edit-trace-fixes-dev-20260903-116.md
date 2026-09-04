# 询价修改流水修复：单价精度误报 + 修改上传附件归属操作行（dev-20260903-116）

状态：⏳待实施（分析来源 traceId=b606cc62f7084019，2026-09-04；用户裁定不登记 sys_task，直接做）

## 现象（实测证据）

sys_oper_log 同一 trace 两条行（inquiry / biz_id=3）：
- 行 136（新增 09:56:20）：detail.attachments=[{id:26,…}] ✓
- 行 137（修改 09:56:56）：detail.changes=["预估数量:2→3","预估单价:100.00→100"]，attachments=[]
- sys_attachment 行 27（09:56:54 修改弹窗内上传）trace_id=NULL → 只挂在单据级，没进修改流水行

## 修复 A：BigDecimal 精度误报为变更（1 个类）

根因：OperLogChangeRecorder.diff()（OperLogChangeRecorder.java:75-79）用 Objects.equals；
BigDecimal.equals 对 scale 敏感（DB decimal 读出 100.00 scale=2 ≠ JSON 100 scale=0）→ 误记"变更"。
同类已正确用法：diffDecimal()（:82-88，compareTo 数值比较），QuotationServiceImpl:443-445/473/483-485
已用它；误用点 = InquiryServiceImpl.java:296（预估单价）+ OrderServiceImpl.java:260/264/265/266
（汇率/总金额/税率/折扣率）。

修复（根因级，一处生效）：
- OperLogChangeRecorder.diff() 开头加分支：oldValue 与 newValue 均为 BigDecimal 时改用
  compareTo 判等（null 语义对齐现有 diffDecimal：两边都 null=相等，一边 null=不等），
  不等才 add。diffDecimal() 保留不动（已有调用方）。
- 不改各调用点、不改 display 文案。

## 修复 B：修改时上传的附件归属到"该次修改"流水行（产品口径已定：要挂）

现状链路（为何新增可见、修改不可见）：
- 新增：前端保存成功后才 flushPending(newId, newTraceId) 上传（views/sales/inquiry/index.vue:1008-1013），
  后端 SysAttachmentServiceImpl.attachToLatestOperLog 按 trace_id 回填到最新流水行（=新增行）。
- 修改：弹窗内 bizId 已存在 → AttachmentUploader 立即上传（components/AttachmentUploader/index.vue:86-112），
  此时"修改"流水行尚未生成，回填机制挂不到；且修改保存请求体无 attachmentIds（oper_param 实证），
  后端 @Log detail="#result.data.detailMessage" 只带 changes、attachments 恒 []。
  新增/修改共用同一弹窗（index.vue AttachmentUploader :biz-id="form.inquiryId"）。

方案（沿用既有模式，不加新通道）：
后端：
1. SalesInquiryEditDTO（sales/domain/dto/SalesInquiryEditDTO.java）新增字段：
   `private List<Long> attachmentIds;`（本次修改会话内上传的附件 id，可空）。
2. InquiryServiceImpl 注入 com.jjx.system.mapper.SysAttachmentMapper（@RequiredArgsConstructor 加
   final 字段；该类已有 LogSaveService/changeRecorder 等 11 个注入，风格一致）。
3. updateInquiry()（InquiryServiceImpl.java:253-257）：算完 changes 后，
   attachmentIds 非空 → attachmentMapper.selectByIds(ids) 取 {id,fileName}，
   用 OperLogDetailBuilder.build(changes, attList)（system/utils/OperLogDetailBuilder.java:25，
   已存在 public build(List,List)）合成 {"changes":[...],"attachments":[{id,fileName}]}；
   为空则维持 changeRecorder.toDetailJson(changes) 现状。写入 vo.detailMessage。
   注意 build diff 必须在 updateById 之前基于 oldInquiry 快照（现代码已如此，勿动顺序）。
4. InquiryController.java:88 的 @Log 不用改（detail=#result.data.detailMessage 原样透传合并后 JSON）。
   不改 @Log 注解、不新增 attachmentIds 注解属性、不碰 OperLogAspect。

前端（views/sales/inquiry/index.vue，编辑弹窗与新增共用）：
5. 模板 AttachmentUploader（index.vue:392-413）加 @success="onUploadSuccess"（组件立即上传模式
   已 emit success(id)，见 AttachmentUploader/index.vue:62-63/105，无需改组件）。
6. script：新增 `const uploadedAttachmentIds = ref<number[]>([])`；
   onUploadSuccess(id) push 去重；打开编辑弹窗时与 handleClose/clearPending 处重置为 []。
7. submitForm() 修改分支（index.vue:1004-1006）：`await inquiryApi.edit({ ...form, attachmentIds: uploadedAttachmentIds.value } as any)`；
   新增分支不动（保持 flushPending 链路）。
8. api/sales/inquiry.ts InquiryBase 接口加 `attachmentIds?: number[]`（第 18 行 interface，避免类型报错）。
   SalesInquiryEditDTO 有 @JsonIgnoreProperties(ignoreUnknown=true)，多余字段无碍。

## 明确不做

- 修改弹窗内"上传后又从列表移除"的服务器残留文件删除语义：维持现状（附件已落单据级），不做。
- 不动 create 路径、不动 AttachmentPanel、不动 trace_id 回填机制、不动 OperLogAspect/@Log 注解。
- 不登记 sys_task（用户裁定：2 不用登记）。
- 不改 Quote（已正确用 diffDecimal）。

## 验证

Codex 侧：
1. jjx-server：`mvn -o clean test-compile`；
   若 InquiryConversionOperLogTest 因 InquiryServiceImpl 构造器变化编译失败，重 stub 该测试并跑
   `mvn -o clean test -Dtest=InquiryConversionOperLogTest -DfailIfNoTests=false`。
2. jjx-web：`npx vue-tsc --noEmit`，只报本次改动相关错误；仓库其它既有报错不修、列出即可。
3. 不要 git commit；工作区其它脏文件（print.vue×2、migrations/53/54、dashboard、QuotationFlowDialog
   删除等并行会话产物）禁止触碰。

人工验收（交给用户，前端 vite 热更新 / 后端需重打包后重启）：
- 修改询价时上传一个文件 → 保存 → 该修改流水行出现该附件 📎，detail 同时含 changes 与 attachments；
- 纯改字段不传附件 → attachments 仍为 []，行不被误标；
- 单价同数值不同精度（如 DB 100.00 与提交 100）→ 不再出现"预估单价:100.00→100"变更行；
- 真实改价（100→150）→ 正常记录一行。

# 1286 横向核查：带证据操作的附件归属链路断点清单（dev-20260902-090）

核查日期：2026-09-02。背景：1284（报价审核附件不进流水）根因=原生 el-upload(action 模式)
XHR 默认 Accept:*/* 触发后端内容协商返回 YAML，onUploadSuccess 拿不到 code/data。
openclaw 已修 OperationPreviewDialog(加 Accept 头) + 后端 WebConfig 清非 JSON 转换器（均未提交、
后端未重打包）。本清单核查同款问题蔓延面与五段链路。

## 五段链路定义
前端 registry 操作 → 前端 api 函数 → 后端 controller 参数 → @Log detail 落库 → flow/日志可见

## A. 上传通道层（新增认知，1284 根因同类）

全站 el-upload 直传点共 18 处，原生 :action（XHR 直传、无 Accept:application/json）仅 3 处：

| 位置 | 状态 | 结论 |
|---|---|---|
| components/OperationPreviewDialog/index.vue | openclaw 已加 Accept:'application/json'（工作区未提交） | ✅已修，待提交 |
| components/AttachmentUploadDialog/index.vue（附件管理弹窗） | 同款未加 Accept 头 | ⚠️当前后端未重打包仍会触发 YAML；后端 WebConfig 清转换器重打包后应被动修复，需实测 |
| views/sales/quotation/components/QuotationFlowDialog.vue | 全站无任何页面 import（死代码） | 无实际影响，建议清理 |

其余 15 处 el-upload 均用 :http-request 自定义 axios（与项目 request.ts 同通道，安全），或无直传。

重要：后端 WebConfig 修复（清 AbstractJackson2HttpMessageConverter）未提交、jar 未重打包，
当前运行环境仍可能对 Accept:*/* 返回 YAML——任何遗留 action 直传点现在实测仍会复现。

## B. registry → api 层（20 个证据操作）

- registry.ts：evidence:true 共 20 个，api 回调全部传 attachmentIds（20/20 join/length）✅
- api 函数层：quotation(7)/sample(3+)/purchase(cancel/approve)/production(start/complete 等)
  均收 attachmentIds 并放入 params ✅（2026-08-29 记录的 sample×3/purchase×3/production×5
  registry 缺口当前已不存在）

## C. 后端 controller / 服务层（B 类范围）

| 模块 | controller 收 attachmentIds | @Log detail=#attachmentIds | flow 落库 | 结论 |
|---|---|---|---|---|
| 报价 | 21 处（send/submit-review/review/status/confirm/reject/modify/copy…） | ✅ | sales_quotation_flow.recordFlow 透传 ✅ | 通（1284 修复后） |
| 样品 | 8 端点（申请/接单/驳回/寄样/确认/拒收/重申请…） | ✅ | 无独立 flow（样品流转走日志+轮次表） | 通 |
| 采购 | cancel、review(approve) 2 处 | ✅ | review_flow.record 透传 ✅ | 通 |
| 生产 | start/complete 等（注释明确仅供 @Log 取值） | ✅ | 无 flow 表（日志即流水，按设计） | 通 |
| 销售订单（C 类） | 审核走 ReviewDialog → ReviewDTO.attachments(body) | ❌ @Log detail 为固定文案"订单状态：审核中→已审核" | review_flow.record 透传 ✅（OrderStatusServiceImpl:171-172） | ⚠️见断点1 |

## 断点清单

1. 【断点C1】销售订单"审核通过/驳回"的操作流水行不显示附件
   - review_flow 流转记录有附件（OrderStatusServiceImpl:171 record 带 reviewDTO.getAttachments）
   - 但 sys_oper_log 该行 detail 是固定文案（OrderStatusController.java:66-67 approveOrder /
     :83-84 rejectOrder），@Log 未接 attachmentIds → TraceTimeline 操作流水行看不到确认书
   - 修复方向：approve/reject 的 @Log detail 改为动态带附件（controller 增加 attachmentIds 参数
     镜像，前端 api 同步传 query；或按 quotation 模式 detail="#attachmentIds"），review_flow 已通不用动
2. 【断点B1】AttachmentUploadDialog 原生直传通道——后端重打包后实测；仍坏则 uploadHeaders 加
   Accept:'application/json'（与 OperationPreviewDialog 同款一行）
3. 【清理项】QuotationFlowDialog 死代码（无引用），其内原生 el-upload 同款隐患，建议删除或标注
4. 【脏数据】历史孤儿附件（sys_attachment 中 biz 存在但无 flow/log 归属的行，如早期 id2-11 等）
   用户自理，本次不动

## 验证口径
- C1 修复后：订单审核通过传确认书 → review_flow.attachment_ids 有值（已有）+ sys_oper_log 该行
  detail 含 attachments → 流水行显示附件
- B1：附件管理弹窗上传不弹"上传响应异常"
- 全站回归：报价/样品/采购各走一条带证据操作

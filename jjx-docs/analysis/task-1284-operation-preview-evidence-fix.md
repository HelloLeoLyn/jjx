# 1284 修复：操作弹窗证据上传时序导致附件不进流水（dev-20260902-089）

## 一句话现象

带证据的操作（审核通过/驳回/发送/客户确认等）里上传的附件，操作成功后没有挂到该操作的
流水/流转记录上。文件本体上传成功（sys_attachment 有行），丢的只是"归属"。

实证（trace 2a6e461dfa844b52，报价单 1）：
- sys_oper_log id14/25（审批 已审核）detail=NULL
- sales_quotation_flow flow2/7 attachment_ids=NULL，但 remark="审核意见" 有值
  → 请求里带了意见、没带附件 ID
- sys_attachment id2/4 上传成功（17:29:28 / 17:32:28，均在对应流水前）
- 对照：询价新增流水 id8 detail={"attachments":[{id:1,...}]} → 同一条
  @Log(detail="#attachmentIds") + 后端回查文件名链路本身是通的

## 根因（前端弹窗，三个嫌疑，本次一并防御）

文件：jjx-web/src/components/OperationPreviewDialog/index.vue
- 证据上传区 el-upload（:84-103），选中即异步上传
- uploadedIds 收集数组（:179），onUploadSuccess push（:256-262）
- confirm()（:271-302）：直接 op.api({..., attachmentIds:[...uploadedIds]})，
  **不校验上传是否完成/失败** → 上传还在飞或失败时点确认，发出空列表
- 弹窗打开（watch modelValue，:305-315）清空 uploadedIds/evidenceFileList 是
  预期行为（新操作=新证据），保留

## 修复要求（只改这一个文件）

1. **上传进行中禁止确认**：跟踪进行中的上传数（el-upload 的 on-change /
   before-upload 进入时 +1，on-success / on-error 落定时 -1）；confirm() 开头
   若 uploadingCount > 0 → ElMessage 提示"文件上传中，请稍候"并 return；
   确认按钮在 uploading 时 disabled/loading。
2. **失败文件不允许带着确认**：记录失败集合（on-error 的文件 uid/name）；
   confirm() 若有失败项 → 提示"存在上传失败的文件，请先移除后重试"并 return。
3. **已选数 = 已成功数校验**：确认前校验证据区"已选文件数"== 已成功上传数，
   不相等即拦截（防"显示已选、实为未传完"）。成功判定以 on-success 收到
   code===200 && data 为准，与现有 onUploadSuccess 判定一致。
4. 保留：打开弹窗（false→true）时清空旧证据状态（新操作新证据语义不变）。

不做：
- 不改 registry.ts（api 已正确传 attachmentIds）
- 不改任何后端文件（aspect/接口已证明可用）
- 不改 TraceTimeline / 任何页面
- 不处理历史孤儿附件（id2/4 属用户脏数据，自理）
- 不引入"孤儿清理"逻辑

## 影响面与回归

该弹窗被全仓库共用，带证据操作共 20 个：
报价（7 操作）、样品单、客户状态、入库、出库、采购订单、生产工单。
- 改动必须对 evidence=false 的操作零影响（confirm 流程原样）
- 无证据时行为完全不变

## 验证（Codex 只做类型检查，手测由用户做）

1. `cd jjx-web && npx vue-tsc --noEmit 2>&1 | grep -E "error TS"` —— 本文件零错误；
   其余文件的错误先 `grep -v "ProcessCard|sample-workbench"` 过滤，剩下的列清单
   报告（可能属用户 WIP，不修）
2. 用户手测清单（写进交付说明）：
   - 报价单审核通过：立刻点确认（文件还在传）→ 应被拦截提示
   - 正常等上传完成再确认 → 成功；流水该行 detail 含 attachments、
     sales_quotation_flow.attachment_ids 有值
   - 上传失败场景：传一个会上传失败的文件 → 确认应被拦截
   - 回归：样品/采购各走一条带证据操作；一条不带证据的操作（如删除类）确认无感
3. 本改动不涉及状态字面量，check:status-enums 不受影响

## 任务信息

- sys_task 1284 / dev-20260902-089，P1
- 关联：sys_task 1286 / dev-20260902-090（横向核查同款问题蔓延面，查完另修）

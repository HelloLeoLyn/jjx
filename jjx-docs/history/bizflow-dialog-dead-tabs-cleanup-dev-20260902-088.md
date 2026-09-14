# 清理询价转报价弹窗无效 Tab（任务 1278 / dev-20260902-088）

2026-09-02 用户判定：操作流水/事件两 tab 为无效展示，登记清理。2026-09-03 处理，采用方案 A。

## 根因（已查实，见任务 description）
- BizFlowDetail 全库唯一使用方 = views/sales/inquiry/index.vue:428-445（未绑 :trace-id）
- Tab3 操作流水：OperationLogPanel 无 traceId 时不请求 → 永远"暂无操作记录"
- Tab4 事件：EventPanel 按 bizId 数字匹配，而后端 @Event( inquiry.converted ) 落 biz_id=询价单号（INQ…）→ 永远匹配不到
- 单据详情/文档流水两 tab 有效（bizId 正常），保留

## 改动（仅 1 文件）
views 不动。jjx-web/src/components/BizFlowDetail/index.vue：
1. 删除 Tab3「操作流水」el-tab-pane（name="ops"，含 OperationLogPanel 用法）
2. 删除 Tab4「事件」el-tab-pane（name="events"，含 EventPanel 用法）
3. 清理 import：OperationLogPanel、EventPanel
4. traceId/bizType/bizId props 保留（文档流水 AttachmentPanel 仍用，勿删）

## 禁止
- 不删除 OperationLogPanel.vue / EventPanel.vue 文件（可能被他处引用，先 grep 确认，有引用就只摘 BizFlowDetail 里的用法）
- 不动 InquiryDetailDialog、不动转报价流程/接口、不动其他任何文件
- 不 git commit

## 验证
1. grep 确认 OperationLogPanel/EventPanel 在 BizFlowDetail 外是否有引用（决定文件去留，文件默认保留）
2. npx vue-tsc --noEmit：BizFlowDetail/index.vue 无 error TS（其他文件报错属并行 WIP）
3. 用户侧：询价转报价弹窗应只剩 单据详情/文档流水 两个 tab

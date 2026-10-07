# 采购付款审批人由登录身份获取

任务：dev-20261007-016；执行者：Codex；日期：2026-10-07。

## 背景

付款审批弹窗原来手填 approverName 并校验必填；单笔和批量接口直接转发姓名，服务保存客户端值。用户要求修复。

## 改动

- 审批人只读展示当前登录用户 realName，无姓名时显示账号名；采用与现有 SecurityUtils.getDisplayName 相同的姓名/账号口径。审批意见、通过和驳回行为保留。
- 单笔及批量前端 API 均移除审批人字段，仅提交审批结果与意见。
- 服务方法移除姓名入参，统一通过 SecurityUtils.getUserId/getDisplayName 获取可信登录身份；身份缺失或显示名为空时拒绝写入。
- 单笔接口不再要求 approverName；批量接口不读取姓名。旧客户端额外传入姓名也不会被用于审批记录。
- 未更改审批枚举、数据库结构或历史审批数据，未新增人员字段。没有服务生命周期操作。其他会话的 EventBridgePayloadContractTest 删除和 BomDetailTable 修改未纳入。

## 验证

- vue-tsc --noEmit 通过；实际 Vue 组件脚本/模板和模拟 API 验证：审批人只读、姓名/账号回退、提交仅含付款 ID/审批结果/意见、批量请求不含姓名。
- 新增 PurchasePaymentApproverTest 四项：通过/驳回取当前身份、缺失身份拒绝写入、空显示名拒绝写入、单笔和批量入口不转发客户端冒填姓名。
- 修改的后端实际源码经 Java 21 + Lombok 在隔离目录编译；JUnit Platform Launcher + 预加载 Mockito 代理运行新四项及原 PurchasePaymentFlowTest 五项，共九项全部通过。隔离输出避免本机 IDE 编译产物干扰。
- mvn -o -DskipTests compile 返回 BUILD SUCCESS，但报告 Nothing to compile；本次后端源码验证以隔离编译及测试为依据。
- check:docs、git diff --check 通过。
- check:status-enums / validate 被既有 engineering/product-spec/index.vue:431 OUTBOUND_STATUS 阻断，未扩大基线。

## 生效与遗留

后端需重新编译并重启，前端刷新；遵守仓库规则，未自行重启服务。未做已登录浏览器联调。历史手填姓名保持原记录。

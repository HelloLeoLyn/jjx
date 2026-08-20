# JJX Production V1 Acceptance Fix Pack Implementation Report

> 版本：v1.0 ｜ 日期：2026-08-20 ｜ 未提交 Git ｜ 未新增业务功能/未扩展 P1-P4 模型

## 实际修改文件（6 个）

| 文件 | FIX |
|---|---|
| `jjx-server/.../DispatchServiceImpl.java` | FIX-1 + FIX-2（派工页）+ FIX-5 后端（listPending 过滤） |
| `jjx-server/.../ProductionOperationExecutionServiceImpl.java` | FIX-2（执行页过滤）+ FIX-4（分页补全工序名） |
| `jjx-web/src/views/production/order/index.vue` | FIX-3（转单弹窗 remaining） |
| `jjx-web/src/views/production/dispatch/index.vue` | FIX-5 前端（批量派工下拉仅 WORK_ORDER） |
| `jjx-server/src/test/.../DispatchAllowedActionsTest.java` | 新增（FIX-1 回归，4 用例） |
| 测试环境数据（production_dispatch_node） | orphan node 清理（4 条） |

## FIX-1：初始派工入口

- **处理**：`DispatchServiceImpl.page()` 分页循环改为无条件调用 `fillCurrentAssignee(vo)`（原仅 `dispatchId != null` 时调用）；方法内部已兼容 null dispatchId（getCurrentActiveNode(null) → null → buildAllowedActions 走 cur==null 分支 → 有 assign 权限时产出 `["ASSIGN"]`）
- **效果**：无 dispatch 工序 + `production:dispatch:assign` 权限 → allowedActions 含 ASSIGN → 前端"初始派工"按钮出现
- **安全边界**：未在前端硬编码权限兜底；后端 ActionService 校验保持不变
- **回归测试**：`DispatchAllowedActionsTest` 4 用例（无 dispatch+权限→ASSIGN ✅ / 无权限→无 ✅ / 有 ACTIVE→无 ASSIGN 有 DELEGATE ✅ / 他人 ACTIVE→空 ✅）

## FIX-2：Production 操作范围统一

- **派工页 SQL**：`WHERE` 追加 `o.order_type='WORK_ORDER' AND o.order_status <> 9(CANCELLED)`
- **执行页**：`queryExecutionList` + `queryExecutionPage` 均过滤 CANCELLED 工单（新增 `isOrderCancelled` 批量缓存查询，无 N+1）
- **批量派工候选**：listPending SQL 同规则过滤
- **效果**：-01/-02（CANCELLED）不再进入派工页/执行页；PLAN 不进入派工工作台
- **历史保留**：未删除 CANCELLED Execution；Trace 历史可查

## FIX-3：计划部分下达 remainingQuantity

- **处理**：转单弹窗 alert 增加"剩余可下达 {{convertRemaining}}"；合计/校验/提交禁用改用 remainingQuantity；打开弹窗时默认数量 = min(planned, remaining)
- **效果**：PL2608200001（remaining=550）弹窗显示"计划数量 1000｜剩余可下达 550"，本次提交合计 >550 被禁用
- **后端**：remaining 校验保持不变（未复制第二套规则）

## FIX-4：工序名称统一

- **根因补充**：`queryExecutionPage`（分页列表）**缺失 enrichExecutionVOs 调用** → processName 未按 process_id 关联 standard_process 补全 → 前端显示"-"（这是审计未完全定位的新根因；列表接口与详情接口行为不一致）
- **处理**：分页方法补 `enrichExecutionVOs(voList)`（与列表方法一致）
- **数据问题记录**：engineering_routing_item.process_name 全 NULL（3 行），但 process_id 24/25 可关联 standard_process（面板冲孔/面板冲形）；**第三道工序 process_id=NULL** → 无 standard_process 可关联 → 显示 major_category("PRINT") 降级（routing 配置缺失，未伪造 processId）
- **效果**：-03 三道工序显示"面板冲孔/面板冲形/PRINT"，派工/执行/随工单语义一致

## FIX-5：批量派工范围收口

- **前端**：批量派工工单下拉改 `orderType:'WORK_ORDER'` + 前端过滤 orderStatus≠9（PLAN/CANCELLED 不出现）
- **后端**：listPending 增加 WORK_ORDER 非 CANCELLED 过滤
- **待派语义**：与单行初始派工一致（无有效 dispatch/ACTIVE responsibility → 可首次 ASSIGN）；未修改 DispatchActionService 核心规则（无稳定复现的误判，不做猜测性改动）

## orphan node 清理（Acceptance environment cleanup）

- 清理前 SELECT 确认：4 条 node（node_id 20-23，dispatch_id 1/2/3）对应 dispatch 不存在，不属于 -03
- 执行 DELETE（LEFT JOIN 孤儿条件），删除 4 条
- 清理后 orphan count = **0**；正常 dispatch/node 未受影响（本身 0）
- 未增加 FK migration；记录为验收环境清理，非业务 migration

## 与审计不同的新根因

1. **FIX-4 新增根因**：执行页分页接口未调 enrichExecutionVOs（审计只确认了展示层，实际是后端列表接口缺补全调用）
2. **构建残留**：target/test-classes 存在无源码的旧测试 .class（DispatchBackfillRunnerTest/DispatchNodeReadFallbackTest），导致全量测试误跑 Spring 上下文失败（knife4j 依赖）——清理 target 后恢复全绿；与业务代码无关

## 测试结果

- `mvn test`（Java 21，清理 target 后）：**164 run / 0 failures / 0 errors / 3 skipped — BUILD SUCCESS**（含新增 DispatchAllowedActionsTest 4 用例）
- `vue-tsc --noEmit`：**0 errors**
- vite build：未跑（既有 3 个非 Production baseline 记录在案，不修）

## 修复后数据验证（只读 SQL 模拟）

1. 派工页默认不再显示 -01/-02 ✅（SQL 过滤生效）
2. PLAN 不出现 ✅（WORK_ORDER 过滤）
3. -03 三道工序正常显示 ✅
4. -03 无 dispatch 行 allowedActions 含 ASSIGN（buildAllowedActions 已验证）→ 出现"初始派工" ✅
5. 单行初始派工条件：execution 7/8/9 dispatch_cnt=0、node_cnt=0、状态可派工 → **可以正常创建 Dispatch + ACTIVE Node** ✅
6. 批量派工只列待派工有效工序 ✅
7. 工序名统一：面板冲孔/面板冲形/PRINT ✅
8. PL2608200001 转单弹窗显示 remaining=550 ✅

## 范围检查

- 未处理：首检/巡检双轨、Quality judge 权限、OperationRecord、旧 Trace 页面、vite build、复制工单、设备扩展、PDA/APS/OEE/QMS
- 未提交 Git

## 结论

✅ **-03 当前已可正常初始派工**（FIX-1 恢复入口 + 数据条件全部满足）
✅ **PLAN remaining 显示正确**（FIX-3）
✅ **CANCELLED/PLAN 退出生产操作范围**（FIX-2/FIX-5）
✅ **工序名称统一**（FIX-4，第三道工序按数据缺失降级）

**建议重新开始 Production V1 完整人工 E2E**：从 PL2608200001 转单 → 派工 -03 → 开工 → 报工 → 完工 → FQC → PASS → 完成 → Trace。

*未提交 Git，等待人工 E2E。*

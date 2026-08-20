# JJX Production P3-C Quality Actions & Production Gate Implementation Report

> 版本：v1.0
> 日期：2026-08-19
> 基线：HEAD = 8dc8970（P3-A 拍板 + P3-B 验收）
> 状态：完成，等待人工验收

---

## 1. 实际修改文件

**新建（4）**
- `QualityJudgeDTO.java`（判定入参：result/totalQty/passQty/failQty/defectDesc/remark）
- `QualityActionService.java`（接口：createInspection/judge/reinspect/createFqcForExecution）
- `QualityActionServiceImpl.java`（实现）
- `QualityActionP3CTest.java`（12 个测试）

**修改（6）**
- `QualityInspectionServiceImpl.java`：create 加 workReportId 一致性校验、inspectionNo 唯一生成、update 不可变守卫、清理 053 返工联动死代码（判定已走 judge）
- `QualityInspectionController.java`：+judge / +reinspect / +createInspection 端点
- `ProductionOperationExecutionServiceImpl.java`：注入 QualityActionService + completeExecution 尾部"最后有效 Execution 完成 → 自动创建 PENDING FQC"
- `ProductionOrderServiceImpl.java`：canCompleteOrder ③ 改为"最新一张 FQC = PASS"；completeOrder 移除"完工后创建 FQC"死锁块
- `WorkReportActionServiceImpl.java`：注入 QualityInspectionService + cancel 质检 gate
- 测试构造同步：WorkReportCancelTest / WorkReportSubmitTest（+qualityInspectionService 参数）

## 2. 正式 Quality 动作（QualityActionService）

| 动作 | 说明 |
|---|---|
| `createInspection(dto)` | 人工创建质检（IPQC 可带 workReportId）；**workReportId 非空 → 后端反查 WorkReport 校验 executionId/orderId 一致**（不信任客户端组合 ID） |
| `judge(id, dto)` | 判定 PASS/FAIL：数量校验（全部 >=0；pass+fail <= inspection；PASS 时 passQty>0）；写入质量事实；**FQC 联动**（PASS→finishedQuantity；FAIL→恢复 execution + rework_flag） |
| `reinspect(id)` | 复检：复制原单上下文（type/orderId/executionId/workReportId/productId/materialId）新建 PENDING，**不覆盖历史** |
| `createFqcForExecution(executionId)` | 最后有效 Execution 完成时自动创建 PENDING FQC；**幂等：同 execution 已有 PENDING 不重复**；历史 FAIL 不阻止新建 |

## 3. 不可变如何保证

- `judge()`：已判定（PASS/FAIL）→ BusinessException "质检结果已确定，不可修改；复检请新建质检单"
- `update()`（LEGACY）：已判定 → 拒绝修改 result/数量；PENDING → 只允许修改数量/缺陷/备注等非判定字段（**result 不接受 PUT 判定**，判定只能走 judge）
- 复检 = 新建记录，旧 FAIL 永久保留（真实 DB 验证 Step5）

## 4. FQC 自动创建位置

**completeExecution 尾部**（`ProductionOperationExecutionServiceImpl`）：
- 条件：同 order 下不存在 `process_order 更大且未完成(非 COMPLETED/SKIPPED)` 的工序 → 当前是最后有效工序
- 动作：`qualityActionService.createFqcForExecution(executionId)`（PENDING，orderId+executionId 有值、workReportId=null）
- **修复了 P3-A 死锁**：FQC 在"最后工序完成时"创建，Order complete 只校验，不再在 completeOrder 里创建

## 5. FAIL 后 Execution 恢复到什么状态 + 为什么

**恢复为 EXECUTION_STATUS.EXECUTING(2)**（`handleFqcFail`）：
- 报工允许 EXECUTING/PAUSED（submit 校验），complete 只允许 EXECUTING（canCompleteExecution）
- **EXECUTING 是唯一同时满足"可继续报工 + 可再次 complete"的现有状态**，不新增 REWORKING/REJECTED 状态机
- 同时清 actualEndTime（未完成语义）+ order.rework_flag=1
- 验证：ACTIVE DispatchNode 责任人仍可报工（submit 只校验 execution 状态 + ACTIVE node assignee，不依赖 dispatch status）

## 6. 复检如何实现

`reinspect(id)` → 复制上下文新建 PENDING 单（不复制结果/数量）→ 完整形成 `FQC #1 FAIL → FQC #2 PENDING → PASS` 历史链（真实 DB 验证 Step3/Step4）。未加 previousInspectionId（按 P3-A 拍板）。

## 7. Order gate 如何修复

`canCompleteOrder` ③ 重构（`ProductionOrderServiceImpl`）：
- 旧：查"存在任一 FQC pass"（且 FQC 在 completeOrder 里才创建 → 死锁）
- 新：**查最新一张 FQC（createTime desc LIMIT 1），result 必须 = pass** → 消除死锁，且 FAIL 后未复检通过时订单不能完成
- `completeOrder` 移除"完工自动创建质检单"块（保留入库）

## 8. finishedQuantity 如何更新

`handleFqcPass`：FQC PASS 判定时 `order.finishedQuantity = FQC.passQty` + rework_flag=0。
**不自动 complete Order**（PASS = 解锁，不是 PASS = 完成）；**不覆盖** WorkReport/Execution qualified（保持生产申报投影，真实 DB 验证 Step5 WorkReport 原样）。

## 9. WorkReport cancel 如何联动

`WorkReportActionServiceImpl.cancel` 加质检 gate（在条件更新前）：
- 关联 Quality 存在 **PASS/FAIL** → 拒绝撤销（"该报工已关联质检判定结果，不允许撤销；如需更正请走质检复检"）
- 仅关联 **PENDING** → 允许撤销 + **同步逻辑删除这些 PENDING 质检**（del_flag=1，@TableLogic 软删，历史可追踪，不留指向已撤销事实的有效质检单）
- 无关联 → 正常撤销（原有逻辑不变）

## 10. inspectionNo 如何解决

`generateInspectionNo()`：`QCI + yyyyMMddHHmmssSSS（毫秒）+ 3位随机`，替代原秒级时间戳，消除自动 FQC 并发冲突风险。

## 11. 完整业务链验证结果（真实 DB 事务回滚）

```
WorkReport(900/100) → Execution complete → FQC#1 PENDING（execution 有值/workReport=NULL）
→ Order gate reject=1（最新FQC非PASS）
→ judge FAIL → FQC#1 FAIL + Execution 恢复 EXECUTING + rework=1
→ 继续 WorkReport(100/0)（ACTIVE node 责任人可报工）→ Execution complete → FQC#2 PENDING
→ FQC#1 FAIL 永久保留（不覆盖）
→ judge PASS（950）→ finishedQuantity=950 + rework=0
→ Order gate pass=1（最新FQC是PASS）
→ WorkReport 原样（900/100 + 100/0 SUBMITTED）
→ ROLLBACK 0 残留 ✅
```

## 12. 测试 / compile

| 项 | 结果 |
|---|---|
| mvn compile | ✅ EXIT=0 |
| production 全量测试 | ✅ **107/107**（95 基线 + 12 新 P3-C） |
| 真实 DB 完整链路（回滚） | ✅ 全部断言通过，0 残留 |
| 生产订单数据 | 4 条（3 PLANNED + 1 CLOSED），未被触碰 |

## 13. 是否修改 P1/P2 核心语义

**否。**
- Dispatch/DispatchNode 模型：0 改动
- WorkReport 数量语义（生产自报）：保持；FAIL 不删除/不修改 WorkReport（真实 DB 验证）
- Execution qualified（WorkReport projection）：保持
- WorkReport submit：0 改动

## 14. 是否发生范围越界

**否。** 未做：自动 IPQC、AQL/SPC、缺陷字典、返工工单、报废审批、质量成本、OQC 完整流程、IQC 采购联动、P4 Trace、前端改动。IPQC 只提供人工创建 + PASS/FAIL 判定（FAIL 只记录质量事实 + 不控制完整生产状态机）。

## 15. 是否满足进入 P3-D

**✅ 满足。** Quality 正式动作齐备、FQC 闭环（创建→判定→gate→finishedQuantity）、FAIL 可继续生产并复检、WorkReport cancel 联动正确、107/107 测试全绿、真实 DB 链路验证通过、P1/P2 语义未破坏。

---

*报告完。等待人工验收后进入 P3-D（Frontend & Final Regression）。*

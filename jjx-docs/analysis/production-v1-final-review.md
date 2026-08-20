# JJX Production V1 Final Review

> 版本：v1.0 ｜ 日期：2026-08-20 ｜ 基线：3da532c ｜ 只读分析，未改代码/库/Git

## 1. Executive Summary

Production V1（P0-P4）主链已经形成且基本可用：**订单 → 工序执行 → 派工责任链 → 报工 → FQC → 完成 → 入库 → 生产履历** 全链路真实落地，无 Trace 事实表、无 Event Sourcing 过度设计，只读投影思路正确。

但发现 **4 个 MUST FIX**（其中 2 个是业务正确性问题）和若干 SHOULD CLEAN。最重要的两个业务正确性问题：

1. **订单"完成"绕过 FQC gate**：前端完成按钮走 `updateOrderStatus`（无质检校验），带 FQC gate 的 `completeOrder`/`completeExecution` 未接线；
2. **操作工角色没有任何菜单权限**：`PRODUCTION 操作工` 角色 0 菜单，实际无法报工（报工权限只挂在"业务操作/全权限"）。

**结论：建议 Production V1 在修复 MUST FIX 后封版。** 当前状态"可用但不严谨"，不建议直接宣布正式封版。

## 2. 当前 Production V1 完整业务链

```
生产管理员: 创建订单 → 审核 → 计划转工单 → 派工(assign-v1)
责任人:     继续派工(DELEGATE) / 改派(REASSIGN) / 退回(RETURN) / 拒绝(REJECT)
执行人员:   工序执行(mine) → 开始(START) → 报工(SUBMIT) → 撤销(CANCEL) → 完成(COMPLETE)
质检人员:   质检工作台 → 判定(PASS/FAIL) → 复检(REINSPECT) → FQC 自动创建
生产管理员: 订单完成(⚠️ 绕过 gate) → 自动入库 → 生产履历(Drawer)
```

链路数据事实（真实库验证）：4 订单 / 9 execution / 3 dispatch / 4 node / 0 work_report / 0 quality（历史数据少，但代码路径全通）。

## 3. 六个页面职责评审

| 页面 | 服务谁 | 核心任务 | 重复 | 旧模型残留 | 死入口/问题 |
|---|---|---|---|---|---|
| 生产订单 | 管理员 | 建单/审核/计划/完成/履历 | 部分（随工单抽屉与履历 Drawer 重叠） | ⚠️ OrderStatusDialog 的 completedQuantity/qualityResult 旧字段 | **完成按钮走无 gate 路径（MUST FIX）**；"查看流水"(traceId) 与"生产履历"并存易混 |
| 派工管理 | 主管/班组长 | 初始派工+责任流转 | 无 | 批量派工(batch-assign legacy)、`/assign` legacy 端点保留 | 批量派工=旧 API 入口（SHOULD CLEAN）；"拒绝派工(整单退回)"与"退回上级"并存但文案已区分 |
| 工序执行 | 执行人/班组长 | 我的任务→开始→报工→完成 | 无（数量事实唯一入口） | "操作员（旧）"详情标签、旧 qualityCheck 双轨 | **首检/巡检按钮=旧 Quality 双轨（MUST 决策）**；"详情"按钮与行内查看重复 |
| 质检管理 | 质检人 | IPQC 创建/FQC 自动/PASS/FAIL/复检 | 与 execution 旧 qualityCheck 双轨 | 旧 Quality update/delete API 保留（前端未用） | 新建/判定按钮**无权限控制**（只看 quality:view 即可判定——权限过宽）；"检验标准"按钮是死入口（提示未开放） |
| 生产追溯 | 管理层 | 订单履历 | **与订单页履历 Drawer 重复** | 整个页面基于旧 trace_log（0 行数据，正/反追溯空壳） | 菜单 visible=0 显示中；建议隐藏或删除 |
| 设备管理 | 设备员 | 档案/状态/维护计划 | 无 | 无 | 够用（未扩展 OEE/IoT 正确）；"维护计划"仅基础 |

**页面职责总体清晰**：订单=管理面、派工=责任面、执行=操作面、质检=质量面、履历=追溯面。无需要新增的菜单。

## 4. 用户操作流程评审（真实流程视角）

- **自然**：执行页"我的当前任务"默认 tab + 开始/报工/撤销/完成 按钮齐全；报工弹窗数量+工时+不良原因完整；撤销有"已关联质检判定禁撤"gate。
- **不自然/问题**：
  1. 订单页完成：点击"完成"→ 弹出通用"目标状态"选择框（可任意选状态）→ 无 FQC 提示 → **业务 gate 被绕过**（MUST FIX）
  2. 执行页"首检/巡检"走旧 qualityCheck（写 execution.quality_check_result JSON + FAIL 自动暂停），质检工作台走新 quality 表——**同一工序两套质量概念**（MUST 决策）
  3. 订单操作区 7 个圆形图标按钮 + 下拉（生产履历 Tickets 已加）→ 按钮偏多但可接受
  4. 报工后要完成工序：报工弹窗→完成按钮是分开的，中间无"最后工序将自动创建 FQC"提示位置问题（提示已有，可接受）

## 5. 状态模型问题

- OrderStatusEnum(0-11) 与前端 WorkOrderEnum 完全一致 ✅
- ExecutionStatusEnum(0-9) 与前端一致 ✅（OVERDUE/ABNORMAL/PENDING_CONFIRMATION 已定义但 P1-P4 未使用——REVISIT LATER）
- DispatchStatusEnum TEAM_ASSIGNED(1) 语义过时（新模型已无"班组级"概念，前端不再展示）→ DEPRECATE
- DispatchNodeStatusEnum(6 状态) 与责任链一致 ✅
- WorkReportStatusEnum(SUBMITTED/CANCELLED) 一致 ✅
- QualityResultEnum(pending/pass/fail) 一致 ✅

**无 MUST FIX 状态问题**（前后端映射一致）。

## 6. 权限问题

**MUST FIX：**
1. **PRODUCTION 操作工角色 0 菜单权限**——操作工无法看到工序执行页、无法报工（报工权限 production:work-report:add 只挂在业务操作/全权限）。这是生产一线不可用问题。
2. **质检判定权限过宽**——judge/reinspect 端点只要求 `production:quality:view`（查看权限即判定），无独立判定权限点；前端新建/判定按钮无 v-hasPermi。任何有质量查看权限的角色都能 PASS/FAIL。

**SHOULD CLEAN：**
3. PRODUCTION 全权限角色缺 dispatch:assign/list（靠另挂"派工主管"补）——角色语义不完整但可用。

## 7. Legacy / TECH-DEBT 分类

| 项 | 分类 | 理由 |
|---|---|---|
| operators JSON 责任判断 | KEEP FOR COMPATIBILITY | P1 Node-first，legacy 仅 fallback 读取，无业务判断 |
| on-write adoption | KEEP FOR COMPATIBILITY | 存量 legacy dispatch 的迁移保护，正常 |
| appendLevel/mergeChain/levelOfUser（DispatchServiceImpl） | DEPRECATE | 仅旧 assign(DispatchAssignDTO) 路径使用，新 UI 用 assignV1 |
| `/assign` legacy 端点（单工序指派） | DEPRECATE | 新 UI 用 /assign-v1；保留兼容 |
| `/batch-assign` + 前端"批量派工"按钮 | DEPRECATE | 旧批量派工，走 legacy 链；建议前端隐藏，待 P1-E 清理 |
| compare-node-legacy / 前端责任链按钮 | KEEP | P1-E cutover 诊断工具，保留无妨 |
| TEAM_ASSIGNED 状态 | DEPRECATE | 无新语义，仅枚举兼容 |
| 旧 Quality update/delete API | KEEP FOR COMPATIBILITY | 前端未调用，但 IQC/OQC 未来可能用 |
| execution qualityCheck JSON 双轨 | **MUST 决策（见 §9）** | 新旧两套质量概念并存 |
| production_operation_record | 见 §10 | 未接线 |
| 旧 trace API/page（trace_log） | 见 §11 | 0 行数据空壳 |
| 订单页"查看流水"(TraceTimeline) | KEEP | 操作日志链路，与生产履历不同维度（但按钮并存易混，SHOULD CLEAN 考虑合并入口） |

## 8. 菜单最终建议

```
保持 6 个可见菜单（生产订单/派工管理/工序执行/质检管理/生产追溯/设备管理）✅
```

**争议项：生产追溯菜单**。P4 已把履历做进订单 Drawer。旧 trace 页面基于 trace_log（0 行）。建议：
- **隐藏"生产追溯"菜单**（visible=1），保留页面文件；订单履历 Drawer 已是唯一入口
- 若未来有"全局追溯码"需求再恢复并复用新 Trace API

## 9. Quality 双轨问题 → 明确建议

**现状双轨**：
- 新轨：production_quality_inspection（IPQC/FQC 工作台，P3 定稿，judge/reinspect/不可变/自动 FQC）
- 旧轨：execution.qualityCheck（quality_check_result JSON + 首检/巡检按钮 + FAIL 自动暂停工序）

**建议：C. 保留有合理职责，但收敛入口**
- 旧轨"首检/巡检"（FIRST/PATROL）本质是**过程抽检记录**（不产生判定单），新轨 IPQC 是**正式质检单**——职责不同，都有存在价值；
- 但当前用户会困惑（执行页和质量页各有一个"质检"）。
- **收敛方案（SHOULD CLEAN，非 MUST）**：执行页"首检/巡检"按钮改为跳转质检工作台创建 IPQC（复用新轨），旧 qualityCheck API 标记 DEPRECATE 保留。或 V1 保留现状但报告明确两轨语义。

## 10. OperationRecord 结论

**未接线，无真实用途。** 表 0 行；CRUD API/前端页面存在但无业务调用；前端菜单已隐藏（visible=1）。

**建议：保持不动（KEEP，不删不接）。** 未来若做"暂停/恢复时间戳、异常、参数记录"过程事件时，优先扩展 execution 字段或重构此表，而不是现在强行接入。

## 11. Trace 菜单/旧页面结论

- 订单履历 Drawer（P4）✅ 唯一正确入口
- 旧 /production/trace 页面：基于 trace_log（0 行），正/反追溯空壳，与 P4 概念无关
- **建议：隐藏菜单（visible=1），页面文件保留；未来可删或复用新 Trace API**

## 12. 数据库技术债

- execution 表 11 个索引，`idx_order_id` 与 `idx_execution_order_status` 前缀重复、`idx_execution_status` 单独存在——轻微冗余（SHOULD CLEAN，不影响正确性）
- production_operation_record：5 个索引但 0 行数据（KEEP）
- trace_log 表 + 3 索引 0 行（KEEP，待删决策）
- quality_inspection：索引合理 ✅；order.trace_id 旧字段（KEEP 兼容）
- 表注释过时：production_operation_record 注释仍为"生产工序记录"（与实际用途不符，KEEP）

## 13. build baseline 问题

3 个非 Production 历史问题（**可以独立 TECH-DEBT，不与 Production V1 发布强相关**）：
1. `inventory/material/components/MaterialCategory.vue` — **0 行空文件**，修复复杂度低（删除或补内容）
2. `product/standard-process/index.vue:179` — 缺结束标签，复杂度低（补闭合标签）
3. `purchase/order/components/OrderDetailDialog.vue` — v-else 无相邻 v-if，复杂度低（补 v-if）

均在 inventory/product/purchase 模块，与 production 无关。**建议：标记 TECH-DEBT 独立处理，不阻塞 V1 收口**（vite build 是整体构建，V1 发布前若要求构建通过则需一并修，但代码影响面极小）。

## 14. MUST FIX BEFORE V1

1. **订单完成走 FQC gate**：前端"完成"按钮接 `completeOrder`/`completeExecution`（带 canCompleteOrder 四条件），`updateOrderStatus` 保留但禁止直接置 COMPLETED（或加 gate）；批量完成同样处理
2. **操作工角色权限**：给 `PRODUCTION 操作工` 挂最小菜单（工序执行页 + work-report:add/cancel + operation-execution:view + quality:view 只读）

## 15. SHOULD CLEAN

3. 质检判定独立权限点（如 production:quality:judge），前端按钮 v-hasPermi
4. 隐藏"生产追溯"菜单（履历 Drawer 已是入口）
5. 前端隐藏"批量派工"按钮（legacy batch-assign），保留 API
6. 执行页"操作员（旧）"标签改名/移除
7. 质检页"检验标准"死按钮（提示未开放→移除或禁用态）
8. TEAM_ASSIGNED 标记 deprecated；OrderStatusDialog 移除 completedQuantity/qualityResult 旧字段
9. 订单页"查看流水"与"生产履历"按钮语义区分（流水=操作日志，履历=生产事实）

## 16. KEEP / LATER

- KEEP：on-write adoption、operators legacy fallback、compare-node-legacy、旧 Quality update/delete API、trace_log 表（待删决策）
- LATER（未来能力，非 V1）：首检/巡检接入 IPQC 正式单、OperationRecord 重构、暂停/恢复时间戳捕获、全局追溯码

## 17. 是否需要 Production V1 Cleanup Work Package

**需要**，但范围小——2 个 MUST FIX + 7 个 SHOULD CLEAN。

## 18. Cleanup 建议拆分（最多 3 个小任务）

- **V1.1-Fix（业务正确性）**：订单完成 gate + 操作工权限 —— 必做
- **V1.2-Clean（UI/权限收敛）**：质检判定权限点 + 隐藏追溯菜单 + 批量派工按钮 + 旧标签/死按钮清理
- **V1.3-Debt（可选）**：vite build 3 个历史问题 + 索引微调（可独立 TECH-DEBT）

## 19. 是否建议 Production V1 正式封版

**条件性建议**：完成 V1.1-Fix（2 个 MUST FIX）后可正式封版；当前状态建议标记"待修复后封版"。

## 20. 后续真正值得开发的业务方向

（仅记录为未来能力，非本轮建议实施）
1. 报工移动端/PDA 化（现场报工是最痛环节）
2. 暂停/恢复时间戳捕获（补 Trace 缺口）
3. 首检/巡检正式化接入 IPQC
4. 质量缺陷字典 + 不良率趋势（SPC 之前的基础）
5. 工序级工时成本归集（为成本核算打底）

---
*只读完成，未修改任何代码/数据库/Git。等待人工评审。*

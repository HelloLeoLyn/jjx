# 生产管理 · 现行真相

> 状态：✅已实施（P0→P4 / V1 已收口，近期 1666 / dev-20260909-004 / 011） | 本文是"现在是什么样"
> 最后复核：**2026-09-23**（补 2026-09-22~23 定案口径）
> 说明：本模块历史上按阶段推进，`analysis/` 里约 34 篇流水是那段历史的留痕。**找"当年为什么这么做"才看它们；"现在是什么样"看本文。**

## 一句话

生产域主线：**生产订单 → 派工 → 工序执行（报工）→ 检验 → 完工收口**；任务模型是一棵树。

## 入口（菜单 43 → /production）

| 菜单 | menu_id | path | perms |
|---|---|---|---|
| 生产订单 | 45 | order | production:order:view |
| 派工管理 | 261 | /production/dispatch | production:task:view |
| 工序执行 | 48 | execution | production:execution:view |
| 生产追溯 | 52 | trace | (无 perms，登录可见) |
| 设备管理 | 49 | equipment | production:equipment:view |
| 生产报表 | 325 | report | production:report:view |
| 报工查询 / 报工审批 / 代报工 | 292 / 293 / 294 | 按钮级 | production:work-report:view / :approve / :proxy |

（质量记录模板 315、质量记录归档 334 挂在「文档管理 316」下；质检入口已迁到独立质量域，见 `current/quality.md`）

## 当前生效的规则

- **任务树**：`Execution（工序执行）→ TaskNode（任务节点）→ WorkReport（报工）`。派工与"我的任务"都按这棵树展示（根任务分页 + 子任务懒加载）。
- **中间节点自动完成**：迁移 `72_auto_complete_mid_tasks.sql`；任务完成链简化为"中间节点自动完成 + 工序完工收口根任务"（dev-20260909-004）。
- **报工**：报工单号 `report_no`（早期 dev-20260827-018 引入）；`@Log` action 已动作化。
- **返工**：`production_operation_execution.execution_type = NORMAL | REWORK`；REWORK 由质量域的 FQC 不良余量触发，完成后自动回到 FQC 复检（见 `current/quality.md`）。
- **完工收口门禁**：必须过 FQC —— 无待检记录、无未处置不良余量、累计合格成品数 ≥ 计划数量；口径为"Y：FQC PASS 的 passQty"。
- **身份与范围配置驱动**：`sys_config` 的 `production_admin` / `production_global_scope` 存 role_key 名单（逗号分隔），**不硬编码角色**；`/mine` 接口支持 `includeCompleted`，一级负责人可见整单工序。
- **工序执行页**已工作台化：上区工单面板（当前/历史 + 我的/全部）联动下区任务树；组件抽为 `TaskTreePanel`，纯函数进 utils。
- 手机端：`/m/**` 移动入口支持按工单号查任务、报工、完工（注意摄像头扫码需 HTTPS 安全上下文，见 `guides/internal-https-setup-guide-20260904.md`）。

## 关键表

`production_order`、`production_operation_execution`、`production_operation_record`、`production_task`、`production_task_event`、`production_work_report`、`production_equipment`、`production_quality_inspection(+_item)`、`production_trace_log`

## 关键代码

- 后端：`com.jjx.production.{controller(15), service.impl}`；重点 `ProductionOrderServiceImpl`（含完工门禁）、`ProductionOperationExecutionServiceImpl`、`ProductionTaskService`、`ProductionOrderStartTransactionService`（开工独立事务，避免自锁）
- 定时：`ProductionOrderTimeoutTask`（超期工单）
- 前端：`views/production/**`（order / dispatch / execution / equipment / trace / report / quality）

## 口径（2026-09-22 ~ 23 定案）

### 1. 工单完工数 = 有效检验批合格累计

- 有效批 = **无后继版本的检验批**；工单 `completed_quantity` 取其合格量累计，**不再用「判定即累计」**；
- 复检换代 / 红冲后**自动重算**（`syncFinishInbound(orderId, "复检换代重算：…")`，dev-20260923-022），不只靠判定那一次回写；
- 历史事故：工单显示 200 而有效合格只剩 100（换代/红冲不重算）——现已闭环；门禁：`check-inbound-lot-integrity.sh` ⑦。

### 2. 补报 / 补产（净损失可补）

- 任务或工序**已完成**时仍允许补报：放行上限 = `min(工序投料量, 工单计划量) × (1 + 损耗率)`；
- 损耗率 = `sys_config` 的 `production.report.overrun-rate`（**缺省 5%**）；
- 报废/不良造成净损失后，工单面板显示「**待补产 + 缺口 N 件 + 一句原因**」，补报弹窗按缺口预填（dev-20260923-028）。

### 3. 完工收口门禁

- 必须过 FQC：无待检记录、无未处置不良余量、累计合格成品数 ≥ 计划数量；口径 = FQC PASS 的 `passQty`（沿用）；
- 与「入库」档的衔接（数量对账栏）见 dev-20260923-024（待做）。

### 4. 单号

- 业务单号一律走 `sys_number_sequence`，**默认 3 位流水 + 溢出告警**；工单/主单前缀调整见 `design/doc-no-rules-dev-20260922-023.md`（第 5 批 A 方案，迁移 207 已落盘但**未执行**）；
- 巡检：`scripts/check-doc-no.sh`（已接进 `npm run validate`）。

## 历史（"当年为什么这样"才看）

- 阶段流水：`analysis/production-p0*` → `p1*` → `p2*` → `p3*` → `p4*`、`production-v1-*`（P0 域清理、P1 派工迁移、P2 报工域、P3 质量集成、P4 追溯）
- 近期：`analysis/production-execution-workbench-dev-20260909-004.md`、`auto-complete-task-chain-dev-20260909-004.md`、`fqc-rework-close-loop-dev-20260909-011.md`
- 设计底稿：`analysis/production-p4a-trace-domain-design.md`、`decision-report-production-2026-08-28.md`、`approval-matrix.md`

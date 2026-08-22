# TT-FINAL-E2E 问题登记（2026-08-21）

> 依据：TT-FINAL-E2E 补充执行规则（FAIL → 登记 → 绕开/准备数据 → 继续；仅 GLOBAL_BLOCKER 停止）。
> 本轮 E2E 为 Service 级多账号模拟（当前环境无法真实登录切换账号、MySQL 不可连）。
> 自动验证通过项不重复登记；以下仅登记真实缺口/风险。看板（sys_task）当前不可写，统一在此登记，后续同步看板。

| 编号 | 标题 | Priority | 场景 | 实际结果 | 预期结果 | 是否阻塞 | 影响 | 建议修改范围 |
|------|------|----------|------|----------|----------|----------|------|--------------|
| TT-E2E-BUG-01 | 工序执行详情 Drawer「操作记录」Tab 仍为空占位 | P2 | I-流水 / 详情 | 详情 Drawer 的“操作记录”Tab 显示空态（未接线） | 展示该工序操作流水 | 否（不影响 Task Tree 流水主链路） | 仅详情页入口体验 | execution/index.vue 详情 Drawer 操作记录 Tab 接入 executionEvents |
| TT-E2E-BUG-02 | 流水对历史动作无回溯 | P1 | E2E-11 | 分配/收回/退回事件来自 sys_oper_log（@Log 从本次版本起才记录）；历史动作无流水 | 全量历史可查 | 否 | 存量 Execution 流水不完整 | 可选：迁移时补写历史事件，或接受“自版本起”语义 |
| TT-E2E-BUG-03 | 主列表筛选区无“工单状态”筛选 | P2 | A-主列表 | 设计稿含工单状态筛选，当前仅 Execution 状态 | 按工单状态筛选 | 否（已按业务需要收口） | 列表过滤体验 | 后续版本增加 orderStatus 筛选（依赖 order 状态投影） |
| TT-E2E-BUG-04 | 真实多账号/UI 无法自动验证 | MANUAL_REQUIRED | 全部 | 环境无法登录切换多账号、无法连 MySQL | 人工按最短验收路径复验 | 否 | 最终人工验收 | 见最终报告 MANUAL_REQUIRED 清单 |

## 测试链数据说明
- 本轮自动 E2E 使用 Service 级内存事实存储，Execution=500（工序总量 200，工单 WO-E2E-001，工序“冲型”），不触碰真实 DB 数据。
- 无真实 DB 污染 → 无需准备/切换测试 Execution；真实数据验证标 MANUAL_REQUIRED。

## 汇总
- PASS 场景数：11/11（E2E-01 ~ E2E-11，含 Complete Gate 与流水）
- FAIL_NON_BLOCKING 数：0（服务级断言全部通过）
- BLOCKED 场景数：0
- 登记 Bug 数：4（含 1 条 MANUAL_REQUIRED 类）
- 自动准备测试数据次数：0（全程内存模拟，未写库）

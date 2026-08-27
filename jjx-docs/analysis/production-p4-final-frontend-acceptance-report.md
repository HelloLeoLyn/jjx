# JJX Production P4 Trace V1 Final Frontend & Acceptance Report

> 版本：v1.0 ｜ 日期：2026-08-20 ｜ 分支：dev ｜ 基线：51dbba6 ｜ 未提交 Git

## 1. 前端修改文件（5 个，全部未提交）

| 文件 | 改动 |
|---|---|
| `jjx-web/src/api/production/trace.ts` | 追加 P4-B API（productionTraceApi.getOrderTrace）+ 类型（TraceEventVO / OrderTraceVO / TraceEventType / TraceCategory）；旧 trace 接口保留不动 |
| `jjx-web/src/views/production/order/components/ProductionTraceDrawer.vue` | **新增**：生产履历 Drawer（订单头 + 分类/工序筛选 + Timeline） |
| `jjx-web/src/views/production/order/components/OrderTableActions.vue` | 操作区新增"生产履历"圆形按钮（Tickets 图标，production:order:view 权限） |
| `jjx-web/src/views/production/order/components/OrderTable.vue` | 透传 `production-trace` 事件 |
| `jjx-web/src/views/production/order/index.vue` | 接入 Drawer（handleProductionTrace → 打开履历） |

diff 已逐一核对：三个组件文件的改动**全部为本轮内容，无历史残留混入**。后端改动：0。

## 2. 生产履历入口

生产管理 → 生产订单 → 每行操作区圆形按钮（Tickets 图标，tooltip"生产履历"）→ 打开 Drawer。
未新建一级"追溯中心"页面。

## 3. Drawer 结构（760px）

- **顶部**：工单号 / 产品 / 计划数量 / 订单状态 tag / 开始时间 / 完成时间（来自 orderHeader）
- **分类筛选**：全部 / 生产执行 / 责任流转 / 报工 / 质量 → **走 P4-B category 参数重新请求**（非前端本地过滤）
- **工序筛选**：从事件 executionId 动态提取（>1 个才显示），本地过滤，不去请求工艺路线
- **Timeline**：el-timeline，每条 = 标题 + description + 操作人 + 状态 + 来源
- 无流程图 / 拓扑图 / 甘特图

## 4. 16 个事件展示（前端零新增类型）

订单：订单创建 / 订单开始 / 订单完成
执行：工序开始 / 工序完成
责任：初始派工 / 继续派工 / 改派 / 退回上级 / 派工拒绝(整单退回) / 派工完成
报工：生产报工 / 撤销报工
质量：创建质检 / 质检通过 / 质检不通过

**状态视觉**：仅 4 类轻量颜色——success（完成/通过）、danger（不通过/撤销/退回）、warning（退回上级）、info（创建/普通），未做 16 套配色，保持 Element Plus 风格。

## 5. 关键行为落实

- **复检不特殊化**：FQC FAILED → 新 CREATED → PASSED 自然三条，前端不推断"复检"
- **报工撤销**：保留原 SUBMITTED 事件，撤销另成一条"撤销报工"（可见 10:00 报工 → 11:00 撤销）
- **责任事件**：dispatchNodeId 为 null 不影响展示；保留 dispatchId 字段但不做二次责任链查询
- **工序名**：直接展示后端降级结果（"工序 1"），前端不补查工艺路线
- **历史订单兼容**：只有订单创建/工序开始/派工动作时正常展示，无"追溯数据不完整"错误
- **空 Timeline**：0 事件显示"暂无生产履历"，不报错
- **加载/错误**：Drawer 打开时 loading；API 失败显示明确错误提示，不影响订单页

## 6. 只读保证

Drawer 仅"查看 / 筛选 / 关闭"，无任何修改/撤销/重试/重新派工/重新质检入口。前端调用唯一入口 `GET /production/trace/order/{orderId}`，禁止分别请求 Execution/Dispatch/WorkReport/Quality 拼接。

## 7. 六个回归场景结果

| 场景 | 验证方式 | 结果 |
|---|---|---|
| A 历史订单缺 WorkReport/Quality | 单测 `emptyWorkReportAndQuality_ok` + 真实 DB order 2（8 事件：ORDER_CREATED/EXECUTION_STARTED/6 条责任） | ✅ |
| B 完整生产订单全链 | 单测 `fullOrderTrace`（3+4+2+1+2=12 事件，时间升序，首 ORDER_CREATED 末 ORDER_COMPLETED） | ✅ |
| C WorkReport submit→cancel 两条 | 单测 `workReportSubmittedAndCancelled_bothEmitted`（顺序 SUBMITTED→CANCELLED，actor 正确） | ✅ |
| D FQC FAIL→新FQC→PASS | 单测 `qualityCreatedPassFail_emitted`（FAILED+CREATED+PASSED 齐现） | ✅ |
| E assign/delegate/reassign/return 顺序 | 单测 `dispatchLogActionMapping`（6 动作按 sourceId 稳定排序，START 跳过） | ✅ |
| F 相同时间多 source 排序稳定 | 单测 `sameEventTime_stableBySourceRank`（ORDER<EXECUTION<DISPATCH<WORK_REPORT<QUALITY 固定） | ✅ |

## 8. 验证结果

- `mvn test`（Java 21）：**149 run, 0 failures, 0 errors, 3 skipped — BUILD SUCCESS**（compile 含在内）
- `vue-tsc --noEmit`：**0 errors**
- `vite build`：**仍被 3 个已知历史非 Production 问题阻塞**（MaterialCategory.vue 空模板 / 缺结束标签 / v-else 无相邻 v-if——均为 inventory 相关历史文件）。**trace 相关文件零错误**，未顺手修其它模块
- Browser：**不可用**（无运行中 Chrome，attach-only 模式；后端 8080 未运行）。按要求未修改浏览器环境，改用 API 单测 + 类型检查 + 真实 DB 只读验证，见下方人工验收项

## 9. 人工 UI 验收项（Browser 可用时）

1. 生产订单页 → 任一订单"生产履历"按钮 → Drawer 打开且 loading 后显示订单头
2. 历史订单（如 WO-PL2608190001-01）→ 显示 8 条事件，无报工/质检段，无错误提示
3. 分类筛选"质量"→ 仅质量事件；切回"全部"恢复
4. 状态 tag 颜色：质检通过=绿、不通过=红、撤销报工=红
5. 空履历订单（如纯草稿单）→ "暂无生产履历"
6. 断网/后端停止时打开 → 明确错误提示，订单页不崩

## 10. P4 Final Gate

- Schema change：**否**（未建 production_trace_event / 未接 trace_log / operation_record / 未改任何表）
- 业务数据修改：**否**（TraceQueryService 仅 SELECT，测试 verify 零写入；前端纯查看）
- 破坏 P1/P2/P3：**否**（未改 Order/Execution/Dispatch/WorkReport/Quality 任何代码）
- 后端新增：仅 P4-B 已验收的 TraceQueryService/Controller/VO/常量 + P4-C 未改后端
- 未提交 Git（P4 Final Gate 通过后统一整理 P4 commit）

## 11. 建议

✅ **建议 P4 Trace V1 正式验收。**

后续可选（非本轮范围）：现有 `views/production/trace` 旧页面（menu 52，visible=0 已隐藏，旧 trace_log 空壳）后续可复用同一 Trace API 或废弃，本轮保持不动。

---
未提交 Git，等待验收。

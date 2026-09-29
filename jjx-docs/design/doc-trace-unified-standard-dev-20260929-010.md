# 单据溯源统一标准：源单唯一 + 规则集中 + 统一继承 + 门禁（dev-20260929-010）

> 日期：2026-09-29 ｜ 任务码：dev-20260929-010（P1，登记人 dahuang）｜ 起草：大黄(OpenClaw)
> 类型：**标准/方案（设计层）**，未改代码、未改数据；只读核查取证
> 状态：用户 2026-09-29 11:49 选「中」——立标准 + 规则表 + 统一继承 + 门禁，覆盖采购/销售/生产；**本轮不做代码实施**
> 来源：用户 11:21「采购管理模块不单是收货流水没有，付款流水也没有」→ 11:30「只是在对应接口接收@Log就行了吧」→ 11:35「系统过于复杂、没有统一标准…采购单才是源单，采购单的 trace_id 就是统一的」
> 关联：`history/iqc-disposition-truth-rootfix-dev-20260929-003.md`｜`modules/system-ops.md`｜`modules/inventory.md`｜任务 `dev-20260929-008`（采购线断链的止血卡，本标准的子集）

---

## 1. 现象（2026-09-29 实测证据）

用户反馈 PO260929001「查看流水」里看不到**收货/付款**（实际入库也看不到）。取证（`sys_oper_log`）：

| 观测 | 实测 |
|---|---|
| 采购单 trace `019fceb9…`（PO260929001）上的节点 | 仅 **4 条**：id140 创建 / 143 修改 / 144 提交审核 / 145 审核通过 |
| 采购收货管理日志 | **13 条，13 条 trace_id = NULL** |
| 采购付款管理日志 | **6 条，全部 NULL** |
| 入库管理日志 | `biz_type=inbound` 31 条中 **3 条 NULL**；另有 34 条 `biz_type=NULL` |
| 入库节点实际挂的 trace | `4765d9b0b99b47cc` —— 那是**演示销售流程**的 trace，**挂错了线** |
| 全库 | 172 条操作日志里 **96 条（56%）trace_id 为空** |

结论：**不是"没写 @Log"，而是"日志串不起来"**——漏挂、错挂、口径不一三种病并存。

## 2. 根因

1. **继承规则按「同 `bizType + bizId`」找**：`OperLogAspect` 的 traceId 回退里，`findTraceIdByBiz(bizType, bizId)` 要求 key 完全一致。而同一采购链路上：
   - 订单 = `purchase_order / bizId=orderId`
   - 收货 = `purchase_receipt / bizId=orderId`
   - 付款 = `purchase_payment / bizId=paymentId`
   - 入库 = `inbound / bizId=inboundId`
   → **key 各不相同，继承必然失败**，退化为 NULL。
2. **继承分支散落**：SpEL → 实体 getTraceId → 按 biz 继承 → 按 source 血缘反查 → 报价特例，五段回退各写各的，谁先命中算谁，**没有单一真源**。
3. **血缘字段不统一**：各表 `source_type/source_id/source_no` 有无不一、口径不一，无法通用解析源单。
4. **没有门禁**：新增单据类型无人强制登记"源单规则"，所以每次新模块都重新踩一遍。

## 3. 目标标准（5 条）

1. **源单唯一**：每张业务单据只有**一个根源单**；`trace_id` 只在根源单创建时生成，全程不变。
2. **下游只继承、不自建**：下游单据的日志/流水一律继承根源单 trace，**禁止生成新 trace**。
3. **血缘显式化**：所有业务单据表统一带 `source_type / source_id / source_no`，作为"找源单"的唯一依据。
4. **规则集中声明**：用一张「单据类型 → 源单解析规则」表替代散落代码；日志/流水写入时**统一按规则解析根 trace**。
5. **门禁**：新增单据类型必须登记源单规则，否则 `check` 失败（照抄"新表必须登记清理归属"的成熟做法）。

## 4. 落地设计（草案）

### 4.1 源单解析规则（覆盖采购/销售/生产）

| 单据类型(bizType) | 源单解析 | 根源单 |
|---|---|---|
| `purchase_order` | 自身 | 采购单 |
| `purchase_receipt` | `order_id → purchase_order` | 采购单 |
| `purchase_payment` | `order_id → purchase_order` | 采购单 |
| `inbound`(source_type=PURCHASE) | `source_id → purchase_order` | 采购单 |
| `outbound`(source_type=SALES) | `source_id → sales_order` | 销售订单 |
| `production_order` | `source_id → sales_order`（或补料时 → 原工单/销售单，**待拍板**） | 见 §6 |
| `work_report / execution / fqc / lot` | 按工单/工序链回溯 | 工单 |

> 规则载体二选一（**待拍板**）：① DB 表 `sys_doc_source_rule`（可配、需门禁维护）；② 代码内集中 registry（一处声明、编译期可校验）。**建议 ②**：规则稳定、改动走评审，避免又多一处"配置真源"。

### 4.2 写入口径

- `biz_type/biz_id` = **日志所属的自身单据**（保持现状语义，便于"本单据操作历史"）。
- 新增 `root_biz_type / root_biz_id`（或仅 `root_trace_id`）用于串联；`trace_id` 统一 = 根源单 trace。
- `OperLogAspect` 的继承逻辑收敛为**一处**：按 §4.1 规则解析根源单 → 取其 trace；**删除五段散落回退**。

### 4.3 展示口径

- 「查看流水」（`TraceTimeline`）按**根源单 trace** 串联展示，覆盖采购/销售/生产全链节点。

### 4.4 门禁与迁移

- 新增 `scripts/check-trace.sh`：① 所有单据类型在规则表里有登记；② 采样断言"下游日志 trace = 源单 trace"；纳入 `npm run validate`。
- 历史 NULL trace：按规则一次性回填（**测试期数据可清，压力小**）。

## 5. 范围与成本

- **止血（小）**：只修采购线（收货/付款/入库挂回采购单 trace）——即 `dev-20260929-008`。
- **标准（中，本次选定）**：规则 + 统一继承 + 口径 + 门禁，覆盖采购/销售/生产。
- **不做**：本轮不实施代码；先定标准与规则。

## 6. 拍板点

1. **跨模块"根"取谁**：采购为某销售订单/生产工单补料时，根 trace 取**销售订单**还是**采购单**？（建议：取业务源头——销售订单；补料/补产同理回原单。）
2. **规则载体**：DB 表 vs 代码 registry（建议代码 registry）。
3. **历史回填**：是否一次性回填历史 NULL trace（建议回填，测试期可清）。
4. **`root` 字段形态**：只存 `root_trace_id` 还是同时存 `root_biz_type/root_biz_id`。

---

> 本文只为标准/方案与拍板点，**不代表已实施**。

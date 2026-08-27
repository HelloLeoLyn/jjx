# JJX Production P3-A Quality Integration Domain Design

> 版本：v1.0（只读分析稿，等待人工评审）
> 日期：2026-08-19
> 基线：HEAD = 8dc8970（P0 + P1 + P2 全部验收通过）
> 性质：**只读分析与领域设计**——未修改代码、未修改数据库、未执行 migration、未提交 Git

---

## 1. Executive Summary

### 核心结论（TL;DR）

当前质量实现**不是一个生产链路中的质量门，而是一个孤立的"质检单管理"**：

- `production_quality_inspection` 表只绑定 `order_id`（订单级），**无法区分工序/报工/责任节点**；
- **FQC 是"订单完工后补一张质检单"**（`completeOrder()` 先置 COMPLETED、后自动创建 FQC），而 `canCompleteOrder()` 又要求"存在 FQC pass"——**两个逻辑互相矛盾**（首次完工永远过不了 FQC 门，除非手工先建 FQC 并标记 pass）；
- **当前没有任何 IPQC 自动创建逻辑**（grep 全库确认）；
- Execution 上另有一套**独立的首检/巡检**（`qualityCheck()` 写 JSON 到 `quality_check_result` 字段），与质检表完全脱节；
- P2 WorkReport 的 `qualifiedQuantity` 语义目前就是"操作员自报合格数量"（SUM 投影到 execution），**没有被当作质量认可数量**——这是正确的，P3 应保持；
- **真实质检数据量为 0**（`production_quality_inspection` 0 行），migration 无历史兼容负担。

### P3 推荐方向（一句话）

**保持 WorkReport = 生产申报事实（不可覆盖），新增 QualityInspection = 质检判定事实（不可覆盖），两者通过 `execution_id`（+可空 `work_report_id`）绑定；IPQC 按需触发（最小规则：默认不触发，未来工序级配置），FQC 作为订单级最终质量门前置到"完工前"；`finished_quantity` 优先取 FQC PASS 认可数量，无 FQC 时回退最后工序生产投影。**

---

## 2. 当前 Quality 数据模型

### 2.1 表结构（production_quality_inspection，真实 SHOW CREATE TABLE）

| 字段 | 类型 | 说明 | 关键性 |
|---|---|---|---|
| inspection_id | bigint PK | 检验ID | 有 |
| inspection_no | varchar(50) UK | 检验单号（QCI+时间戳） | 有 |
| inspection_type | varchar(20) | IQC/IPQC/OQC（**注释里没有 FQC**） | 有 |
| order_id | bigint NULL | **关联工单ID（唯一生产关联）** | 有 |
| material_id | bigint NULL | 关联物料ID | 有 |
| product_id | bigint NULL | 关联产品ID | 有 |
| inspector | varchar(50) | 检验员（**字符串，无 inspector_id**） | 有 |
| inspect_time | datetime | 检验时间 | 有 |
| result | varchar(10) | pending/pass/fail（**小写，P0 枚举统一过**） | 有 |
| total_qty | int | 检验总数（**INT，非 DECIMAL**） | 有 |
| pass_qty | int | 合格数 | 有 |
| fail_qty | int | 不合格数 | 有 |
| defect_desc | varchar(500) | 缺陷描述 | 有 |
| remark / del_flag / create_by / create_time / update_by / update_time | | 审计字段 | 有 |

**索引**：uk_inspection_no、idx_order_id、idx_type、idx_result。

### 2.2 缺失字段（P3 需要的）

- ❌ `execution_id` —— 无法绑定工序
- ❌ `work_report_id` —— 无法绑定报工
- ❌ `dispatch_node_id` —— 无责任节点（**P3 结论：不冗余，通过 work_report 追溯**）
- ❌ `inspection_status` —— 状态/结果混用（见 §12）
- ❌ 数量为 INT，无法承载 DECIMAL(18,4) 小数数量（报工是 DECIMAL）

### 2.3 检验项子表（production_quality_inspection_item）

inspection_id / check_item / standard / actual_value / result / remark——标准检验项模板，P3 可复用。

---

## 3. 当前 Quality API / Service

| API | 方法 | 说明 |
|---|---|---|
| /production/quality/page | GET | 分页（inspectionNo/type/orderId/result 过滤） |
| /production/quality/{id} | GET | 详情（含 items） |
| /production/quality | POST | 创建（inspectionNo 自动生成，result 默认 pending） |
| /production/quality | PUT | **更新结果（普通覆盖式 updateById，无状态机）** |
| /production/quality/{id} | DELETE | 删除（**物理删 + 删 items**） |
| /production/quality/statistics | GET | 统计 |
| /production/quality/export-pdf/{id} / export-excel/{id} | GET | 导出（给客户） |

**Service 关键事实（QualityInspectionServiceImpl）：**
- `create()`：默认 `result=pending`，可带 items；
- `update()`：**任意覆盖** result/totalQty/passQty/failQty/defectDesc——**无不可变约束**；
- update 内含 053 返工联动：**FAIL → order.rework_flag=1；PASS → rework_flag=0**（同一张单可反复改结果来回翻转 rework_flag）；
- 无 execution/workReport 任何关联逻辑。

---

## 4. P0 质检枚举复核

### 4.1 Java 枚举（P0-01 已统一）

- `QualityInspectionTypeEnum`：IQC/IQC、IPQC、**FQC（完工检验）**、OQC——**枚举有 FQC**；
- `QualityInspectionResultEnum`：PENDING("pending")/PASS("pass")/FAIL("fail")。

### 4.2 数据库真实值

- **表注释**：`inspection_type` 注释为 "IQC-来料检, IPQC-过程检, OQC-成品检"——**没有 FQC**；
- **真实数据**：0 行（无实际值可验证）；
- **不变量**：表注释与枚举定义不一致（注释缺 FQC），但无数据冲突。

### 4.3 前端映射

- quality/index.vue：`row.inspectionTypeName || row.inspectionType` 展示，`getInspectionTypeTag()` 有类型→tag 映射（需确认 FQC 是否在 tag map）；
- 前端 quality.ts 类型 QualityVO：inspectionType/inspectionTypeName 字符串透传。

### 4.4 结论

| 项 | 一致？ |
|---|---|
| Java Enum 四类型 | ✅ 一致（含 FQC） |
| 表注释 | ⚠️ 注释缺 FQC（仅注释问题，无数据） |
| 前端 tag 映射 | ⚠️ 需复核 FQC tag 色（P3-D 顺手核对，本轮不改） |
| 自动建单逻辑 | FQC 有（completeOrder 内）；IPQC **无** |

**残留清单（本轮不修改）**：① 表注释缺 FQC；② 前端 type tag 映射可能缺 FQC 色；③ total_qty INT 与 DECIMAL 报工数量体系不一致。

---

## 5. 当前 FQC 逻辑（完整分析）

**触发点**：`ProductionOrderServiceImpl.completeOrder(orderId)`（唯一 FQC 创建点，grep 确认）。

**流程（真实代码顺序）**：
1. `canCompleteOrder()` 四门检查（状态=进行中 / 全部工序 COMPLETED/SKIPPED / **存在 result=pass 的 FQC** / finishedQuantity>0）；
2. 通过后 → 置 COMPLETED → 核算人工成本 → **自动创建 FQC 质检单（result=pending）** → **自动生成成品入库单**（createFromProduction，数量=f finishedQuantity）。

**致命矛盾（P3 必须拍板）：**

```
canCompleteOrder 要求"已有 FQC pass 记录"
        ↓
completeOrder 通过后才"自动创建 FQC（pending）"
        ↓
首次调用 completeOrder 时 FQC 不存在 → canCompleteOrder 永远 false
        ↓
除非：手工先创建 FQC 并 PUT 成 pass（但前端无编辑入口！）
```

**前端 reality check**：quality/index.vue 的"新建检验"按钮是 `console.log('新建检验')` TODO 占位，无创建表单；列表只有"详情"无"编辑"。**结论：当前 FQC 门在生产订单页面上实际不可达**（或依赖完全手工的 API 调用），这是一个 P3 必须修复的断链。

**其余事实：**
- inspectionQuantity（total_qty）来源：**无**——自动创建的 FQC total_qty/pass_qty/fail_qty 全空（int 默认 0）；
- 谁填写结果：`update()` PUT 接口（无前端入口）；
- FQC PASS 如何影响订单：rework_flag 清除（PASS 分支）；订单完成状态在创建 FQC **之前**已置位——FQC 实际不 gate 订单完成（逻辑上它发生在完成后）；
- FQC FAIL：rework_flag=1 标记（工单页标红），**不撤销已完成状态、不改 finishedQuantity、不触发任何重开**；
- 重复创建：**每次 completeOrder 都 new 一张 FQC**（无幂等检查；但 completeOrder 本身只对 IN_PROGRESS 状态可达，实际重复机会低）；
- 唯一约束：uk_inspection_no（时间戳生成，重复概率极低）。

**重点判断：当前 FQC 是"订单级最终检验"还是"最后一道工序检验"？**
→ **两者都不是**。它是"订单完成后补录的一张订单级质检单"。概念上应归为"订单级最终检验"，但时序上完全错位（在完成后才创建）。

---

## 6. 当前 IPQC 逻辑

**grep 全后端确认：没有任何自动创建 IPQC 的代码。** `QualityInspectionTypeEnum.IPQC` 仅作为类型枚举存在，`create()` 支持手工传 IPQC 类型（DTO 透传），但无任何 Service/联动创建。

**当前无自动过程检验链。** Execution 侧有独立的首检/巡检（qualityCheck：FIRST/PATROL 写 JSON 到 quality_check_result），但那不是 IPQC 单据——是工序执行内的简易检查记录，与质检表无关。

---

## 7. P2 WorkReport 语义复核

### 7.1 当前正式定义（P2-A 设计 + P2-C 实现）

`ProductionWorkReport` = 操作员一次不可覆盖的生产申报事实。字段：executionId / dispatchId / dispatchNodeId / reporterId / reporterName / equipmentId / equipmentName / qualifiedQuantity / defectiveQuantity / laborHours / machineHours / workStartTime / workEndTime / reportTime / defectReason / reportStatus(SUBMITTED/CANCELLED)。

### 7.2 语义确认

**WorkReport.qualifiedQuantity = 操作员自报合格数量（production claim），不是质量部门认可数量。** ✅

**P2 实现没有把它当最终质量事实使用**：projection 只是 SUM 到 execution 展示/完工数量，没有任何质量判定写入或覆盖。这是 P3 可以放心依赖的正确基线。

### 7.3 关键：P2 已把 execution.qualifiedQuantity 落库为 WorkReport SUM

- `WorkReportProjectionServiceImpl.recalculate()`：SUBMIT/CANCEL 时 SUM SUBMITTED 报工 → 写回 execution.output/qualified/defective/labor/machine；
- `updateOrderCompletedQuantity()`：execution.qualifiedQuantity（现=报工 SUM）→ order.completedQuantity（各工序汇总）/ finishedQuantity（最后工序）；
- **这意味着：当前 finished_quantity 已经是"最后工序的生产申报汇总"（间接）**，不是质量认可。P3 引入 FQC 认可后，finished_quantity 语义需要决策（见 §21）。

---

## 8. 生产申报 / 质检 / 认可数量三分法

### 8.1 三个概念的正式定义

| 概念 | 英文 | 来源 | 代表 | 特点 |
|---|---|---|---|---|
| A. 生产申报数量 | Produced/Reported Quantity | WorkReport | reportedQualified / reportedDefective | 操作员自报，一次一报，不可覆盖，**无质量含义** |
| B. 质检检验数量 | Inspected Quantity | QualityInspection | inspectionQuantity(totalQty) | 质检员实际抽检/全检数量 |
| C. 认可合格数量 | Accepted Quantity | 质量判定结果 | acceptedQuantity | 质量判定后系统认可的合格数，**用于订单完成/入库** |

### 8.2 JJX 是否需要区分？

**需要，且 P3 必须正式区分 A 与 C**：

- 现实中 A ≠ C：操作员报 950 合格/50 不良，质检抽检后可能认可 930 / 拒绝 70（抽检比例、缺陷判定）；
- 但 JJX 是薄膜开关制造，P3 V1 **不建设 AQL/复杂抽样**（用户明确禁止）——检验方式默认"全数复核"或"按批抽检但结果整批判定"；
- 因此 P3 V1 的 C 计算规则建议最小化：**C = QualityInspection 结果录入的 pass_qty（质检员直接填认可合格数）**，系统不做抽样推断。

### 8.3 三量的落点建议（P3 V1）

| 量 | 落点 | 说明 |
|---|---|---|
| A 生产申报 | WorkReport（已有，不动） | SUM → execution 展示（P2 已做） |
| B 检验数量 | QualityInspection.total_qty（已有字段） | 质检员录入 |
| C 认可合格 | **QualityInspection.pass_qty（已有字段，语义升级为 accepted）** | 质检员录入，PASS 判定时生效 |

**不新增 acceptedQuantity 字段**（§19 展开）——复用 pass_qty 避免冗余字段，语义在 VO/文档层定义为"认可合格数量"。

---

## 9. WorkReport qualifiedQuantity 字段决策

**方案对比：**

| 方案 | 做法 | 迁移成本 | 前端 | 报表 | Trace | 质量语义 | 评价 |
|---|---|---|---|---|---|---|---|
| A | 保留字段名，定义=生产自报 | 0 | 0 | 0 | 0 | 靠文档约定 | ✅ 推荐 |
| B | 改名为 reportedQualifiedQuantity | 高（DDL+全链路） | 高 | 高 | 高 | 明确但代价大 | ❌ |
| C | 新增 acceptedQuantity projection | 中（新字段/表） | 中 | 中 | 中 | 清晰但需投影服务 | ⚠️ 可 P3 后期 |

**推荐：方案 A + 最小 C 落点。**

理由：
1. P2 已验收的模型（WorkReport 表 + 投影 + 前端报工 Drawer + 85 测试）**稳定且刚提交**，B 的大改名违背"优先避免大规模改已稳定模型"；
2. 语义其实已经在代码注释/报告里明确（"操作员自报"），缺的只是**文档强化 + 前端 label 明确**（报工 Drawer 标注"本次合格数量（自报）"）；
3. C 的认可数量由 **QualityInspection.pass_qty 承担**（§19），不需要动 WorkReport 字段。

---

## 10. Quality 与 Order / Execution / WorkReport 关系（绑定模型）

### 10.1 方案对比

| 方案 | 绑定 | IPQC | FQC | IQC/OQC | 评价 |
|---|---|---|---|---|---|
| A | Quality → Order | 无法定位工序 | 够用 | 够用 | 当前现状，无法支撑 IPQC |
| B | Quality → Execution | ✅ 定位工序 | 定位最后工序 | 需额外 order_id | 基本够用，workReport 级粒度丢失 |
| C | Quality → WorkReport | ✅ 精确到批次 | 不适配（FQC 是订单级） | 不适用 | IPQC 好、FQC 差 |
| **D** | **orderId + executionId + workReportId(nullable)** | ✅ executionId 必填、workReportId 可选 | ✅ executionId=最后工序、workReportId=null | ✅ 保持 orderId，不强依赖 | **✅ 推荐** |

### 10.2 推荐模型（用户 §10 提出的候选方向，验证合理）

```
QualityInspection:
  order_id      必填（所有类型都有订单上下文）
  execution_id  IPQC/FQC 必填；IQC/OQC 可空
  work_report_id IPQC 推荐填（精确到批次）；FQC=null；IQC/OQC=null
```

**分级绑定规则：**

| 类型 | order_id | execution_id | work_report_id | 粒度 |
|---|---|---|---|---|
| IQC | ✅ | 空 | 空 | 采购/物料级（P3 不动其语义） |
| IPQC | ✅ | ✅ | 可空/推荐 | 工序级，可精确到某次报工 |
| FQC | ✅ | ✅（最后有效工序） | 空 | 订单级最终检验 |
| OQC | ✅ | 空 | 空 | 出货级（P3 不动） |

**结论：方案 D 合理且最小。** 新增 2 个可空字段（execution_id、work_report_id），不动 IQC/OQC 现有语义。

---

## 11. 是否需要 dispatchNodeId 冗余

**不需要。** 理由：
1. WorkReport 已记录 dispatch_node_id，IPQC 绑定 work_report_id 时可追溯责任节点（join 一层）；
2. FQC 是订单级，责任追溯无意义；
3. 冗余字段增加一致性维护成本（派工流转时容易失配），违背"优先避免重复字段"。

**追溯路径（已有）：** QualityInspection → execution_id → ProductionDispatch.execution_id → dispatch_node → assignee。无需新字段。

---

## 12. Quality 状态机（status vs result 拆分）

### 12.1 当前混用事实

- 表只有一个 `result`（pending/pass/fail），**同时承担"任务状态"和"检验结果"**；
- `update()` 可任意覆盖 result，无状态流转约束；
- delete 是物理删除（可删 pending 或已完成的单）。

### 12.2 P3 V1 是否需要拆分？

**需要最小的"状态"概念，但建议不新增字段——用 result 现有值承载：**

| 当前值 | 语义 | 是否够用 |
|---|---|---|
| pending | 待检（=任务未完成） | ✅ 够用 |
| pass | 已检+合格（=任务完成+结果合格） | ✅ 够用 |
| fail | 已检+不合格（=任务完成+结果不合格） | ✅ 够用 |

**结论：P3 V1 不拆 status/result 两个字段。** 维持 `result` 单字段三值，但**收紧写入规则**（§17/§25：完成后不可直接改 result，需走取消/复检）。

理由：P3 只有"待检→已检"一个状态维度，pending/pass/fail 已经隐含状态；为标准化拆两字段是过度设计（用户明确警告）。

---

## 13. IPQC 触发策略

### 13.1 方案对比

| 方案 | 做法 | 评价 |
|---|---|---|
| A | 每次 WorkReport 提交都自动生成 IPQC | ❌ 过度，工序全检成本高 |
| B | 只有配置"需过程检"的工序才生成 | ⚠️ 理想，但**工艺路线无 qualityRequired 字段**（grep 确认 EngineeringRoutingItem 无 quality 字段） |
| C | 人工创建 IPQC | ✅ 当前唯一可行，但违背"不要让用户手工重复建质检单" |

### 13.2 结论

**P3 V1 采用最小规则：不自动创建 IPQC；保留人工创建能力（走现有 POST /production/quality，type=IPQC），并允许手工关联 executionId。**

理由：
1. 工艺路线/工序表当前**没有 qualityRequired / inspectionMode 字段**（grep 确认），方案 B 需要 schema 变更 + 数据维护，P3 不该铺开；
2. JJX 当前真实生产数据 0 报工 0 质检，没有业务压力证明"每次报工必须质检"；
3. 用户明确倾向"不是每次 WorkReport 都强制质检"；
4. 未来 P3+ 可在工艺路线加 `inspection_mode`（NONE/IPQC/FQC）配置，P3 先留设计钩子（VO 层可空字段），不落库。

**设计钩子（本轮不实施）：** EngineeringRoutingItem 未来加 `quality_required`（0/1）或 `inspection_mode`（NONE/IPQC/FQC），P3-B 只讨论不建。

---

## 14. FQC 触发策略

### 14.1 当前链路时序（错误）

```
Execution 最后工序 complete
  → Order canComplete（要求已有 FQC pass——断链）
  → Order complete → 创建 FQC（太晚）→ 入库
```

### 14.2 正确时序（P3 推荐）

```
最后工序生产完成（Execution complete，P2 规则）
  → 若需 FQC：创建 FQC（pending，execution_id=最后工序，order_id=工单）
  → 质检员录入检验结果（total/pass/fail + 判定）
  → FQC PASS → Order 可 complete → 入库（数量=f finishedQuantity）
  → FQC FAIL → Order 不可 complete → rework_flag=1 → 业务继续（§23）
```

### 14.3 触发点定位

**FQC 创建时机：最后一道工序 Execution complete 时自动创建**（而非 Order complete 时）。理由：
1. 让"质检"真正发生在"订单完成"之前，消除断链；
2. 最后工序 complete 时已具备 execution_id / 数量上下文；
3. Order complete 保持为"质量门通过后的终态动作"。

**"最后一道工序"判定**：process_order 最大且状态 COMPLETED 的 execution（与 updateOrderCompletedQuantity 的 finishedQty 判定一致，052 口径）。

---

## 15. Execution Complete 与 Quality Gate

### 15.1 P3 规则（最小化）

| 场景 | 规则 |
|---|---|
| 无 FQC 需求（默认，P3 V1 全部工序） | 保持 P2：≥1 条 SUBMITTED WorkReport → 可柔性 complete，**无 Quality gate** |
| 有 FQC 需求（P3 V1 无工序级配置 → 实际不触发） | 未来：最后工序 complete 前需 FQC PASS |

**结论：P3 V1 实际上不改 completeExecution 的 gate**（因为没有 qualityRequired 配置，所有工序都走"无质检"路径）；FQC 的 gate 放在 **Order complete**（§21/§22）。

不引入 `qualityRequired` 字段（工艺路线无此字段，§13.2 已论证）。

---

## 16. WorkReport Submit 是否自动触发 Quality

**P3 V1：不自动触发。** 理由：
1. IPQC 无触发配置（§13），无规则可依据；
2. 自动建单涉及"重复提交/取消"的生命周期联动（§17），复杂度超出 P3 V1 目标；
3. 人工创建 IPQC（可选填 work_report_id）已覆盖"对某批次报工做过程检"的需求。

**但 WorkReport SUBMIT 联动设计钩子（P3-B 不实施、P3-C 可选）：** 若未来工序配置 inspection_mode=IPQC，则在 SUBMIT 事务内自动创建 pending IPQC（execution_id + work_report_id + 数量快照），并在 CANCEL 时联动（§17）。

---

## 17. WorkReport CANCEL 与 Quality

### 17.1 场景设计（P3 V1 拍板建议）

| 场景 | 规则 |
|---|---|
| WorkReport 被 CANCEL，关联 Quality 仍 PENDING | **允许 CANCEL，同时自动 CANCEL 关联的 pending Quality**（若 P3 未来自动建单；当前人工建的 IPQC 不自动撤，仅提示） |
| WorkReport 被 CANCEL，关联 Quality 已 COMPLETED（pass/fail） | **禁止 CANCEL WorkReport**（P2 当前仅禁止"已完成 execution"的 CANCEL——需加强为"关联已判定 Quality 的报工禁撤"） |
| Quality 已判定但 WorkReport 未撤 | 保持现状（quality 是独立事实） |

### 17.2 事务边界

```
CANCEL WorkReport 事务内：
  ① 校验：execution 未完成（P2 已有）
  ② 校验：无已判定(非pending)关联 Quality（P3 新增）
  ③ 若有 pending 关联 Quality（未来自动建单场景）→ 同事务置 CANCELLED
  ④ 更新 report_status=CANCELLED → 投影重算（P2 已有）
```

**P3 V1 推荐：②③ 仅当存在"由报工触发的自动质检"时生效；当前人工建的 IPQC 不强制联动（记录为 TECH-DEBT）。**

---

## 18. Quality PASS 后是否修改 WorkReport

**绝对禁止。** 正式原则：

```
WorkReport = 操作员生产申报事实（一次一报，不可覆盖，P2 定）
QualityInspection = 质检判定事实（独立记录）
二者永不互相覆盖
```

示例（真实场景）：
```
WorkReport: qualified=950, defective=50（操作员自报）
QualityInspection: total_qty=1000, pass_qty=930, fail_qty=70（质检认可 930）
→ 禁止 UPDATE work_report SET qualified=930
→ execution 展示两套数：生产申报汇总（950/50） vs 质检认可（930/70）
```

---

## 19. Accepted Quantity 落点

### 19.1 候选方案

| 方案 | 落点 | 评价 |
|---|---|---|
| A | QualityInspection.qualifiedQuantity（=pass_qty） | ✅ 已有字段，语义升级 |
| B | Execution 增加 acceptedQuantity | ❌ 大改 execution schema（用户警告不要） |
| C | Order finished 直接从 Quality 算 | 部分采纳（§21） |
| D | 新 QualityProjection 服务 | ⚠️ 可为 P3-C 提供，但不必新增表 |

### 19.2 结论

**A + D 最小组合：**
- **认可数量落 QualityInspection.pass_qty**（已有字段，语义正式定义为"认可合格数量"；VO 增加 acceptedQuantity 别名只读字段 = pass_qty，纯展示）；
- **不新增 accepted_quantity 列**（避免冗余，字段理由 §34）；
- 未来如需"按质检认可汇总"，在 QualityProjectionService（P3-C）里 SUM pass_qty 即可，不改表。

---

## 20. Execution projection 是否继续用 WorkReport

**方案 1（推荐）：Execution 保持 P2 语义 = 生产申报汇总（WorkReport SUM）；Quality 独立表示质检认可。**

- Execution.qualifiedQuantity = SUM(SUBMITTED WorkReport.qualifiedQuantity)——**不动**；
- 质量认可走 QualityInspection.pass_qty——**新增独立投影**（P3-C QualityProjectionService）；
- 前端 Execution 页面可同时展示"生产申报合格"与"质检认可"两列（P3-D 设计，§29）。

理由：P2 刚验收的 projection 语义稳定；Quality 认可与生产申报是两回事，混在一列会造成"报工后数字跳变"的困惑。

---

## 21. ProductionOrder finished_quantity

### 21.1 当前语义（052 口径）

```
finished_quantity = 最后一道工序（process_order 最大）的 execution.qualifiedQuantity
                  = 最后一道工序的 WorkReport 生产申报合格汇总（P2 后间接）
```

### 21.2 P3 推荐

| 场景 | finished_quantity |
|---|---|
| 有 FQC 且 PASS | **= FQC.pass_qty（质检认可合格数）** |
| 无 FQC | 沿用最后工序生产投影（当前逻辑） |
| FQC FAIL | 不更新（保持上次数值或 0），Order 不可 complete |

**实现方式（P3-C）：** Order complete 前置校验 FQC PASS 时，将 `finished_quantity = FQC.pass_qty` 写入；completeOrder 内现有"完工后建 FQC"逻辑**移除/重构**为"完工前校验 FQC"。

**这是 P3 最关键的订单数量语义变更，必须人工拍板。**

---

## 22. ProductionOrder completed_quantity

**保持不变（P0 定稿）：completed_quantity = 各工序合格汇总（展示口径），不是成品最终数量。**

P3 确认：
- completed_quantity 继续由 updateOrderCompletedQuantity 维护（工序汇总）；
- finished_quantity 升级为"FQC 认可数优先"（§21）；
- 入库数量**继续用 finished_quantity**（inventory 068 口径已对）。

---

## 23. 质检不合格（FAIL）后行为

### 23.1 最小行为设计（不建返工系统）

| 动作 | P3 V1 规则 |
|---|---|
| Execution 状态 | **保持已完成**（工序生产事实已发生，不撤销） |
| Order complete | **禁止**（FQC FAIL → canCompleteOrder 不过） |
| 继续新增 WorkReport | **允许**（如果 execution 未冻结；已 complete 的 execution 由 P2 报工状态限制） |
| 重新检验 | **允许：新建第二张质检单（复检）**，不 UPDATE 原单（§24） |
| rework_flag | 沿用现有：FAIL → order.rework_flag=1（工单页标红） |
| finished_quantity | 不因 FAIL 清零（保留生产投影；Order 未 complete 时本就不可入库） |

### 23.2 明确不做

返工工单 / 返工路线 / 报废审批 / 不合格品隔离——全部 P3 范围外（用户禁止）。

---

## 24. 复检模型

**结论：Quality 采用与 WorkReport 一致的"每次检验生成记录，不覆盖历史"不可变事实模型。**

```
QI#1 (FQC, execution=最后工序, result=FAIL, pass=900)
QI#2 (FQC, execution=最后工序, result=PASS, pass=980)   ← 复检新单
```

**判断依据：**
- 当前 `update()` 可把同一张单 FAIL→PASS 来回改（无历史），违反不可变原则；
- P3 收紧：**已判定（pass/fail）的质检单禁止直接改 result/数量**；更正走"取消原单 + 新建复检单"；
- 前端需新增"复检"入口（P3-D）。

**表结构是否支持：** 是——每张单独立 inspection_id/inspection_no，天然支持多张；只需收紧 update 规则（Service 层，无 DDL）。

---

## 25. Quality 不可变原则

### 25.1 当前问题

- `PUT /production/quality` 可任意覆盖 result/数量（Service 无状态校验）；
- 前端无编辑入口（实际改不了），但 API 敞口存在；
- delete 可物理删除任意单（含已判定）。

### 25.2 P3 收紧规则（Service 层，无 DDL）

| 状态 | 允许操作 |
|---|---|
| pending | 可录入结果（pending→pass/fail）、可编辑数量、可删除 |
| pass/fail | **禁止直接改 result/数量**（immutable）；允许"新建复检单"；允许删除仅限 admin 且需审计（或干脆禁止删除，P3-D 定） |

**实现：** QualityInspectionServiceImpl.update() 加状态守卫（已判定 → BusinessException "质检结果已确定，不可直接修改；请新建复检单"）。

---

## 26. Quality 与设备/人员

| 信息 | 是否记录 | 说明 |
|---|---|---|
| inspector | ✅ 已有（字符串，无 inspector_id） | P3 保持；未来可加 inspector_id（TECH-DEBT） |
| inspect_time | ✅ 已有 | 录入结果时后端写 |
| productionReporter / equipment | ❌ 不冗余 | IPQC 关联 work_report_id → 可追溯 reporter/equipment/dispatchNode（§11） |

---

## 27. Quality 与 DispatchNode

**确认：Quality 不直接影响 DispatchNode 状态。**

- 质检是质量事实，不是责任流转；
- FQC FAIL → rework_flag=1（订单级标记），责任节点保持现状；
- 如需重新生产：通过新的生产动作（新的 WorkReport 或未来返工流程），责任流转走 P1 Dispatch 既有动作（ASSIGN/DELEGATE/REASSIGN/RETURN）。

---

## 28. 前端 Quality 页面现状与复用

### 28.1 现状（quality/index.vue，486 行）

- 布局：看板式（左统计卡片 + 中表格 + 右不良分析占位）；
- 表格列：检验单号/产品/关联订单/检验类型/检验数量/合格数量/不良数量/合格率/检验员/检验时间/结果/操作（仅详情）；
- 查询条件：inspectionNo/inspectionType/orderId/result（API 支持）；
- 新建按钮：**TODO 占位**（console.log，无表单）；
- 详情：只读展示 + items。

### 28.2 P3-D 复用建议（不重写）

| 现有能力 | 复用 |
|---|---|
| 表格+筛选+统计 | ✅ 全复用 |
| 详情弹窗 | ✅ 复用，增加 execution/workReport 关联展示 |
| 新建按钮 | 改造为真实创建表单（type 选择：IPQC 需选 execution；FQC 走自动） |
| 结果录入 | 改造为"检验结果录入"弹窗（total/pass/fail/defectDesc + 判定）→ POST/PUT 规则收紧后调用 |
| 类型 tag/结果 tag | ✅ 复用（补 FQC 色） |

### 28.3 需新增

- 复检入口（FAIL 单上"复检"按钮 → 新建 QI#2）；
- execution/workReport 关联列（工序/报工号）。

---

## 29. Execution 页面 Quality 集成预设计（P3-D）

Execution 详情 Drawer 增加"质量状态"区块（P3-A 只设计不开发）：

```
质量状态：无需质检（默认） | 待质检（FQC pending） | 质检中 | 已通过（FQC pass） | 未通过（FQC fail）
报工历史行：该报工是否触发 IPQC（未来） / 关联质检结果
```

数据来源：QualityInspection 按 execution_id 查询（P3-C 提供 read API），非新字段。

---

## 30. Quality API V1（最小）

### 30.1 新增 API（复用现有 Controller 风格）

| API | 方法 | 用途 |
|---|---|---|
| GET /production/quality/execution/{executionId} | GET | Execution 关联质检列表（IPQC/FQC） |
| GET /production/quality/work-report/{reportId} | GET | WorkReport 关联质检（P3-C 可延后） |
| PUT /production/quality/{id}/result | PUT | 结果录入（受不可变守卫）——**或复用现有 PUT /production/quality + 状态守卫** |
| POST /production/quality/{id}/reinspect | POST | 复检（新建 QI#2，复制上下文） |
| POST /production/quality/fqc/{orderId} | POST | 手工补建 FQC（应急；正常走最后工序自动建） |

### 30.2 复用优先

- 现有 POST /production/quality 扩展 DTO（+executionId/workReportId 可空）即可支持 IPQC/FQC 创建；
- 现有 PUT /production/quality 加状态守卫即可（不需要新端点）；
- 现有 GET /page 加 executionId/workReportId 过滤参数即可。

**结论：V1 只需扩展现有 DTO/Query + 2 个新端点（reinspect、execution 关联查询），不做大 CRUD。**

---

## 31. FQC 与入库（正确顺序）

### 31.1 当前顺序（错误）

```
Order complete（已置 COMPLETED）→ 建 FQC → 自动入库
```

### 31.2 P3 正确顺序（推荐）

```
最后工序 complete → 自动建 FQC（pending）
  → 质检录入 → FQC PASS
  → Order complete（canCompleteOrder 校验 FQC pass + finished>0）
  → 自动入库（createFromProduction，数量=finished_quantity=FQC.pass_qty）
```

**要求满足：** "订单先入库后发现 FQC FAIL"必须杜绝——FQC PASS 是 Order complete 的前置条件，而入库只在 complete 后触发。

**入库数量来源（inventory 068 已定）：** finished_quantity；P3 后 finished_quantity=FQC.pass_qty（§21）。

---

## 32. 历史 Quality 数据兼容

**真实数据量：production_quality_inspection = 0 行。**（work_report=0、dispatch=3、node=4、execution=9 均为 P1/P2 测试数据）

**结论：无历史兼容负担。** P3-B migration 可直接：
1. ALTER 增加 execution_id / work_report_id（可空）；
2. 无数据需要回填（0 行）；
3. total_qty INT→DECIMAL 变更（可选，若 P3 数量要小数；0 行无风险）。

**不伪造 execution/workReport 关联**（无数据可伪造）。

---

## 33. 数据库变更分析（每个字段给理由）

| 变更 | 理由 | 必须？ |
|---|---|---|
| + execution_id BIGINT NULL（索引） | 绑定工序（IPQC/FQC 核心） | ✅ 必须 |
| + work_report_id BIGINT NULL（索引） | 绑定报工批次（IPQC 精确粒度） | ✅ 必须（可空） |
| total_qty/pass_qty/fail_qty INT → DECIMAL(18,4) | 与 WorkReport/execution 数量体系一致（报工是 DECIMAL(18,4)） | ⚠️ 强烈建议（0 行无风险） |
| + inspection_status varchar | ❌ 不需要（§12 result 单字段够用） | ❌ |
| + accepted_quantity | ❌ 不需要（§19 pass_qty 复用） | ❌ |
| + dispatch_node_id | ❌ 不需要（§11 work_report 追溯） | ❌ |
| + inspector_id | ⚠️ 可延后（TECH-DEBT） | ❌ |

**最终 P3-B migration 建议（最小）：2 个新列 + 2 个索引 + 数量 DECIMAL 化（可拆两个 migration 或一个）。**

---

## 34. 索引设计

| 索引 | 依据 |
|---|---|
| idx_execution_id (execution_id) | IPQC/FQC 按 execution 查列表（高频） |
| idx_work_report_id (work_report_id) | 报工→质检追溯（低频，可为普通 KEY） |

**不过度索引**：现有 idx_order_id/idx_type/idx_result 保留；组合索引（order_id+type+result）现有查询已覆盖 canCompleteOrder 的 FQC pass 查询（order_id + type + result 三列 eq，**当前无组合索引，P3 可加一个 idx_order_type_result**——评估：现有 0 行数据量级，非必须；标注可选）。

---

## 35. P3 Work Package

| WP | 内容 | 依赖 |
|---|---|---|
| P3-A | Quality Integration Domain Design（本报告） | — |
| P3-B | Quality Data Model/Foundation：migration（execution_id/work_report_id/DECIMAL）+ Entity/VO/DTO 扩展 + 枚举复核修正（表注释/前端 tag） | P3-A 拍板 |
| P3-C | Quality Actions & Production Gate：result 守卫（不可变）+ FQC 自动创建（最后工序 complete 时）+ canCompleteOrder 重构（FQC PASS 前置）+ finished_quantity=FQC.pass_qty + rework 联动修正 + QualityProjection（execution 关联质检列表） | P3-B |
| P3-D | Frontend & Final Regression：Quality 页（创建/录入/复检/关联列）+ Execution 页质量状态 + 全量回归 + Final Gate | P3-C |

**结论：拆分合理，与 P1/P2 的 WP 结构一致。** 建议 P3-B 含"表注释修复 + 前端 type tag 补 FQC"。

---

## 36. P3 明确不做（范围护栏）

✅ 不建完整 QMS、检验标准库、AQL、SPC、复杂抽样
✅ 不建返工工单、报废审批、质量成本
✅ 不建供应商质量、IQC 采购联动、OQC 完整流程
✅ 不建计量设备、质检图片系统
✅ 不建 Trace Event 总线（P4）
✅ 不建成本、APS、OEE
✅ **不破坏 P1 Dispatch / P2 WorkReport 事实模型**
✅ **不把 Quality 结果反写覆盖 WorkReport**
✅ 不改已完成 execution 的生产申报事实

---

## 37. 风险

| 风险 | 等级 | 缓解 |
|---|---|---|
| FQC 时序重构（完工后建→完工前建）影响现有 completeOrder 调用方 | 中 | P3-C 前先全量搜索 completeOrder 调用点；canCompleteOrder 兼容"无 FQC 需求"路径 |
| finished_quantity 语义变更影响入库数量 | 中 | 入库已用 finished_quantity（068 口径），改的是它的来源；P3-C 单测覆盖 |
| rework_flag 现有逻辑（FAIL/PASS 翻转）与新不可变规则冲突 | 低 | 复检模型下 FAIL 单不再被 PASS 翻转，rework_flag 由"存在 FAIL 未复检"推导（P3-C 细化） |
| Quality 不可变收紧后手工修正成本上升 | 低 | 复检入口补齐即可（P3-D） |
| IPQC 无自动触发导致过程检仍靠人工 | 低 | P3 V1 明确定义边界，未来工艺路线加 inspection_mode 配置（设计钩子） |

---

## 38. TECH-DEBT

1. 表注释缺 FQC（inspection_type 注释只写 IQC/IPQC/OQC）
2. 前端 type tag 映射可能缺 FQC 色
3. inspector 无 inspector_id（字符串）
4. quality_check_result JSON（execution 首检/巡检）与质检表双轨——P3 明确：**首检/巡检保留为工序内简易记录，不并入质检表**（语义不同：它是执行过程检查，不是批量质检单）
5. 现有 PUT /production/quality 无任何状态守卫（API 敞口）
6. QualityInspectionVO 无 orderNo/productName 之外的生产上下文（P3-B 补 execution/process 信息）
7. completeOrder 的 FQC 自动创建在完工后（断链，P3-C 修复）
8. 无 FQC 幂等/唯一约束（uk_inspection_no 时间戳，理论上可重复）

---

## 39. 最终人工决策点（必须拍板）

| # | 决策点 | P3-A 推荐 |
|---|---|---|
| 1 | Quality 绑定模型 | **orderId + executionId + work_report_id(nullable)**（方案 D） |
| 2 | WorkReport qualified 语义 | **保持"生产自报"**，不改名（方案 A） |
| 3 | Execution qualified 语义 | **保持 WorkReport projection**（方案 1：生产申报汇总） |
| 4 | IPQC 触发规则 | **V1 不自动触发**，人工创建（type=IPQC + executionId）；未来工序级 inspection_mode 配置（设计钩子） |
| 5 | FQC 触发规则 | **最后工序 Execution complete 时自动创建**（重构现有"完工后建"） |
| 6 | Quality gate 位置 | **Order complete**（FQC PASS 前置）；Execution complete 不 gate（V1 无工序级配置） |
| 7 | finished_quantity | **FQC PASS 时 = FQC.pass_qty**；无 FQC 沿用最后工序生产投影 |
| 8 | WorkReport cancel 与 Quality | **有关联已判定 Quality 的报工禁撤**；pending 关联单自动撤（V1 人工建单场景仅提示不强制） |
| 9 | FAIL 后怎么继续 | Execution 保持完成；Order 禁 complete；rework_flag=1；**允许新建复检单** |
| 10 | 复检 | **新建记录不覆盖历史**（QI#1 FAIL → QI#2 PASS），与 WorkReport 不可变一致 |
| 11 | Quality 不可变 | **已判定（pass/fail）禁止直接改 result/数量**；pending 可编辑；删除收紧 |
| 12 | 数据库字段 | **+execution_id、+work_report_id（可空+索引）；数量 INT→DECIMAL(18,4)**；不新增 status/accepted_quantity/dispatch_node_id |

---

## 40. 结论

P3-A 确认了：**当前 Quality 是孤立的质检单管理，与生产主链存在两处断链（FQC 时序倒置、无 IPQC 链路）**。P3 的目标不是建设 QMS，而是**把质检接入"报工→工序→订单"主链作为质量门**：

- 保持 P2 生产申报事实不动（WorkReport = 自报）；
- 新增质检判定事实（QualityInspection = 认可/拒绝，绑定 execution + 可空 work_report）；
- 修复 FQC 时序（最后工序完成 → FQC → PASS → Order complete → 入库）；
- finished_quantity 升级为"质检认可优先"；
- 最小 schema 变更（2 列 + 索引 + DECIMAL 化），0 历史数据兼容负担。

**等待人工评审 12 项决策点后进入 P3-B。**

---

*报告完。本轮只读，未改任何代码/数据库/Git。*

# 单号·第 5 批（工单号两套合一 + 采购入库拆号）执行前复核（dev-20260923-029，看板 2247）

> 性质：**复核稿**（2026-09-23 20:0x 大黄）｜只分析、未改本批代码
> 被复核对象：`design/doc-no-batch5-workorder-merge-dev-20260923-029.md` + 迁移 `207_rename_prod_order_prefix_pm_wo.sql`（Hermes 交付，用户 09-23 已拍板 A）

---

## 0. 复核结论

设计稿与迁移**质量高、可直接用**：现状实证（8 行生成点全核对）、目标口径、12 条改动清单、T 位专项（§5）、有意不做（§6）、回归清单（§7）、6 条风险（§8）——我逐条核对**未发现事实性错误**。

但有 **1 处必须补 + 2 处必须明确**，否则执行会出问题：

| # | 级别 | 事项 |
|---|---|---|
| **1** | **P1（会静默重复入库）** | `InventoryInboundServiceImpl:1884-1886` 用 `likeRight(inbound_no, 采购单号)` 找出「本采购单已生成的入库单」→ 汇总 `alreadyInByMaterial` → 决定本次 `toIn = 已收数量 − 已入库量`。**拆号成 `IN+yyMMdd+3` 后，单号里不再含采购单号 → 该查询恒空 → `alreadyIn = 0` → 每次收货都把整额再入一遍**（重复/超量入库）。设计稿 §3 第 5 条只写了「多次收货 `-2` 语义改为多张独立单」，**没写明这个查询必须改为按 `source_type='PURCHASE' + source_id=采购订单ID`**（production 侧 `:2141-2145` 已有同款先例：**不再依赖单号**）。 |
| **2** | 需明确工作量 | T 位告警的「复用现成通道」其实是**新建接线**：`BusinessNumberOverflowEvent` 目前**只由 `RedisSequenceService:279`** 在号段溢出时发布，而任务号 T 来自 `incrementTaskSeq`（`production_operation_execution.task_seq` 的 DB 计数器、不经号段）→ 必须在 `nextTaskNo()` 里**手动构造并发布该事件**；且事件字段（编号类型/周期/配置位数/当前位数/当前流水）本为号段设计，按「工序」为周期时要凑语义，落库到 dev 待办的文案要能读得懂。（§5 已如实写明「告警不阻断、不修正」✓） |
| **3** | 文档同步 | §8 风险 1（完工入库 `-FI\d{2}` 定长）**已在本批之前修掉**：`dev-20260923-032`（看板 2250）2026-09-23 20:0x 落地（序号改 `-FI(\d+)$` 全量解析 + 撞号向后找空号 + 门禁 ⑨），稿里「若第 5 批先实施，两者必须一起回归」应更新为「**已修复**；回归时确认门禁 ⑨ = 0 即可」。 |

**另 4 条观察（不阻塞）**：

4. `scripts/check-doc-no.sh` **今天没有前缀登记表基线**（三查=规则键存在性 / 容量 ≥80% / 时间戳式编号），所以 §3 第 9 条「前缀登记表基线同步」当前只落在文档层面；要真拦「前缀偏离登记表」需给脚本加一份 JSON 基线（我在 2250 复核里也提过同一缺口）。
5. 领料 `InventoryOutboundServiceImpl:1283` 用 `COUNT(sourceType,sourceId) + 1`（红冲/删除后会撞号）→ 稿子建议改「最大后缀 + 1」**正确**；`outbound_no` 有唯一索引，撞号会以 SQL 报错暴露（**不静默**），优先级低于第 1 条。
6. 存量与稿子的微小偏差：`production_task` 实测 **9 行**（稿写 7）→ 不影响结论。
7. 前端识别正则这条稿子做得很到位：`useScanner.ts:30` + 调用点 + `mobile/notices.vue:108` + `mobile/home.vue:237`（两处内联副本）+ `scan.vue:26` 占位文案 **已全部列出** ✓ 不用补。

---

## 1. 复核证据

**① 现状表逐条对码**（设计稿 §1 → 当前代码）

| 项 | 稿子 | 实测 |
|---|---|---|
| 工单号两套 | `OrderServiceImpl:698-699` 号段 `WPO` / `ProductionOrderServiceImpl:1373-1375` 派生 `WO-<计划号>-NN` | ✓ 号段 `generateBusinessNumberByType("production_order","WPO","yyMMdd",3)`；派生 `"WO-" + planNo + "-" + %02d`（后缀解析用**全量** `substring(prefix.length())` → 本身无定长坑） |
| 任务号 | `<工单号>-P%02d-T%03d`，`ProductionTaskServiceImpl:1037-1038` | ✓ 现为 `"-T" + String.format("%03d", taskSeq)` |
| 领料 | `PICK-<工单号>[-n]`：`:897` / `:1283` | ✓ `:897` 有存在性校验并**抛 BusinessException（响亮）**；`:1283` COUNT+1 |
| 完工入库 / 红冲 | `<工单号>-FI%02d` / `<原单号>-R` | ✓（`-FI` 定长已由 2250 修掉） |
| 采购入库 | `buildPurchaseInboundNo(po)` 返回 `PO…`，多次收货 `-2` | ✓ `:1977-1981` 返回 `po.getOrderNo()`（原样）；`:1921` 多次收货 `+"-"+(existingList.size()+1)` |
| 兜底 | `:266` `"IN-" + System.currentTimeMillis()` | ✓ 存在 |

**② 迁移 207**：只动 `sys_config` 三行（`production_plan` PL→PM、`production_order` WPO→WO、`inbound` 仅改 remark），**条件更新 + 幂等 + 可回滚 + 不动 `sys_number_sequence`** ✓；与 §3 第 11 条一致；实测 `biz_no_rule.inbound` 的 `IN` 前缀**本就存在** ✓。**存量无 `WPO…` 号**（`SUM(order_no LIKE 'WPO%') = 0`）→ 不存在新旧号混用存量，撞号风险≈0 ✓。

**③ 全仓扫「依赖单号形态」的位置**：`likeRight(inbound_no)` 共 **2 处**（`:1886` 拆号后失效=第 1 条；`:2192` 是 FI，已修）；`likeRight(outbound_no)` **0 处**；前端工单号正则 **3 处副本**（稿已列）。

**④ 门禁现状**：`check-inbound-lot-integrity.sh` 九查（含新加的 ⑨「有效 FQC 批必有入库单」）；`check-doc-no.sh` 三查（无前缀基线）。

---

## 2. 建议执行顺序

0. **前置**：无（2250 已修）；迁移前核 `sys_number_sequence` 当日 `production_order` 用量（稿 §8 风险 4）
1. **代码**：按 §3 十二条 + **新增第 13 条**（采购「已收量」查询改按 `source_type/source_id`，见 §0 第 1 条）
2. **同批上线**：迁移 207 + 前端 3 处正则 + 代码 —— 只切一边会产出第三种格式（稿 §8 风险 6）
3. **回归**（稿 §7 + 我补 4 条）：
   - 同一采购单**连续两次收货**：第二次只入差额，**不得把已收量再入一遍**（直击第 1 条）
   - 任务号 T 从 99→100 时，overflow 事件是否真的落到 dev 待办
   - 新工单号形如 `WO260923001`，旧 `WO-PL260923001-01` 仍可查询/打印/追溯
   - `check:lot:strict` 九查 = 0、`check:doc-no` 通过、`npm run validate` 全绿
4. **收尾**：总则 `doc-no-rules-dev-20260922-023.md` §4/§6/§9 更新 + 本批复核稿登记；设计稿 §8 风险 1 标注「已修复（de0cbe9e）」

---

## 3. 一句话结论

> 稿子与迁移**可以直接用**；执行前**必须补一条改动**——采购「已收量」查询从「按单号前缀」改为「按 `source_type + source_id`」，否则拆号当天就会**静默重复入库**（超收防护打空）。T 位告警按「新建接线」估工，别当现成的；FI 定长那条已经修完了。

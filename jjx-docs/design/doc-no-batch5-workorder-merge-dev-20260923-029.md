# 单号·第 5 批（A 方案）：工单号两套合一 + 采购入库拆号 + 任务号 T 位改 2 位

> 任务：dev-20260923-029（task 2247）· 起草 Hermes 2026-09-23（**已拍板：A + 任务 T 位 2 位 + 主单 PL→PM 同批 + T 超 99 复用进位告警**）
> 上游：dev-20260922-023（task 2163，status=2 待验收）《单号规则总表》§6 第 5 批 / §8.1 风险 / §4 总则
> 状态：**✅ 已实施（2026-09-23 20:1x~20:3x 大黄，用户「按顺序执行」）**：代码 + 前端 + 迁移 207 全部落地并验证（详见文末「实施记录」）；待重启后端回归运行态。
> 无遗留待拍板项：采购入库兜底时间戳（`:266`）并入本批（§3 第 6 条）

## 0. 结论先行

1. A 的实质 = **停用「WO-<计划号>-NN」派生路径，工单号一律走号段** `WO+yyMMdd+3`；父子关系改由字段承载，不再塞进单号。
2. 目标形态（字符数均实测）：

   | 单据 | 现格式 / 字符数 | A 之后 / 字符数 |
   |---|---|---|
   | 工单号 | `WO-PL260923001-01` / 17 | `WO260923001` / 11 |
   | 任务号 | `…-01-P02-T001` / 26 | `WO260923001-P02-T01` / 19 |
   | 领料出库单 | `PICK-WO-PL…-01-1` / 24 | `PICK-WO260923001-1` / 18 |
   | 完工入库单 | `WO-PL…-01-FI01` / 22 | `WO260923001-FI01` / 16 |
   | 采购入库单 | `PO260923001`（复用采购单号）/ 11 | `IN260923001` / 11 |

3. 收益：总则 §3 点名的「最该修的一条」——**同一实体两种格式（WPO… 与 WO-<主单>-NN 并存）就此消失**；全链派生号一起变短。
4. 代价：报表/打印凡「按位数截取 / 定长假设」必须逐处复核（总则 §8.2），§8 已列出现网一处真实截取代码。
5. 本批**不动存量**（总则 §6「存量不追改」），不改唯一索引，不缩列宽。

## 1. 现状实证（只读，2026-09-23）

| 环节 | 现格式 | 代码位置 |
|---|---|---|
| 工单（销售订单提交生产，直建工单） | `WPO+yyMMdd+3`（`WPO26092xxxxx`） | `OrderServiceImpl.java:698-699`（号段 `production_order`） |
| 工单（生产计划下挂子单） | `WO-<计划号>-NN`，序号 = 该计划子单最大后缀 + 1 | `ProductionOrderServiceImpl.java:1373-1375` |
| 任务 | `<工单号>-P%02d-T%03d` | `ProductionTaskServiceImpl.java:1017-1039`（:1037-1038 拼接） |
| 领料出库 | `PICK-<工单号>` / `PICK-<工单号>-<seq>`，seq = 该工单历史单数 COUNT + 1 | `InventoryOutboundServiceImpl.java:897` / `:1283` |
| 完工入库 | `<工单号>-FI%02d`（`-FI` 序号取「最大两位后缀 + 1」）；无 lotId 时 `FINISH-<工单号>` | `InventoryInboundServiceImpl.java:2193` / `:2181` |
| 完工红冲 | `<原入库单号>-R`，红冲占新流水 | `InventoryInboundServiceImpl.java:2535` |
| 采购入库 | **复用采购单号**：`buildPurchaseInboundNo(po)` 返回 `PO…`，多次收货加 `-2` | `InventoryInboundServiceImpl.java:1971-1975` / `:1759` / `:1915` |
| 采购入库兜底 | `IN-` + 毫秒（时间戳拼号，总则 §4 禁止项） | `InventoryInboundServiceImpl.java:266` |
| 成品批次 | `BATCH-<批号>` 优先，否则 `BATCH-<工单号>` | `InventoryInboundServiceImpl.java:2254` / `:2446` / `:2665-2668` |

存量规模（2026-09-23 实测，决定本批风险很低）：

- `production_order` 2 行（`PL260923001` 主单 + `WO-PL260923001-01` 工单）
- `production_task` 7 行（`WO-PL260923001-01-P0x-T00n`，均 26 字符）
- 领料出库 `PICK-WO-%` 2 行；完工入库 `FINISH-WO-%` 0 行；批次 `BATCH-WO-%` 0 行
- 采购入库 2 行（`PO260923001`、`PO260923001-2`，`source_no` 同为 `PO260923001`）
- 号段前缀：无 `WO` 键被占用（现有 `WPO`/`PL`/`IN`/`WF`/`PICK`/`TASK`… 共 31 个 `biz_no_rule` 键）

## 2. 目标口径（A，已拍板）

1. **工单号**：`WO+yyMMdd+3`（如 `WO260923001`）。`biz_no_rule.production_order` 的前缀 `WPO` → `WO`；派生路径 `generateWorkOrderNo()`（`ProductionOrderServiceImpl.java:1373`）停用，改为走号段。
2. **任务号**：`<工单号>-P%02d-T%02d`（T 位 **2 位**，拍板追加）。
3. **采购入库**：走 `biz_no_rule.inbound`（`IN+yyMMdd+3`，键已存在），不再复用采购单号；补录/兜底路径（`:266`）同批处理。
4. **父子关系放字段**：`parent_order_id`（工单↔计划）、`execution_id`/`parent_task_id`（任务↔工序↔父任务）——单号内不再内嵌上级单号（总则 §4 第 4 条）。
5. **存量不追改**：老号 `WO-PL260923001-01`、`PO260923001` 永久可查、可打印（总则 §6）。
6. **批次号不动**：总则 §4 第 3 条把 `BATCH-…` 列为贴标/追溯可读性例外。

## 3. 改动清单（执行时按此逐条核）

代码（11 处生成点）：

1. `OrderServiceImpl.java:698-699` —— 号段 bizType 保持 `production_order`，效果 = 前缀随配置变成 `WO`（只需改配置，不改代码）。
2. `ProductionOrderServiceImpl.java:1373-1375` —— `generateWorkOrderNo()` 停用派生逻辑（含「最大后缀 + 1」查库），改调号段服务；同步删除其单元测试假设。
3. `ProductionTaskServiceImpl.java:1037-1038` —— `%03d` → `%02d`，并加 §5 的超限处理。
4. `ProductionTask.java:36` —— 注释 `{工单号}-P{工序序号}-T{任务序号}` 更新为 2 位口径。
5. `InventoryInboundServiceImpl.java:1971-1975` / `:1759` / `:1915` —— 采购入库单号改号段 `IN+yyMMdd+3`；多次收货的 `-2` 语义改为「多张独立入库单」（每张占新流水）。
   - ⚠️ **2026-09-23 复核补充（大黄，见 history/doc-no-batch5-prereview-dev-20260923-029.md）**：`InventoryInboundServiceImpl:1884-1886` 的「本采购单已生成的入库单」是**按单号前缀** `likeRight(inbound_no, 采购单号)` 查的，拆号后**必然查空** → `alreadyInByMaterial=0` → 每次收货按「已收数量」整额再入一遍（**静默重复入库**）。拆号必须同批把它改为 `eq(source_type,'PURCHASE').eq(source_id, 采购订单ID)`（照 production 侧 `:2141-2145` 先例：不再依赖单号），并加回归用例「同一 PO 连续两次收货只入差额」。
6. `InventoryInboundServiceImpl.java:266` —— 兜底默认值去掉时间戳（改成显式报错或走号段），兜底不得再产生时间戳号。
7. `InventoryOutboundServiceImpl.java:897` / `:1283` —— 领料号 `PICK-<工单号>[-n]`：前缀与结构不变，工单号变短后自动变短；`:1283` 的 COUNT + 1 建议一并改成「最大后缀 + 1」（与工单号 V1 修复口径一致，避免红冲/删除后撞号）。
8. `InventoryInboundServiceImpl.java:2193` —— 完工入库 `-FI%02d` 结构不变；但 `:2190` 的正则 `-FI\d{2}` + `substring(len-2)` 是**硬编码定长截取**，见 §8 风险 1。
9. `scripts/check-doc-no.sh` —— 前缀登记表基线同步（`WO` 取代 `WPO`、新增 `IN` 的实际使用）。
10. `jjx-docs/design/doc-no-rules-dev-20260922-023.md` §4/§6/§9 —— 总则里「任务保持 `<工单号>-P01-T001`」的示例改 2 位、第 5 批标记为已实施。
11. `biz_no_rule.*` 配置 —— `production_order` 前缀改 `WO`；若 ① 同批（主单 PL→PM）则连 `production_plan` 一起改。
12. **前端工单号识别（不改会直接坏，扫不到工单）**：`jjx-web/src/composables/useScanner.ts:30`
    `WORK_ORDER_NO_REGEX = /^(WPO\d{8,}|WO-[\w-]+-\d{1,2})$/`，只有 WPO… 与 `WO-…-NN` 两种形态。
    调用点：`useScanner.ts:34/52/79`（扫描默认匹配）、`mobile/scan.vue:77/132/133`（扫到非工单号会弹「识别到非工单号内容」／「工单号格式不正确」）、
    `mobile/notices.vue:108-109`、`mobile/home.vue:237`（两处内联副本 `text.match(/WO-[\w-]+-\d{1,2}|WPO\d{8,}/)`）、`mobile/scan.vue:26` 占位文案。
    A 之后工单号是 `WO260923001` → 上述正则**一处都匹配不到**，必须同批改为 `^(WO\d{9}|WPO\d{8,}|WO-[\w-]+-\d{1,2})$` 这种同时容纳新旧号的形态（新老并存期）。

配套（非代码）：§7 回归清单、§8 风险复核、README 状态更新。

## 4. 迁移稿（已落盘，待执行）

**文件：`jjx-docs/sql/migrations/207_rename_prod_order_prefix_pm_wo.sql`**（拍板「主单 PL→PM 一起做」后落盘）

内容：`biz_no_rule.production_plan` 前缀 `PL`→`PM`、`biz_no_rule.production_order` 前缀 `WPO`→`WO`，并把 `biz_no_rule.inbound` 的 remark 去掉「预留」字样。无 DDL、无存量单据改动；条件更新（只在当前前缀仍是旧值时才改）→ 可重复执行、第二次 0 行。

**上线顺序（关键）**：本迁移必须与代码改动（停派生路径、采购入库拆号、T 位 2 位、前端识别正则）**同批上线**，否则旧派生路径仍会产出 `WO-<主单号>-NN`，比现状更糟（§8 风险 6）。

执行前核对：`sys_number_sequence` 当日用量；`biz_no_rule` 无 `WO`/`PM` 键冲突（2026-09-23 实测 31 个键中无 `WO`/`PM`）。

编号取号规矩（本次照办）：`NN = max(ops.schema.applied, 目录现存最大号) + 1`；落盘时 applied 最大 206、目录最大 206 → 取 207（并行会话会抢号，写文件前再核一次）。

## 5. 任务号 T 位改 2 位：容量与配套（本批最需要盯的一条）

实测事实：

- 生成处是**手写拼装**（`ProductionTaskServiceImpl.java:1037-1038`），**不经号段服务** → 总则 §5.3 那套「溢出自动进位 + 告警」覆盖不到它。
- `String.format("%02d", seq)` 在 `seq=100` 时**不报错**，静默变长成 `T100`（3 位）→ 破坏定长，"按位数截取"的下游立刻失效。
- `task_seq` 存在 `production_operation_execution.task_seq`，**按工序累计、不按天重置**（`incrementTaskSeq`，先递增再拼号）。
  实测现状：execution 1「面板冲孔」task_seq=4、execution 2「面板冲形」task_seq=3 → 短期远够，但一个工序长期反复派工/返工会逼近 99。
- 走号段服务的类型（QL/NCR/CAPA/IQD…）超限时是**报错拦业务**；本处相反，是**静默污染**——必须显式补策略。

已拍板（2026-09-23 用户，采纳推荐）：

- ✅ **㈠ 复用现成的进位告警通道**：发布 `BusinessNumberOverflowEvent` → `BusinessNumberOverflowListener`（`@TransactionalEventListener(AFTER_COMMIT)`）→ `NotifyTaskService.notifyAndCreateTask()` = 站内通知 + **dev 看板待办**（priority=high，内容含编号类型/周期/配置位数/当前位数/当前流水，并提示"请评估是否调大位数"）。零新机制，与总则 §5.3 一致；号继续变长但**看得见**；告警失败不阻断业务。
  理由：`task_seq` 现状最大 4，试用期触及 99 几乎不可能；真要触及，在 dev 待办里出现比当场卡死业务更合适。
- ❌ 不采用 ㈡ 超 99 自动扩位（与"定长早失效"代价相比没有额外收益）；❌ 不采用「T 位保持 3 位」（用户已明确要 2 位）。
- 告警的局限（如实记录，别指望它有问题全兜）：它不阻断、不修正，只负责"可见"；若 dev 看板待办无人看，等价于没有。真要零容忍应改为**抛错拦截**（用友/金蝶风格）。

实施点：`nextTaskNo()`（`:1017-1039`），且**以工序为单位**判断超限（不是全局）。

## 6. 有意不做（防误判为本批失败）

- 存量老号回改/回填（总则 §6：存量不追改）。
- 唯一索引与列宽调整（18/18 单号列已有唯一索引；`production_task.task_no` 是 `varchar(96)`，富余）。
- 批次号 `BATCH-…` 改短（贴标可读性例外）。
- 红冲后缀 `-R` 改格式（保留：红冲占新流水、不重用）。
- `check-doc-no.sh` 接进 `npm run validate`（另立 **dev-20260923-030 / task 2248**）。
- 撤销流、处置冻结等质检线任务（那是 021/022 的线）。

## 7. 回归清单（执行后必须过一遍）

| # | 动作 | 断言 |
|---|---|---|
| 1 | 销售订单提交生产（走 `OrderServiceImpl` 那条路）建一张工单 | 工单号为 `WO26092xxxxx`；不再出现 `WPO…` |
| 2 | 生产计划下挂子工单 | 同样为 `WO26092xxxxx`；**同一实体只有一种格式** |
| 3 | 连续建任务至第 99 / 第 100 个 | 行为符合 §5 所拍口径（告警 or 扩位），不得静默 |
| 4 | 采购订单收货两次 | 两张独立 `IN26092xxxxx`；不再等于采购单号 |
| 5 | 完工入库 + 红冲 | `WO260923001-FI01` / `WO260923001-FI01-R`；FI 序号仍「最大后缀 + 1」 |
| 6 | 领料出库 | `PICK-WO260923001-1` |
| 7 | 存量老号查询/打印 | `WO-PL260923001-01`、`PO260923001` 正常显示与打印 |
| 8 | 移动端扫工单号（扫码、手工输入、通知里点工单号） | 新旧两种形态都能识别并跳转；不再弹「工单号格式不正确」 |
| 9 | 门禁 | `npm run validate` 绿（含 `check:stock:strict` 五项全 0） |
| 10 | 报表/打印 | 逐处复核「按位数截取」假设（§8 风险 1 是已知一处） |

## 8. 风险与缓解

1. ~~**定长截取代码（已知实测一处，P2）**~~ **【2026-09-23 20:0x 已修复：dev-20260923-032 / 看板 2250，commit `de0cbe9e`——序号改 `-FI(\d+)$` 全量解析 + 撞号向后找空号 + 门禁⑨；回归时确认 `check:lot:strict` ⑨=0 即可】**：`InventoryInboundServiceImpl.java:2190` 用 `Pattern.quote(orderNo) + "-FI\\d{2}"` 过滤 + `substring(len-2)` 解析序号。
   推论：某工单的完工入库单到第 100 张时会写成 `-FI100`（3 位，`%02d` 不报错、静默变长）——**第 100 张本身能出单**；从第 101 张起，该正则不再匹配 `-FI100` → 「最大后缀」退化为 99 → 下次仍算 `100` → 唯一性检查命中已存在 → **静默 return null（不出入库单，只打 warn）**。
   缓解：**已单独登记 dev-20260923-032（task 2250）**——含完整证据链、A/B/C 方案（推荐 A：按 `-FI(\d+)$` 全数字解析 + 判重改为不依赖单号）与验收用例；若第 5 批先实施，两者必须一起回归。
   同类坑本仓已有先例：`InventoryInboundServiceImpl.java:2138-2140` 注释记录过「旧防重只查 `FINISH-<工单号>-FQC-` 前缀，改按检验批出单后新单名是 `<工单号>-FI<NN>` → 匹配不到，导致重复建单」，最终修法是**不再依赖单号**做防重。本处建议照同一思路（按字段/不限位数解析）。
2. **报表/打印定长假设**（总则 §8.2）：新号 11 位、老号 17 位并存期，凡按位数截取/固定列宽排版（生产工单打印、批次标签）都要逐处复核。**同类已实测的还有前端工单号识别正则**（§3 第 12 条）：它按 `WPO…`/`WO-…-NN` 两种形态硬编码，A 之后**扫不到工单、还会直接弹「工单号格式不正确」** —— 属"必须同批改"，漏改后果比报表更显眼。
3. **排序口径**：新老号并存后禁止字符串排序，按「前缀 + 日期 + 流水数值」解析（总则 §5.3）。
4. **前缀变更撞号**：改 `biz_no_rule.production_order` 前缀前核 `sys_number_sequence` 当日用量与唯一索引（照 `scripts/check-doc-no.sh` 的容量检查）。
5. **人工可读性下降**：A 之后号里不再含计划号，看号无法判断属于哪张计划单（靠 `parent_order_id`/界面）——**这是 A 的固有代价**，已拍板接受。
6. **两条路必须同时切**：只改一条会同时存在 `WPO…` 与 `WO…` 两种新号，反而制造第三种格式（比现状更糟）→ 第 1、2 条改动必须同批上线。

---

## 9. 实施记录（2026-09-23 20:1x~20:3x，大黄，用户「按顺序执行」）

**§3 十二条逐条落地 + 复核新增 2 条**（第 13 条来自本会话复核稿，第 14 条为执行中新发现）：

| 条 | 落地 |
|---|---|
| 1 | `OrderServiceImpl:698-699` 无需改（号段 + 配置前缀）✓ |
| 2 | `ProductionOrderServiceImpl.generateWorkOrderNo()` → 停用 `WO-<计划号>-NN` 派生（原「最大后缀+1」查库整段删除），改调 `redisSequenceService.generateBusinessNumberByType("production_order","WO","yyMMdd",3)`；新增注入 `RedisSequenceService` |
| 3 | `ProductionTaskServiceImpl.nextTaskNo()` → T 位 `%03d`→`%02d`；`taskSeq > 99` 时构造并发布 `BusinessNumberOverflowEvent("production_task_seq", "工序执行#<id>-P<po>", 2, 实际位数, seq)`（注入 `ApplicationEventPublisher`，try/catch 不阻断建号） |
| 4 | `ProductionTask.taskNo` 注释更新为 2 位口径 + 超限说明 |
| 5 | `InventoryInboundServiceImpl` 采购入库拆号：`buildPurchaseInboundNo()`（按采购单号）**删除**，新增 `nextInboundNo()`（号段 `inbound`，IN+yyMMdd+3）；`createFromPurchase` 幂等由「单号相同」改**语义判重**（该 PO 已有未取消入库单 → 跳过整单生成）；`createInboundRecordFromPurchase` 每次收货一张独立单（去掉 `-2/-3` 后缀） |
| 6 | `:266` 兜底去掉 `"IN-" + System.currentTimeMillis()` → 改走 `nextInboundNo()`（退货入库/事件桥等不传单号的调用方一并受益） |
| 7 | `InventoryOutboundServiceImpl:1283` 领料号序号由 `COUNT+1` 改**最大后缀+1**（`PICK-<工单号>(\d+)$` 全量解析）；`:897` 前缀结构不变 |
| 8 | 完工入库 `-FI` 结构不变（定长隐患已由 dev-20260923-032 修掉，见 §8 风险 1 标注） |
| 9 | `check-doc-no.sh`：本批确认 20 个规则键齐全、容量 <80%、无时间戳拼号 → 应通过；脚本本身无「前缀登记表」基线项，故第 9 条只落文档 |
| 10 | `doc-no-rules-dev-20260922-023.md` §3 表（工单/任务行）+ §5 从属单据示例 + §8/§9 第 5 批标记**已更新为已实施** |
| 11 | 迁移 207（PL→PM / WPO→WO / inbound remark）**已执行**，ops.schema.applied 记到 207 |
| 12 | 前端识别正则：`composables/useScanner.ts`（`WORK_ORDER_NO_REGEX` 增 `WO\d{9}`）+ `mobile/notices.vue` + `mobile/home.vue`（两处内联副本）+ `mobile/scan.vue` 占位文案 → 全部容纳新旧形态 |
| **13（复核新增）** | 采购「已生成入库单明细量」查询由 `likeRight(inbound_no, 采购单号)` 改按 `source_type='PURCHASE' + source_id`（否则拆号后恒空 → 重复整额入库） |
| **14（执行中新发现）** | `QualityNcrServiceImpl.reworkTaskNo()`（dev-20260923-035 返工任务号，稿子写时还没落地）也是 `-T%03d` → 同批改 `%02d`；单测 `QualityNcrReworkTaskNoTest` 断言同步（3 例） |

**验证**：`mvn -o compile`（JDK21，删 class 强制重编 + `strings` 核验新标记）；`mvn -o test -Dtest=QualityNcrReworkTaskNoTest`（3/3）+ `InventoryInboundConcurrencyGuardTest,OrderCompletionStageResolverTest,TraceQueryServiceTest`（26/26）；`vue-tsc --noEmit` 0 错；`npm run validate` 全绿（六条门禁 + vue-tsc）。

**未做/待回归（重启后）**：① 新建工单号应为 `WO260923001`、任务号 `…-P02-T01`；② 旧 `WO-PL260923001-01` 系列仍可查询/打印/追溯；③ 同一采购单连收两次只入差额（第 13 条回归用例）；④ T 超 99 的进位告警落入 dev 待办；⑤ 领料 `PICK-<工单号>-N` 递增不撞号。

**存量**：`production_order` 2 行（1 行派生 `WO-PL260923001-01` + 1 行计划）、`production_task` 9 行、`PICK-WO*` 2 行、`FINISH-WO*`/`BATCH-WO*` 各 0 行 —— 全部不追改（总则 §6）。

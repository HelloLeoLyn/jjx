# JJX 文档库（入口 = 全书目录 + 指导书）

本目录只放文档。代码在 jjx-server / jjx-web，数据脚本在 jjx-docs/sql。

三层结构：**`modules/` 是"现在是什么样"**（要维护）／**`history/` 是"当时怎么做的"**（快照，只留痕）／**`standards/` `reference/` `guides/` 是"规矩和手册"**（长期有效）。

## 怎么用这一页

1. 先看「目录」，找到你要找的那类文档在哪一章；
2. 再看「导读」，它给的是**阅读顺序**（不是清单）；
3. **判新旧只看本页的 ✅ / ⏳ / ⚠️ 标记，不看文件修改时间**（本仓库 mtime 被历次整理刷新过，不可信）。

> 👋 **第一次来？先看这篇**：`guides/onboarding-20260914.md` —— 接手与本地起环境（怎么把系统跑起来、怎么算跑通、新手最常踩的 8 个坑）。

> ⏳ **要上生产？先过这一页**：`guides/production-readiness-plan-20260914.md` —— 生产前置核查清单（7 组 26 项，🔴硬前置 / 🟡待拍板）。**26 项没核查完，不安排上线。**

## 目录（章 → 篇）

### 第 1 章 上生产
- `guides/production-readiness-plan-20260914.md` —— 生产前置核查清单（7 组 26 项）⏳ 待逐项核查（现只有第 22 项已核）

### 第 2 章 手册（怎么干一件事）
- `guides/onboarding-20260914.md` —— **接手与本地起环境（新手第一步）**：起后端/前端、库怎么来、怎么算跑通、8 个常见坑 ✅
- `guides/docs-operation-guide-20260914.md` —— 文档作业指导书：写/查文档先看这篇 ✅
- `guides/scripts-commands-20260914.md` —— 脚本命令手册：每条命令的用途/危险等级/前置 ✅
- `guides/ops-runbook-20260910.md` —— 排查手册（30 秒看全局、链路分段、故障分型）✅
- `guides/internal-https-setup-guide-20260904.md` —— 内网 HTTPS 全量方案（证书、装手机、排障）✅
- `guides/paddleocr-project-isolation-20260912.md` —— OCR 服务隔离与启动 ✅

### 第 3 章 各模块现在是什么样
- `modules/inventory.md` —— 库存管理（统一库存身份）✅
- `modules/quality.md` —— 质量管理（IQC/IPQC/FQC + 不合格处置）✅
- `modules/production.md` —— 生产管理（订单/派工/工序执行/报工）✅
- `modules/system-ops.md` —— 系统管理 + 运维 + 环境 ✅
- ⬜ 待写：`modules/sales.md`、`modules/purchase.md`、`modules/engineering.md`、`modules/print.md`

> 规则：**一个模块只允许一篇**现行真相，文件名不带日期。

### 第 4 章 为什么这么设计（`design/`）
业务流程、架构、方案（各篇自带日期；长期有效，但按当时口径读）。
- `design/event-task-state-sync-dev-20260921-016.md` —— **事件 → 任务状态联动通用方案（`task_effects`）**：把只能“关闭”的 `close_source_events` 升级为可“推进/关闭/改派/留审计”的声明式动作，业务状态状态机为唯一真源 ⏳ 设计稿，待实现（建议并入事件系统阶段 012~014）
- `design/delivery-flow-039-040-dev-20260921-039.md` —— **发货流程改造（分批发货 / 运输中退场 / 拒收回流）+ 生产完工入库口径**：发货明细表、按明细出库、拒收自动回冲库存与订单可重发、入库事件 sourceDesc 与工单号、转量产按报价单带价 ✅ 已实现待回归（039/040）
- `design/fqc-inbound-per-lot-dev-20260922-022.md` —— **成品完工入库改「一个检验批一张入库单」（完整方案）**：单据/批次粒度、判定即出单、复检红字/差额二选一（建议红冲+重出）、与 017/018/019/020/022 的合流顺序、存量不迁移、回归与风险（含唯一键 NULL 语义、IDE 污染 target/classes 两条新风险）⏳ 方案稿待拍板（与 021 稿合并，未开工）
- `design/doc-no-rules-dev-20260922-023.md` —— **单号规则总表（销售→生产→质检全链）**：现状四套规则混用核查、业内对照（SAP/Dynamics/金蝶用友 + 8 条原则）、新总则与前缀登记表、旧→新映射、5 批改造顺序、存量不追改、溢出进位三条配套 ⏳ 总表已出待拍板（未开工）
- `design/stock-ledger-receiving-issuing-balance-dev-20260923-016.md` —— **库存台账「批次明细」按业内收发存口径改造（方案）**：业内三层（单据/流水/余额）+ 收发存三栏、与现状差距（原始量在单据+流水里、缺展示与流水入口）、页面三列+变动流水抽屉+0 结存标识、**不给批次表加列（避免第二真源）**、用当日 7 个批次验证"流水派生结存 = 批次表结存"15/15 一致、分期与验收 ✅ 一期已实现待回归（dev-20260923-017：三栏+变动流水抽屉+已用尽标识；批次表未加列），二期/三期未开工
- `design/fqc-judgement-upper-bound-dev-20260923-021.md` —— **成品检验「判定可合格上界」闸门（一期设计稿）**：可判合格上界 = 批批量 − 链上已报废未回收 − 让步未客户确认；业内依据（ISO 9001 §8.7 / SAP QM 使用决策 / 数量守恒）、落点（applyJudgement 校验 + judgement-guard 只读接口 + 前端预填不良量 + 可操作提示）、边界与例外（REWORK 回收通道）、验收用例 ✅ 一期已实现待回归（dev-20260923-021：上界校验 + 复检默认不良量 + 可操作提示；二期/三期未开工）
- `design/fqc-disposition-freeze-void-recalc-dev-20260923-022.md` —— **成品检验·二期：处置结论冻结 / 随批作废 / 换代重算**：业内 UD 冻结口径、本期三项（失效批禁止处置 · 换代时不良单随批 VOID · 换代后重算工单有效合格）、非本期（撤销流需新权限点+库存反向）、验收用例与风险 ✅ 本期已实现待回归（dev-20260923-022）：失效批禁处置 / 随批 VOID / 换代重算工单 / **撤销流（本期支持报废，权限点 quality:ncr:revoke + 迁移 206）**；让步/返工撤销待下一步
- `design/quantity-conservation-gate-dev-20260923-023.md` —— **成品检验·三期：数量守恒巡检门禁**：扩 `check-inbound-lot-integrity.sh` 至八查（④判定守恒 ⑤不良台账守恒 ⑥有效批可判上界 ⑦工单完工口径 ⑧放行不超合格），接进 npm run validate；含"链级重复计入属模型固有"的口径说明与待议项 ✅ 本期已实现（dev-20260923-023）
- `design/purchase-receiving-migration-dev-20260923-003.md` —— **采购收货口径 + 「采购订单收货」迁移/复制可行性**：两条收货入口对比（`/purchase/receipt/*` vs `POST /purchase/order/{id}/receive`）、后端同源（都调 `createInboundRecordFromPurchase`，收货≠入库）、推荐「共享组件 + 收货权限收口」方案 A，并列出 6 个缺陷（检验端点缺失 404 / 收货弹窗已收与本次同字段 / 权限前后端错位 / 票据只落临时盘不入库 / 批量收货逐条建多张入库单 / ReceiveDialog 死代码）✅ 已实现待回归（dev-20260923-004，A2+检验走 IQC；另发现⑦列表不含明细已修）
- `design/doc-no-batch5-workorder-merge-dev-20260923-029.md` —— **单号·第 5 批（A 方案）：工单号两套合一 + 采购入库拆号 + 任务号 T 位 2 位**：停用 `WO-<计划号>-NN` 派生路径、工单号一律走号段 `WO+yyMMdd+3`（17→11 位，任务号 26→19、领料 24→18、完工 22→16）、采购入库改 `IN+yyMMdd+3`、T 位 3→2 及超 99 复用进位告警（已拍板）、父子关系放字段不塞单号；含 11 处生成点清单、9 条回归、风险（含一处 `-FI\d{2}` 定长截取在 3 位数时静默失效）；存量不追改 ⏳ 设计稿 + 迁移 207 已交付（committed，迁移干跑幂等未执行）；本批代码未开工（task 2247 status=0）；待拍板已清零

### 第 5 章 查事实（`reference/`）
权限矩阵、组件用法、开发规范摘要、模板清单 —— 长期有效。

### 第 6 章 决策记录（`decisions/`）
一条决定一篇（带日期与状态）✅
- `decisions/sales-delivery-receipt-policy-20260914.md` —— 销售发货/签收/回签口径（D1~D7）✅

### 第 7 章 历史（`history/`）
131 篇实施记录 / 方案 / 分析 / 核查报告 ⚠️ **默认按历史看，不保证反映当前实现**；清单见 `history/INDEX.md`。

### 第 8 章 总方案与路线
- `guides/master-plan-20260914.md` —— 整体目标、现状进度、M0~M4 路线图、多 agent 协作规则 ✅

### 第 9 章 素材与数据（不是文档，按需查）
`assets/` 图片素材｜`print_template/` 打印模板原件｜`requirements/` `sources/` 原始材料（含导入源数据）｜`tasks/` 历史任务导入｜`sql/` 迁移与数据脚本｜`accounts/` 账号（勿外传）｜`standards/` 规范（`CONVENTIONS.md` 唯一真源）

## 导读（我是谁 / 我要干嘛 → 按顺序读）

| 我是谁 / 我要干嘛 | 按顺序读 |
|---|---|
| **我是新手，第一次接手这个系统** | 第 2 章 **《接手与本地起环境》**（先看这篇）→ `AGENTS.md` → `standards/CONVENTIONS.md` |
| 新人 agent（只会干活） | `AGENTS.md`（根）→ `standards/CONVENTIONS.md` → 第 2 章《脚本命令手册》 |
| **我要上生产** | 第 1 章（26 项核查）→ 第 2 章《脚本命令手册》（迁移/备份怎么跑） |
| 我要查某模块现在是什么样 | 第 3 章 → 该模块那一篇 |
| 我要改代码 / 提交 | `AGENTS.md` + `standards/CONVENTIONS.md` |
| 我要写 / 整理文档 | 第 2 章《文档作业指导书》 |
| 我要跑脚本 / 动数据库 | 第 2 章《脚本命令手册》 |
| 我要查当时的来龙去脉 | 第 7 章 → `history/INDEX.md` 按文件名/主题搜 |
| 我要看整体目标与进度 | 第 8 章《整体方案与路线》 |

## 维护标准（新文档必须遵守）

1. **先判类型**：现在是什么样 → `modules/`；当时怎么做的 → `history/`（**必须登记 `history/INDEX.md`**）；为什么这么设计 → `design/`；手册/排障/部署 → `guides/`；查事实 → `reference/`；决策 → `decisions/`；原始需求材料 → `requirements/`；DB 脚本 → `sql/`；过期 → `archive/YYYY/`。
2. **命名**：`history/` `design/` `guides/` `reference/` `decisions/` 用 `<主题>-YYYYMMDD.md`（有任务码写 `<主题>-dev-YYYYMMDD-NNN.md`）；`modules/` 用 `<模块>.md` 不带日期；禁止 `final`/`new`/`copy` 后缀。
3. **头信息**（正文第一屏固定 5 行）：`状态` / `任务` / `被取代或取代` / `什么情况看这篇` / `最后复核`。
4. **写完跑门禁**：`cd jjx-web && npm run check:docs`（R1 登记 INDEX、R2 UTF-8 BOM、R3 命名、R4 `modules/` 不带日期；存量基线只许缩小）。
5. **改名前先 `git grep` 全仓引用**（`sys_task.description`、脚本头注释、技能文档里的路径要同步）。
6. **禁止**：根目录散文件、临时文件（`~$` 等）、双份备份（用 git）。

## 现状快照

- 2026-09-03 整理：删 jjx-docs 内旧测试床残留；根目录 13 个散文件归档/归位；建 `archive/2026`；写本 README 与 analysis/INDEX.md。
- 2026-09-10 整理：建 `current/` 现行真相层（库存/质量/生产/系统运维 4 篇）；本 README 增加"我要干嘛 → 看哪篇"入口表。
- 2026-09-14 按业内标准归位目录（任务 dev-20260914-004）：`current/`→`modules/`、`flows/`→`design/`、`analysis/`→`history/`、`reference/` 拆三类（手册→`guides/`、事实留 `reference/`）；门禁脚本与基线同步改名。**归不进这八类的先不动**（`specs/` 9 篇因命名规则无日期、`reference/` 3 篇旧总结、`sql/`、`accounts/`、`assets/`、`print_template/`、`requirements/`、`sources/`、`tasks/`）。注意：本次改名后，历史 `sys_task.description` 里写的 `jjx-docs/analysis/...` 旧路径不再有效，按文件名搜索即可找到（文件本身都在 `history/`）。
- 2026-09-14 补齐两本手册（dev-20260914-004/005）：`guides/docs-operation-guide-20260914.md`、`guides/scripts-commands-20260914.md`；`scripts/` 下 5 个脚本补 `--help` 与危险等级/前置标注，**新增或改脚本必须同步该手册**（CONVENTIONS §1）。
- 2026-09-14 入口改造 + 整体方案（dev-20260914-009）：本页由"表格式入口"改为**全书目录 + 指导书**；新增 `guides/master-plan-20260914.md`（目标/进度/M0~M4/多 agent 规则）；`guides/production-readiness-plan-20260914.md`（生产前置 26 项）；CONVENTIONS 追加 §11 多 agent 并行协作四条硬规则。

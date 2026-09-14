# JJX 文档库（入口 = 全书目录 + 指导书）

本目录只放文档。代码在 jjx-server / jjx-web，数据脚本在 jjx-docs/sql。

三层结构：**`modules/` 是"现在是什么样"**（要维护）／**`history/` 是"当时怎么做的"**（快照，只留痕）／**`standards/` `reference/` `guides/` 是"规矩和手册"**（长期有效）。

## 怎么用这一页

1. 先看「目录」，找到你要找的那类文档在哪一章；
2. 再看「导读」，它给的是**阅读顺序**（不是清单）；
3. **判新旧只看本页的 ✅ / ⏳ / ⚠️ 标记，不看文件修改时间**（本仓库 mtime 被历次整理刷新过，不可信）。

## 目录（章 → 篇）

### 第 1 章 上生产
- `guides/production-readiness-plan-20260914.md` —— 生产前置核查清单（7 组 26 项）⏳ 待逐项核查（现只有第 22 项已核）

### 第 2 章 手册（怎么干一件事）
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

### 第 5 章 查事实（`reference/`）
权限矩阵、组件用法、开发规范摘要、模板清单 —— 长期有效。

### 第 6 章 决策记录（`decisions/`）
一条决定一篇（带日期与状态）⏳ 目录待启用（写第一条决策记录时创建）。

### 第 7 章 历史（`history/`）
131 篇实施记录 / 方案 / 分析 / 核查报告 ⚠️ **默认按历史看，不保证反映当前实现**；清单见 `history/INDEX.md`。

### 第 8 章 总方案与路线
- `guides/master-plan-20260914.md` —— 整体目标、现状进度、M0~M4 路线图、多 agent 协作规则 ✅

### 第 9 章 素材与数据（不是文档，按需查）
`assets/` 图片素材｜`print_template/` 打印模板原件｜`requirements/` `sources/` 原始材料（含导入源数据）｜`tasks/` 历史任务导入｜`sql/` 迁移与数据脚本｜`accounts/` 账号（勿外传）｜`standards/` 规范（`CONVENTIONS.md` 唯一真源）

## 导读（我是谁 / 我要干嘛 → 按顺序读）

| 我是谁 / 我要干嘛 | 按顺序读 |
|---|---|
| 新人 agent（只会干活） | `AGENTS.md`（根）→ `standards/CONVENTIONS.md` → 第 2 章两本手册 |
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

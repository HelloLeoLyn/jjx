# JJX 文档库（入口页）

本目录只放文档。代码在 jjx-server / jjx-web，数据脚本在 jjx-docs/sql。

**进来先看这一页就够了。**

## 谁从哪进

| 谁 | 从哪进 |
|---|---|
| 人（写文档 / 查文档） | 就是这一页 |
| 代码 agent（改代码 / 跑脚本） | 仓库根 `AGENTS.md` + `standards/CONVENTIONS.md` |

## 门（我要干嘛 → 去哪）

| 我要干嘛 | 去哪 | 时效 |
|---|---|---|
| 我要写/改代码、要提交 | `AGENTS.md` + `standards/CONVENTIONS.md` | ✅ 现行（唯一真源） |
| 我要跑脚本、要动数据库 | `guides/scripts-commands-20260914.md`（命令手册）+ `sql/` | ✅ 现行 |
| 我要写 / 整理文档 | `guides/docs-operation-guide-20260914.md`（作业指导书：放哪/叫啥名/头信息/登记/检查） | ✅ 现行 |
| 我要查某模块现在是什么样 | `modules/<模块>.md` | ✅ 现行（会过期，定期复核） |
| 我要查当时怎么做的、为什么 | `history/INDEX.md` → 具体某篇 | ⚠️ 历史快照，不保证最新 |
| 我要查规矩（命名/落盘/备份） | `standards/CONVENTIONS.md` | ✅ 现行 |
| 我要部署 / 排障 | `guides/ops-runbook-20260910.md`、`guides/internal-https-setup-guide-20260904.md` | ✅ 现行（手册） |
| 我要看流程 / 架构 / 方案 | `design/` | ✅ 现行（设计） |

**判断时效只看这张表，不要看文件修改时间**——本仓库 mtime 被历次整理刷新过，不可信。

## 目录（八类）

| 目录 | 放什么 | 说明 |
|---|---|---|
| `guides/` | 怎么干一件事：**文档作业指导书**、**脚本命令手册**、运维 runbook、排障、部署 | 长期有效 |
| `reference/` | 查事实：权限矩阵、组件用法、模板清单、开发规范摘要 | 长期有效 |
| `modules/` | 模块**现在**是什么样（一个模块一篇，文件名不带日期） | 唯一会过期的文档类型 |
| `design/` | 为什么这么设计：业务流程、架构、方案 | 长期有效 |
| `decisions/` | 决策记录：一条决定一篇（带日期 + 状态） | 待启用（写第一条决策时创建） |
| `history/` | 当时怎么做的：实施记录、规格快照（131 篇，清单见 `history/INDEX.md`） | 默认按历史看 |
| `archive/` | 过期件（按年份 `archive/YYYY/`） | 不再更新 |
| 素材与数据 | `assets/`（图片）`print_template/`（模板原件）`requirements/` `sources/`（原始材料）`tasks/`（历史导入）`sql/`（迁移与数据脚本）`accounts/`（账号）`standards/`（规范） | 不是文档，按需查 |

## modules/ 现行真相（只读这个就够）

| 模块 | 文件 | 状态 |
|---|---|---|
| 库存管理（统一库存身份） | `modules/inventory.md` | ✅ 已写 |
| 质量管理（IQC/IPQC/FQC + 不合格处置） | `modules/quality.md` | ✅ 已写 |
| 生产管理（订单/派工/工序执行/报工） | `modules/production.md` | ✅ 已写 |
| 系统管理 + 运维 + 环境 | `modules/system-ops.md` | ✅ 已写 |
| 销售管理 | `modules/sales.md` | ⬜ 待写 |
| 采购管理 | `modules/purchase.md` | ⬜ 待写 |
| 工程管理 / 产品 | `modules/engineering.md` | ⬜ 待写 |
| 打印体系 | `modules/print.md` | ⬜ 待写 |

规则：**一个模块只允许一篇现行真相**（`modules/<模块>.md`）。新的实施记录一律进 `history/`，只当"某条规则的来历"，需要时才点开。

## 维护标准（新文档必须遵守）

1. **先判类型**：现行真相 → `modules/`；当时怎么做的（实施记录/分析/核查）→ `history/`；为什么这么设计 → `design/`；手册/运维/排障 → `guides/`；查事实（矩阵/清单/组件用法）→ `reference/`；决策 → `decisions/`；原始需求材料 → `requirements/`；DB 脚本 → `sql/`；过期 → `archive/YYYY/`。
2. **命名**：`history/`、`design/` 用 `<主题>-dev-YYYYMMDD-NNN.md`（无任务码退化为 `<主题>-YYYYMMDD.md`）；`modules/` 用 `<模块>.md` 不带日期；`guides/`、`reference/` 用 `<主题>-YYYYMMDD.md`。禁止 `final`/`new`/`copy` 后缀（版本交给 git）。
3. **文档头**（正文第一屏固定 5 行）：`状态：✅已实施 | ⏳待做 | 待确认 | 已废弃` + 任务号；`任务：dev-YYYYMMDD-NNN`；`被取代/取代：路径`（没有写"无"）；`什么情况看这篇：一句话`；`最后复核：YYYY-MM-DD`（现行真相类必填）。
4. **登记与自检**：新建 `history/` 文档必须登记 `history/INDEX.md`（零容忍，门禁管）；所有文档 UTF-8 **带 BOM**；改完跑 `cd jjx-web && npm run check:docs`（已并入 `npm run validate`；存量债务在 `scripts/docs-baseline.json`，**只许缩小**，不要用 `--write-baseline` 全量重写）。
5. **生命周期**：被新文档取代 → 头部写 `已被 <新文档路径> 取代`，并移 `archive/YYYY/`；彻底过期的分析移 `archive/YYYY/`；**改名前先 `git grep` 全仓引用**（`sys_task.description`、脚本、技能文档里的路径要同步，否则失效）。
6. **禁止**：根目录散文件、临时文件（`~$` 等）、node_modules、双份备份（用 git）。

## 现状快照

- 2026-09-03 整理：删 jjx-docs 内旧测试床残留；根目录 13 个散文件归档/归位；建 archive/2026；写本 README 与 analysis/INDEX.md。
- 2026-09-10 整理：建 `current/` 现行真相层（库存/质量/生产/系统运维 4 篇）；本 README 增加"我要干嘛 → 看哪篇"入口表。
- 2026-09-14 按业内标准归位目录（任务 dev-20260914-004）：`current/`→`modules/`、`flows/`→`design/`、`analysis/`→`history/`、`reference/` 拆三类（手册→`guides/`、事实留 `reference/`）；门禁脚本与基线同步改名。**归不进这八类的先不动**（`specs/` 9 篇因命名规则无日期、`reference/` 3 篇旧总结、`sql/`、`accounts/`、`assets/`、`print_template/`、`requirements/`、`sources/`、`tasks/`）。注意：本次改名后，历史 `sys_task.description` 里写的 `jjx-docs/analysis/...` 旧路径不再有效，按文件名搜索即可找到（文件本身都在 `history/`）。
- 2026-09-14 补齐两本手册（任务 dev-20260914-004/005）：`guides/docs-operation-guide-20260914.md`（文档作业指导书：速查卡 + 命名/头信息/索引/流程/禁止项）、`guides/scripts-commands-20260914.md`（脚本命令手册：每条命令的用途/危险等级🟢🟡🔴/前置/输出怎么读）。`scripts/` 下 5 个脚本补齐了 `--help` 与危险等级/前置标注；**新增或改脚本必须同步该手册**（规则已进 CONVENTIONS §1）。

# JJX 文档库

本目录只放文档。代码在 jjx-server / jjx-web，数据脚本在 jjx-docs/sql。

**先分清三层**：`current/` 是"现在是什么样"（现行真相，要维护）；`analysis/ specs/ flows/` 是"当时怎么做的"（快照，留痕）；`standards/ reference/` 是"规矩和手册"（长期有效）。

## 先看这里：我要干嘛 → 看哪篇

| 我要干嘛 | 看哪篇 | 时效 |
|---|---|---|
| 动手改代码 / 提交 | `AGENTS.md` + `standards/CONVENTIONS.md` | ✅ 现行（规范，唯一真源） |
| 部署 / 环境出问题 | `reference/ops-runbook-20260910.md` + `reference/internal-https-setup-guide-20260904.md` | ✅ 现行（手册） |
| 查某模块现在是什么样 | `current/<模块>.md` | ✅ 现行（真相） |
| 查某功能当时的设计 / 为什么这么做 | `analysis/<主题>-dev-YYYYMMDD-NNN.md` | ⚠️ 快照，不保证最新 |
| 查某次改动 / 谁改的 | `sys_task` / `sys_oper_log`（按任务码 dev-YYYYMMDD-NNN） | 📜 历史记录 |
| 查原始需求 / 实施规格 | `requirements/`、`specs/` | 📌 长期 |

**判断时效只看这张表，不要看文件修改时间**——本仓库的 mtime 被历次整理刷新过，不可信。

## current/ 现行真相（模块当前状态，只读这个就够）

| 模块 | 文件 | 状态 |
|---|---|---|
| 库存管理（统一库存身份） | `current/inventory.md` | ✅ 已写 |
| 质量管理（IQC/IPQC/FQC + 不合格处置） | `current/quality.md` | ✅ 已写 |
| 生产管理（订单/派工/工序执行/报工） | `current/production.md` | ✅ 已写 |
| 系统管理 + 运维 + 环境 | `current/system-ops.md` | ✅ 已写 |
| 销售管理 | `current/sales.md` | ⬜ 待写 |
| 采购管理 | `current/purchase.md` | ⬜ 待写 |
| 工程管理 / 产品 | `current/engineering.md` | ⬜ 待写 |
| 打印体系 | `current/print.md` | ⬜ 待写 |

规则：**一个模块只允许一篇现行真相**（`current/<模块>.md`）。新的实施记录一律进 `analysis/`，只当"某条规则的来历"，需要时才点开。

## 目录导航

| 目录 | 放什么 | 说明 |
|---|---|---|
| `current/` | 各模块**现行真相**（当前生效的规则/入口/权限/关键表） | 唯一会"过期"的文档类型，定期复核 |
| requirements/ | 需求原始材料（用户给的表/描述/邮件转述） | 不改写原文 |
| specs/ | 开发规格（dev-YYYYMMDD-NNN 实施图纸） | 交 codex 前定稿，含【明确不做】 |
| analysis/ | 分析 / 方案 / 盘点 / 核查报告 **+ 全部实施记录** | 122 篇，见 analysis/INDEX.md（默认当历史看） |
| reference/ | 体系知识 / 矩阵 / 手册（https、排查） | 长期有效 |
| flows/ | 流程图 / 流程说明 | |
| standards/ | 协作规范（CONVENTIONS.md 唯一真源） | 改规范改这里，同步根 AGENTS.md |
| sql/ | migrations（编号迁移）+ 数据脚本 | 见 sql/migrations 内注释 |
| print_template/ | 打印模板原件（xls/doc/xlsx） | 上传台账用 |
| assets/ | 图片 / 附件 | |
| accounts/ | 测试账号等敏感信息 | 勿外传 |
| tasks/ | 历史任务导入源（tasks.json + migrate_dev_tasks.py） | 历史归档，勿改 |
| archive/ | 过期 / 一次性文档（按年份） | 可随时查阅，不再更新 |

## 维护标准（新文档必须遵守）

1. **先判类型**：现行真相 → `current/`；实施记录/分析 → `analysis/`；需求 → `requirements/`；规格 → `specs/`；迁移 → `sql/migrations/NN_<name>.sql`。
2. 命名：一律 `<主题>-<dev-YYYYMMDD-NNN>.md`；无任务号的用 `<主题>-YYYYMMDD.md`；`current/` 下的现行真相用 `<模块>.md`（不带日期）。
   禁止：无日期文件名（current/ 除外）、`final`/`new`/`copy` 后缀（版本交给 git）。
3. 文档头：正文第一行写状态行——`状态：✅已实施 | ⏳待做 | 待确认 | 已废弃`，附任务号。
4. **新建 analysis/ 文档必须登记 analysis/INDEX.md**（生成脚本见其头部注释），UTF-8 带 BOM；改完文档跑 `cd jjx-web && npm run check:docs` 自检（已并入 `npm run validate`；存量债务在 `scripts/docs-baseline.json`，只许缩小）。
5. 生命周期：
   - 被新文档取代 → 在头部写 `已被 <新文档路径> 取代`，并移 `archive/YYYY/`；
   - 彻底过期的分析移 archive/YYYY/；
   - **sys_task.description / 技能文档引用的路径不许擅自移动**——要动先 `git grep` 全仓引用并同步。
6. 禁止：根目录散文件、临时文件（~$ 等）、node_modules、双份备份（用 git）。

## 现状快照

- 2026-09-03 整理：删 jjx-docs 内旧测试床残留（server.js/node_modules/test/，活体在 /mnt/d/openclaw-workspace/docs）；根目录 13 个散文件归档/归位；建 archive/2026；task-analysis/task-dashboard 并入 archive；写本 README 与 analysis/INDEX.md。引用保护：analysis/ 下文档**未改名未移动**，sys_task 引用路径全部有效。
- 2026-09-10 整理：建 `current/` 现行真相层（库存/质量/生产/系统运维 4 篇）；本 README 增加"我要干嘛 → 看哪篇"入口表；明确 analysis/ 默认按历史快照看待。**未移动任何 analysis/ 文件**，引用路径继续有效。

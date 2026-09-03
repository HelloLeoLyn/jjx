# JJX 文档库

本目录只放文档。代码在 jjx-server / jjx-web，数据脚本在 jjx-docs/sql。

## 目录导航

| 目录 | 放什么 | 说明 |
|---|---|---|
| requirements/ | 需求原始材料（用户给的表/描述/邮件转述） | 不改写原文 |
| specs/ | 开发规格（dev-YYYYMMDD-NNN 实施图纸） | 交 codex 前定稿，含【明确不做】 |
| analysis/ | 分析 / 方案 / 盘点 / 核查报告 | 91 篇，见 analysis/INDEX.md |
| reference/ | 体系知识 / 矩阵 / 台账源头数据 | 长期有效 |
| flows/ | 流程图 / 流程说明 | |
| sql/ | migrations（编号迁移）+ 数据脚本 | 见 sql/migrations 内注释 |
| print_template/ | 打印模板原件（xls/doc/xlsx） | 上传台账用 |
| assets/ | 图片 / 附件 | |
| accounts/ | 测试账号等敏感信息 | 勿外传 |
| tasks/ | 历史任务导入源（tasks.json + migrate_dev_tasks.py） | 历史归档，勿改 |
| archive/ | 过期 / 一次性文档（按年份） | 可随时查阅，不再更新 |

## 维护标准（新文档必须遵守）

1. 放哪：
   - 需求原文 → requirements/
   - 实施规格 → specs/（文件内第一行写任务号）
   - 分析/方案/盘点/核查 → analysis/
   - 长期知识/矩阵 → reference/
   - 迁移 SQL → sql/migrations/NN_<name>.sql
2. 命名：一律 `<主题>-<dev-YYYYMMDD-NNN>.md`；无任务号的用 `<主题>-YYYYMMDD.md`。
   禁止：无日期文件名、`final`/`new`/`copy` 后缀（版本交给 git）。
3. 文档头：正文第一行写状态行——`状态：✅已实施 | ⏳待做 | 待确认 | 已废弃`，附任务号。
4. 生命周期：
   - 任务核销后，spec 在 analysis 里留档即可，不删；
   - 彻底过期的分析移 archive/YYYY/；
   - **sys_task.description / 技能文档引用的路径不许擅自移动**——要动先 `git grep` 全仓引用并同步。
5. 禁止：根目录散文件、临时文件（~$ 等）、node_modules、双份备份（用 git）。
6. 索引：analysis/ 增删后更新 analysis/INDEX.md（生成脚本见其头部注释）。

## 现状快照（2026-09-03 整理）

- 整理动作：删 jjx-docs 内旧测试床残留（server.js/node_modules/test/，活体在 /mnt/d/openclaw-workspace/docs）；根目录 13 个散文件归档/归位；建 archive/2026；task-analysis/task-dashboard 并入 archive；写本 README 与 analysis/INDEX.md。
- 引用保护：analysis/ 下 91 篇**未改名未移动**，sys_task 引用路径全部有效。

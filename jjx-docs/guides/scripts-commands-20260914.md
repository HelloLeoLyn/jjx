# 脚本命令手册（唯一入口）

状态：✅已实施
任务：dev-20260914-005
被取代 / 取代：无
什么情况看这篇：要跑仓库里的脚本（迁移/备份/导出/清理/自检）时，先看这一页
最后复核：2026-09-14

> 规则：**新增或修改 `scripts/` 下的脚本，必须同步更新这一页。**（规范见 `standards/CONVENTIONS.md`）
> 危险等级：🟢 只读（随时可跑） / 🟡 写文件或本地配置（可回退） / 🔴 改数据库（必须走"备份 → 人工确认 → 执行"）

## 0. 常用速查

| 我要干嘛 | 命令 | 等级 |
|---|---|---|
| 开工先自检 | `bash scripts/agent-preflight.sh` | 🟢 |
| 看库迁移版本差多少 | `bash scripts/db-migrate.sh --status` | 🟢 |
| 看初始化快照还新不新 | `bash scripts/db-export-init-subset.sh --verify` | 🟢 |
| 清理前先看会删什么 | `bash scripts/db-clean-test-data.sh` | 🟢 |
| 装 git 闸门（每 clone 一次） | `bash scripts/install-hooks.sh` | 🟡 |
| 重出初始化数据子集 | `bash scripts/db-export-init-subset.sh --task dev-YYYYMMDD-NNN` | 🟡 |
| 立刻拍一份全库快照 | `bash scripts/db-backup.sh --tag before-xxx --task dev-YYYYMMDD-NNN` | 🟡 |
| 看全库备份会落哪/会清谁 | `bash scripts/db-backup.sh --dry-run` | 🟢 |
| 执行一个迁移 | `bash scripts/db-migrate.sh <NN_x.sql> --yes --task dev-YYYYMMDD-NNN` | 🔴 |
| 清理测试数据 | `bash scripts/db-clean-test-data.sh --execute` | 🔴 |
| 改完代码/文档自查 | `cd jjx-web && npm run validate` | 🟢 |

---

## 1. scripts/agent-preflight.sh —— 开工自检

- 用途：动手前一条命令看全局——git 闸门是否装、库迁移版本是否与代码一致、只读账号是否可用、文档门禁过不过、工作区有没有别人的 WIP。
- 危险等级：🟢 只读（不写库、不写文件；只调用 `db-migrate.sh --status` 与 `node scripts/check-docs.mjs`）。
- 前置：在仓库内执行；数据库可连（连不上会标 ✘，但不会改任何东西）。
- 命令：`bash scripts/agent-preflight.sh`
- 输出怎么读：5 段逐项 ✓ / ⚠ / ✘ —— **✘＝阻塞项（挡开工）**，**⚠＝提醒项（不挡开工）**；结尾会写清是"自检通过"还是"未通过"，通不过就先处理标 ✘ 的项。
- 退出码：`0`=无阻塞项（可能带 ⚠ 提醒）；`1`=有阻塞项 ✘。
- 两类常见 ⚠：① 工作区有未提交改动（只是提醒你提交时别混别人的 WIP）② `jjx_ro` 不可用（只影响 commit-msg 的任务码真实性校验）。
- 备注（2026-09-14 修复，任务 dev-20260914-012）：曾误报「库中未记录已应用版本」——原因是抓的字段名和 `db-migrate.sh --status` 实际输出（`已应用: 66,67,…`）对不上；现已按"取集合最大值 vs 目录最大号"比较，"首次登记前"才提示未记录，且只算 ⚠。

## 2. scripts/db-migrate.sh —— 迁移唯一执行通道

- 用途：执行迁移的**唯一**入口（内部固定顺序：备份 → 执行 → 写 `sys_config.ops.schema.*`；备份失败或执行失败都不写版本）。
- 危险等级：🔴 改数据库（执行迁移）／🟡 只写版本记录（`--record`）／🟢 只读（`--status`）。
- 前置：迁移文件放在 `jjx-docs/sql/migrations/`（`NN_<描述>.sql`）；`JJX_BACKUP_DIR` 在仓库外可写；要动库必须带真实任务码。
- 命令：
  - 查版本：`bash scripts/db-migrate.sh --status`
  - 执行：`bash scripts/db-migrate.sh <NN_x.sql> --yes --task dev-YYYYMMDD-NNN`
  - 接管已有库只登记版本：`bash scripts/db-migrate.sh --record <NN> --yes`
- 输出怎么读：三段 1/3 备份（给字节数/表数/md5）→ 2/3 执行 → 3/3 记录版本；中途报错即中止且不记版本。
- 退出码：0=成功，非 0=中止（未执行或未记版本）。
- 注意：**不要直接 `mysql < 文件`** 绕过本脚本（等于没有备份、也没有版本记录）。

## 3. scripts/db-export-init-subset.sh —— 初始化数据子集（滚动重出）

- 用途：按清单导出"进入初始化脚本的正式数据子集"快照（给新环境开账用，**不是全库备份**）；也可只读校验最新快照是否还与新库一致。
- 危险等级：🟡 只读库 + 写仓库文件（产出新快照，可回退）／`--verify` 为 🟢 纯只读。
- 前置：清单 `jjx-docs/sql/init/init-subset-tables.txt` 存在且其中的表都在库里（有已下线的表会直接中止）；`--task` 必须是真实任务码。
- 命令：
  - 重出：`bash scripts/db-export-init-subset.sh --task dev-YYYYMMDD-NNN`
  - 只体检：加 `--dry-run`（打印逐表行数，不落文件）
  - 只读校验：`bash scripts/db-export-init-subset.sh --verify`（✓最新 / ⚠行数有差异 / ✘缺表或表集合不一致）
- 输出怎么读：逐表行数 + 合计 → 文件路径/字节数/md5/表数/总行数 → 列出旧份（**不自动删**，删旧份要人工确认后单独做）。
- 退出码：0=成功或快照一致，1=失败或有差异。
- 注意：产出会进 git（属 CONVENTIONS §2 例外备案），git 里**只留最新一份**。

## 4. scripts/db-clean-test-data.sh —— 清理测试数据入口

- 用途：`jjx-docs/sql/00_clean_test_data.sql`（81 条 TRUNCATE + 1 条 DELETE）的**唯一**入口。
- 危险等级：🟢 无参数=只读体检（不写库）／🔴 `--execute` 真清理（固定顺序：体检 → 全库备份 → 人工确认 → 执行）。
- 前置：`--execute` 必须**在终端手工执行**（agent/管道一律拒绝）；确认方式=手工输入库名 `jjx_erp_db`；`JJX_BACKUP_DIR` 可写。
- 命令：
  - 体检：`bash scripts/db-clean-test-data.sh`
  - 真清：`bash scripts/db-clean-test-data.sh --execute`
- 输出怎么读：① TRUNCATE 组（有数据的表逐条列出行数 + 合计）② DELETE 组（将删/保留条数）③ 与初始化清单交叉（非 0 要警惕）→ 顺带自动跑一次快照校验 → 执行后打印备份路径/md5，并在 `$JJX_BACKUP_DIR/clean-test-data-log.txt` 留痕（脚本会清空 `sys_oper_log`，库里留不下痕迹）。
- 退出码：0=体检通过或清理成功，1=拒绝执行/中止/失败。

## 5. scripts/clean-archive-ocr-data.sh —— 清档案 OCR 测试数据（并行会话产出）

- 用途：只删历史档案 OCR 的测试档案及其草稿产物（范围很窄，1~2 条）。
- 危险等级：🔴 改数据库。**注意：本脚本用 `--yes` 放行，与上面"🔴 必须手输库名"的统一口径不一致**（待用户决定是否统一）。
- 前置：默认只预览；`--yes` 才备份并删除。
- 命令：`bash scripts/clean-archive-ocr-data.sh`（预览）／加 `--yes`（真删）。
- 退出码：0=成功，非 0=中止。

## 6. scripts/db-backup.sh —— 独立全库备份（2026-09-14 新增，任务 dev-20260914-027）

- 用途：不触发任何库内变更，只想**立刻拿一份全库快照**时用（补上此前只能手敲 `mysqldump` = 绕过规范入口的缺口）。
- 与其它脚本的边界：迁移/清测试数据各自**内部自带**全库备份（本脚本不替代它们）；`db-export-init-subset.sh` 出的是初始化交付物、**不是备份**。
- 危险等级：🟡 只读数据库 + 写仓库外文件（可回退）；`--dry-run` 为 🟢 纯预览（不落盘）。
- 前置：`mysqldump` 可用；数据库可达；`JJX_BACKUP_DIR`（默认仓库同级 `jjx-backups/`）位于 Git 仓库外且可写。
- 命令：
  - 例行：`bash scripts/db-backup.sh`
  - 带来源/任务码：`bash scripts/db-backup.sh --tag before-xxx --task dev-YYYYMMDD-NNN`
  - 只看不写：`bash scripts/db-backup.sh --dry-run`
  - 常用开关：`--reason <文案>`、`--out-dir <dir>`、`--keep-days <N>`（默认 14）、`--no-clean`
- 输出怎么读：报告库/目录/文件名/原因/任务码/清理策略 → 备份后给 **字节数 / 表数 / md5** + 恢复命令 → 再走过期清理段。
- 产物：`jjx_erp_db_backup_YYYYMMDD-HHmm[_tag].sql`，同名冲突自动加 `-2/-3`，**不覆盖**；文件头 1~3 行=备份人/原因/任务码，并追加一行到 `<备份目录>/db-backup-log.txt` 留痕。
- 清理口径：只删 `<备份目录>` 下超过 `--keep-days`（默认 14 天）的 `jjx_erp_db_backup_*.sql`（= 全库备份）；guard 表级备份、`.tar.gz` 等**只提示不删**。
- 退出码：0=备份成功；1=前置不满足 / 备份失败 / 产物异常（<1KB 或 0 张表）。
- 注意：备份写入 Git 仓库外是硬要求（CONVENTIONS §2），`--out-dir` 传仓库内路径会直接被拒。

## 7. scripts/install-hooks.sh —— 安装 git 闸门

- 用途：把本仓库的 `pre-commit` + `commit-msg` 钩子挂上（`core.hooksPath=scripts/hooks`）。
- 危险等级：🟡 只写本地 git 配置，不碰数据库；可卸载。
- 前置：在仓库内执行；`scripts/hooks/*` 存在。
- 命令：`bash scripts/install-hooks.sh`；卸载：`git config --unset core.hooksPath`
- 输出怎么读：确认 `core.hooksPath = scripts/hooks` + 已启用钩子清单。
- 退出码：0=成功。

## 8. 自动跑（不用手敲）

| 钩子 | 什么时候跑 | 拦什么 |
|---|---|---|
| `scripts/hooks/pre-commit` | 每次 `git commit` | `jjx-docs/sql/`（除 backups/）与 `jjx-docs/standards/` 下的删除/移动；`status-magic-baseline.json` 放大 |
| `scripts/hooks/commit-msg` | 每次 `git commit` | 提交信息必须有任务码 `dev-YYYYMMDD-NNN`，且该码真实存在于 `sys_task`（用只读账号 `jjx_ro` 校验；库不可达时只提醒不阻塞） |

单次跳过：`git commit --no-verify`（确认后果再用）。

## 9. npm 门禁（在 `jjx-web/` 下跑）

| 命令 | 查什么 |
|---|---|
| `npm run check:status-enums` | 状态魔法值（必须用具名枚举，基线只许缩小） |
| `npm run check:docs` | 文档规则（`history/`：登记 INDEX、BOM、命名；`modules/`：不带日期 + BOM） |
| `npm run validate` | 上面两条 + `vue-tsc --noEmit`（提交前自查跑这个） |

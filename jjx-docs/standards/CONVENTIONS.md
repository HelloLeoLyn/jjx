# JJX ERP 协作规范（备份/文档/命名/提交）

> **唯一真源**：本文件。多 agent（OpenClaw / Hermes / Codex）共享同一仓库 `/home/administrator/jjx`，所有与"落盘、命名、提交"相关的动作以本文件为准。
> 仓库根 `AGENTS.md` 是速查摘要；修改规范请改这里并同步摘要。

---

## 1. 路径总表

| 场景 | 固定位置 | 说明 |
|---|---|---|
| DB 全量备份 | `sql/backups/` | 改库前必做；随仓库提交 |
| DB 迁移/上线脚本 | `jjx-docs/sql/` | 可重复执行、幂等优先 |
| 分析/方案/测试计划/报告 | `jjx-docs/analysis/` | 用户可读，UTF-8 **带 BOM** |
| 打印模板/素材 | `jjx-docs/assets/` `jjx-docs/print_template/` | |
| 需求/参考归档 | `jjx-docs/requirements/` `jjx-docs/reference/` `jjx-docs/archive/` | |
| 每日工作记录（OpenClaw） | workspace `memory/YYYY-MM-DD.md` | 其他 agent 可选 |
| 临时/中间产物 | `/tmp` 或仓库 `.tmp/`（gitignore） | 当日清理，不进正式目录 |
| 任务登记 | `sys_task` 表 | `dev-YYYYMMDD-NNN` |

---

## 2. 数据库备份规范（红线：先备份再动库）

**触发时机**：任何迁移脚本执行前、批量 UPDATE/DELETE 前、修复疑似脏数据前、跨环境导数据前。

**统一命令**（固定参数，不用花式选项）：

```bash
mkdir -p /home/administrator/jjx/sql/backups
mysqldump -u root -p123456 --default-character-set=utf8mb4 \
  --single-transaction --set-gtid-purged=OFF --no-tablespaces \
  jjx_erp_db > /home/administrator/jjx/sql/backups/jjx_erp_db_backup_$(date +%Y%m%d-%H%M)_<tag>.sql
md5sum /home/administrator/jjx/sql/backups/jjx_erp_db_backup_*.sql
```

**命名**：`jjx_erp_db_backup_YYYYMMDD-HHmm[_tag].sql`
- `<tag>` 用简短英文原因：`before-iqc-migration`、`before-biz-no-rule`、`daily`、`before-cleanup`。无 tag 表示例行。
- 文件头第 1~3 行注释写明：备份人（agent 名）、原因、关联任务码（若有）。

**验证**：执行后必须 `md5sum` + `grep -c "CREATE TABLE"` 抽查，并在汇报里给出 md5。
**保留**：默认随仓库提交（跨机器一致）；单文件 > 20MB 先 gzip（`.sql.gz`）再入库；超 100MB 不入库，放共享盘并在文件位置留 `.gitkeep`+README 说明。
**禁止**：备份写到各自 workspace 的任意目录（如 `memory/*.sql`、`/tmp/backup.sql`）。

---

## 3. 迁移/上线 SQL 规范

- 位置：`jjx-docs/sql/`
- 命名：`YYYYMMDD_<域>_<用途>.sql`（如 `20260906_unified_iqc_phase1.sql`）；同批次多阶段用 `_phase1/2/3` 后缀
- 内容要求：
  - 幂等优先（`ADD COLUMN IF NOT EXISTS` 不可用时，先查 information_schema 或 `WHERE NOT EXISTS` 守卫）
  - 破坏性语句（DROP/TRUNCATE/DELETE）必须显式注释原因，单独文件，禁止与建表混在一个"安全"文件里
  - 文件编码 UTF-8；执行后登记 sys_task 或在本文件/任务描述留执行记录（时间、执行人 agent）
- 执行纪律：先备份（第 2 节）→ 审阅 → 执行 → 验证 → 汇报

---

## 4. 分析/方案/测试计划文档

- 位置：`jjx-docs/analysis/`；命名 `YYYYMMDD-<英文短横线主题>.md`
- **UTF-8 带 BOM**（手机阅读不乱码），验证：
  ```bash
  python3 -c "d=open('文件','rb').read(); assert d[:3]==b'\xef\xbb\xbf'; d.decode('utf-8')"
  ```
- 只产 md，不产 PDF（2026-08-13 定）；要交付给用户时用 MEDIA 直接发文件
- 内容骨架：背景/现状证据（数据或代码行号）/方案/验证步骤/待确认点——数据说话，不写猜测

---

## 5. git 提交规范

- 分支：日常开发 `dev`；AI 个人分支按需（`ai/dahuang`）
- message 格式：`type(scope): 中文描述（任务码 dev-YYYYMMDD-NNN）`
  - type: `feat` `fix` `refactor` `docs` `style` `chore` `perf` `test`
  - 例：`fix(order-no): 销售订单单号统一 yyMMdd+3位（dev-20260907-012）`
- 一个提交只做一件事；**不混入无关文件**（提交前 `git status` 核对，只 add 自己的文件）
- 严禁：`git reset --hard` / `git clean` / `git checkout .` / `git push -f`（force push 会从远端抹掉别人的提交）等会吞掉他人改动或历史的操作；确需回退先 `git stash` 并告知他人，恢复远端用正常 push 补回
- **禁止删除/移动共享目录**（`jjx-docs/sql/`、`sql/backups/`、`jjx-docs/standards/`）里的任何文件——疑似冗余先问，不直接删；误删用 `git ls-files -d | xargs git restore` 恢复
- 推送：push 前先 fetch 确认无冲突；GitHub 走 `ssh://git@github.com/HelloLeoLyn/jjx.git dev`（本机 https 被全局改写，勿用默认 push）

---

## 6. 任务登记

- 开发任务统一 `sys_task`（kanban_module='dev'），任务码 `dev-YYYYMMDD-NNN`（取当日最大 N+1，查询时按数字排序）
- 完成后状态置 `2`（待审核），描述含：背景/方案/改动/提交 hash/验证待办；待用户审核后拖到已完成

---

## 7. 会话间交接（多 agent）

- 交接用**落盘文件 + 绝对路径**，不在聊天里传长内容
- 重要结论写进仓库文档（如上路径）或本规范相关文件；跨 agent 需要周知的规则只放本文件与仓库根 AGENTS.md

---

## 8. 检查清单（每次动手前 10 秒）

- [ ] 这次会碰数据库吗？→ 先备份（第 2 节）
- [ ] 产物文件路径/命名符合第 1 节？会不会和别人冲突？
- [ ] 会不会覆盖别人的未提交改动？（git status 核对）
- [ ] 提交信息带 type + 任务码？只含本次相关文件？

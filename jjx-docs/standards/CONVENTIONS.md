# JJX ERP 协作规范（备份/文档/命名/提交）

> **唯一真源**：本文件。多 agent（OpenClaw / Hermes / Codex）共享同一仓库 `/home/administrator/jjx`，所有与"落盘、命名、提交"相关的动作以本文件为准。
> 仓库根 `AGENTS.md` 是速查摘要；修改规范请改这里并同步摘要。

---

## 1. 路径总表

| 场景 | 固定位置 | 说明 |
|---|---|---|
| DB 全量备份 | `jjx-docs/sql/backups/` | 改库前必做；随仓库提交 |
| DB 表级/行级 guard 备份 | `jjx-docs/sql/backups/` | 清理/修复特定表前；命名 `<表域>_<topic>_YYYYMMDD-HHmm[_tag].sql` |
| DB 迁移/上线脚本 | `jjx-docs/sql/migrations/` | 序号 `NN_<描述>.sql` 递增；幂等优先 |
| 分析/方案/测试计划/报告 | `jjx-docs/analysis/` | `<主题>[-dev-YYYYMMDD-NNN].md`；登记 INDEX.md；UTF-8 **带 BOM**。**默认按历史快照看待**，不保证反映当前实现 |
| **现行真相（各模块当前状态）** | `jjx-docs/current/<模块>.md` | 一个模块只允许一篇，不带日期；命名 `<模块>.md`；会过期、需定期复核；历史指针留在文末 |
| 文档库入口导航 | `jjx-docs/README.md` | "我要干嘛 → 看哪篇"（判断时效只看这张表，不看文件 mtime） |
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
mkdir -p /home/administrator/jjx/jjx-docs/sql/backups
mysqldump -u root -p123456 --default-character-set=utf8mb4 \
  --single-transaction --set-gtid-purged=OFF --no-tablespaces \
  jjx_erp_db > /home/administrator/jjx/jjx-docs/sql/backups/jjx_erp_db_backup_$(date +%Y%m%d-%H%M)_<tag>.sql
md5sum /home/administrator/jjx/jjx-docs/sql/backups/jjx_erp_db_backup_*.sql
```

**命名**：`jjx_erp_db_backup_YYYYMMDD-HHmm[_tag].sql`
- `<tag>` 用简短英文原因：`before-iqc-migration`、`before-biz-no-rule`、`daily`、`before-cleanup`。无 tag 表示例行。
- 文件头第 1~3 行注释写明：备份人（agent 名）、原因、关联任务码（若有）。

**验证**：执行后必须 `md5sum` + `grep -c "CREATE TABLE"` 抽查，并在汇报里给出 md5。
**表级/行级 guard 备份**（清理 sys_task 等特定表/行前）：同样落 `jjx-docs/sql/backups/`，命名 `<表域>_<topic>_YYYYMMDD-HHmm[_tag].sql`（如 `sys_task_cleanup_20260907-0930.sql`），md5 照验。
**保留**：默认随仓库提交（跨机器一致）；单文件 > 20MB 先 gzip（`.sql.gz`）再入库；超 100MB 不入库，放共享盘并在文件位置留 `.gitkeep`+README 说明。
**禁止**：备份写到各自 workspace 的任意目录（如 `memory/*.sql`、`/tmp/backup.sql`）。

---

## 3. 迁移/上线 SQL 规范

- 位置：`jjx-docs/sql/migrations/`
- 命名：`NN_<描述>.sql`，NN 取目录现存最大序号 +1（如 `67_xxx.sql`）；同批多阶段可 `NN_a_<desc>.sql / NN_b_<desc>.sql`（2026-09-07 决议 C1）
- 存量平铺 dated 文件（`jjx-docs/sql/2026*.sql`，含 20260906_unified_iqc_*）为历史遗留：不迁移、不重复；新迁移一律进 migrations/
- 内容要求：
  - 幂等优先（`ADD COLUMN IF NOT EXISTS` 不可用时，先查 information_schema 或 `WHERE NOT EXISTS` 守卫）
  - 破坏性语句（DROP/TRUNCATE/DELETE）必须显式注释原因，单独文件，禁止与建表混在一个"安全"文件里
  - 文件编码 UTF-8；执行后登记 sys_task 或在本文件/任务描述留执行记录（时间、执行人 agent）
- 执行纪律：先备份（第 2 节）→ 审阅 → 执行 → 验证 → 汇报
- **唯一执行通道**：`bash scripts/db-migrate.sh <NN_xxx.sql> --yes --task dev-YYYYMMDD-NNN`
  内部固定顺序：前置检查（文件名/位置/非空/库可达）→ 全库备份（记 md5、表数、任务码）→ 执行 → 写 `sys_config.ops.schema.version` → 输出摘要。备份异常或执行失败都会中止且**不记录版本**。
  **不要直接 `mysql < file`**——绕过入口等于没有备份、也没有版本记录。
  - 查看已应用版本 / 待执行迁移清单：`bash scripts/db-migrate.sh --status`
  - 接管已有库、登记当前版本：`bash scripts/db-migrate.sh --record <NN> --yes`

---

## 4. 分析/方案/测试计划文档

- 位置：`jjx-docs/analysis/`；命名 `<主题>-dev-YYYYMMDD-NNN.md`（无任务码可退化为 `-YYYYMMDD.md`；存量 90 篇以现状为准，2026-09-07 决议 C2）
- 新文档**登记 `analysis/INDEX.md`**（或跑其再生命令）后随代码提交
- **UTF-8 带 BOM**（手机阅读不乱码），验证：
  ```bash
  python3 -c "d=open('文件','rb').read(); assert d[:3]==b'\xef\xbb\xbf'; d.decode('utf-8')"
  ```
- 只产 md，不产 PDF（2026-08-13 定）；要交付给用户时用 MEDIA 直接发文件
- **门禁**：`cd jjx-web && npm run check:docs`（已并入 `npm run validate`）。校验 4 条：新文档是否登记 INDEX.md、是否带 BOM、命名是否合规范、`current/` 是否一个模块一篇。存量债务在 `scripts/docs-baseline.json`，**只许缩小**（`--write-baseline` 收窄）
- 内容骨架：背景/现状证据（数据或代码行号）/方案/验证步骤/待确认点——数据说话，不写猜测

---

## 5. git 提交规范

- 分支：日常开发 `dev`；AI 个人分支按需（`ai/dahuang`）
- message 格式：`type(scope): 中文描述（任务码 dev-YYYYMMDD-NNN）`
  - type: `feat` `fix` `refactor` `docs` `style` `chore` `perf` `test`
  - 例：`fix(order-no): 销售订单单号统一 yyMMdd+3位（dev-20260907-012）`
- 一个提交只做一件事；**不混入无关文件**（提交前 `git status` 核对，只 add 自己的文件）
- 严禁：`git reset --hard` / `git clean` / `git checkout .` / `git push -f`（force push 会从远端抹掉别人的提交）等会吞掉他人改动或历史的操作；确需回退先 `git stash` 并告知他人，恢复远端用正常 push 补回
- **禁止删除/移动共享目录**（`jjx-docs/sql/`、`jjx-docs/sql/backups/`、`jjx-docs/standards/`）里的任何文件——疑似冗余先问，不直接删；**看到他人/批量删除状态也先问再恢复**，不自动 git restore
- 推送：push 前先 fetch 确认无冲突；GitHub 走 `ssh://git@github.com/HelloLeoLyn/jjx.git dev`（本机 https 被全局改写，勿用默认 push）
- **闸门（git hooks，每个 clone 装一次）**：`bash scripts/install-hooks.sh` → 设置 `core.hooksPath=scripts/hooks`。已启用两个：
  - `pre-commit` 拦：① `jjx-docs/sql/` `jjx-docs/standards/` 下的删除/移出（不可逆）② `status-magic-baseline.json` 新增条目或数值放大
  - `commit-msg` 拦：① 必须带任务码 `dev-YYYYMMDD-NNN`（关：`git config jjx.requireTaskCode false`）② 该码必须**真实存在于 sys_task**（用只读账号校验；关：`git config jjx.verifyTaskCode false`；库连不上时只提醒、不阻塞提交）
  - 单次跳过：`git commit --no-verify`（确认过后果再用）；卸载：`git config --unset core.hooksPath`

---

## 6. 任务登记

- 开发任务统一 `sys_task`（kanban_module='dev'），任务码 `dev-YYYYMMDD-NNN`（取当日最大 N+1，查询时按数字排序）
- 取号（用只读账号即可，`commit-msg` 闸门里也会打印）：
  ```sql
  SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(task_code,'-',-1) AS UNSIGNED)),0)+1
  FROM sys_task WHERE task_code LIKE CONCAT('dev-',DATE_FORMAT(NOW(),'%Y%m%d'),'-%');
  ```
  登记（插入前先做 sys_task 表级 guard 备份）：
  ```sql
  INSERT INTO sys_task (task_code,task_type,kanban_module,title,status,priority,create_by)
  VALUES ('dev-YYYYMMDD-NNN','DEV','dev','<标题>',0,'P3','<agent>');
  ```
- 完成后状态置 `2`（待审核），描述含：背景/方案/改动/提交 hash/验证待办；待用户审核后拖到已完成

---

## 7. 会话间交接（多 agent）

- 交接用**落盘文件 + 绝对路径**，不在聊天里传长内容
- 重要结论写进仓库文档（如上路径）或本规范相关文件；跨 agent 需要周知的规则只放本文件与仓库根 AGENTS.md

---

## 8. 检查清单（每次动手前 10 秒）

- [ ] 这次是「讨论」还是「执行」？讨论 → 一个字都不落盘（§9）
- [ ] 这台机器装过 git 闸门吗？→ `bash scripts/install-hooks.sh`（每个 clone 一次）
- [ ] 这次会碰数据库吗？→ 先备份（第 2 节）
- [ ] 产物文件路径/命名符合第 1 节？会不会和别人冲突？
- [ ] 会不会覆盖别人的未提交改动？（git status 核对）
- [ ] 提交信息带 type + 任务码？只含本次相关文件？

---

## 9. 讨论与执行边界（讨论不落盘；OpenClaw / Hermes / Codex 全部适用，无豁免）

**动手前先判请求类型**，判据如下：

| 类型 | 识别特征 | 允许的动作 |
|---|---|---|
| 讨论/征询 | "你有什么建议""要不要…""你觉得怎么搞""业内是不是…" | 只给判断 + 依据。**不落盘**：不改仓库文件、不动数据库、不建 cron、不提交 |
| 提问 | "是不是…""会不会…""为什么…" | 只给结论 + 证据（实际命令与实际输出） |
| 报障 | 贴报错、说"打不开"、说某个功能报错 | 只诊断：结论 + 命令 + 输出。**修复动作等用户明确指令** |
| 规模修正 | "不用那么复杂""算了""先放着" | 缩小范围，不新增产出物 |
| 执行 | "做 / 执行 / 改 / 写 / 建 / 你来" | 才动手，且**只做点名范围**：不顺手扩展、不顺手加码、不顺手优化邻近代码 |

补充约束：

- **拿不准就按"讨论"处理**，先问清三件事：动什么、动哪里、产出是什么，等用户点头再动。
- **落盘只发生在执行任务时**。备份、迁移、分析文档登记都属于"任务的一部分"，不是"顺手"；讨论阶段的产物要不要留由用户决定，agent 不自行撤销也不自行追加。
- 用户明确点名范围时（如"只做第 1 步"），越过范围的产出即为违规。

教训存档（2026-09-10）：Hermes 在与用户讨论运维方案期间，连续落了 2 个文档并改了 INDEX.md，被用户叫停并立此条。

---

## 10. 权限面与强制手段（谁能写什么、靠什么拦）

**原则：能机械强制的写进闸门；不能强制的必须写清"没人拦得住，只能事后人审"，不要假装有约束。**

| 资源面 | 谁可写 | 强制手段 | 强度 |
|---|---|---|---|
| 数据库 `jjx_erp_db` 写 | 仅本机执行者，且必须走 `scripts/db-migrate.sh` | 入口脚本强制"先全库备份 → 再执行 → 写版本号"；不备份执行不了 | 半硬（root 仍可直连绕过） |
| 数据库**只读查看** | 任何人 → 用只读账号 `jjx_ro` | MySQL 授权（仅 SELECT；写操作返回 1142） | **硬** |
| `jjx-docs/sql/**`、`jjx-docs/standards/**` 的删除/移出 | 无人（需用户批准） | `pre-commit` 拦截 | **硬** |
| `status-magic-baseline.json` 新增/放大 | 无人 | `pre-commit` 拦截（只许缩小） | **硬** |
| 文档规则（INDEX 登记 / BOM / 命名 / `current/` 一模块一篇） | 任何人，须过门禁 | `npm run check:docs`（已并入 `validate`） | 硬（需主动跑；建议开工自检） |
| 提交信息任务码（且须真实登记） | 任何人 | `commit-msg` 默认硬拦：① 必须带码 ② 用只读账号查 `sys_task` 确认该码真实存在（库不可达时放行不阻塞）。开关 `jjx.requireTaskCode` / `jjx.verifyTaskCode`；单次跳过 `--no-verify` | **硬**（可自行关闭，弱化点） |
| 业务代码文件 | Codex / Hermes / OpenClaw | 无（靠分工与评审） | 未强制 |
| 讨论阶段是否落盘 | 无人在意 | 无（§9 只约束自觉，机器判不了） | 未强制 |

**分工（按能力定，不按意愿定）**
- **数据库写**：本机执行者（用户 / Hermes）。Codex 沙箱连不上 MySQL —— 它只写代码文件 + 跑编译，SQL 落文件由本机执行。
- **提交**：有 git 写权限的 agent；提交前必须过 `core.hooksPath`（`bash scripts/install-hooks.sh`）。
- **只读排查**：任何 agent 一律用 `jjx_ro`，不要用 root 查数据。

**只读账号**
```bash
mysql -h127.0.0.1 -u jjx_ro -pjjx_ro_2026 jjx_erp_db   # SELECT only
# 撤销：DROP USER 'jjx_ro'@'127.0.0.1', 'jjx_ro'@'localhost';
```

**开工自检（一条命令把上面能查的都查了）**
```bash
bash scripts/agent-preflight.sh
```



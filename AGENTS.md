# JJX Agent Engineering Rules

## Discuss vs execute — never write files while discussing (applies to ALL agents: OpenClaw / Hermes / Codex)

Judge the request type BEFORE touching anything:

- **Discuss / opinion** ("你有什么建议", "要不要…", "你觉得怎么搞", "业内是不是…") → opinion + evidence only. No repo file writes, no DB changes, no cron, no commits.
- **Question** ("是不是…", "为什么…") → give the fact plus the command/output that proves it.
- **Bug report** (pasted error, "打不开") → diagnose only (conclusion + command + output); do NOT apply the fix until explicitly told.
- **Scope correction** ("不用那么复杂", "算了") → shrink the scope; do not add deliverables.
- **Execute** ("做 / 执行 / 改 / 写 / 建 / 你来") → only then act, **only within the named scope**, no drive-by extras.

Unsure which it is → treat it as discuss and ask three things first: what to touch, where, what the deliverable is.
Writing files is legitimate only as part of an executed task (backups / migrations / doc registration are part of the task, not "顺便"). Full rule: `jjx-docs/standards/CONVENTIONS.md` §9.

## Service lifecycle — explicit user instruction required

- Never start, stop, restart, reload, kill, or replace a running service/process unless the user explicitly requests that exact lifecycle operation in the current request.
- An implementation, build, test, deploy-preparation, or E2E request does **not** authorize service lifecycle changes. Finish the code/build work, report that a restart is required, and let the user perform it.
- This applies to backend/frontend dev servers, OCR services, systemd units, containers, background processes, port occupants, and similar runtime operations.
- Read-only checks such as health requests, port inspection, process listing, and log reading remain allowed when relevant; they must not mutate runtime state.

## Status enums

- Before changing status-related UI or logic, search and reuse the existing enum under `jjx-web/src/enums/`.
- Status display, comparisons, filters, permissions, button visibility, and transitions must use named enum members. Numeric or string status literals are forbidden outside enum definitions, migrations, and dedicated test fixtures.
- Do not create page-local `STATUS_MAP`, `STATUS_NAMES`, or equivalent mappings when a domain enum exists.
- Run `npm run check:status-enums` and the relevant type/build validation after frontend status changes.
- Do not expand `scripts/status-magic-baseline.json` to admit new violations. Existing entries are migration debt and may only be removed.

## Inventory ledger rules (库存口径铁律 — full spec: `jjx-docs/standards/CONVENTIONS.md` §13)

- **流水是唯一真源**：`inventory_transaction` 只增不改（append-only）；纠错用反向/红冲单，禁止 UPDATE/DELETE 流水行。
- **余额是派生值**：`结存 = 累计入库 − 累计出库 ± 盘点调整`；`inventory_stock_item.quantity`/`inventory_stock.total_quantity` 一律由流水重算得出，必须可重算一致。
- **变动唯一入口**：只允许 `InventoryStockMutationService.applyDelta(stock, delta, transaction)`（强制带流水类型、不得为负）；禁止直接 UPDATE 批次/汇总数量；新增变动类型必须同步 `TransactionTypeEnum` 与 `transaction_type` 枚举。
- **单据不可改**：入库/出库单只能红冲，原始量永久保留（页面/报表必须同时给「收/发/结存」三栏，只给结存视为缺陷）。
- **批次可追溯**：批次 → 入库单（→供应商/来料检验）→ 出库单（→工单/销售单）→ 成品批次，`batch_no`/`source_*`/`lot_id` 不得省略。
- **Gate**：`npm run validate` → `check:stock:strict`（`scripts/check-stock-summary.sh --strict`）必须五项全 0（含 ④批次结存=流水派生结存、⑤有流水无批次行）；改了库存写入路径必须重跑。
- 批次表**不存**「累计入库/累计出库」列（避免第二真源），收/发由流水聚合。

## Backup & document conventions (multi-agent: OpenClaw / Hermes / Codex share this repo)

Full spec: `jjx-docs/standards/CONVENTIONS.md` — single source of truth.

Quick rules:
- **DB backup（2026-09-28 用户口径，覆盖此前的仓内/仓外/入库/索引要求）**: 只有三类需要备份——① 迁移/表结构变更 ② 清库 ③ 批量 UPDATE/DELETE 或脏数据订正；**任务登记、单行 status/remark、幂等配置/字典新增一律免备份**。备份放**仓库内** `jjx-docs/sql/backups/`（`JJX_BACKUP_DIR` 可覆盖），**由用户手工执行、只保留最新一份**（生成新份即删旧份，该目录内永远只有 1 份全库备份）。agent 与脚本**不再自动生成备份文件、不再维护 `backup-index.tsv`**，也不往仓库外放备份。全库导出**必须排除人事档案表 `hr_employee`**（仓库为公开，入库即永久留在 git 历史）。
- Migration scripts: `jjx-docs/sql/migrations/NN_<desc>.sql`（NN = `ops.schema.applied` 最大号与目录现存最大号的较大者 + 1）；已应用的迁移留在仓库内 `migrations/applied/`（由 `db-migrate.sh` 记账回查成功后同名归档），**是否执行过以目标库账本为准，不看文件是否在 `applied/`**。
- Analysis / test-plan / design reports: `jjx-docs/history/<topic>-dev-YYYYMMDD-NNN.md`, register in `history/INDEX.md`, UTF-8 BOM.
  → gate it with `npm run check:docs` (run from `jjx-web/`); it is part of `npm run validate`. Existing debt lives in `scripts/docs-baseline.json` and may only shrink (`--write-baseline` to narrow).
- 表级 guard 备份只在**批量/破坏性订正**前做（登记任务、改一行状态不做）：命名 `<表域>_<topic>_YYYYMMDD-HHmm.sql`、放 `jjx-docs/sql/backups/`、**只留最新一份**；不再维护 `backup-index.tsv`。
- Commit message: `type(scope): 中文描述（任务码 dev-YYYYMMDD-NNN）`; never mix unrelated files.
- **Task completion = auto-commit (2026-09-23 user rule, all agents)**: a finished task (code/scripts/docs, self-checks green) is **committed and pushed by its author without waiting for user approval** — the message must carry that task's code (hook-enforced) and contain only that task's files (no other session's WIP). **Right after the commit, set `sys_task.status=2` (待审核)** and record change list + commit hash + verification + leftovers in `remark`/`description`. Overrides only when the user explicitly says "don't commit / analysis only".
- NEVER `git reset --hard` / `git clean` / `git push -f`. Files under `jjx-docs/sql/` (except legacy `backups/`) and `jjx-docs/standards/` must not be deleted or moved. Legacy tracked backups may be removed only in an explicit cleanup task.
- Scratch/temp files: `/tmp` or repo `.tmp/` (gitignored), clean same day.
- **Git gates (hooks)**: run `bash scripts/install-hooks.sh` **once per clone** (sets `core.hooksPath=scripts/hooks`).
  `pre-commit` blocks: deletions/moves under `jjx-docs/sql/` except `backups/` and the same-name/unchanged `migrations/NN_x.sql → migrations/applied/NN_x.sql` archive move, deletions/moves under `jjx-docs/standards/`, and any expansion of `status-magic-baseline.json`. Backup cleanup is warned but allowed.
  `commit-msg` requires the task code `dev-YYYYMMDD-NNN` and verifies it really exists in `sys_task` (read-only check; fail-open when the DB is unreachable). Disable per clone: `git config jjx.requireTaskCode false` / `jjx.verifyTaskCode false`.
  `pre-push` runs the table baseline gate (`scripts/check-model-baseline.sh`, CONVENTIONS §15) and blocks a red result; fail-open when the DB is unreachable.
  Single-use bypass: `git commit --no-verify` / `git push --no-verify` — only when you have confirmed the consequences.

## Task completion report format (all agents — full spec: `jjx-docs/standards/CONVENTIONS.md` §5)

Every task's closing report in chat uses this fixed 6-line template, line-by-line verifiable (task code + commit hash mandatory). Keep the report terse: put background, reasoning, command output and per-file detail in `sys_task.remark` (≤500, else `description`) or a `jjx-docs/history/` doc — **not** in the report.

```text
任务码：dev-YYYYMMDD-NNN
提交：<hash>（已 push）
改：<N 文件，一句话>
验：mvn / vue-tsc / check:docs / 基线 → 全过（没跑写未跑）
DB：<迁移NN / 表 / 权限>（无则写 无）
遗留：<一句话；无则写 无>
```

示例：
```text
任务码：dev-20261011-017
提交：8600503d（已 push）
改：3 文件（产品列表删「作业规范」行内动作；迁移 267 去重菜单入口；表级 guard 备份）
验：mvn / vue-tsc / check:docs / 基线 → 全过
DB：迁移 267 / sys_menu·sys_role_menu / 删 menu404+授权、405/406 重挂 407、补授 407 给 role16
遗留：工程/产品角色需重新登录刷新权限快照
```

## Tool failure and recovery rules

- `sys_task.remark` is `varchar(500)`: check `CHAR_LENGTH` before writing and query it again afterward. Never suppress database errors with `2>/dev/null` or an empty catch.
- `git checkout -- <file>` restores from the index, not necessarily `HEAD`. To restore explicitly from `HEAD`, use `git checkout HEAD -- <file>` or `git restore --source=HEAD --staged --worktree <file>`, after confirming the file has no one else's work.

## Proposals do not override user rules

- An agent proposal, even when approved for implementation, does not silently replace an earlier explicit user rule. Before changing `jjx-docs/standards/CONVENTIONS.md` or another policy source, inspect the rule's history and state any conflict with the existing user rule explicitly for approval.

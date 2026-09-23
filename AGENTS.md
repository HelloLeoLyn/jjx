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
- **DB risk-based backup**: destructive/批量 DML、表结构变更、风险修复必须先备份（脚本按风险分级：高风险全库快照 / 低风险表级）；低风险幂等配置/字典新增由用户按需决定是否备份。备份统一放 `JJX_BACKUP_DIR`（**2026-09-22 起默认仓库外 `~/jjx-backups/`**；仓库内只留索引 `jjx-docs/sql/backups/backup-index.tsv`，2026-09-21 的“仓内 backups/”口径已作废）。
- Migration scripts: `jjx-docs/sql/migrations/NN_<desc>.sql`（NN = `ops.schema.applied` 最大号与目录现存最大号的较大者 + 1；已应用的成批迁移在应用后移出仓库到 `~/jjx-backups/migrations-removed_YYYYMMDD-HHmm/`，故只看目录会撞号）。
- Analysis / test-plan / design reports: `jjx-docs/history/<topic>-dev-YYYYMMDD-NNN.md`, register in `history/INDEX.md`, UTF-8 BOM.
  → gate it with `npm run check:docs` (run from `jjx-web/`); it is part of `npm run validate`. Existing debt lives in `scripts/docs-baseline.json` and may only shrink (`--write-baseline` to narrow).
- Table-level guard backups before row cleanups: `$JJX_BACKUP_DIR/<table>_<topic>_YYYYMMDD-HHmm.sql`（默认仓库外 `~/jjx-backups/`；完成后在 `jjx-docs/sql/backups/backup-index.tsv` 追加一行：时间/类型/文件/md5/字节/表数/执行人/任务码）。
- Commit message: `type(scope): 中文描述（任务码 dev-YYYYMMDD-NNN）`; never mix unrelated files.
- **Task completion = auto-commit (2026-09-23 user rule, all agents)**: a finished task (code/scripts/docs, self-checks green) is **committed and pushed by its author without waiting for user approval** — the message must carry that task's code (hook-enforced) and contain only that task's files (no other session's WIP). **Right after the commit, set `sys_task.status=2` (待审核)** and record change list + commit hash + verification + leftovers in `remark`/`description`. Overrides only when the user explicitly says "don't commit / analysis only".
- NEVER `git reset --hard` / `git clean` / `git push -f`. Files under `jjx-docs/sql/` (except legacy `backups/`) and `jjx-docs/standards/` must not be deleted or moved. Legacy tracked backups may be removed only in an explicit cleanup task.
- Scratch/temp files: `/tmp` or repo `.tmp/` (gitignored), clean same day.
- **Git gates (hooks)**: run `bash scripts/install-hooks.sh` **once per clone** (sets `core.hooksPath=scripts/hooks`).
  `pre-commit` blocks: deletions/moves under `jjx-docs/sql/` except `backups/`, deletions/moves under `jjx-docs/standards/`, and any expansion of `status-magic-baseline.json`. Backup cleanup is warned but allowed.
  `commit-msg` requires the task code `dev-YYYYMMDD-NNN` and verifies it really exists in `sys_task` (read-only check; fail-open when the DB is unreachable). Disable per clone: `git config jjx.requireTaskCode false` / `jjx.verifyTaskCode false`.
  Single-commit bypass: `git commit --no-verify` — only when you have confirmed the consequences.

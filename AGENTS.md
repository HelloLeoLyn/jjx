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

## Status enums

- Before changing status-related UI or logic, search and reuse the existing enum under `jjx-web/src/enums/`.
- Status display, comparisons, filters, permissions, button visibility, and transitions must use named enum members. Numeric or string status literals are forbidden outside enum definitions, migrations, and dedicated test fixtures.
- Do not create page-local `STATUS_MAP`, `STATUS_NAMES`, or equivalent mappings when a domain enum exists.
- Run `npm run check:status-enums` and the relevant type/build validation after frontend status changes.
- Do not expand `scripts/status-magic-baseline.json` to admit new violations. Existing entries are migration debt and may only be removed.

## Backup & document conventions (multi-agent: OpenClaw / Hermes / Codex share this repo)

Full spec: `jjx-docs/standards/CONVENTIONS.md` — single source of truth.

Quick rules:
- **DB change first** (any migration / bulk DML / risky fix): back up BEFORE touching data. Full dumps and guard backups go to the Git-external `JJX_BACKUP_DIR` (default: sibling `jjx-backups/`), not into the repository.
- Migration scripts: `jjx-docs/sql/migrations/NN_<desc>.sql` (next max NN+1).
- Analysis / test-plan / design reports: `jjx-docs/analysis/<topic>-dev-YYYYMMDD-NNN.md`, register in INDEX.md, UTF-8 BOM.
  → gate it with `npm run check:docs` (run from `jjx-web/`); it is part of `npm run validate`. Existing debt lives in `scripts/docs-baseline.json` and may only shrink (`--write-baseline` to narrow).
- Table-level guard backups before row cleanups: `$JJX_BACKUP_DIR/<table>_<topic>_YYYYMMDD-HHmm.sql` (outside Git).
- Commit message: `type(scope): 中文描述（任务码 dev-YYYYMMDD-NNN）`; never mix unrelated files.
- NEVER `git reset --hard` / `git clean` / `git push -f`. Files under `jjx-docs/sql/` (except legacy `backups/`) and `jjx-docs/standards/` must not be deleted or moved. Legacy tracked backups may be removed only in an explicit cleanup task.
- Scratch/temp files: `/tmp` or repo `.tmp/` (gitignored), clean same day.
- **Git gates (hooks)**: run `bash scripts/install-hooks.sh` **once per clone** (sets `core.hooksPath=scripts/hooks`).
  `pre-commit` blocks: deletions/moves under `jjx-docs/sql/` except `backups/`, deletions/moves under `jjx-docs/standards/`, and any expansion of `status-magic-baseline.json`. Backup cleanup is warned but allowed.
  `commit-msg` requires the task code `dev-YYYYMMDD-NNN` and verifies it really exists in `sys_task` (read-only check; fail-open when the DB is unreachable). Disable per clone: `git config jjx.requireTaskCode false` / `jjx.verifyTaskCode false`.
  Single-commit bypass: `git commit --no-verify` — only when you have confirmed the consequences.

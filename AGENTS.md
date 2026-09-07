# JJX Agent Engineering Rules

## Status enums

- Before changing status-related UI or logic, search and reuse the existing enum under `jjx-web/src/enums/`.
- Status display, comparisons, filters, permissions, button visibility, and transitions must use named enum members. Numeric or string status literals are forbidden outside enum definitions, migrations, and dedicated test fixtures.
- Do not create page-local `STATUS_MAP`, `STATUS_NAMES`, or equivalent mappings when a domain enum exists.
- Run `npm run check:status-enums` and the relevant type/build validation after frontend status changes.
- Do not expand `scripts/status-magic-baseline.json` to admit new violations. Existing entries are migration debt and may only be removed.

## Backup & document conventions (multi-agent: OpenClaw / Hermes / Codex share this repo)

Full spec: `jjx-docs/standards/CONVENTIONS.md` — single source of truth.

Quick rules:
- **DB change first** (any migration / bulk DML / risky fix): back up BEFORE touching data →
  `sql/backups/jjx_erp_db_backup_YYYYMMDD-HHmm[_tag].sql` (mysql root/123456, utf8mb4).
- Migration scripts: `jjx-docs/sql/YYYYMMDD_<domain>_<purpose>.sql`.
- Analysis / test-plan / design reports: `jjx-docs/analysis/YYYYMMDD-<topic>.md`.
- Commit message: `type(scope): 中文描述（任务码 dev-YYYYMMDD-NNN）`; never mix unrelated files.
- NEVER `git reset --hard` / `git clean` / `git push -f` / delete files under `jjx-docs/sql`, `sql/backups`, `jjx-docs/standards` (restore: `git ls-files -d | xargs git restore`).
- Scratch/temp files: `/tmp` or repo `.tmp/` (gitignored), clean same day.

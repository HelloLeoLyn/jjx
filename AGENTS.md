# JJX Agent Engineering Rules

## Status enums

- Before changing status-related UI or logic, search and reuse the existing enum under `jjx-web/src/enums/`.
- Status display, comparisons, filters, permissions, button visibility, and transitions must use named enum members. Numeric or string status literals are forbidden outside enum definitions, migrations, and dedicated test fixtures.
- Do not create page-local `STATUS_MAP`, `STATUS_NAMES`, or equivalent mappings when a domain enum exists.
- Run `npm run check:status-enums` and the relevant type/build validation after frontend status changes.
- Do not expand `scripts/status-magic-baseline.json` to admit new violations. Existing entries are migration debt and may only be removed.

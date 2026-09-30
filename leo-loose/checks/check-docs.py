#!/usr/bin/env python3
"""Read-only checks for this portable documentation kit (Python 3.9+)."""

import argparse
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit


REQUIRED = (
    "README.md", "AGENTS.md", "docs/reference/project-shapes.md",
    "docs/reference/public-practices.md", "docs/how-to/start-a-project.md",
    "templates/project-profile.md", "templates/project-agents.md",
    "workflows/bootstrap.md", "workflows/implement.md", "workflows/diagnose.md",
    "workflows/review.md", "workflows/handoff.md", "checks/README.md",
    "checks/check-docs.py",
)
LINK = re.compile(r"\[[^\]\n]*\]\(([^)\n]+)\)")
FENCE = re.compile(r"^\s{0,3}(`{3,}|~{3,})(.*)$")


def inspect(root):
    errors = []
    for name in REQUIRED:
        if not (root / name).is_file():
            errors.append(f"missing required file: {name}")
    files = sorted(root.rglob("*.md"))
    indexed = set()
    link_count = 0
    for path in files:
        relative = path.relative_to(root)
        if not path.resolve().is_relative_to(root):
            errors.append(f"file outside kit: {relative}")
            continue
        try:
            content = path.read_text(encoding="utf-8-sig")
        except (UnicodeError, OSError) as exc:
            errors.append(f"cannot read {relative}: {exc}")
            continue
        if not content.strip():
            errors.append(f"empty document: {relative}")
        fence = None
        prose = []
        for line in content.splitlines():
            match = FENCE.match(line)
            if match:
                token, suffix = match.groups()
                if fence is None:
                    fence = token
                elif token[0] == fence[0] and len(token) >= len(fence) and not suffix.strip():
                    fence = None
                continue
            if fence is None:
                prose.append(line)
        if fence is not None:
            errors.append(f"unclosed code fence: {relative}")
        for raw in LINK.findall("\n".join(prose)):
            href = raw.strip().removeprefix("<").removesuffix(">")
            parsed = urlsplit(href)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            link_count += 1
            target = (path.parent / unquote(parsed.path)).resolve()
            if not target.is_relative_to(root):
                errors.append(f"link outside kit: {relative} -> {href}")
            elif not target.exists():
                errors.append(f"broken link: {relative} -> {href}")
            elif path.name in {"README.md", "INDEX.md"}:
                indexed.add(target)
    for path in files:
        if path != root / "README.md" and path.resolve() not in indexed:
            errors.append(f"not in root README index: {path.relative_to(root)}")
    return errors, len(files), link_count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent,
                        help="documentation kit root; defaults to this script's parent kit")
    args = parser.parse_args()
    root = args.root.resolve()
    if not root.is_dir():
        parser.error(f"root is not a directory: {root}")
    errors, documents, links = inspect(root)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print(f"PASS: {documents} Markdown files, {links} local links; required files, "
          "UTF-8, code fences, index and portable links checked")
    return 0


if __name__ == "__main__":
    sys.exit(main())

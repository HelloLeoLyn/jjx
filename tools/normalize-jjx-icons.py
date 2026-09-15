#!/usr/bin/env python3
"""Normalize JJX SVG icon canvases to a common 64x64 viewBox.

The original directory is backed up before this script is run. This keeps the
source artwork intact while removing differences in root viewBox/width/height
that make identical CSS sizes render at visibly different scales.
"""

from __future__ import annotations

import re
from io import BytesIO
from pathlib import Path

import cairosvg
from PIL import Image


ROOT = Path(__file__).resolve().parents[1] / "jjx-web/src/icons/jjx"
TARGET = 64.0
PADDING = 4.0


def number(value: str) -> float:
    match = re.search(r"-?\d+(?:\.\d+)?", value)
    if not match:
        raise ValueError(value)
    return float(match.group(0))


def attr(attrs: str, name: str) -> str | None:
    match = re.search(rf"\b{name}\s*=\s*(['\"])(.*?)\1", attrs, re.S)
    return match.group(2) if match else None


def normalize(path: Path) -> bool:
    source = path.read_text(encoding="utf-8")
    root_match = re.search(r"<svg\b([^>]*)>", source, re.S | re.I)
    close_match = re.search(r"</svg\s*>\s*$", source, re.S | re.I)
    if not root_match or not close_match:
        raise ValueError(f"invalid SVG root: {path}")

    attrs = root_match.group(1)
    view_box = attr(attrs, "viewBox")
    if view_box:
        parts = re.split(r"[\s,]+", view_box.strip())
        if len(parts) != 4:
            raise ValueError(f"invalid viewBox: {path}")
        x, y, width, height = map(float, parts)
    else:
        width = number(attr(attrs, "width") or "64")
        height = number(attr(attrs, "height") or "64")
        x = y = 0.0

    content = source[root_match.end() : close_match.start()]
    # Render once to measure the actual painted bounds instead of trusting the
    # source canvas, which varies widely between imported icons.
    preview = re.sub(r"currentColor", "#000000", source, flags=re.I)
    png = cairosvg.svg2png(bytestring=preview.encode("utf-8"), output_width=512, output_height=512)
    image = Image.open(BytesIO(png)).convert("RGBA")
    alpha = image.getchannel("A").point(lambda value: 255 if value > 8 else 0)
    bbox = alpha.getbbox()
    if bbox:
        bx1, by1, bx2, by2 = (value * TARGET / 512 for value in bbox)
        x, y, width, height = bx1, by1, bx2 - bx1, by2 - by1
    inner = TARGET - PADDING * 2
    scale = min(inner / width, inner / height) if width and height else 1.0
    tx = (TARGET - width * scale) / 2 - x * scale
    ty = (TARGET - height * scale) / 2 - y * scale

    # Icons are rendered through SvgIcon with currentColor. Replace hard-coded
    # paint colors while preserving fill="none" for outline geometry.
    source = re.sub(
        r"(\b(?:fill|stroke)\s*=\s*['\"])(?!none['\"])(?:#[0-9a-fA-F]{3,8}|black|white|[a-zA-Z]+)(['\"])",
        r"\1currentColor\2",
        source,
    )
    source = re.sub(
        r"(\b(?:fill|stroke)\s*:\s*)(?!none\b)(?:#[0-9a-fA-F]{3,8}|black|white|[a-zA-Z]+)",
        r"\1currentColor",
        source,
    )
    root_match = re.search(r"<svg\b([^>]*)>", source, re.S | re.I)
    close_match = re.search(r"</svg\s*>\s*$", source, re.S | re.I)
    attrs = root_match.group(1)
    content = source[root_match.end() : close_match.start()]

    # Keep namespace and other harmless root attributes, but replace sizing.
    new_attrs = re.sub(r"\s+(?:viewBox|width|height)\s*=\s*(['\"])(.*?)\1", "", attrs, flags=re.S)
    new_root = (
        f'<svg{new_attrs} viewBox="0 0 {TARGET:g} {TARGET:g}">'
        f'<g transform="translate({tx:.6f} {ty:.6f}) scale({scale:.6f})">'
        f"{content}</g></svg>"
    )
    prefix = source[: root_match.start()]
    path.write_text(prefix + new_root + "\n", encoding="utf-8")
    return True


def main() -> None:
    files = sorted(ROOT.glob("*.svg"))
    changed = 0
    for path in files:
        normalize(path)
        changed += 1
    print(f"normalized={changed} target=64x64 padding=4")


if __name__ == "__main__":
    main()

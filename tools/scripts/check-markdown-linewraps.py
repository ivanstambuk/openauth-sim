#!/usr/bin/env python3
"""
Guard against Markdown list items that manually wrap onto the next line.

GitHub renders a hard line break when a list entry is split with a newline but
no blank line. This script flags staged `.md` files where a bullet/numbered
item ends with a newline and the following line starts with indentation
(two or more spaces) rather than a new list marker. Authors should keep those
list entries on one physical line so renderers format them consistently.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys
from typing import Iterable, List, Sequence, Tuple


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Detect wrapped Markdown list entries that should stay on one line."
    )
    parser.add_argument(
        "files",
        nargs="*",
        help="Markdown files to check (typically provided by the pre-commit hook).",
    )
    return parser.parse_args()


def is_list_marker(text: str) -> bool:
    stripped = text.lstrip()
    return bool(
        stripped.startswith("- ")
        or stripped.startswith("* ")
        or stripped.startswith("+ ")
        or re.match(r"\d+[.)]\s", stripped)
    )


def line_starts_code_fence(line: str) -> Tuple[bool, str]:
    stripped = line.lstrip()
    for fence in ("```", "~~~"):
        if stripped.startswith(fence):
            return True, fence
    return False, ""


def find_violations(path: pathlib.Path) -> List[Tuple[int, str]]:
    violations: List[Tuple[int, str]] = []
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except UnicodeDecodeError as exc:
        raise SystemExit(f"{path}: unable to decode as UTF-8 ({exc})")

    in_fence = False
    fence_token = ""

    for idx, line in enumerate(lines):
        if not in_fence:
            starts_fence, token = line_starts_code_fence(line)
            if starts_fence:
                in_fence = True
                fence_token = token
                continue
        else:
            starts_fence, token = line_starts_code_fence(line)
            if starts_fence and token == fence_token:
                in_fence = False
            continue

        stripped = line.lstrip()
        if not stripped:
            continue
        if not is_list_marker(stripped):
            continue

        clean_line = stripped.rstrip()
        if not clean_line:
            continue

        trailing_char = clean_line[-1]
        if trailing_char not in {",", ";", ":", "`"}:
            continue

        # Check the following line for an indented continuation.
        if idx + 1 >= len(lines):
            continue
        next_line = lines[idx + 1]
        next_stripped = next_line.lstrip()
        indent_width = len(next_line) - len(next_stripped)

        if indent_width < 2:
            continue
        if not next_stripped:
            continue
        if is_list_marker(next_stripped):
            continue

        # Allow indented fenced code blocks under a bullet (common pattern).
        starts_fence, _ = line_starts_code_fence(next_line)
        if starts_fence:
            continue

        next_lower = next_stripped.lower()
        bad_prefixes = (
            "and ",
            "and`",
            "and(",
            "and[",
            "or ",
            "or`",
            "or(",
            "or[",
            "command",
        )
        if not any(next_lower.startswith(prefix) for prefix in bad_prefixes):
            continue

        violations.append((idx + 1, stripped))

    return violations


def main() -> int:
    args = parse_args()
    files = [pathlib.Path(name) for name in args.files]
    markdown_files = [
        path for path in files if path.suffix.lower() == ".md" and path.is_file()
    ]
    if not markdown_files:
        return 0

    failed = False
    for md_file in markdown_files:
        problems = find_violations(md_file)
        if not problems:
            continue
        failed = True
        for line_no, snippet in problems:
            print(
                f"{md_file}:{line_no}: list item wraps onto the next line; "
                "keep the entry on one physical line",
                file=sys.stderr,
            )
            print(f"   offending text: {snippet}", file=sys.stderr)

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())

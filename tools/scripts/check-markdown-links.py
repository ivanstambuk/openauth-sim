#!/usr/bin/env python3
"""
Detect broken local Markdown links.

The project intentionally mixes two styles:
- repo-root paths like (docs/4-architecture/roadmap.md) or (README.md)
- relative paths like (../2-how-to/use-hotp-from-java.md)

This checker validates that local link targets resolve to existing tracked paths. It ignores:
- external URLs (http/https/mailto/tel)
- pure anchors (#section)
- build outputs (build/**) and other ephemeral paths
"""

from __future__ import annotations

import argparse
import pathlib
import posixpath
import re
import subprocess
import sys
from dataclasses import dataclass
from typing import Iterable, Iterator, List, Optional, Sequence, Set, Tuple


SKIP_SCHEMES = ("http://", "https://", "mailto:", "tel:")
SKIP_PREFIXES = (
    "build/",
    ".gradle/",
    "node_modules/",
    "out/",
    "target/",
)


@dataclass(frozen=True)
class Link:
    target: str
    line: int
    raw: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate local Markdown link targets exist.")
    parser.add_argument("files", nargs="*", help="Markdown files to scan.")
    return parser.parse_args()


def git_root() -> pathlib.Path:
    try:
        root = subprocess.check_output(
            ["git", "rev-parse", "--show-toplevel"], text=True
        ).strip()
    except (OSError, subprocess.CalledProcessError) as exc:
        raise SystemExit(f"Unable to locate git repository root ({exc})")
    return pathlib.Path(root)


def git_tracked_paths(root: pathlib.Path) -> Tuple[Set[str], Set[str]]:
    output = subprocess.check_output(["git", "ls-files"], cwd=root, text=True)
    files = {line.strip() for line in output.splitlines() if line.strip()}
    dirs: Set[str] = set()
    for name in files:
        parent = posixpath.dirname(name)
        while parent and parent != ".":
            dirs.add(parent)
            parent = posixpath.dirname(parent)
    return files, dirs


def line_starts_code_fence(line: str) -> Tuple[bool, str]:
    stripped = line.lstrip()
    for fence in ("```", "~~~"):
        if stripped.startswith(fence):
            return True, fence
    return False, ""


def strip_inline_code(text: str) -> str:
    # Best-effort: remove inline code segments to reduce false positives.
    parts = text.split("`")
    if len(parts) < 3:
        return text
    return "".join(part for idx, part in enumerate(parts) if idx % 2 == 0)


def find_matching_bracket(text: str, start: int) -> int:
    depth = 0
    idx = start
    while idx < len(text):
        ch = text[idx]
        if ch == "\\":
            idx += 2
            continue
        if ch == "[":
            depth += 1
        elif ch == "]":
            depth -= 1
            if depth == 0:
                return idx
        idx += 1
    return -1


def find_matching_paren(text: str, start: int) -> int:
    depth = 0
    idx = start
    while idx < len(text):
        ch = text[idx]
        if ch == "\\":
            idx += 2
            continue
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return idx
        idx += 1
    return -1


def split_link_target(raw: str) -> str:
    target = raw.strip()
    if not target:
        return ""
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1].strip()
    # Drop optional titles: (path "title") or (path 'title')
    in_quote: Optional[str] = None
    for idx, ch in enumerate(target):
        if ch in {"'", '"'}:
            if in_quote is None:
                in_quote = ch
            elif in_quote == ch:
                in_quote = None
        if in_quote is None and ch.isspace():
            return target[:idx]
    return target


def extract_links(lines: Sequence[str]) -> Iterator[Link]:
    in_fence = False
    fence_token = ""
    for line_no, line in enumerate(lines, start=1):
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

        scan = strip_inline_code(line)
        idx = 0
        while idx < len(scan):
            if scan[idx] != "[":
                idx += 1
                continue
            end_bracket = find_matching_bracket(scan, idx)
            if end_bracket == -1:
                idx += 1
                continue
            if end_bracket + 1 >= len(scan) or scan[end_bracket + 1] != "(":
                idx = end_bracket + 1
                continue
            end_paren = find_matching_paren(scan, end_bracket + 1)
            if end_paren == -1:
                idx = end_bracket + 1
                continue

            raw_target = scan[end_bracket + 2 : end_paren]
            target = split_link_target(raw_target)
            if target:
                yield Link(target=target, line=line_no, raw=line.rstrip())
            idx = end_paren + 1


def is_external(target: str) -> bool:
    lower = target.lower()
    return lower.startswith(SKIP_SCHEMES)


def should_skip_path(path: str) -> bool:
    for prefix in SKIP_PREFIXES:
        if path.startswith(prefix):
            return True
    return False


def candidate_targets(source: str, target: str) -> List[str]:
    # Strip query fragments first; anchors are not validated by this checker.
    path_part = target.split("#", 1)[0].split("?", 1)[0]
    if not path_part:
        return []

    if path_part.startswith("/"):
        return [posixpath.normpath(path_part.lstrip("/"))]

    source_dir = posixpath.dirname(source)

    candidates: List[str] = []

    def add(path: str) -> None:
        normal = posixpath.normpath(path)
        if normal and normal not in candidates:
            candidates.append(normal)

    if path_part.startswith("./"):
        add(posixpath.join(source_dir, path_part))
        add(path_part[2:])
        return candidates

    if path_part.startswith("../"):
        add(posixpath.join(source_dir, path_part))
        return candidates

    # Support both conventions:
    # - plain "file.md" links relative to the current markdown file
    # - repo-root style links like "docs/..." or "README.md"
    add(posixpath.join(source_dir, path_part))
    add(path_part)
    return candidates


def validate_file(
    repo_root: pathlib.Path, tracked_files: Set[str], tracked_dirs: Set[str], path: pathlib.Path
) -> List[str]:
    if path.suffix.lower() != ".md":
        return []
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except UnicodeDecodeError as exc:
        return [f"{path}: unable to decode as UTF-8 ({exc})"]

    rel = path.relative_to(repo_root).as_posix()
    problems: List[str] = []
    for link in extract_links(lines):
        if link.target.startswith("#"):
            continue
        if is_external(link.target):
            continue
        candidates = candidate_targets(rel, link.target)
        if not candidates:
            continue

        matched = False
        for candidate in candidates:
            if not candidate:
                continue
            if should_skip_path(candidate):
                matched = True
                break
            if candidate in tracked_files or candidate in tracked_dirs:
                matched = True
                break
            if (repo_root / candidate).exists():
                matched = True
                break

        if not matched:
            problems.append(
                f"{rel}:{link.line}: missing link target '{link.target}' (candidates {candidates})"
            )

    return problems


def main() -> int:
    args = parse_args()
    if not args.files:
        return 0

    repo_root = git_root()
    tracked_files, tracked_dirs = git_tracked_paths(repo_root)

    failed = False
    for name in args.files:
        path = pathlib.Path(name)
        if not path.is_absolute():
            path = repo_root / path
        if not path.is_file():
            continue
        problems = validate_file(repo_root, tracked_files, tracked_dirs, path)
        if not problems:
            continue
        failed = True
        for problem in problems:
            print(problem, file=sys.stderr)

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())

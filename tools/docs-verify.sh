#!/usr/bin/env bash
set -euo pipefail

log() { echo "[docs-verify] $*" >&2; }
die() { log "$*"; exit 1; }

usage() {
  cat >&2 <<'EOF'
Usage: tools/docs-verify.sh [--all]

Fast verification lane for doc-only work:
- runs Spotless formatting for misc files (Markdown/YAML/JSON)
- checks Markdown list-item line wraps
- checks local Markdown links for missing targets

Default mode checks changed Markdown files (staged + unstaged + untracked).
Use --all to scan all tracked Markdown files.
EOF
}

collect_changed_markdown() {
  {
    git diff --name-only --diff-filter=ACMR
    git diff --name-only --staged --diff-filter=ACMR
    git ls-files --others --exclude-standard -- '*.md' || true
  } | awk '/\.md$/ { print }' | sort -u
}

main() {
  local mode="changed"
  if [[ ${1:-} == "--help" || ${1:-} == "-h" ]]; then
    usage
    exit 0
  fi
  if [[ ${1:-} == "--all" ]]; then
    mode="all"
    shift
  fi
  if [[ $# -ne 0 ]]; then
    usage
    exit 2
  fi

  local repo_root
  repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "Run this from inside a Git repository."
  cd "$repo_root"

  log "Running Spotless misc formatting (Markdown/YAML/JSON)"
  ./gradlew --no-daemon spotlessMiscApply

  local -a markdown_files=()
  if [[ $mode == "all" ]]; then
    while IFS= read -r file; do
      markdown_files+=("$file")
    done < <(git ls-files -- '*.md')
  else
    while IFS= read -r file; do
      markdown_files+=("$file")
    done < <(collect_changed_markdown)
  fi

  if (( ${#markdown_files[@]} == 0 )); then
    log "No Markdown changes detected; skipping markdown checks."
    exit 0
  fi

  log "Checking Markdown list entries for manual line wraps"
  python3 tools/scripts/check-markdown-linewraps.py "${markdown_files[@]}"

  log "Checking Markdown links for missing targets"
  python3 tools/scripts/check-markdown-links.py "${markdown_files[@]}"

  log "Docs verification completed"
}

main "$@"


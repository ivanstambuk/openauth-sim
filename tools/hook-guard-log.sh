#!/usr/bin/env bash
set -euo pipefail

log() { echo "[hook-guard-log] $*" >&2; }
die() { log "$*"; exit 1; }

usage() {
  cat >&2 <<'EOF'
Usage: tools/hook-guard-log.sh <verification-command> [args...]

Runs the verification command and appends a hook-guard entry to docs/_current-session.md:
- timestamp
- `git config core.hooksPath` output
- verification command + PASS/FAIL
EOF
}

main() {
  if [[ $# -lt 1 ]]; then
    usage
    exit 2
  fi

  local repo_root session_log timestamp hooks_path cmd_display exit_code
  repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || die "Run this from inside a Git repository."
  session_log="$repo_root/docs/_current-session.md"
  timestamp="$(date -Is)"

  cd "$repo_root"

  hooks_path="$(git config core.hooksPath || true)"
  if [[ -z $hooks_path ]]; then
    hooks_path="<unset>"
  fi

  cmd_display="$(printf '%q ' "$@")"
  cmd_display="${cmd_display% }"

  mkdir -p "$(dirname "$session_log")"
  touch "$session_log"

  {
    printf "\n## Hook guard %s\n\n" "$timestamp"
    printf '%s\n' '`git config core.hooksPath`:'
    printf "%s\n\n" "$hooks_path"
    printf 'Verification: `%s` ' "$cmd_display"
  } >>"$session_log"

  set +e
  "$@"
  exit_code=$?
  set -e

  if [[ $exit_code -eq 0 ]]; then
    printf "(PASS)\n" >>"$session_log"
  else
    printf "(FAIL - exit %s)\n" "$exit_code" >>"$session_log"
  fi

  exit "$exit_code"
}

main "$@"


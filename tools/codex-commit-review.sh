#!/usr/bin/env bash
set -euo pipefail

log() { echo "[codex-commit-review] $*" >&2; }
die() { log "$*"; exit 1; }
require_command() {
  local binary=$1 guidance=$2
  command -v "$binary" >/dev/null 2>&1 || die "$guidance"
}

sanitize_body() {
  local content
  content="$(sed 's/[ \t]*$//' <<<"$1")"
  printf "%s" "$content"
}

validate_subject() {
  local subject=$1
  local types='feat|fix|docs|style|refactor|perf|test|chore|build|ci|revert'
  local cc_regex="^(${types})(\\([a-z0-9._-]+\\))?!?: [^\\s].{0,71}$"
  if [[ ! $subject =~ $cc_regex ]]; then
    die "Codex returned a non-Conventional Commit subject: '$subject'"
  fi
  if [[ $subject =~ \.\s*$ ]]; then
    die "Codex returned a subject ending with a period: '$subject'"
  fi
}

codex_tmp_dir=""

cleanup_tmp_dir() {
  if [[ -n "${codex_tmp_dir:-}" && -d "${codex_tmp_dir}" ]]; then
    rm -rf "${codex_tmp_dir}"
  fi
}
trap cleanup_tmp_dir EXIT

main() {
  require_command python3 "Install python3"
  require_command codex "Install Codex CLI: npm i -g @openai/codex"
  require_command git "Install Git to inspect the staged diff."

  if git diff --staged --quiet; then
    die "No staged changes detected. Stage files before running Codex commit generation."
  fi

  local staged_list docs_list diffstat docs_patch
  staged_list="$(git diff --staged --name-only | sed 's/^/- /')"
  docs_list="$(git diff --staged --name-only -- 'docs/**' | sed 's/^/- /')"
  diffstat="$(git diff --staged --no-color --stat)"
  docs_patch="$(git diff --staged --no-color --unified=0 -- 'docs/**' | sed 's/[[:space:]]\+$//' | head -c 100000)"

  local prompt input payload raw result_json subject body codex_cmd
  read -r -d '' prompt <<'RULES' || true
You are an assistant that writes Conventional Commit messages.
Output only JSON in the form:
{"subject": "single-line subject", "body": "optional multi-line body"}

Rules:
1) Subject must follow Conventional Commits with types: feat, fix, docs, style, refactor, perf, test, chore, build, ci, revert.
2) Subject ≤ 72 characters, imperative mood, no trailing period.
3) If ONLY docs/ staged → use type "docs" and summarise the documentation change.
4) If docs/ AND code staged → subject summarises functional change; body must exist and contain exactly one line: Spec impact: <summary of documentation/spec impact>.
5) If no docs staged → body is optional; omit when not needed.
6) Never include additional text, explanations, or markdown outside the JSON.
7) Be specific in the subject; prefer action-oriented verbs.
RULES

  read -r -d '' input <<PAYLOAD || true
STAGED_FILE_LIST:
${staged_list:-<none>}
---
DOCS_FILE_LIST:
${docs_list:-<none>}
---
DIFFSTAT:
${diffstat:-<none>}
---
DOCS_DIFF_COMPACT:
${docs_patch:-<none>}
PAYLOAD

  codex_tmp_dir="$(mktemp -d)"
  printf "%s\n%s" "$prompt" "$input" >"$codex_tmp_dir/payload.txt"
  payload="$(cat "$codex_tmp_dir/payload.txt")"

  codex_cmd=${CODEX_CMD:-codex exec --model gpt-5-codex --sandbox read-only --skip-git-repo-check --color never}

  log "Generating commit message with Codex via: $codex_cmd"
  raw="$(cd "$codex_tmp_dir" && printf "%s" "$payload" | bash -lc "$codex_cmd --cd \"$codex_tmp_dir\" -- -")" || die "Codex invocation failed."

  result_json="$codex_tmp_dir/codex-result.json"
  if ! CODEX_RAW="$raw" python3 - "$result_json" <<'PY'
import json, os, sys

raw = os.environ["CODEX_RAW"]


def extract_json(text: str) -> dict:
    start = text.find('{')
    while start != -1:
        depth = 0
        in_string = False
        escape = False
        for pos in range(start, len(text)):
            ch = text[pos]
            if escape:
                escape = False
                continue
            if ch == '\\':
                escape = True
                continue
            if ch == '"':
                in_string = not in_string
                continue
            if in_string:
                continue
            if ch == '{':
                depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0:
                    candidate = text[start:pos + 1]
                    try:
                        obj = json.loads(candidate)
                        if isinstance(obj, dict):
                            return obj
                    except json.JSONDecodeError:
                        break
        start = text.find('{', start + 1)
    raise ValueError("JSON object not found in Codex output")


obj = extract_json(raw)
if "subject" not in obj or not isinstance(obj["subject"], str):
    raise ValueError("JSON missing subject")
body = obj.get("body") or ""
if not isinstance(body, str):
    raise ValueError("Body must be a string")
with open(sys.argv[1], "w", encoding="utf-8") as handle:
    json.dump({"subject": obj["subject"], "body": body}, handle)
PY
  then
    die "Codex output did not contain a valid commit JSON payload"
  fi

  subject="$(python3 - "$result_json" <<'PY'
import json, sys
print(json.load(open(sys.argv[1]))["subject"], end="")
PY
)"
  body="$(python3 - "$result_json" <<'PY'
import json, sys
print(json.load(open(sys.argv[1]))["body"], end="")
PY
)"

  subject="$(sanitize_body "$subject")"
  body="$(sanitize_body "$body")"

  if [[ -z $subject ]]; then
    die "Codex returned an empty subject."
  fi

  validate_subject "$subject"

  if [[ -n $body ]]; then
    printf "%s\n\n%s\n" "$subject" "$body"
  else
    printf "%s\n" "$subject"
  fi
}

main "$@"

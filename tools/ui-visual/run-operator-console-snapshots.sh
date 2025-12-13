#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tool_dir="${repo_root}/tools/ui-visual"

port="${UI_VISUAL_PORT:-18080}"
base_url="${UI_VISUAL_BASE_URL:-http://localhost:${port}}"
run_id="$(date -u +"%Y-%m-%dT%H-%M-%SZ")"
out_dir="${UI_VISUAL_OUT_DIR:-${repo_root}/build/ui-snapshots/${run_id}}"
max_runs="${UI_VISUAL_MAX_RUNS:-10}"
snapshots_root="${repo_root}/build/ui-snapshots"
db_path="${out_dir}/credentials.db"
log_path="${out_dir}/rest-api.log"

if [[ ! "${max_runs}" =~ ^[0-9]+$ ]]; then
  echo "[ui-visual] UI_VISUAL_MAX_RUNS must be a non-negative integer (got: ${max_runs}); defaulting to 10" >&2
  max_runs="10"
fi

prune_snapshot_runs() {
  local root="$1"
  local keep="$2"
  local exclude_dir="$3"

  if [[ -z "${keep}" ]]; then
    return 0
  fi
  if [[ ! "${keep}" =~ ^[0-9]+$ ]]; then
    echo "[ui-visual] UI_VISUAL_MAX_RUNS must be a positive integer (got: ${keep}); skipping prune" >&2
    return 0
  fi
  if [[ "${keep}" -le 0 ]]; then
    keep=0
  fi
  if [[ ! -d "${root}" ]]; then
    return 0
  fi

  mapfile -t candidates < <(find "${root}" -mindepth 1 -maxdepth 1 -type d -name '20??-??-??T*' -printf '%f\n' | LC_ALL=C sort)
  local count="${#candidates[@]}"
  if [[ "${count}" -le "${keep}" ]]; then
    return 0
  fi

  local delete_target=$((count - keep))
  echo "[ui-visual] Pruning ${delete_target} old snapshot run(s) from ${root} (keeping ${keep} prior runs)"
  local deleted=0
  for name in "${candidates[@]}"; do
    if [[ "${deleted}" -ge "${delete_target}" ]]; then
      break
    fi
    if [[ -n "${exclude_dir}" ]] && [[ "${name}" == "${exclude_dir}" ]]; then
      continue
    fi
    rm -rf -- "${root:?}/${name}"
    deleted=$((deleted + 1))
  done
}

mkdir -p "${snapshots_root}"

keep_before="${max_runs}"
if [[ "${out_dir}" == "${snapshots_root}/"* ]]; then
  if [[ "${max_runs}" -gt 0 ]]; then
    keep_before=$((max_runs - 1))
  fi
fi

if [[ "${max_runs}" -gt 0 ]]; then
  prune_snapshot_runs "${snapshots_root}" "${keep_before}" "$(basename "${out_dir}")"
fi

mkdir -p "${out_dir}"

if [[ ! -d "${tool_dir}/node_modules" ]]; then
  (cd "${tool_dir}" && npm ci)
fi

(cd "${tool_dir}" && npx playwright install chromium)

cleanup() {
  if [[ -n "${rest_pid:-}" ]] && kill -0 "${rest_pid}" 2>/dev/null; then
    kill -- "-${rest_pid}" 2>/dev/null || true
    wait "${rest_pid}" 2>/dev/null || true
  fi
}

trap cleanup EXIT

cd "${repo_root}"

SERVER_PORT="${port}" OPENAUTH_SIM_PERSISTENCE_DATABASE_PATH="${db_path}" \
  setsid ./gradlew --no-daemon --init-script tools/run-rest-api.init.gradle.kts runRestApi \
  >"${log_path}" 2>&1 &
rest_pid="$!"

deadline=$((SECONDS + 90))
while ((SECONDS < deadline)); do
  if curl -sf "${base_url}/ui/console" >/dev/null; then
    break
  fi
  sleep 1
done

if ! curl -sf "${base_url}/ui/console" >/dev/null; then
  echo "REST API did not become ready at ${base_url} within 90s. Log: ${log_path}" >&2
  exit 1
fi

node "${tool_dir}/capture-operator-console.mjs" --base-url "${base_url}" --out-dir "${out_dir}"

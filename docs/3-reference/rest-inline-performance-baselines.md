# REST Inline Evaluation Performance Baselines

This page records cross-protocol REST performance baselines using the Node load harness at `tools/perf/rest-inline-node-load.js`. Each entry captures throughput and latency percentiles for a fixed duration, so future runs can be compared against the same scenarios.

Harness command:

```bash
node tools/perf/rest-inline-node-load.js \
  --baseUrl http://localhost:8080 \
  --durationSeconds 30 \
  --concurrency 32 \
  --targetTps 500 \
  --maxP95 50 \
  --maxP99 100
```

### Gradle wrappers

For convenience, the root build also exposes two Gradle tasks:

- `restInlineLoadTest` – runs the Node harness against an already-running REST API:

  ```bash
  ./gradlew --no-daemon restInlineLoadTest
  ```

  You can override the harness parameters via Gradle properties, for example:

  ```bash
  ./gradlew --no-daemon restInlineLoadTest \
    -PrestInlineBaseUrl=http://localhost:8080 \
    -PrestInlineDurationSeconds=30 \
    -PrestInlineConcurrency=32 \
    -PrestInlineTargetTps=500 \
    -PrestInlineMaxP95=50 \
    -PrestInlineMaxP99=100
  ```

- `restInlinePerfSuite` – one-shot suite that:
  1. Starts the REST API via `runRestApi` (using `tools/run-rest-api.init.gradle.kts`).
  2. Waits until the base URL responds.
  3. Executes the Node harness with the configured thresholds and baseline file.
  4. Shuts the REST API process down.

  ```bash
  ./gradlew --no-daemon restInlinePerfSuite
  ```

  The same Gradle properties (`restInlineBaseUrl`, `restInlineDurationSeconds`, `restInlineConcurrency`, `restInlineTargetTps`, `restInlineMaxP95`, `restInlineMaxP99`, `restInlineBaselineFile`, `restInlineBaselineTolerance`) can be used to tune this task when needed.

When adding new baselines:
- Keep the command (duration, concurrency, thresholds) consistent unless you explicitly note a change.
- Record the environment (OS, CPU model, Java + Node versions) so differences between hosts are explainable.
- Append new rows instead of editing existing entries so trends stay visible.

## Baseline – 2025‑11‑18 (operator workstation)

Environment:
- Host: `ASUSMAIN` (operator workstation)
- OS / CPU / Java / Node: _fill in when convenient (e.g. output of `uname -a`, `java -version`, `node -v`)_.

Run parameters:
- Duration: 30 seconds per scenario
- Concurrency: 32 in-flight requests
- Target throughput: 500 requests/second
- Latency thresholds: p95 ≤ 50 ms, p99 ≤ 100 ms

Results:

| Scenario ID          | Description                               | Endpoint                                      | Throughput TPS | p95 ms | p99 ms | Max ms |
|----------------------|-------------------------------------------|-----------------------------------------------|----------------|--------|--------|--------|
| `hotp_inline`        | HOTP inline evaluation                   | `POST /api/v1/hotp/evaluate/inline`           | 3452.92        | 17.54  | 28.00  | 80.11 |
| `totp_inline`        | TOTP inline evaluation                   | `POST /api/v1/totp/evaluate/inline`           | 6638.75        | 8.91   | 14.07  | 42.40 |
| `ocra_evaluate`      | OCRA inline evaluation                   | `POST /api/v1/ocra/evaluate`                  | 4059.33        | 14.69  | 23.03  | 65.49 |
| `emv_cap_inline`     | EMV/CAP inline evaluation                | `POST /api/v1/emv/cap/evaluate`               | 5484.58        | 10.98  | 17.54  | 55.83 |
| `webauthn_inline`    | WebAuthn (FIDO2) inline evaluation       | `POST /api/v1/webauthn/evaluate/inline`       | 2369.05        | 25.14  | 35.35  | 82.81 |
| `eudiw_wallet_simulate` | EUDIW OpenID4VP wallet simulation     | `POST /api/v1/eudiw/openid4vp/wallet/simulate`| 3930.84        | 15.37  | 25.84  | 101.51 |
| `eudiw_validate`     | EUDIW OpenID4VP presentation validation  | `POST /api/v1/eudiw/openid4vp/validate`       | 3751.81        | 15.89  | 25.22  | 63.57 |

All scenarios met the configured thresholds (`targetTps`, `maxP95`, `maxP99`) with zero failures in this run.
